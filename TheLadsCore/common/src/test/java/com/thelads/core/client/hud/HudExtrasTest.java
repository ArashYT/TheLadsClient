package com.thelads.core.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.RaisedModule;
import com.thelads.core.modules.SubtitlesModule;
import com.thelads.core.modules.TabListModule;
import com.thelads.core.modules.TitleTweakerModule;
import com.thelads.core.modules.VanillaHudModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The pure logic behind the parity 1.9.19 HUD widgets and vanilla HUD tweaks (rows 18-33). */
class HudExtrasTest {
    private static final String[] MODULES = {HudExtras.DAMAGE_INDICATOR, HudExtras.JUMP_RESET, HudExtras.MEDIA, HudExtras.HORSE,
        HudExtras.PLAYERS, HudExtras.TOTEMS, HudExtras.BLOCK_INFO, HudExtras.CUSTOM_TEXT, HudExtras.CUSTOM_SHAPE, HudExtras.CUSTOM_IMAGE,
        HudExtras.HEAD, HudExtras.ABOVE, HudExtras.ENTITIES, HudExtras.SHADER, HudExtras.LORE, SubtitlesModule.NAME, TitleTweakerModule.NAME,
        "ToastControl", VanillaHudModule.NAME};

    @BeforeEach
    void clean() {
        JumpReset.reset();
        for (int i = 0; i < 10; i++) JumpReset.tick(false, true, 0); // past any jump an earlier test left
        TotemPops.clear();
        DamageNumbers.clear();
    }

    @Test
    void everyWidgetIsRegisteredOffByDefaultWithAHud() {
        ModuleManager fresh = new ModuleManager(); // the defaults, untouched by other tests
        for (String name : MODULES) {
            assertNotNull(fresh.getModule(name), name);
            assertFalse(fresh.getModule(name).isEnabledSetting(), name + " defaults off");
        }
        for (String name : MODULES) {
            if (name.equals(SubtitlesModule.NAME) || name.equals(TitleTweakerModule.NAME) || name.equals("ToastControl") || name.equals(VanillaHudModule.NAME)) continue;
            assertNotNull(HudManager.getInstance().get(name), name + " has a HUD element");
        }
    }

    @Test
    void onlyTheWidgetsShowingHiddenInformationAreGated() {
        for (String gated : new String[] {HudExtras.DAMAGE_INDICATOR, HudExtras.HORSE, HudExtras.PLAYERS, HudExtras.TOTEMS})
            assertTrue(HypixelSafetyManager.isRiskyModule(gated), gated);
        for (String visual : new String[] {HudExtras.BLOCK_INFO, HudExtras.CUSTOM_TEXT, HudExtras.ENTITIES, HudExtras.JUMP_RESET, HudExtras.MEDIA,
            SubtitlesModule.NAME, VanillaHudModule.NAME})
            assertFalse(HypixelSafetyManager.isRiskyModule(visual), visual);
    }

    @Test
    void jumpOnTheHitsTickIsPerfectAndBuildsAStreak() {
        JumpReset.jumped();
        JumpReset.tick(true, true, 1000);
        assertEquals(JumpReset.PERFECT, JumpReset.rating);
        assertEquals(0, JumpReset.offset);
        assertEquals(1, JumpReset.streak);
        JumpReset.tick(false, false, 1050);
        JumpReset.jumped();
        JumpReset.tick(true, true, 1100);
        assertEquals(2, JumpReset.streak);
    }

    @Test
    void jumpsEitherSideOfTheHitAreEarlyOrLate() {
        JumpReset.jumped();
        JumpReset.tick(false, true, 0); // jumped the tick before the hit
        JumpReset.tick(true, false, 50);
        assertEquals(JumpReset.EARLY, JumpReset.rating);
        assertEquals(-1, JumpReset.offset);
        assertEquals(0, JumpReset.streak);

        for (int i = 0; i < 10; i++) JumpReset.tick(false, true, 60);
        JumpReset.tick(true, true, 100); // hit on the ground
        JumpReset.tick(false, true, 150);
        JumpReset.jumped();
        JumpReset.tick(false, true, 200); // two ticks late
        assertEquals(JumpReset.LATE, JumpReset.rating);
        assertEquals(2, JumpReset.offset);
        assertEquals("Late (+100 ms) x0", JumpResetHudElement.text(JumpReset.rating, JumpReset.offset, JumpReset.streak, true, true));
    }

