package com.thelads.core.client.social;

import com.google.gson.JsonObject;
import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.social.modsync.ModSyncEntry;
import com.thelads.core.client.social.modsync.ModSyncFlow;
import com.thelads.core.client.social.modsync.ModSyncHash;
import com.thelads.core.client.social.modsync.ModSyncList;
import com.thelads.core.client.social.p2p.P2PSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ModSync joined to the sessions: a host and a guest SocialState over a real P2P link on this computer. The host publishes its
 * modsHash and serves its jar on stream 2; the guest asks the trust question, fetches, asks for the restart and quits instead of
 * connecting; the host invites the guest again once it is back, and a game restarted for that host accepts that invite by itself.
 */
@Timeout(60)
class ModSyncWiringTest {
    private static final UUID HOST = UUID.fromString("11111111-2222-3333-4444-555555555555"),
            GUEST = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa");
    private static final SocialBridge.GameInfo FABRIC = new SocialBridge.GameInfo("1.21.11", "fabric", "plain", "", "", "", "1.13.9");

    @TempDir
    Path dir;

    @AfterEach
    void reset() {
        HostSession.skipLoopbackForTest = false;
        HypixelSafetyManager.setP2PSession(null, null);
        System.clearProperty(ModSyncFlow.HOST_PROPERTY);
    }

    private static final class Bridge implements SocialBridge {
        final Path gameDir;
        final CompletableFuture<Void> quit = new CompletableFuture<>(), connected = new CompletableFuture<>();
        final List<String> toasts = new CopyOnWriteArrayList<>();

        Bridge(Path gameDir) {
            this.gameDir = gameDir;
        }

        @Override public Path gameDir() { return gameDir; }
        @Override public GameInfo gameInfo() { return FABRIC; }
        @Override public String worldId() { return "w"; }
        @Override public String worldName() { return "World"; }
        @Override public void quitGame() { quit.complete(null); }
        @Override public void connectToLocal(int port, String displayName) { connected.complete(null); }
        @Override public void toast(String title, String body, String acceptAction, String denyAction) { toasts.add(title + ": " + body); }
    }

    private static void await(String what, BooleanSupplier done) throws InterruptedException {
        for (int i = 0; i < 400 && !done.getAsBoolean(); i++) Thread.sleep(25);
        assertTrue(done.getAsBoolean(), what);
    }

    private static boolean sent(List<JsonObject> messages, String type, String key, String value) {
        for (JsonObject m : messages) {
            if (type.equals(m.get("t").getAsString()) && m.has(key) && value.equals(m.get(key).getAsString())) return true;
        }
        return false;
    }

