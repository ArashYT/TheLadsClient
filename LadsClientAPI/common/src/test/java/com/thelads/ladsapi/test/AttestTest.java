package com.thelads.ladsapi.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.ladsapi.api.LadsPlayer;
import com.thelads.ladsapi.config.LadsConfig;
import com.thelads.ladsapi.rules.LadsRules;
import com.thelads.ladsapi.session.AttestVerifier;
import com.thelads.ladsapi.session.AttestVerifier.Result;
import com.thelads.ladsapi.session.LadsSession;
import com.thelads.ladsapi.session.SessionManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The anti-spoof handshake (PROTOCOL.md section 6): building and checking the relay's token, and what the session makes of it. */
public class AttestTest {
    private static final UUID ALICE = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID BOB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final String NONCE = "0123456789abcdef0123456789abcdef", SID = "fedcba9876543210fedcba9876543210";
    private static final long NOW = 1_800_000_000L;
    // The relay's own test (cosmetics-server/test_attest.py) signs this token with the same key: both sides agree on every byte.
    private static final String VECTOR_PUBLIC = "A6EHv/POEL4dcN0Y50vAmWfk1jCbpQ1fHdyGZBJVMbg=";
    private static final String VECTOR_TOKEN =
            "v1.2000000120.mneOUEZocqYGZghZLGLXfCSjdJrGOWBbiyGgAsAzf0yLUG9_kV_vMhbZjYRPZIUC0gif9MuIbT7yKFkoGZ5lCA";

    private static KeyPair relay, stranger;
    private static String relayPublic;