    @Test
    void aGroundedHitWithoutAJumpIsMissedAndAnAirHitIsNotRated() {
        JumpReset.tick(true, true, 0);
        for (int i = 1; i <= JumpReset.WINDOW; i++) JumpReset.tick(false, true, i * 50L);
        assertEquals(JumpReset.NONE, JumpReset.rating, "still in the window");
        JumpReset.tick(false, true, 1000);
        assertEquals(JumpReset.MISSED, JumpReset.rating);
        JumpReset.reset();
        JumpReset.tick(true, false, 2000); // in the air: nothing to reset
        for (int i = 1; i < 10; i++) JumpReset.tick(false, false, 2000 + i * 50L);
        assertEquals(JumpReset.NONE, JumpReset.rating);
    }

    @Test
    void healthDropsBecomeFloatingNumbersThatRiseAndFade() {
        assertEquals(0, DamageNumbers.observe(7, 20, 0, 64, 0, 0), 0.001, "first sight only remembers");
        DamageNumbers.endTick();
        assertEquals(7, DamageNumbers.observe(7, 13, 0, 64, 0, 0), 0.001);
        DamageNumbers.endTick();
        String shown = null;
        int slot = -1;
        for (int i = 0; i < DamageNumbers.slots(); i++) if (DamageNumbers.text(i, 0, 1000) != null) { shown = DamageNumbers.text(i, 0, 1000); slot = i; }
        assertEquals("3.5" + DamageNumbers.HEART, shown);
        assertEquals(64.75, DamageNumbers.y(slot, 1000, 1000), 1e-9);
        assertEquals(255, DamageNumbers.alpha(slot, 500, 1000));
        assertTrue(DamageNumbers.alpha(slot, 900, 1000) < 255);
        assertNull(DamageNumbers.text(slot, 1001, 1000), "gone after its life");
        assertEquals("4" + DamageNumbers.HEART, DamageNumbers.format(8));
        DamageNumbers.observe(7, 20, 0, 64, 0, 0); // healed: no number
        DamageNumbers.endTick();
        DamageNumbers.endTick(); // not seen this tick: forgotten, so its next sight only remembers
        assertEquals(0, DamageNumbers.observe(7, 1, 0, 64, 0, 0), 0.001);
    }

    @Test
    void totemPopsCountPerPlayerAndStartOverOnDeath() {
        assertEquals(1, TotemPops.pop(1, "Notch", 0));
        assertEquals(2, TotemPops.pop(1, "Notch", 10));
        assertEquals(1, TotemPops.pop(2, "jeb_", 20));
        TotemPops.died(1);
        assertEquals(1, TotemPops.size());
        assertEquals("jeb_", TotemPops.name(0));
        TotemPops.expire(30_000, 5_000);
        assertEquals(0, TotemPops.size());
        for (int i = 0; i < TotemPops.MAX + 3; i++) TotemPops.pop(100 + i, "p" + i, i);
        assertEquals(TotemPops.MAX, TotemPops.size(), "the oldest make room");
    }

    @Test
    void titlesFitTheScreenAndRepeatsAreNotCopiedToChat() {
        assertEquals(1.5f, TitleTweakerModule.drawScale(1.5f, false, 200, 0, 400), 1e-6);
        assertEquals(380 / 800f, TitleTweakerModule.drawScale(1f, true, 200, 0, 400), 1e-6); // 4x title 800 wide on a 400 GUI
        assertEquals(1f, TitleTweakerModule.drawScale(1f, true, 20, 30, 400), 1e-6);
        TitleTweakerModule titles = new TitleTweakerModule();
        titles.setEnabled(true);
        titles.toChat.set(true);
        titles.refresh();
        assertEquals("Victory!", titles.chatLine("Victory!", false));
        assertNull(titles.chatLine("Victory!", false), "servers resend titles every tick");
        assertEquals("Victory!", titles.chatLine("Victory!", true), "a subtitle is its own line");
    }

    @Test
    void subtitleColourKeepsVanillasFade() {
        assertEquals(0xFFFF8000, SubtitlesModule.fade(0xFF8000, 255));
        assertEquals(0xFF7F3F00, SubtitlesModule.fade(0xFF8000, 127));
    }

    @Test
    void heldItemNameIsKeptOrCutBeforeItsFade() {
        VanillaHudModule vanilla = new VanillaHudModule();
        vanilla.refresh();
        assertEquals(5, vanilla.highlightTicks(5, true), "off: vanilla");
        vanilla.setEnabled(true);
        vanilla.keepName.set(true);
        vanilla.refresh();
        assertEquals(40, vanilla.highlightTicks(5, true));
        assertEquals(0, vanilla.highlightTicks(0, false), "nothing held: nothing to show");
        vanilla.keepName.set(false);
        vanilla.noFade.set(true);
        vanilla.refresh();
        assertEquals(0, vanilla.highlightTicks(9, true));
        assertEquals(30, vanilla.highlightTicks(30, true));
    }