    @Test
    void guestFetchesTheHostsJarAndRestartsThenTheHostInvitesItBack() throws Exception {
        Path hostDir = dir.resolve("host"), guestDir = dir.resolve("guest");
        Files.createDirectories(hostDir.resolve("mods"));
        Files.createDirectories(guestDir);
        byte[] data = new byte[300_000];
        new Random(7).nextBytes(data);
        Path jar = hostDir.resolve("mods/friend-mod.jar");
        Files.write(jar, data);
        ModSyncEntry mod = new ModSyncEntry("friendmod", "Friend Mod", "1.0", "friend-mod.jar", data.length, "", ModSyncHash.sha512(jar), "", "");
        Files.createDirectories(hostDir.resolve(ModSyncFlow.LIST_FILE).getParent());
        Files.writeString(hostDir.resolve(ModSyncFlow.LIST_FILE), ModSyncList.toJson(List.of(mod)));

        Bridge hostBridge = new Bridge(hostDir), guestBridge = new Bridge(guestDir);
        List<JsonObject> hostSent = new CopyOnWriteArrayList<>();
        SocialState host = new SocialState(() -> hostBridge, () -> null, null), guest = new SocialState(() -> guestBridge, () -> null, null);
        host.setClientSender(hostSent::add);
        guest.setClientSender(m -> {});

        String hash = host.gameInfo().modsHash();
        assertEquals(ModSyncList.hash(List.of(mod)), hash, "the host's presence names its jars");
        assertEquals("", guest.gameInfo().modsHash());
        SocialBridge.GameInfo hostGame = new SocialBridge.GameInfo("1.21.11", "fabric", "plain", "", "", hash, "1.13.9");
        guest.addFriendLocally(new FriendEntry(HOST, "Hosty", null,
                new Presence("hosting", hostGame, null, new Presence.WorldAd("w", "World", "invite", 8, 0)), 0));
        host.addFriendLocally(new FriendEntry(GUEST, "Guesty", null, new Presence("menu", FABRIC, null, null), 0));
        assertFalse(guest.needsModSync(HOST), "no list from the launcher: nothing would start the game again, so it never syncs");
        Files.createDirectories(guestDir.resolve(ModSyncFlow.LIST_FILE).getParent());
        Files.writeString(guestDir.resolve(ModSyncFlow.LIST_FILE), ModSyncList.toJson(List.of()));
        assertTrue(guest.needsModSync(HOST));
        assertFalse(host.needsModSync(GUEST), "a friend without a modsHash is never asked on stream 2");

        HostSession.skipLoopbackForTest = true;
        HostOptions options = new HostOptions();
        options.scope = HostOptions.Scope.INVITE;
        host.hostSession().initForTest(options, 25565);
        host.hostSession().accept(GUEST);
        P2PSession.IceConfig cfg = new P2PSession.IceConfig(List.of(), List.of(), false);
        String sid = "modsync-wiring-" + System.nanoTime();
        host.hostSession().handleP2PStart(sid, GUEST, cfg, data1 -> guest.guestSession().onSignal(data1));
        guest.guestSession().start(sid, HOST, "Hosty", "w", cfg, data1 -> host.hostSession().onSignal(sid, data1));

        await("the trust question", () -> guest.trustQuestion() != null);
        SocialState.TrustQuestion question = guest.trustQuestion();
        assertEquals("Trust Hosty's mods?", question.title());
        assertTrue(question.message().startsWith("1 mod file (292 KB) in Hosty's world is not on Modrinth or CurseForge"), question.message());
        assertEquals("Hosty", guest.connectingTo(), "still on the connecting screen");
        assertFalse(guestBridge.quit.isDone());
        assertFalse(Files.exists(guestDir.resolve(ModSyncFlow.FOLDER).resolve(HOST.toString()).resolve(mod.localName())), "nothing from the host before the answer");
        question.answer().complete(true);

        guestBridge.quit.get(20, TimeUnit.SECONDS);
        assertFalse(guestBridge.connected.isDone(), "the game restarts instead of connecting without the mods");
        assertTrue(ModSyncHash.matches(guestDir.resolve(ModSyncFlow.FOLDER).resolve(HOST.toString()).resolve(mod.localName()), mod));
        assertTrue(Files.readString(guestDir.resolve(ModSyncFlow.RELAUNCH_FILE)).contains(HOST.toString()));
        assertFalse(Files.exists(guestDir.resolve("mods")), "the guest's own mods folder is never written");
        assertFalse(guest.guestSession().isActive());
        assertEquals(List.of(), guestBridge.toasts);

        // The guest's old game goes offline, the restarted one comes online: only then the host sends the invite again
        JsonObject offline = SocialProtocol.msg(SocialProtocol.PRESENCE, "uuid", GUEST.toString(), "state", "offline");
        JsonObject online = SocialProtocol.msg(SocialProtocol.PRESENCE, "uuid", GUEST.toString(), "state", "menu");
        host.onMessage(online);
        assertFalse(sent(hostSent, SocialProtocol.INVITE, "to", GUEST.toString()), "online without having been away is the old game");
        await("the new invite", () -> {
            host.onMessage(offline);
            host.onMessage(online);
            return sent(hostSent, SocialProtocol.INVITE, "to", GUEST.toString());
        });
        hostSent.clear();
        host.onMessage(offline);
        host.onMessage(online);
        assertFalse(sent(hostSent, SocialProtocol.INVITE, "to", GUEST.toString()), "once");
        assertTrue(host.hostSession().acceptedUuids().contains(GUEST));
    }