    @BeforeAll
    static void keys() throws Exception {
        relay = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        stranger = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] x509 = relay.getPublic().getEncoded();
        relayPublic = Base64.getEncoder().encodeToString(Arrays.copyOfRange(x509, x509.length - 32, x509.length));
    }

    /** What the relay does (cosmetics.py attest_token). */
    private static String token(KeyPair key, UUID uuid, String nonce, String sid, long exp) {
        try {
            Signature sign = Signature.getInstance("Ed25519");
            sign.initSign(key.getPrivate());
            sign.update(("lads-attest-v1|" + uuid.toString().replace("-", "") + "|" + nonce + "|" + sid + "|" + exp).getBytes(StandardCharsets.UTF_8));
            return "v1." + exp + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign.sign());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static AttestVerifier verifier() {
        return new AttestVerifier(List.of(relayPublic));
    }

    @Test
    public void relayVectorChecksOut() {
        AttestVerifier python = new AttestVerifier(List.of(VECTOR_PUBLIC));
        assertEquals(Result.OK, python.check(VECTOR_TOKEN, ALICE, NONCE, SID, 2_000_000_000L));
        assertEquals(Result.BAD_SIGNATURE, verifier().check(VECTOR_TOKEN, ALICE, NONCE, SID, 2_000_000_000L));
    }

    @Test
    public void goodToken() {
        assertEquals(Result.OK, verifier().check(token(relay, ALICE, NONCE, SID, NOW + 120), ALICE, NONCE, SID, NOW));
    }

    @Test
    public void expiredToken() {
        String token = token(relay, ALICE, NONCE, SID, NOW);
        assertEquals(Result.OK, verifier().check(token, ALICE, NONCE, SID, NOW + AttestVerifier.SKEW)); // clocks differ a little
        assertEquals(Result.EXPIRED, verifier().check(token, ALICE, NONCE, SID, NOW + AttestVerifier.SKEW + 1));
        assertEquals(Result.EXPIRED, verifier().check(token(relay, ALICE, NONCE, SID, NOW + 86_400), ALICE, NONCE, SID, NOW),
                "an expiry a day ahead is not the relay's two minutes");
    }

    @Test
    public void wrongNonceUuidOrServer() {
        String token = token(relay, ALICE, NONCE, SID, NOW + 120);
        assertEquals(Result.BAD_SIGNATURE, verifier().check(token, ALICE, SID, SID, NOW), "another hello's nonce");
        assertEquals(Result.BAD_SIGNATURE, verifier().check(token, BOB, NONCE, SID, NOW), "another player's token");
        assertEquals(Result.BAD_SIGNATURE, verifier().check(token, ALICE, NONCE, NONCE, NOW), "a token made for another server");
    }

    @Test
    public void wrongKeyChangedExpiryAndGarbage() {
        AttestVerifier verifier = verifier();
        assertEquals(Result.BAD_SIGNATURE, verifier.check(token(stranger, ALICE, NONCE, SID, NOW + 120), ALICE, NONCE, SID, NOW));
        String token = token(relay, ALICE, NONCE, SID, NOW);
        assertEquals(Result.BAD_SIGNATURE, verifier.check(token.replace("." + NOW + ".", "." + (NOW + 100) + "."), ALICE, NONCE, SID, NOW + 100),
                "a later expiry written into an old token");
        for (String bad : new String[] {null, "", "v1", "v1..", "v2." + token.substring(3), "v1.abc.def", "v1.123.!!!!", "v1.123.AAAA",
                "v1.-5." + token.substring(token.lastIndexOf('.') + 1), token + ".x", "x".repeat(500)}) {
            assertEquals(Result.MALFORMED, verifier.check(bad, ALICE, NONCE, SID, NOW), String.valueOf(bad));
        }
        assertEquals(Result.NO_KEY, new AttestVerifier(List.of("not a key", "AAAA")).check(token, ALICE, NONCE, SID, NOW));
        assertFalse(new AttestVerifier(List.of()).ready());
        assertTrue(new AttestVerifier(AttestVerifier.RELAY_KEYS).ready(), "the built-in relay key loads");
    }

    // ---- the session ----

    private static final class Server {
        final FakePlatform platform;
        final LadsConfig config = new LadsConfig();
        final SessionManager manager;
        final List<UUID> verifiedEvents = new ArrayList<>();

        Server(Path dir) {
            platform = new FakePlatform("paper", false, dir);
            config.setRelayPublicKeys(List.of(relayPublic));
            manager = new SessionManager(platform, config, new LadsRules());
            manager.setClock(() -> NOW);
            manager.addVerifiedListener((LadsPlayer p) -> verifiedEvents.add(p.uuid()));
        }

        FakePlayerHandle join(UUID uuid, String name) {
            FakePlayerHandle player = new FakePlayerHandle(uuid, name, "world");
            platform.addPlayer(player);
            manager.join(player);
            platform.advanceTicks(1);
            return player;
        }

        JsonObject hello(FakePlayerHandle player) {
            JsonObject hello = JsonParser.parseString(player.getSentStrings().get(0)).getAsJsonObject();
            assertEquals("hello", hello.get("t").getAsString());
            return hello;
        }

        void reply(FakePlayerHandle player, String token) {
            String json = "{\"t\":\"hello\",\"v\":1,\"lads\":\"1.13.9\",\"mc\":\"26.3\"" + (token == null ? "" : ",\"token\":\"" + token + "\"") + "}";
            manager.receive(player, json.getBytes(StandardCharsets.UTF_8));
        }

        /** The token the relay would sign for this player's hello. */
        String relayToken(FakePlayerHandle player, UUID provenUuid) {
            JsonObject hello = hello(player);
            return token(relay, provenUuid, hello.get("nonce").getAsString(), hello.get("sid").getAsString(), NOW + 120);
        }
    }

    @Test
    public void helloCarriesNonceAndServerId(@TempDir Path dir) {
        Server server = new Server(dir);
        JsonObject a = server.hello(server.join(ALICE, "Alice")), b = server.hello(server.join(BOB, "Bob"));
        assertTrue(a.get("nonce").getAsString().matches("[0-9a-f]{32}"));
        assertTrue(a.get("sid").getAsString().matches("[0-9a-f]{32}"));
        assertNotEquals(a.get("nonce").getAsString(), b.get("nonce").getAsString(), "one nonce per session");
        assertEquals(a.get("sid").getAsString(), b.get("sid").getAsString());
        assertEquals(1, a.get("v").getAsInt()); // what an old client reads is still there
        assertTrue(a.has("server") && a.has("caps"));
    }

    @Test
    public void realClientIsVerifiedBySecondHello(@TempDir Path dir) {
        Server server = new Server(dir);
        FakePlayerHandle alice = server.join(ALICE, "Alice");
        server.reply(alice, null); // the first hello cannot wait for the relay
        LadsSession session = server.manager.getSessions().get(ALICE);
        assertTrue(session.isLads());
        assertFalse(session.isVerified());
        assertFalse(server.manager.verified(ALICE));
        int sent = alice.getSentPayloads().size();

        server.reply(alice, server.relayToken(alice, ALICE));
        assertTrue(session.isVerified());
        assertTrue(server.manager.verified(ALICE));
        assertTrue(server.manager.player(ALICE).orElseThrow().verified());
        assertEquals(List.of(ALICE), server.verifiedEvents);
        assertEquals(sent, alice.getSentPayloads().size(), "the second hello starts no second policy or presence");

        server.reply(alice, server.relayToken(alice, ALICE));
        assertEquals(List.of(ALICE), server.verifiedEvents, "verified once");
    }

    @Test
    public void tokenInTheFirstHelloCountsToo(@TempDir Path dir) {
        Server server = new Server(dir);
        FakePlayerHandle alice = server.join(ALICE, "Alice");
        server.reply(alice, server.relayToken(alice, ALICE));
        assertTrue(server.manager.verified(ALICE));
    }

    @Test
    public void helloWithoutTokenStaysUnverified(@TempDir Path dir) {
        Server server = new Server(dir);
        FakePlayerHandle old = server.join(ALICE, "OldClient"); // a 1.13.8 client, or any program that imitates the hello
        server.reply(old, null);
        server.reply(old, null);
        assertTrue(server.manager.getSessions().get(ALICE).isLads(), "it still joins and gets its policy");
        assertFalse(server.manager.verified(ALICE));
        assertFalse(server.manager.verified(BOB), "a player who never said hello");
        assertTrue(server.verifiedEvents.isEmpty());
    }

    @Test
    public void perksNeedTheTokenUnlessTheServerSaysOtherwise(@TempDir Path dir) {
        Server server = new Server(dir);
        FakePlayerHandle old = server.join(ALICE, "OldClient"); // answers the hello, cannot prove itself
        server.reply(old, null);
        FakePlayerHandle real = server.join(BOB, "Bob");
        server.reply(real, null);
        server.reply(real, server.relayToken(real, BOB));
        UUID vanilla = UUID.randomUUID();
        assertFalse(server.manager.perks(ALICE));
        assertTrue(server.manager.perks(BOB));
        assertFalse(server.manager.perks(vanilla), "no hello, no perks");

        server.config.setPerksNeedVerified(false); // the time before every player has a client that can prove itself
        assertTrue(server.manager.perks(ALICE));
        assertTrue(server.manager.perks(BOB));
        assertFalse(server.manager.perks(vanilla));

        server.config.setPerksNeedVerified(true);
        server.config.setVerifyClient(false); // a server that does not check gives perks as before 1.2.0
        assertTrue(server.manager.perks(ALICE));
        assertFalse(server.manager.perks(vanilla));
    }

    @Test
    public void foreignTokensStayUnverified(@TempDir Path dir) {
        Server server = new Server(dir);
        FakePlayerHandle alice = server.join(ALICE, "Alice"), bob = server.join(BOB, "Bob");
        server.reply(alice, null);
        server.reply(bob, null);
        JsonObject bobHello = server.hello(bob);
        String sid = bobHello.get("sid").getAsString(), bobNonce = bobHello.get("nonce").getAsString();

        server.reply(bob, server.relayToken(alice, ALICE)); // Alice's token, replayed by Bob
        server.reply(bob, token(relay, ALICE, bobNonce, sid, NOW + 120)); // the relay proved Alice's account, not Bob's
        server.reply(bob, token(relay, BOB, bobNonce, SID, NOW + 120)); // made for another server
        server.reply(bob, token(stranger, BOB, bobNonce, sid, NOW + 120)); // not the relay's key
        server.reply(bob, token(relay, BOB, bobNonce, sid, NOW - 3600)); // an hour old
        assertFalse(server.manager.verified(BOB));

        server.reply(bob, token(relay, BOB, bobNonce, sid, NOW + 120));
        assertTrue(server.manager.verified(BOB));
        assertFalse(server.manager.verified(ALICE));
    }

    @Test
    public void onlyTheFirstFewTokensAreChecked(@TempDir Path dir) {
        Server server = new Server(dir);
        FakePlayerHandle alice = server.join(ALICE, "Alice");
        server.reply(alice, null);
        String bad = token(stranger, ALICE, NONCE, SID, NOW + 120);
        for (int i = 0; i < 8; i++) {
            server.platform.advanceTicks(20); // stay under the message rate limit
            server.reply(alice, bad);
        }
        server.platform.advanceTicks(20);
        server.reply(alice, server.relayToken(alice, ALICE));
        assertFalse(server.manager.verified(ALICE));
    }

    @Test
    public void newSessionNewNonce(@TempDir Path dir) {
        Server server = new Server(dir);
        FakePlayerHandle first = server.join(ALICE, "Alice");
        String oldToken = server.relayToken(first, ALICE);
        server.manager.quit(ALICE);
        FakePlayerHandle second = server.join(ALICE, "Alice");
        server.reply(second, oldToken); // the token of the last visit
        assertFalse(server.manager.verified(ALICE));
    }

    @Test
    public void requireLadsClientNeedsTheToken(@TempDir Path dir) {
        Server server = new Server(dir);
        server.config.setRequireLadsClient(true);
        FakePlayerHandle real = server.join(ALICE, "Real"), faker = server.join(BOB, "Faker");
        FakePlayerHandle vanilla = server.join(UUID.randomUUID(), "Vanilla");
        server.reply(real, null);
        server.reply(real, server.relayToken(real, ALICE));
        server.reply(faker, null);

        server.platform.advanceTicks(100);
        assertEquals(server.config.getRequireLadsClientMessage(), vanilla.getKickMessage());
        assertFalse(faker.isKicked(), "the relay may take a few seconds");
        server.platform.advanceTicks(server.config.getVerifyTimeout() * 20 - 100);
        assertEquals(server.config.getRequireVerifiedMessage(), faker.getKickMessage());
        assertFalse(real.isKicked());
    }

    @Test
    public void verifyClientOffIsTheOldProtocol(@TempDir Path dir) {
        Server server = new Server(dir);
        server.config.setVerifyClient(false);
        server.config.setRequireLadsClient(true);
        FakePlayerHandle alice = server.join(ALICE, "Alice");
        JsonObject hello = server.hello(alice);
        assertFalse(hello.has("nonce") || hello.has("sid"));
        server.reply(alice, token(relay, ALICE, NONCE, SID, NOW + 120));
        assertFalse(server.manager.verified(ALICE));
        server.platform.advanceTicks(1000);
        assertFalse(alice.isKicked(), "without verification the hello is all there is");
    }

    @Test
    public void noUsableKeyAsksForNoProof(@TempDir Path dir) {
        Server server = new Server(dir);
        server.config.setRelayPublicKeys(List.of("garbage"));
        assertNull(server.manager.attest());
        assertFalse(server.hello(server.join(ALICE, "Alice")).has("nonce"));
    }
}
