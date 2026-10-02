package com.thelads.core.v1_21_11.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.client.hud.AutohideFade;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.client.util.ClientPaths;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.config.SliderOption;
import com.thelads.core.v1_21_11.adapter.GuiGraphicsLadsAdapter;
import com.thelads.core.v1_21_11.adapter.VanillaGameBridge12111;
import com.thelads.core.v1_21_11.feature.qa.mixin.GuiQaInvoker;
import com.thelads.core.v1_21_11.gui.DraggableHudScreen12111;
import com.thelads.core.v1_21_11.mixin.hud.BossBarAccessor;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.gui.render.state.GuiItemRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.GuiTextRenderState;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * U3 in-world QA of the 1.21.11 HUD pipeline, after the menu access probe (26.x Version133/134 HUD checks plus the 1.21.x ports):
 * Autohide hides the real hotbar layer and every Lads draw, fades in from hidden with back-to-back frames, carries partial alpha into
 * plates, text, items and Xaero's minimap blit, keeps editor previews opaque; SmoothHotbar, BossBar, Scoreboard, mod icons, the
 * adapter's shadow/metrics/armor/boss-bar draws and the bridge data. Uses the real game APIs on fresh render states; restores everything.
 */
final class NativeHudProbe {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static boolean started, finished;
    private static int passed;
    private static long iconDeadline;
    private NativeHudProbe() {}

    /** The last in-world probe is done (passed or failed). */
    static boolean finished() { return finished; }

