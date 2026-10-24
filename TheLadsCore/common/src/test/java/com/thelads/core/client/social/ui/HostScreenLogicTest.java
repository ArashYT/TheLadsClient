package com.thelads.core.client.social.ui;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.social.HostOptions;
import com.thelads.core.client.social.SocialBridge;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HostScreenLogicTest {

    @Test
    void optionsRoundTripToHostOptions() {
        HostOptions original = new HostOptions();
        original.scope = HostOptions.Scope.INVITE;
        original.approval = HostOptions.Approval.AUTO_FRIENDS;
        original.maxPlayers = 12;
        original.guestGameMode = "creative";
        original.guestOpLevel = 2;
        original.pvp = false;
        original.difficulty = "hard";
        original.gamerules.put("keepInventory", "true");
        original.gamerules.put("mobGriefing", "false");
        original.gamerules.put("doDaylightCycle", "true");
        original.viewDistanceCap = 16;
        original.relayOnly = true;
        original.backupOnOpen = false;
        original.showJoinable = false;
        original.welcome = "Welcome to the custom world!";

        FakeHostCallbacks callbacks = new FakeHostCallbacks(original);
        HostScreenLogic logic = new HostScreenLogic(callbacks);

        HostOptions result = logic.buildOptions();

        assertEquals(HostOptions.Scope.INVITE, result.scope);
        assertEquals(HostOptions.Approval.AUTO_FRIENDS, result.approval);
        assertEquals(12, result.maxPlayers);
        assertEquals("creative", result.guestGameMode);
        assertEquals(2, result.guestOpLevel);
        assertFalse(result.pvp);
        assertEquals("hard", result.difficulty);
        assertEquals("true", result.gamerules.get("keepInventory"));
        assertEquals("false", result.gamerules.get("mobGriefing"));
        assertEquals("true", result.gamerules.get("doDaylightCycle"));
        assertEquals(16, result.viewDistanceCap);
        assertTrue(result.relayOnly);
        assertFalse(result.backupOnOpen);
        assertFalse(result.showJoinable);
        assertEquals("Welcome to the custom world!", result.welcome);
    }

    @Test
    void optionsModifiedInUiReflectedInBuildOptions() {
        HostOptions initial = new HostOptions();
        FakeHostCallbacks callbacks = new FakeHostCallbacks(initial);
        HostScreenLogic logic = new HostScreenLogic(callbacks);

        logic.setScope(HostOptions.Scope.LAN);
        logic.setApproval(HostOptions.Approval.INVITE_ONLY);
        logic.setMaxPlayers(4);
        logic.setGuestGameMode("adventure");
        logic.setGuestOpLevel(1);
        logic.setPvp(false);
        logic.setDifficulty("peaceful");
        logic.setViewDistanceCap(10);
        logic.setRelayOnly(true);
        logic.setBackupOnOpen(false);
        logic.setShowJoinable(false);
        logic.setWelcome("Welcome!");
        logic.setGamerule("keepInventory", "true");

        HostOptions result = logic.buildOptions();
        assertEquals(HostOptions.Scope.LAN, result.scope);
        assertEquals(HostOptions.Approval.INVITE_ONLY, result.approval);
        assertEquals(4, result.maxPlayers);
        assertEquals("adventure", result.guestGameMode);
        assertEquals(1, result.guestOpLevel);
        assertFalse(result.pvp);
        assertEquals("peaceful", result.difficulty);
        assertEquals(10, result.viewDistanceCap);
        assertTrue(result.relayOnly);
        assertFalse(result.backupOnOpen);
        assertFalse(result.showJoinable);
        assertEquals("Welcome!", result.welcome);
        assertEquals("true", result.gamerules.get("keepInventory"));
    }

    @Test
    void guestActionsTriggerCallbacks() {
        HostOptions options = new HostOptions();
        FakeHostCallbacks callbacks = new FakeHostCallbacks(options);
        UUID guestId = UUID.randomUUID();
        callbacks.guests.add(new SocialBridge.GuestInfo(guestId, "Guest1", 45, false, "survival"));

        HostScreenLogic logic = new HostScreenLogic(callbacks);
        logic.setSection(HostScreenLogic.Section.GUESTS);

        TestGraphics g = new TestGraphics(480, 270);
        logic.render(g, 0, 0);

        // Kick
        logic.triggerGuestAction(guestId, SocialBridge.GuestAction.KICK);
        assertEquals(1, callbacks.recordedActions.size());
        assertEquals(guestId, callbacks.recordedActions.get(0).id);
        assertEquals(SocialBridge.GuestAction.KICK, callbacks.recordedActions.get(0).action);

        // Op
        logic.triggerGuestAction(guestId, SocialBridge.GuestAction.OP);
        assertEquals(2, callbacks.recordedActions.size());
        assertEquals(SocialBridge.GuestAction.OP, callbacks.recordedActions.get(1).action);

        // Mute (Finding 8)
        logic.triggerGuestAction(guestId, SocialBridge.GuestAction.MUTE);
        assertEquals(3, callbacks.recordedActions.size());
        assertEquals(SocialBridge.GuestAction.MUTE, callbacks.recordedActions.get(2).action);

        // GameMode cycle (Finding 8)
        logic.triggerGuestAction(guestId, SocialBridge.GuestAction.CREATIVE);
        assertEquals(4, callbacks.recordedActions.size());
        assertEquals(SocialBridge.GuestAction.CREATIVE, callbacks.recordedActions.get(3).action);
    }

    @Test
    void guestListScrollBounds() {
        HostOptions options = new HostOptions();
        FakeHostCallbacks callbacks = new FakeHostCallbacks(options);
        // Add 20 guests to exceed panel height
        for (int i = 0; i < 20; i++) {
            callbacks.guests.add(new SocialBridge.GuestInfo(UUID.randomUUID(), "Guest" + i, 30, false, "survival"));
        }

        HostScreenLogic logic = new HostScreenLogic(callbacks);
        logic.setSection(HostScreenLogic.Section.GUESTS);

        TestGraphics g = new TestGraphics(480, 270);
        logic.render(g, 0, 0);

        assertTrue(logic.maxScroll() > 0, "maxScroll must be > 0 when guests overflow panel");
        assertEquals(0, logic.scrollOffset());

        // Scroll down
        logic.mouseScrolled(100, 100, -5);
        assertTrue(logic.scrollOffset() > 0);

        // Clamp at maxScroll
        logic.mouseScrolled(100, 100, -100);
        assertEquals(logic.maxScroll(), logic.scrollOffset());

        // Clamp at 0
        logic.mouseScrolled(100, 100, 100);
        assertEquals(0, logic.scrollOffset());
    }

    @Test
    void nullCallbacksGuestsDoesNotThrow() {
        HostScreenLogic.Callbacks nullCallbacks = new HostScreenLogic.Callbacks() {
            @Override public HostOptions currentOptions() { return new HostOptions(); }
            @Override public List<SocialBridge.GuestInfo> guests() { return null; }
        };

        HostScreenLogic logic = new HostScreenLogic(nullCallbacks);
        logic.setSection(HostScreenLogic.Section.GUESTS);

        TestGraphics g = new TestGraphics(480, 270);
        assertDoesNotThrow(() -> logic.render(g, 0, 0));
    }

    @Test
    void startAndStopHostingTriggersCallbacks() {
        HostOptions options = new HostOptions();
        FakeHostCallbacks callbacks = new FakeHostCallbacks(options);
        HostScreenLogic logic = new HostScreenLogic(callbacks);

        assertFalse(callbacks.isHosting());
        logic.toggleHosting();
        assertTrue(callbacks.startedHosting);

        callbacks.hosting = true;
        logic.toggleHosting();
        assertTrue(callbacks.stoppedHosting);
    }

    static class FakeHostCallbacks implements HostScreenLogic.Callbacks {
        HostOptions options;
        boolean hosting = false;
        boolean startedHosting = false;
        boolean stoppedHosting = false;
        final List<SocialBridge.GuestInfo> guests = new ArrayList<>();
        final List<RecordedGuestAction> recordedActions = new ArrayList<>();
        boolean closed = false;

        record RecordedGuestAction(UUID id, SocialBridge.GuestAction action) {}

        FakeHostCallbacks(HostOptions options) {
            this.options = options;
        }

        @Override public HostOptions currentOptions() { return options; }
        @Override public void saveOptions(HostOptions o) { this.options = o; }
        @Override public boolean isHosting() { return hosting; }
        @Override public void startHosting(HostOptions o) { this.options = o; startedHosting = true; }
        @Override public void stopHosting() { stoppedHosting = true; }
        @Override public List<SocialBridge.GuestInfo> guests() { return guests; }
        @Override public void guestAction(UUID id, SocialBridge.GuestAction action) {
            recordedActions.add(new RecordedGuestAction(id, action));
        }
        @Override public void onClose() { closed = true; }
    }

    record TestGraphics(int w, int h) implements LadsGraphics {
        @Override public void fill(int a, int b, int c, int d, int e) {}
        @Override public void drawText(String t, int x, int y, int c, boolean s) {}
        @Override public void drawCenteredText(String t, int x, int y, int c, boolean s) {}
        @Override public int textWidth(String t) { return t == null ? 0 : t.length() * 6; }
        @Override public int fontHeight() { return 9; }
        @Override public void pushPose() {}
        @Override public void popPose() {}
        @Override public void translate(float x, float y) {}
        @Override public void scale(float x, float y) {}
        @Override public void enableScissor(int a, int b, int c, int d) {}
        @Override public void disableScissor() {}
        @Override public void blit(String t, int x, int y, int u, int v, int w, int h) {}
        @Override public void drawHead(String n, String u, int x, int y, int s) {}
        @Override public int getScaledWidth() { return w; }
        @Override public int getScaledHeight() { return h; }
    }
}
