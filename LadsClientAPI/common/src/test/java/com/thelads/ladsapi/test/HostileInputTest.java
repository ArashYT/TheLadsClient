package com.thelads.ladsapi.test;

import com.thelads.ladsapi.api.LadsPlayer;
import com.thelads.ladsapi.config.LadsConfig;
import com.thelads.ladsapi.rules.LadsRules;
import com.thelads.ladsapi.session.LadsSession;
import com.thelads.ladsapi.session.SessionManager;
import com.thelads.ladsapi.util.VersionUtil;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/** Everything a hostile client can put in a lads:api message: PROTOCOL.md says malformed input is dropped, never a crash or kick. */
public class HostileInputTest {

    private static final class Fixture {
        final FakePlatform platform;
        final SessionManager manager;
        final FakePlayerHandle player = new FakePlayerHandle(UUID.randomUUID(), "Mallory", "world");

        Fixture(Path dir, LadsConfig config) {
            platform = new FakePlatform("paper", false, dir);
            manager = new SessionManager(platform, config, new LadsRules());
            platform.addPlayer(player);
            manager.join(player);
            platform.advanceTicks(1);
            player.getSentPayloads().clear();
        }

        void send(String json) {
            manager.receive(player, json.getBytes(StandardCharsets.UTF_8));
        }

        LadsSession session() {
            return manager.getSessions().get(player.uuid());
        }
    }

    /** ladsapi:A/17, B/10: valid JSON with the wrong field types used to throw out of receive(). */
    @Test
    public void wrongFieldTypesAreDroppedWithoutAnException(@TempDir Path dir) {
        String[] hostile = {
                "{\"t\":null}", "{\"t\":{}}", "{\"t\":[]}", "{\"t\":5}", "{\"t\":true}",
                "{\"t\":\"hello\"}", "{\"t\":\"hello\",\"v\":null}", "{\"t\":\"hello\",\"v\":\"x\"}", "{\"t\":\"hello\",\"v\":\"1\"}",
                "{\"t\":\"hello\",\"v\":{}}", "{\"t\":\"hello\",\"v\":[1]}", "{\"t\":\"hello\",\"v\":1.5}", "{\"t\":\"hello\",\"v\":1e400}",
                "{\"t\":\"hello\",\"v\":NaN}", "{\"t\":\"hello\",\"v\":99999999999}", "{\"t\":\"hello\",\"v\":true}",
                "[]", "\"hello\"", "7", "null", "{", "{\"t\":\"hel", "\u0000\u0001\u0002"
        };
        for (int i = 0; i < hostile.length; i++) {
            // a fresh player each time: the 8 messages per second limit runs before parsing and would hide a bug
            Fixture f = new Fixture(dir, new LadsConfig());
            String payload = hostile[i];
            assertDoesNotThrow(() -> f.send(payload), payload);
            assertFalse(f.session().isLads(), "not a valid hello: " + payload);
            assertTrue(f.player.getSentPayloads().isEmpty(), payload);
            assertFalse(f.player.isKicked(), payload);
        }
    }

    @Test
    public void ackWithWrongTypesIsIgnoredInDebugMode(@TempDir Path dir) {
        LadsConfig config = new LadsConfig();
        config.setDebug(true);
        Fixture f = new Fixture(dir, config);
        f.send("{\"t\":\"hello\",\"v\":1,\"lads\":\"1.11.1\",\"mc\":\"26.3\"}");
        assertTrue(f.session().isLads());

        for (String ack : new String[] {"{\"t\":\"ack\",\"rev\":\"x\",\"ignored\":{},\"refused\":5}", "{\"t\":\"ack\",\"rev\":null}",
                "{\"t\":\"ack\",\"rev\":1.5}", "{\"t\":\"ack\",\"rev\":{}}"}) {
            assertDoesNotThrow(() -> f.send(ack), ack);
        }
    }

