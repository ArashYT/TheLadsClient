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
    void ruleLabelsAreReadable() {
        assertEquals("Daylight Cycle", HostScreenLogic.ruleLabel("doDaylightCycle"));
        assertEquals("Keep Inventory", HostScreenLogic.ruleLabel("keepInventory"));
        assertEquals("Players Sleeping Percentage", HostScreenLogic.ruleLabel("playersSleepingPercentage"));
    }

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
    void bannedSectionUnbansAndBansSurviveSaving() {
        FakeHostCallbacks callbacks = new FakeHostCallbacks(new HostOptions());
        String id = UUID.randomUUID().toString();
        callbacks.banned.add(new HostScreenLogic.Banned(id, "Griefer"));
        HostScreenLogic logic = new HostScreenLogic(callbacks);
        logic.setSection(HostScreenLogic.Section.GUESTS);
        logic.render(new TestGraphics(480, 270), 0, 0);
        assertTrue(logic.buildOptions().banned.contains(id), "saving the screen keeps the ban");

        HostScreenLogic.Box unban = logic.unbanButtonBox(id);
        assertNotNull(unban, "a banned player gets an Unban button");
        assertTrue(logic.mouseClicked(unban.x() + 2, unban.y() + 2, 0));
        assertEquals(List.of(id), callbacks.unbanned);
        assertFalse(logic.buildOptions().banned.contains(id), "unbanned: no longer saved as banned");
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

    /** lads-host.json with scope OFF (the screen used to offer it): Start Hosting sent OFF, which HostSession reads as "stop", so nothing happened. */
    @Test
    void startHostingWithSavedScopeOffOpensToFriends() {
        HostOptions saved = new HostOptions();
        saved.scope = HostOptions.Scope.OFF;
        FakeHostCallbacks callbacks = new FakeHostCallbacks(saved);
        HostScreenLogic logic = new HostScreenLogic(callbacks);

        logic.toggleHosting();
        assertTrue(callbacks.startedHosting);
        assertEquals(HostOptions.Scope.FRIENDS, callbacks.options.scope);

        logic.setScope(HostOptions.Scope.OFF);
        assertEquals(HostOptions.Scope.FRIENDS, logic.buildOptions().scope);
    }

    @Test
    void makeOperatorButtonIsOnEveryGuestCardAndGivesOp() {
        FakeHostCallbacks callbacks = new FakeHostCallbacks(new HostOptions());
        UUID guestId = UUID.randomUUID();
        callbacks.guests.add(new SocialBridge.GuestInfo(guestId, "Guest1", 45, false, "survival"));
        HostScreenLogic logic = new HostScreenLogic(callbacks);
        logic.setSection(HostScreenLogic.Section.GUESTS);
        logic.render(new TestGraphics(480, 270), 0, 0);

        HostScreenLogic.Box op = logic.operatorButtonBox(guestId);
        assertNotNull(op, "a guest card shows Make Operator");
        assertTrue(logic.mouseClicked(op.x() + 2, op.y() + 2, 0));
        assertEquals(SocialBridge.GuestAction.OP, callbacks.recordedActions.get(0).action);

        callbacks.guests.set(0, new SocialBridge.GuestInfo(guestId, "Guest1", 45, true, "survival"));
        logic.render(new TestGraphics(480, 270), 0, 0);
        op = logic.operatorButtonBox(guestId);
        assertTrue(logic.mouseClicked(op.x() + 2, op.y() + 2, 0));
        assertEquals(SocialBridge.GuestAction.DEOP, callbacks.recordedActions.get(1).action, "an operator's button takes it away");
    }

    @Test
    void portTypedInTheBoxIsKeptOnlyWhenValid() {
        FakeHostCallbacks callbacks = new FakeHostCallbacks(new HostOptions());
        HostScreenLogic logic = new HostScreenLogic(callbacks);
        logic.setPort(25570);
        assertEquals(25570, logic.buildOptions().port);
        logic.setPort(80);
        assertEquals(0, logic.buildOptions().port, "below 1024: any free port, as vanilla");
        logic.setPort(70000);
        assertEquals(0, logic.buildOptions().port);
    }

    @Test
    void vanillaLanOptionsRoundTrip() {
        HostOptions options = new HostOptions();
        options.port = 30000;
        options.allowCommands = true;
        options.hostGameMode = "creative";
        options.forceGameMode = true;
        options.sharePacks = false;
        options.shareAllPacks = false;
        options.sharedPacks = new ArrayList<>(List.of("file/A.zip"));
        HostOptions result = new HostScreenLogic(new FakeHostCallbacks(options)).buildOptions();
        assertEquals(30000, result.port);
        assertEquals(Boolean.TRUE, result.allowCommands);
        assertEquals("creative", result.hostGameMode);
        assertTrue(result.forceGameMode);
        assertFalse(result.sharePacks);
        assertFalse(result.shareAllPacks);
        assertEquals(List.of("file/A.zip"), result.sharedPacks);
        assertTrue(new HostOptions().sharePacks, "sharing packs is on by default");
        assertTrue(new HostOptions().shareAllPacks);
    }

    @Test
    void ruleSwitchStartsFromTheWorldsValue() {
        FakeHostCallbacks callbacks = new FakeHostCallbacks(new HostOptions());
        callbacks.live.put("keepInventory", "true");
        HostScreenLogic logic = new HostScreenLogic(callbacks);
        assertEquals("true", logic.ruleValue("keepInventory"), "the world's value, not vanilla's default");
        assertEquals("true", logic.ruleValue("mobGriefing"), "unknown: vanilla's default");
        assertEquals("100", logic.ruleValue(HostScreenLogic.SLEEP_RULE));
        logic.setSection(HostScreenLogic.Section.GAMERULES);
        logic.render(new TestGraphics(480, 600), 0, 0);
        assertTrue(clickKey(logic, "keepInventory"));
        assertEquals("false", logic.buildOptions().gamerules.get("keepInventory"));
    }

    @Test
    void guestRenderDistanceIsZeroOrTwoToThirtyTwo() {
        HostScreenLogic logic = new HostScreenLogic(new FakeHostCallbacks(new HostOptions()));
        logic.setViewDistanceCap(1);
        assertEquals(2, logic.buildOptions().viewDistanceCap);
        logic.setViewDistanceCap(40);
        assertEquals(32, logic.buildOptions().viewDistanceCap);
        logic.setViewDistanceCap(0);
        assertEquals(0, logic.buildOptions().viewDistanceCap, "0 = same as the host's");
    }

    @Test
    void doneSavesAndAppliesToTheWorld() {
        FakeHostCallbacks callbacks = new FakeHostCallbacks(new HostOptions());
        HostScreenLogic logic = new HostScreenLogic(callbacks);
        logic.setDifficulty("hard");
        logic.close();
        assertNotNull(callbacks.applied);
        assertEquals("hard", callbacks.applied.difficulty);
        assertEquals("hard", callbacks.options.difficulty);
        assertTrue(callbacks.closed);
    }

    @Test
    void everySectionRendersAtSmallAndLargeSizes() {
        FakeHostCallbacks callbacks = new FakeHostCallbacks(new HostOptions());
        callbacks.canShare = true;
        callbacks.packs.put("file/A.zip", "A");
        for (HostScreenLogic.Section s : HostScreenLogic.Section.values()) {
            HostScreenLogic logic = new HostScreenLogic(callbacks);
            logic.setSection(s);
            assertDoesNotThrow(() -> logic.render(new TestGraphics(320, 240), 10, 10), s.name());
            assertDoesNotThrow(() -> logic.render(new TestGraphics(960, 540), 10, 10), s.name());
        }
    }

    /** LADS_UI_SHOTS=<folder>: a PNG of every Host screen section and of the Friends screen's Invites tab (a look without the game). */
    @Test
    void picturesOfTheHostScreen() throws Exception {
        String folder = System.getenv("LADS_UI_SHOTS");
        org.junit.jupiter.api.Assumptions.assumeTrue(folder != null && !folder.isBlank(), "LADS_UI_SHOTS is not set");
        new java.io.File(folder).mkdirs();
        HostOptions options = new HostOptions();
        options.viewDistanceCap = 8;
        FakeHostCallbacks callbacks = new FakeHostCallbacks(options);
        callbacks.hosting = true;
        callbacks.canShare = true;
        callbacks.packs.put("file/Faithful 32x.zip", "Faithful 32x");
        callbacks.packs.put("file/Lads UI", "Lads UI");
        callbacks.guests.add(new SocialBridge.GuestInfo(UUID.randomUUID(), "Cyrax", 38, false, "survival"));
        callbacks.guests.add(new SocialBridge.GuestInfo(UUID.randomUUID(), "TTVArashLIVE", 12, true, "creative"));
        callbacks.banned.add(new HostScreenLogic.Banned(UUID.randomUUID().toString(), "Griefer42"));
        for (int[] size : new int[][]{{480, 270}, {640, 360}}) {
            for (HostScreenLogic.Section s : HostScreenLogic.Section.values()) {
                HostScreenLogic logic = new HostScreenLogic(callbacks);
                logic.setSection(s);
                if (s == HostScreenLogic.Section.PACKS) logic.setShareAllPacks(false);
                Awt g = new Awt(size[0], size[1]);
                logic.render(g, 0, 0);
                g.save(folder, "host-" + size[0] + "-" + s.name().toLowerCase());
            }
        }
        FriendsScreenLogic friends = new FriendsScreenLogic(new FriendsScreenLogic.Callbacks() {
            @Override public List<com.thelads.core.client.social.FriendEntry> friends() { return List.of(); }
            @Override public List<com.thelads.core.client.social.SocialState.Invite> invites() {
                return List.of(new com.thelads.core.client.social.SocialState.Invite("i1", UUID.randomUUID(), "Survival Island", false, null),
                        new com.thelads.core.client.social.SocialState.Invite("i2", UUID.randomUUID(), "play.thelads.net", true, "play.thelads.net"));
            }
        });
        friends.setTab(FriendsScreenLogic.Tab.INVITES);
        Awt g = new Awt(480, 270);
        friends.render(g, 0, 0);
        g.save(folder, "friends-invites");
    }

    private static final class Awt implements LadsGraphics {
        final int w, h;
        final java.awt.image.BufferedImage image;
        final java.awt.Graphics2D g;
        final java.awt.FontMetrics metrics;
        final java.util.ArrayDeque<java.awt.geom.AffineTransform> poses = new java.util.ArrayDeque<>();
        Awt(int w, int h) {
            this.w = w;
            this.h = h;
            image = new java.awt.image.BufferedImage(w * 2, h * 2, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            g = image.createGraphics();
            g.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.BOLD, 9));
            g.scale(2, 2);
            metrics = g.getFontMetrics();
            g.setClip(0, 0, w, h);
        }
        @Override public void fill(int x0, int y0, int x1, int y1, int color) { g.setColor(new java.awt.Color(color, true)); g.fillRect(x0, y0, x1 - x0, y1 - y0); }
        @Override public void drawText(String text, int x, int y, int color, boolean shadow) {
            g.setColor(new java.awt.Color(color, true));
            g.drawString(text, x, y + metrics.getAscent() - 1);
        }
        @Override public void drawCenteredText(String text, int cx, int y, int color, boolean shadow) { drawText(text, cx - textWidth(text) / 2, y, color, shadow); }
        @Override public int textWidth(String text) { return metrics.stringWidth(text); }
        @Override public int fontHeight() { return 9; }
        @Override public void pushPose() { poses.push(g.getTransform()); }
        @Override public void popPose() { g.setTransform(poses.pop()); }
        @Override public void translate(float x, float y) { g.translate(x, y); }
        @Override public void scale(float sx, float sy) { g.scale(sx, sy); }
        @Override public void enableScissor(int x0, int y0, int x1, int y1) { g.setClip(x0, y0, x1 - x0, y1 - y0); }
        @Override public void disableScissor() { g.setClip(0, 0, w, h); }
        @Override public void blit(String texture, int x, int y, int u, int v, int width, int height) { }
        @Override public void drawHead(String username, String uuid, int x, int y, int size) { fill(x, y, x + size, y + size, 0xFF6A4A3A); }
        @Override public int getScaledWidth() { return w; }
        @Override public int getScaledHeight() { return h; }
        void save(String folder, String name) throws Exception { javax.imageio.ImageIO.write(image, "png", new java.io.File(folder, name + ".png")); }
    }

    /** Clicks the middle of the switch row whose label is that rule's (the row spans the content's width). */
    private static boolean clickKey(HostScreenLogic logic, String rule) {
        try {
            java.lang.reflect.Field f = HostScreenLogic.class.getDeclaredField("clickables");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String, HostScreenLogic.Box> boxes = (java.util.Map<String, HostScreenLogic.Box>) f.get(logic);
            HostScreenLogic.Box b = boxes.get("toggle:rule:" + rule);
            return b != null && logic.mouseClicked(b.x() + b.w() / 2, b.y() + b.h() / 2, 0);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    static class FakeHostCallbacks implements HostScreenLogic.Callbacks {
        HostOptions applied;
        final java.util.Map<String, String> live = new java.util.HashMap<>();
        final java.util.Map<String, String> packs = new LinkedHashMap<>();
        boolean canShare = false;
        @Override public void apply(HostOptions o) { applied = o; }
        @Override public java.util.Map<String, String> liveGamerules() { return live; }
        @Override public boolean canSharePacks() { return canShare; }
        @Override public java.util.Map<String, String> shareablePacks() { return packs; }

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
        final List<HostScreenLogic.Banned> banned = new ArrayList<>();
        final List<String> unbanned = new ArrayList<>();
        @Override public List<HostScreenLogic.Banned> banned() { return banned; }
        @Override public void unban(String uuid) { unbanned.add(uuid); banned.removeIf(b -> b.uuid().equals(uuid)); }
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