    static void tick() {
        if (finished || !NativeWorldVerification.active() || !NativeMenuAccessProbe.finished()) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (!started) {
                if (!NativeWorldVerification.worldReady()) return;
                started = true;
                run(mc);
                iconDeadline = System.nanoTime() + 10_000_000_000L;
                return;
            }
            // Mod icons load off-thread; the Lads menu shows the real icon once its texture is registered.
            var state = new GuiRenderState();
            if (!com.thelads.core.v1_21_11.gui.ModIcons.draw(new GuiGraphics(mc, state, 0, 0), "fabric-api", 0, 0, 24)) {
                check(System.nanoTime() < iconDeadline, "the Fabric API mod icon loads within 10 seconds");
                return;
            }
            check(count(state) == 1, "a loaded mod icon is drawn from its texture");
            finished = true;
            LOGGER.info("Lads HUD pipeline probe END: {} passed, 0 failed; Autohide on the real hotbar and Lads HUD render states, "
                + "Xaero minimap blit, SmoothHotbar, BossBar, Scoreboard, mod icons, adapter and bridge data; fixture restored", passed);
        } catch (Throwable failure) {
            finished = true;
            LOGGER.error("Lads HUD pipeline probe FAILED after {} checks", passed, failure);
        }
    }

    private static void run(Minecraft mc) {
        var snapshot = ConfigManager.toJson(); var screen = mc.screen; var bridge = LadsGameBridge.get();
        var boots = mc.player.getItemBySlot(EquipmentSlot.FEET).copy(); int selected = mc.player.getInventory().getSelectedSlot();
        var bosses = ((BossBarAccessor) mc.gui.getBossOverlay()).ladsEvents(); UUID boss = UUID.randomUUID();
        var board = mc.level.getScoreboard(); var sidebar = board.getDisplayObjective(DisplaySlot.SIDEBAR);
        var objective = board.addObjective("lads_qa_u3", ObjectiveCriteria.DUMMY, Component.literal("Lads QA"), ObjectiveCriteria.RenderType.INTEGER, false, null);
        boolean hadSpeed = mc.player.hasEffect(MobEffects.SPEED);
        try {
            LadsGameBridge.set(new VanillaGameBridge12111());
            // These checks call the HUD microseconds apart; under the HUD FPS cap they would all see one replayed build.
            com.thelads.core.config.HudSettings.getInstance().setHudFpsCapEnabled(false);
            for (String name : List.of("Autohide", "SmoothHotbar", "BossBar", "Scoreboard", "ArmorHUD")) check(ModuleSupport.isBuiltIn(name), name + " is a built-in module");
            check(!FabricLoader.getInstance().isModLoaded("autohidehud"), "the retired Auto Hide HUD jar is not loaded");
            check(FabricLoader.getInstance().isModLoaded("xaerominimap") == ModuleSupport.isBuiltIn("Minimap"), "Minimap is built in exactly when Xaero's minimap is loaded");
            check(Files.exists(ClientPaths.getBaseDir().resolve(".lads-adopted-autohidehud")), "the retired jar's on/off choice was adopted once for this profile");
            HudSettings.getInstance().replaceGroups(List.of()); HudSettings.getInstance().clearPositions();
            for (var element : HudManager.getInstance().getElements()) module(element.getModuleName()).setEnabled(false);
            for (String name : List.of("FPS", "ArmorHUD", "Autohide")) module(name).setEnabled(true);
            HudSettings.getInstance().setBackgrounds(true); HudSettings.getInstance().setGlobalBackground(0xAA000000);
            ((ColorOption) module("FPS").getOption("Background")).setUseGlobal(true);
            ((BoolOption) module("Autohide").getOption("Show when hurt or hungry")).set(false);
            ((BoolOption) module("Autohide").getOption("Show while moving")).set(false);
            ((SliderOption) module("Autohide").getOption("Fade milliseconds")).setValue(0);
            mc.player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS)); mc.setScreen(null);
            var state = new GuiRenderState(); var graphics = new GuiGraphics(mc, state, 0, 0);

            NativeAutohide.scopeOpacity = 0;
            graphics.fill(1, 1, 30, 30, -1); graphics.drawString(mc.font, "hidden", 2, 2, -1, false); graphics.renderItem(new ItemStack(Items.STONE), 3, 3);
            check(count(state) == 0, "fully faded sprites, text and items are not submitted");
            NativeAutohide.scopeOpacity = .5f; graphics.fill(1, 1, 30, 30, -1);
            check(anyElement(state, e -> e instanceof FadedElement), "partial opacity is carried into the GUI vertex state");
            NativeAutohide.scopeOpacity = 1; state.reset();

            NativeAutohide.update(); NativeAutohide.activity = System.nanoTime() - 60_000_000_000L;
            ((GuiQaInvoker) mc.gui).ladsQaHotbar(graphics, mc.getDeltaTracker());
            check(count(state) == 0, "the idle real hotbar layer (hotbar, highlight, status bars, XP) submits nothing");
            check(NativeAutohide.scopeOpacity == 1, "the hotbar restores the GUI opacity scope");
            NativeAutohide.renderLadsHud(graphics);
            check(count(state) == 0, "idle Lads text, plates and armor all disappear");
            graphics.fill(1, 1, 3, 3, -1); check(count(state) > 0, "later GUI content remains visible"); state.reset();

            // Fade back in from fully hidden with calls microseconds apart (far above 143 FPS, where 1.3.4 snapped every step back to 0).
            ((SliderOption) module("Autohide").getOption("Fade milliseconds")).setValue(350);
            check(NativeAutohide.opacity == 0, "idle Autohide reaches fully hidden");
            NativeAutohide.activity = NativeAutohide.frame = System.nanoTime();
            long until = System.nanoTime() + 2_000_000_000L; int calls = 0; float shown = 0;
            while (shown < 1 && System.nanoTime() < until) { shown = NativeAutohide.update(); calls++; }
            check(shown == 1 && calls > 1000, "fade-in from hidden completes with back-to-back frames (" + calls + " calls)");

            ((SliderOption) module("Autohide").getOption("Fade milliseconds")).setValue(1000);
            half();
            NativeAutohide.renderLadsHud(graphics);
            check(anyElement(state, e -> e instanceof FadedElement), "a Lads plate carries partial alpha into the GUI render state");
            check(anyText(state, t -> (t.color >>> 24) > 0 && (t.color >>> 24) < 255), "Lads text carries partial alpha into the deferred text state");
            check(anyItem(state, i -> ((FadedItem) (Object) i).ladsOpacity() > 0 && ((FadedItem) (Object) i).ladsOpacity() < 1), "Lads armor carries partial alpha into the deferred item state");
            state.reset();

            if (MinimapIntegration.available()) {
                // Fabric resolves HUD element replacements while the HUD renders.
                if (MinimapIntegration.faded == null) { mc.gui.render(graphics, mc.getDeltaTracker()); state.reset(); }
                check(MinimapIntegration.faded != null, "Xaero's HUD element runs inside the Autohide scope");
                NativeAutohide.activity = System.nanoTime() - 60_000_000_000L; NativeAutohide.opacity = 0; NativeAutohide.frame = System.nanoTime();
                MinimapIntegration.faded.render(graphics, mc.getDeltaTracker());
                check(count(state) == 0, "a hidden HUD hides Xaero's minimap");
                half(); NativeAutohide.PICTURES.clear();
                MinimapIntegration.faded.render(graphics, mc.getDeltaTracker());
                PictureInPictureRenderState[] map = {null};
                state.forEachPictureInPicture(picture -> { if (picture.getClass().getName().startsWith("xaero.")) map[0] = picture; });
                check(map[0] != null, "Xaero submits its minimap picture");
                Float alpha = NativeAutohide.PICTURES.get(map[0]);
                check(alpha != null && alpha > 0 && alpha < 1, "the minimap picture keeps the partial HUD opacity for its blit (" + alpha + ")");
                state.reset();
                var renderer = new PictureInPictureRenderer<PictureInPictureRenderState>(mc.renderBuffers().bufferSource()) {
                    public Class<PictureInPictureRenderState> getRenderStateClass() { return PictureInPictureRenderState.class; }
                    protected void renderToTexture(PictureInPictureRenderState picture, PoseStack pose) {}
                    protected String getTextureLabel() { return "lads qa"; }
                    void blit(PictureInPictureRenderState picture, GuiRenderState target) { blitTexture(picture, target); }
                };
                try { renderer.blit(map[0], state); } finally { renderer.close(); }
                int faded = AutohideFade.tintPremultiplied(-1, alpha);
                check(anyElement(state, e -> e instanceof BlitRenderState blit && blit.color() == faded), "the minimap blit fades every premultiplied channel");
                state.reset();
            }

            var controller = new DraggableHudScreen(() -> {});
            mc.setScreen(new DraggableHudScreen12111(null, controller)); state.reset();
            NativeAutohide.renderLadsHud(graphics); check(count(state) == 0, "the editor suppresses the duplicate live HUD pass");
            controller.render(new GuiGraphicsLadsAdapter(graphics, mc.font), -1, -1);
            check(count(state) > 0 && !anyElement(state, e -> e instanceof FadedElement), "editor controls and previews stay visible and opaque while Autohide is enabled");
            controller.close(); mc.setScreen(null); state.reset();
            NativeAutohide.activity = NativeAutohide.frame = System.nanoTime(); NativeAutohide.opacity = 1;

            var adapter = new GuiGraphicsLadsAdapter(graphics, mc.font);
            adapter.drawCenteredText("Shadow", 50, 50, -1, false); adapter.drawCenteredText("Shadow", 50, 60, -1, true);
            List<Boolean> shadows = new ArrayList<>(); state.forEachText(text -> shadows.add(text.dropShadow));
            check(shadows.equals(List.of(false, true)), "centered HUD text honours the Shadow switch, got " + shadows); state.reset();
            Object key = adapter.textMetricsKey();
            check(key != null && key == adapter.textMetricsKey(), "HUD text widths are cached per font");
            GuiGraphicsLadsAdapter.invalidateMetrics(); check(adapter.textMetricsKey() != key, "a font reload invalidates cached widths");
            adapter.drawArmorItem(0, 5, 5, false); check(countItems(state) == 1, "ArmorHUD draws the equipped boots as an item"); state.reset();
            adapter.drawBossBars(10, 10, 5, true, true); check(anyElement(state, e -> true) && anyText(state, t -> true), "the BossBar preview draws a bar and its name"); state.reset();

            bosses.put(boss, new LerpingBossEvent(boss, Component.literal("QA boss"), .5f, BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS, true, false, false));
            check(new VanillaGameBridge12111().bossBarCount() == 1, "the bridge counts live boss bars");
            module("BossBar").setEnabled(true); mc.gui.getBossOverlay().render(graphics);
            check(count(state) == 0, "BossBar replaces vanilla's boss bars");
            ((BoolOption) module("BossBar").getOption("Darken sky")).set(false);
            check(!mc.gui.getBossOverlay().shouldDarkenScreen(), "Darken sky off stops a boss darkening the sky");
            module("BossBar").setEnabled(false); mc.gui.getBossOverlay().render(graphics);
            check(count(state) > 0 && mc.gui.getBossOverlay().shouldDarkenScreen(), "vanilla boss bars and sky return with BossBar off");
            bosses.remove(boss); state.reset();

            board.setDisplayObjective(DisplaySlot.SIDEBAR, objective);
            board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("QA"), objective).set(7);
            LadsGameBridge.set(new VanillaGameBridge12111());
            var sidebarSnapshot = LadsGameBridge.get().getScoreboard();
            check(sidebarSnapshot != null && sidebarSnapshot.title().equals("Lads QA") && sidebarSnapshot.lines().contains(new LadsGameBridge.ScoreLine("QA", "7")),
                "Scoreboard reads the real sidebar objective");
            module("Scoreboard").setEnabled(true); ((GuiQaInvoker) mc.gui).ladsQaSidebar(graphics, objective);
            check(count(state) == 0, "Scoreboard replaces vanilla's sidebar");
            module("Scoreboard").setEnabled(false); ((GuiQaInvoker) mc.gui).ladsQaSidebar(graphics, objective);
            check(count(state) > 0, "vanilla's sidebar returns with Scoreboard off"); state.reset();

            mc.player.addEffect(new MobEffectInstance(MobEffects.SPEED, 600));
            var data = new VanillaGameBridge12111();
            var effects = data.getActivePotionEffects();
            check(effects.contains(Component.translatable("effect.minecraft.speed").getString() + " (30s)") && effects.stream().noneMatch(e -> e.startsWith("effect.")),
                "effect names are translated, got " + effects);
            check(data.getActivePotionEffects() == effects, "effect names are cached within a tick");
            check(data.getBiomeId() != null && data.getBiomeId().contains(":"), "the biome ID is namespaced: " + data.getBiomeId());
            check(data.getYaw() >= 0 && data.getYaw() < 360, "yaw is normalized to [0, 360)");
            check(data.getAbsorption() >= 0 && data.getSaturation() >= 0, "absorption and integrated-server saturation are real values");
            String bootsName = new ItemStack(Items.DIAMOND_BOOTS).getHoverName().getString();
            check(data.getArmor().stream().anyMatch(piece -> piece.name().equals(bootsName) && piece.maximum() > 0), "ArmorHUD lists the equipped boots with durability");

            // Rest on slot 1 first (SmoothHotbar off snaps there), whatever slot an earlier run left selected.
            module("SmoothHotbar").setEnabled(false);
            var hotbar = (GuiQaInvoker) mc.gui;
            mc.player.getInventory().setSelectedSlot(0); hotbar.ladsQaItemHotbar(graphics, mc.getDeltaTracker()); state.reset();
            module("SmoothHotbar").setEnabled(true);
            mc.player.getInventory().setSelectedSlot(4); hotbar.ladsQaItemHotbar(graphics, mc.getDeltaTracker());
            float moving = selectionOffset(state); state.reset();
            check(moving < -40, "SmoothHotbar slides the highlight from the previous slot (" + moving + ")");
            module("SmoothHotbar").setEnabled(false); hotbar.ladsQaItemHotbar(graphics, mc.getDeltaTracker());
            check(Math.abs(selectionOffset(state)) < 1e-3, "without SmoothHotbar the highlight jumps to the slot"); state.reset();
            com.thelads.core.v1_21_11.gui.ModIcons.draw(graphics, "fabric-api", 0, 0, 24);
        } finally {
            NativeAutohide.scopeOpacity = 1; NativeAutohide.activity = NativeAutohide.frame = System.nanoTime(); NativeAutohide.opacity = 1;
            NativeAutohide.PICTURES.clear();
            bosses.remove(boss);
            board.setDisplayObjective(DisplaySlot.SIDEBAR, sidebar); board.removeObjective(objective);
            if (!hadSpeed) mc.player.removeEffect(MobEffects.SPEED);
            mc.player.getInventory().setSelectedSlot(selected);
            mc.player.setItemSlot(EquipmentSlot.FEET, boots);
            LadsGameBridge.set(bridge); ConfigManager.applyJson(snapshot);
            if (mc.screen != screen) mc.setScreen(screen);
        }
    }

    /** Half-faded HUD for the next update(): hidden target, 1 s fade, microseconds elapsed. */
    private static void half() {
        NativeAutohide.activity = System.nanoTime() - 60_000_000_000L; NativeAutohide.opacity = .5f; NativeAutohide.frame = System.nanoTime();
    }
    /** The selected-slot highlight (24x23, or 24x24 tiled with Hovering Hotbar) and its X translation. */
    private static float selectionOffset(GuiRenderState state) {
        float[] offset = {Float.NaN};
        state.forEachElement(element -> {
            if (element instanceof BlitRenderState blit && blit.x1() - blit.x0() == 24 && blit.y1() - blit.y0() == 23) offset[0] = blit.pose().m20();
            if (element instanceof net.minecraft.client.gui.render.state.TiledBlitRenderState tiled && tiled.x1() - tiled.x0() == 24 && tiled.y1() - tiled.y0() == 24) offset[0] = tiled.pose().m20();
        }, GuiRenderState.TraverseRange.ALL);
        check(!Float.isNaN(offset[0]), "the hotbar drew its selected-slot highlight");
        return offset[0];
    }
    private static Module module(String name) { return ModuleManager.getInstance().getModule(name); }
    private static int count(GuiRenderState state) {
        int[] n = {0};
        state.forEachElement(e -> n[0]++, GuiRenderState.TraverseRange.ALL); state.forEachText(e -> n[0]++); state.forEachItem(e -> n[0]++); state.forEachPictureInPicture(e -> n[0]++);
        return n[0];
    }
    private static int countItems(GuiRenderState state) { int[] n = {0}; state.forEachItem(e -> n[0]++); return n[0]; }
    private static boolean anyElement(GuiRenderState state, Predicate<GuiElementRenderState> test) {
        boolean[] found = {false}; state.forEachElement(e -> found[0] |= test.test(e), GuiRenderState.TraverseRange.ALL); return found[0];
    }
    private static boolean anyText(GuiRenderState state, Predicate<GuiTextRenderState> test) { boolean[] found = {false}; state.forEachText(t -> found[0] |= test.test(t)); return found[0]; }
    private static boolean anyItem(GuiRenderState state, Predicate<GuiItemRenderState> test) { boolean[] found = {false}; state.forEachItem(i -> found[0] |= test.test(i)); return found[0]; }
    private static void check(boolean result, String description) {
        if (!result) throw new IllegalStateException("HUD pipeline QA: " + description);
        passed++;
    }
}