    /** ladsapi:A/15, B/9: the version strings end up in %ladsapi_version%, /ladsapi inspect, the kick text and consumer plugins. */
    @Test
    public void hostileVersionTextNeverReachesConsumers(@TempDir Path dir) {
        Fixture f = new Fixture(dir, new LadsConfig());
        String longText = "9".repeat(30_000);
        f.send("{\"t\":\"hello\",\"v\":1,\"lads\":\"&k&lFREE RANKS <click:run_command:/spawn>\",\"mc\":\"" + longText + "\"}");

        LadsSession s = f.session();
        assertTrue(s.isLads(), "a valid v:1 hello is still a Lads client, only its text is replaced");
        assertEquals("unknown", s.getLadsVersion());
        assertEquals("unknown", s.getMcVersion());
        LadsPlayer api = s.getLadsPlayer();
        assertEquals("unknown", api.clientVersion());
        assertEquals("unknown", api.minecraftVersion());
    }

    @Test
    public void versionTextIsCleanedByOneRule() {
        for (String ok : new String[] {"1.11.1", "26.3", "1.20.1", "1.8.9", "dev", "1.12.0-SNAPSHOT", "1.12.0+build_7", "25w14a"}) {
            assertEquals(ok, VersionUtil.clean(ok));
        }
        for (String bad : new String[] {null, "", " ", "1.0 beta", "§4owner", "&4owner", "<red>x", "1.0\n", "a".repeat(33), "1.0\u0000"}) {
            assertEquals("unknown", VersionUtil.clean(bad), String.valueOf(bad));
        }
    }

    @Test
    public void minClientVersionKickShowsOnlyCleanText(@TempDir Path dir) {
        LadsConfig config = new LadsConfig();
        config.setMinClientVersion("1.9.13");
        Fixture f = new Fixture(dir, config);
        f.send("{\"t\":\"hello\",\"v\":1,\"lads\":\"§c§lHACKED\",\"mc\":\"26.3\"}");
        assertTrue(f.player.isKicked());
        assertFalse(f.player.getKickMessage().contains("§"));
        assertTrue(f.player.getKickMessage().contains("unknown"));
    }

    /** PROTOCOL.md section 5: a `v` the server does not know means non-Lads; the next valid hello still works. */
    @Test
    public void unknownProtocolVersionIsTreatedAsNonLads(@TempDir Path dir) {
        Fixture f = new Fixture(dir, new LadsConfig());
        for (String v : new String[] {"2", "3", "0", "-1", "1000000"}) {
            f.send("{\"t\":\"hello\",\"v\":" + v + ",\"lads\":\"9.9.9\",\"mc\":\"26.3\"}");
            assertFalse(f.session().isLads(), "v=" + v);
        }
        assertTrue(f.player.getSentPayloads().isEmpty(), "nothing is answered to a protocol we do not speak");

        f.send("{\"t\":\"hello\",\"v\":1,\"lads\":\"1.11.1\",\"mc\":\"26.3\"}");
        assertTrue(f.session().isLads());
        assertEquals("1.11.1", f.session().getLadsVersion());
    }

    @Test
    public void payloadsAboveTheLimitAreDroppedAtTheLimitTheyAreRead(@TempDir Path dir) {
        Fixture f = new Fixture(dir, new LadsConfig());
        String head = "{\"t\":\"hello\",\"v\":1,\"lads\":\"1.11.1\",\"pad\":\"";
        String tail = "\"}";
        int padLen = SessionManager.MAX_IN - head.length() - tail.length();

        f.send(head + "x".repeat(padLen + 1) + tail); // 32,001 bytes
        assertFalse(f.session().isLads());

        f.send(head + "x".repeat(padLen) + tail); // exactly 32,000 bytes
        assertTrue(f.session().isLads());
    }
    @Test
    public void policyOver524288BytesIsDropped(@TempDir Path dir) {
        Fixture f = new Fixture(dir, new LadsConfig());
        f.send("{\"t\":\"hello\",\"v\":1,\"lads\":\"1.11.1\",\"mc\":\"26.3\"}");
        f.player.getSentPayloads().clear();

        f.manager.getConfig().setNotice("X".repeat(600_000));
        f.session().sendPolicy();

        assertTrue(f.player.getSentPayloads().isEmpty());
    }
}
