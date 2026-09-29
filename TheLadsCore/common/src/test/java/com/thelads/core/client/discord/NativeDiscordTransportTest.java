package com.thelads.core.client.discord;

import club.minnced.discord.rpc.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NativeDiscordTransportTest {
    static class MockNativeIpc implements DiscordRPC {
        String id; DiscordEventHandlers handlers; DiscordRichPresence presence;
        final List<String> calls = new ArrayList<>();
        public void Discord_Initialize(String id, DiscordEventHandlers handlers, boolean register, String steamId) {
            assertFalse(register); assertNull(steamId); this.id = id; this.handlers = handlers; calls.add("initialize");
        }
        public void Discord_RunCallbacks() { handlers.ready.accept(null); handlers.disconnected.accept(1000, "ignored user data"); handlers.errored.accept(4000, "ignored user data"); }
        public void Discord_UpdatePresence(DiscordRichPresence presence) { this.presence = presence; calls.add("update"); }
        public void Discord_ClearPresence() { calls.add("clear"); }
        public void Discord_Shutdown() { calls.add("shutdown"); }
        public void Discord_UpdateConnection() { fail("Connection is owned by the bundled native IPC worker"); }
        public void Discord_Respond(String user, int reply) { fail("Join requests are not supported"); }
        public void Discord_UpdateHandlers(DiscordEventHandlers handlers) { fail("Unexpected handler replacement"); }
        public void Discord_Register(String id, String command) { fail("Must not register or launch applications"); }
        public void Discord_RegisterSteamGame(String id, String steamId) { fail("Must not register Steam applications"); }
    }
    @Test void lazyBindingUsesOnlyPresenceCallbacksAndClearsBeforeShutdown() {
        MockNativeIpc ipc = new MockNativeIpc(); int[] loads = {0};
        var transport = new NativeDiscordTransport(() -> { loads[0]++; return ipc; });
        assertEquals(0, loads[0]);
        List<String> events = new ArrayList<>();
        transport.initialize("123456789012345678", new DiscordRpcService.Events() {
            public void ready() { events.add("ready"); }
            public void disconnected(int code) { events.add("disconnected:" + code); }
            public void error(int code) { events.add("error:" + code); }
        });
        assertEquals(1, loads[0]); assertEquals("123456789012345678", ipc.id);
        transport.callbacks(); assertEquals(List.of("ready", "disconnected:1000", "error:4000"), events);
        transport.update(new DiscordPresence("Playing multiplayer", "Minecraft 26.2", 100));
        assertEquals("Minecraft 26.2", ipc.presence.state); assertEquals(100, ipc.presence.startTimestamp);
        assertNull(ipc.presence.partyId); assertNull(ipc.presence.joinSecret); assertNull(ipc.presence.spectateSecret);
        assertNull(ipc.presence.matchSecret); assertNull(ipc.presence.largeImageKey);
        assertNull(ipc.handlers.joinGame); assertNull(ipc.handlers.spectateGame); assertNull(ipc.handlers.joinRequest);
        transport.clear(); transport.shutdown();
        assertEquals(List.of("initialize", "update", "clear", "shutdown"), ipc.calls);
    }
}