    @Test
    void tabKeyTogglesAndAnimates() {
        TabListModule tab = new TabListModule();
        ((com.thelads.core.config.DropdownOption) tab.getOption("Key Mode")).setIndex(1);
        tab.refresh();
        assertTrue(tab.wanted(true), "a press opens it");
        assertTrue(tab.wanted(false), "and it stays open");
        assertFalse(tab.wanted(true), "the next press closes it");
        TabListModule animated = new TabListModule();
        ((com.thelads.core.config.BoolOption) animated.getOption("Animated Open/Close")).set(true);
        animated.refresh();
        assertEquals(0, animated.openness(true, 0), 1e-6, "first frame starts closed");
        assertEquals(1 / 3f, animated.openness(true, 50_000_000L), 1e-3);
        assertEquals(1 / 3f, animated.openness(true, 50_000_000L), 1e-3, "a second ask in the same frame changes nothing");
        assertEquals(2 / 3f, animated.openness(true, 150_000_000L), 1e-3, "a long frame counts as 50 ms");
        assertEquals(1f, animated.openness(true, 200_000_000L), 1e-6);
        assertTrue(animated.tabKey(false, 210_000_000L), "still closing: drawn");
        long frame = 210_000_000L;
        for (int i = 0; i < 3; i++) animated.tabKey(false, frame += 50_000_000L);
        assertFalse(animated.tabKey(false, frame + 50_000_000L), "closed after 150 ms");
    }

    @Test
    void raisedPartsMoveAndScaleOnlyWhenChanged() {
        RaisedModule raised = new RaisedModule();
        raised.refresh();
        for (boolean moved : raised.moved) assertFalse(moved);
        ((com.thelads.core.config.SliderOption) raised.getOption("Health Scale")).setValue(150);
        ((com.thelads.core.config.SliderOption) raised.getOption("Air Y Offset")).setValue(-20);
        raised.refresh();
        assertTrue(raised.moved[RaisedModule.HEALTH]);
        assertEquals(1.5f, raised.scale[RaisedModule.HEALTH], 1e-6);
        assertEquals(-20, raised.dy[RaisedModule.AIR]);
        assertFalse(raised.moved[RaisedModule.FOOD]);
        assertEquals(400 / 2f - 91, RaisedModule.pivotX(RaisedModule.HEALTH, 400), 1e-6);
        assertEquals(400 / 2f + 91, RaisedModule.pivotX(RaisedModule.FOOD, 400), 1e-6);
        raised.setEnabled(false);
        raised.refresh();
        assertFalse(raised.moved[RaisedModule.HEALTH], "off: vanilla");
    }

    @Test
    void customTextFillsKnownPlaceholdersOnly() {
        DefaultGameBridge game = new DefaultGameBridge();
        String text = CustomTextHudElement.resolve("{fps} FPS at {x},{y} {nope}\\n{FPS}", game);
        assertEquals(game.getFps() + " FPS at " + game.getPlayerX() + "," + game.getPlayerY() + " {nope}\n" + game.getFps(), text);
    }

    @Test
    void horseStatsUseTheKnownCurves() {
        assertEquals(5.29, HudExtras.jumpHeight(1.0), 0.01, "the strongest horse jumps about 5.3 blocks");
        assertEquals(1.09, HudExtras.jumpHeight(0.4), 0.01);
        assertEquals(14.57, HudExtras.blocksPerSecond(0.3375), 0.01, "the fastest horse");
        assertEquals("Jump: 5.29 blocks\nSpeed: 52.5 km/h\nHealth: 15.0/15" + DamageNumbers.HEART,
            HorseStatsHudElement.text(5.29f, 14.57f, 30, 30, 1 | 2 | 4 | 1 << 3));
        assertEquals("Iron Pickaxe", HudExtras.toolName("pickaxe", 2));
        assertEquals("Any tool", HudExtras.toolName(null, 0));
    }

    @Test
    void mediaClockAndProgressRunOnWhilePlaying() {
        assertEquals("1:23", MediaPlayerHudElement.clock(83_000));
        assertEquals("0:05", MediaPlayerHudElement.clock(5_999));
        MediaSession.durationMs = 200_000;
        MediaSession.positionMs = 10_000;
        MediaSession.positionAt = 1_000;
        MediaSession.playing = true;
        assertEquals(12_000, MediaSession.position(3_000));
        assertEquals(200_000, MediaSession.position(999_999), "never past the end");
        MediaSession.playing = false;
        assertEquals(10_000, MediaSession.position(3_000), "paused: it stays");
    }
}
