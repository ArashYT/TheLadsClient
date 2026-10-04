package com.thelads.core.client.discord;

import com.thelads.core.modules.DiscordRpcModule;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class DiscordRpcServiceTest {
    private static final String ID = "123456789012345678"; // Fixture only; never reaches Discord.
    private static final DiscordPresence.Privacy PRIVATE = new DiscordPresence.Privacy(false, false, false, true, 0);
    private static final DiscordPresence MENU = new DiscordPresence("In the menus", "Minecraft 26.2", 10);
    static class MockIpc implements DiscordRpcService.Transport {
        DiscordRpcService.Events events;
        boolean connect = true, failInitialize;
        final List<String> calls = new ArrayList<>();
        final List<DiscordPresence> updates = new ArrayList<>();
        public void initialize(String id, DiscordRpcService.Events events) {
            calls.add("initialize:" + id); this.events = events;
            if (failInitialize) throw new UnsatisfiedLinkError("fixture failure");
        }
        public void callbacks() { if (connect) { connect = false; events.ready(); } }
        public void update(DiscordPresence presence) { calls.add("update"); updates.add(presence); }
        public void clear() { calls.add("clear"); }
        public void shutdown() { calls.add("shutdown"); }
    }
    static DiscordRpcService.Desired request(boolean enabled, String id, DiscordPresence.Privacy privacy, DiscordPresence presence) {
        return new DiscordRpcService.Desired(enabled, id, privacy, presence);
    }
    @Test void missingOptInAndInvalidApplicationIdNeverCreateIpc() {
        int[] created = {0};
        try (var service = new DiscordRpcService(() -> { created[0]++; return new MockIpc(); }, () -> 0, false)) {
            service.submit(request(false, ID, PRIVATE, MENU)); service.pump();
            service.submit(request(true, "", PRIVATE, MENU)); service.pump();
            service.submit(request(true, "18446744073709551616", PRIVATE, MENU)); service.pump();
            assertEquals(0, created[0]); assertTrue(service.status().contains("application ID"));
        }
    }
    @Test void requiresReadyAndThrottlesChangedActivity() {
        var ipc = new MockIpc(); ipc.connect = false; var time = new AtomicLong();
        try (var service = new DiscordRpcService(() -> ipc, time::get, false)) {
            service.submit(request(true, ID, PRIVATE, MENU)); service.pump(); assertTrue(ipc.updates.isEmpty());
            ipc.events.ready(); service.pump(); assertEquals(1, ipc.updates.size());
            service.pump(); assertEquals(1, ipc.updates.size());
            var world = new DiscordPresence("Playing singleplayer", "Minecraft 26.2", 20);
            service.submit(request(true, ID, PRIVATE, world)); service.pump(); assertEquals(1, ipc.updates.size());
            time.set(15_000); service.pump(); assertEquals(world, ipc.updates.getLast());
        }
    }
    @Test void privacyReductionClearsPriorDataWithoutWaitingForThrottle() {
        var ipc = new MockIpc();
        try (var service = new DiscordRpcService(() -> ipc, () -> 0, false)) {
            var publicAddress = new DiscordPresence.Privacy(true, false, false, true, 0);
            service.submit(request(true, ID, publicAddress, new DiscordPresence("Multiplayer", "private.example", 10))); service.pump();
            service.submit(request(true, ID, PRIVATE, MENU)); service.pump();
            assertEquals(List.of("initialize:" + ID, "update", "clear", "update"), ipc.calls);
            assertEquals(MENU, ipc.updates.getLast());
        }
    }
    @Test void disableAndApplicationChangeClearAndCloseOldConnection() {
        var first = new MockIpc(); var second = new MockIpc(); var queue = new ArrayDeque<>(List.of(first, second));
        try (var service = new DiscordRpcService(queue::remove, () -> 0, false)) {
            service.submit(request(true, ID, PRIVATE, MENU)); service.pump();
            service.submit(request(true, "223456789012345678", PRIVATE, MENU)); service.pump();
            assertEquals(List.of("initialize:" + ID, "update", "clear", "shutdown"), first.calls);
            service.submit(request(false, ID, PRIVATE, MENU)); service.pump();
            assertEquals(List.of("initialize:223456789012345678", "update", "clear", "shutdown"), second.calls);
            service.pump(); assertEquals(4, second.calls.size());
        }
    }
    @Test void nativeLoadFailureBacksOffAndRecoversWithoutThrowingIntoGame() {
        var broken = new MockIpc(); broken.failInitialize = true; var healthy = new MockIpc();
        var queue = new ArrayDeque<>(List.of(broken, healthy)); var time = new AtomicLong();
        try (var service = new DiscordRpcService(queue::remove, time::get, false)) {
            service.submit(request(true, ID, PRIVATE, MENU)); service.pump();
            assertTrue(service.status().contains("unavailable")); assertTrue(broken.calls.contains("shutdown"));
            time.set(4_999); service.pump(); assertEquals(1, queue.size());
            time.set(5_000); service.pump(); assertEquals(List.of(MENU), healthy.updates);
        }
    }
    @Test void disconnectAndRejectedActivityRetryWithNoStaleSession() {
        var first = new MockIpc(); var second = new MockIpc(); var queue = new ArrayDeque<>(List.of(first, second)); var time = new AtomicLong();
        try (var service = new DiscordRpcService(queue::remove, time::get, false)) {
            service.submit(request(true, ID, PRIVATE, MENU)); service.pump();
            first.events.disconnected(1000); service.pump(); assertTrue(first.calls.contains("shutdown"));
            time.set(5_000); service.pump(); assertEquals(1, second.updates.size());
            second.events.error(4000); service.pump(); assertTrue(service.status().contains("4000"));
            assertEquals("shutdown", second.calls.getLast());
        }
    }
    @Test void missingDiscordTimesOutAndCloseIsIdempotent() {
        var ipc = new MockIpc(); ipc.connect = false; var time = new AtomicLong();
        var service = new DiscordRpcService(() -> ipc, time::get, false);
        service.submit(request(true, ID, PRIVATE, MENU)); service.pump();
        time.set(30_000); service.pump(); assertTrue(service.status().contains("Waiting"));
        service.close(); service.close(); service.submit(request(true, ID, PRIVATE, MENU)); service.pump();
        assertTrue(ipc.updates.isEmpty()); assertEquals(1, Collections.frequency(ipc.calls, "shutdown"));
    }
    @Test void actualWorkerRunsTransportOffCallingThreadAndClearsOnClose() throws Exception {
        var entered = new CountDownLatch(1); var leave = new CountDownLatch(1); var cleared = new CountDownLatch(1);
        String caller = Thread.currentThread().getName(); String[] thread = {null};
        var ipc = new MockIpc() {
            public void initialize(String id, DiscordRpcService.Events events) {
                thread[0] = Thread.currentThread().getName(); super.initialize(id, events); entered.countDown();
                try { assertTrue(leave.await(2, TimeUnit.SECONDS)); } catch (InterruptedException error) { throw new AssertionError(error); }
            }
            public void clear() { super.clear(); cleared.countDown(); }
        };
        try (var service = new DiscordRpcService(() -> ipc)) {
            service.submit(request(true, ID, PRIVATE, MENU)); assertTrue(entered.await(2, TimeUnit.SECONDS));
            // A blocked IPC initialization must not block game-thread disable requests.
            service.submit(request(false, ID, PRIVATE, MENU)); leave.countDown();
            assertNotEquals(caller, thread[0]);
        } finally { leave.countDown(); }
        assertTrue(cleared.await(1, TimeUnit.SECONDS)); assertTrue(ipc.updates.isEmpty());
    }
    @Test void defaultsShowScreensAndServerHostButNeverIpsWorldsOrDimensions() {
        var module = new DiscordRpcModule();
        assertTrue(module.isEnabled()); assertTrue(module.shareServerAddress.get()); assertFalse(module.shareWorldName.get());
        var game = new DiscordPresence.Game(DiscordPresence.Place.MULTIPLAYER, "26.2", "Play.Example.net:25565", "Private world", "minecraft:the_nether", "Paused", 123);
        var shown = DiscordPresence.from(game, module.privacy());
        assertEquals(new DiscordPresence("Playing on play.example.net", "Paused", 123), shown);
        for (String address : new String[] {"192.168.1.20:25565", "73.12.4.9", "[::1]:25565", "localhost", "my-pc.local", ""})
            assertEquals("Playing multiplayer", DiscordPresence.from(new DiscordPresence.Game(DiscordPresence.Place.MULTIPLAYER, "26.2", address, null, null, null, 1), module.privacy()).details(), address);
        var full = new DiscordPresence.Privacy(true, true, true, false, 0);
        var world = new DiscordPresence.Game(DiscordPresence.Place.SINGLEPLAYER, "1.8.9", null, "Private world", "minecraft:the_nether", null, 123);
        assertEquals(new DiscordPresence("Playing in Private world", "In the Nether", 0), DiscordPresence.from(world, full));
        assertEquals(new DiscordPresence("Playing singleplayer", "Minecraft 1.8.9", 123), DiscordPresence.from(world, module.privacy()));
        var menu = new DiscordPresence.Game(DiscordPresence.Place.MENU, "26.3", null, null, null, "Browsing servers", 5);
        assertEquals(new DiscordPresence("Browsing servers", "Minecraft 26.3", 0), DiscordPresence.from(menu, full));
        assertEquals("In the menus", DiscordPresence.from(menu, new DiscordPresence.Privacy(true, true, true, true, 1)).details());
        for (int level : new int[] {1, 2}) {
            var presence = DiscordPresence.from(game, new DiscordPresence.Privacy(true, true, true, false, level));
            assertFalse(presence.toString().contains("example")); assertFalse(presence.toString().contains("Paused"));
        }
    }
    // Stand-ins named like the game's screens: labels go by simple class name, a version suffix dropped, then superclasses.
    static class TitleScreen {}
    static class GuiVideoSettings {}
    static class LadsSettingsScreen26 {}
    static class DraggableHudScreen189 {}
    static class KeyBindsScreen {}
    static class LadsKeyBindsScreen extends KeyBindsScreen {}
    static class SodiumOptionsScreen {}
    static class class_442 {}
    @Test void everyScreenGetsALabel() {
        assertEquals("In the main menu", DiscordPresence.screen(TitleScreen.class));
        assertEquals("In video settings", DiscordPresence.screen(GuiVideoSettings.class));
        assertEquals("In the Lads menu", DiscordPresence.screen(LadsSettingsScreen26.class));
        assertEquals("Editing the HUD", DiscordPresence.screen(DraggableHudScreen189.class));
        assertEquals("Changing keybinds", DiscordPresence.screen(LadsKeyBindsScreen.class));
        assertEquals("In Sodium Options", DiscordPresence.screen(SodiumOptionsScreen.class));
        assertEquals("In the menus", DiscordPresence.screen(class_442.class));
        assertEquals("In the menus", DiscordPresence.screen(new Object() {}.getClass()));
    }
    @Test void savedComingSoonEntryIsDroppedOnceSoPresenceStartsOn() {
        var old = com.google.gson.JsonParser.parseString("{\"DiscordRPC\":{\"enabled\":false,\"options\":{\"Application ID\":\"\",\"Share activity\":false}}}").getAsJsonObject();
        com.thelads.core.config.ConfigManager.migrateDiscord(old);
        assertFalse(old.has("DiscordRPC"));
        var chosen = com.google.gson.JsonParser.parseString("{\"DiscordRPC\":{\"enabled\":false,\"options\":{\"Detail Level\":1}}}").getAsJsonObject();
        com.thelads.core.config.ConfigManager.migrateDiscord(chosen);
        assertFalse(chosen.getAsJsonObject("DiscordRPC").get("enabled").getAsBoolean());
    }
    @Test void unicodeTextFitsNativeBufferWithoutBrokenSurrogatesOrControls() {
        String output = DiscordPresence.text("😀".repeat(70) + "\n\u0000");
        assertTrue(output.getBytes(StandardCharsets.UTF_8).length <= 127);
        assertEquals(31, output.codePointCount(0, output.length()));
        assertFalse(output.contains("\n")); assertFalse(output.contains("\u0000"));
        assertTrue(DiscordRpcService.validApplicationId(ID));
        assertFalse(DiscordRpcService.validApplicationId("000000000000000000"));
    }
}