    @Test
    void aSyncThatFailsJoinsWithoutTheHostsMods() throws Exception {
        Path hostDir = dir.resolve("host"), guestDir = dir.resolve("guest");
        Files.createDirectories(hostDir.resolve("mods"));
        Path jar = hostDir.resolve("mods/friend-mod.jar");
        Files.write(jar, new byte[5000]);
        ModSyncEntry mod = new ModSyncEntry("friendmod", "Friend Mod", "1.0", "friend-mod.jar", 5000, "", ModSyncHash.sha512(jar), "", "");
        for (Path game : List.of(hostDir, guestDir)) {
            Files.createDirectories(game.resolve(ModSyncFlow.LIST_FILE).getParent());
            Files.writeString(game.resolve(ModSyncFlow.LIST_FILE), ModSyncList.toJson(game == hostDir ? List.of(mod) : List.of()));
        }
        Bridge hostBridge = new Bridge(hostDir), guestBridge = new Bridge(guestDir);
        SocialState host = new SocialState(() -> hostBridge, () -> null, null), guest = new SocialState(() -> guestBridge, () -> null, null);
        host.setClientSender(m -> {});
        guest.setClientSender(m -> {});
        SocialBridge.GameInfo hostGame = new SocialBridge.GameInfo("1.21.11", "fabric", "plain", "", "", host.gameInfo().modsHash(), "1.13.9");
        guest.addFriendLocally(new FriendEntry(HOST, "Hosty", null,
                new Presence("hosting", hostGame, null, new Presence.WorldAd("w", "World", "friends", 8, 0)), 0));
        Files.write(jar, new byte[5000 + 1]); // the host's jar changed after the launcher listed it: the host refuses to send it

        HostSession.skipLoopbackForTest = true;
        host.hostSession().initForTest(new HostOptions(), 25565);
        host.hostSession().accept(GUEST);
        P2PSession.IceConfig cfg = new P2PSession.IceConfig(List.of(), List.of(), false);
        String sid = "modsync-fail-" + System.nanoTime();
        host.hostSession().handleP2PStart(sid, GUEST, cfg, data -> guest.guestSession().onSignal(data));
        guest.guestSession().start(sid, HOST, "Hosty", "w", cfg, data -> host.hostSession().onSignal(sid, data));

        await("the trust question", () -> guest.trustQuestion() != null);
        guest.trustQuestion().answer().complete(true);
        guestBridge.connected.get(20, TimeUnit.SECONDS);
        assertFalse(guestBridge.quit.isDone());
        assertEquals(List.of("Friend's world: Could not get Hosty's mods: joining without them"), guestBridge.toasts);
        assertFalse(Files.exists(guestDir.resolve(ModSyncFlow.RELAUNCH_FILE)), "no restart is left behind");
        assertTrue(guest.guestSession().isActive());
        guest.guestSession().close("test over");
    }

    @Test
    void aGameRestartedForAHostAcceptsThatHostsNextInviteOnce() {
        System.setProperty(ModSyncFlow.HOST_PROPERTY, HOST.toString());
        Bridge bridge = new Bridge(dir);
        List<JsonObject> sentMessages = new CopyOnWriteArrayList<>();
        SocialState guest = new SocialState(() -> bridge, () -> null, null);
        guest.setClientSender(sentMessages::add);
        JsonObject world = new JsonObject();
        world.addProperty("name", "World");

        guest.onMessage(SocialProtocol.msg(SocialProtocol.INVITE, "inviteId", "other1", "from", GUEST.toString(), "world", world));
        assertFalse(sent(sentMessages, SocialProtocol.INVITE_ACCEPT, "inviteId", "other1"), "another friend's invite still asks");
        assertEquals(1, bridge.toasts.size());

        guest.onMessage(SocialProtocol.msg(SocialProtocol.INVITE, "inviteId", "back1", "from", HOST.toString(), "world", world));
        assertTrue(sent(sentMessages, SocialProtocol.INVITE_ACCEPT, "inviteId", "back1"));
        assertEquals(1, bridge.toasts.size(), "no question for the join the player already accepted");

        guest.onMessage(SocialProtocol.msg(SocialProtocol.INVITE, "inviteId", "back2", "from", HOST.toString(), "world", world));
        assertFalse(sent(sentMessages, SocialProtocol.INVITE_ACCEPT, "inviteId", "back2"), "only the first one");
        assertEquals(2, bridge.toasts.size());
    }
}
