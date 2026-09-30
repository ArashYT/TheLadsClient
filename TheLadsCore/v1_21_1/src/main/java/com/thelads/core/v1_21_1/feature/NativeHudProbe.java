package com.thelads.core.v1_21_1.feature;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.core.client.CpsTracker;
import com.thelads.core.client.bridge.LadsGameBridge;
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
import com.thelads.core.v1_21_1.adapter.GuiGraphicsLadsAdapter;
import com.thelads.core.v1_21_1.adapter.VanillaGameBridge121;
import com.thelads.core.v1_21_1.feature.qa.mixin.GuiQaInvoker;
import com.thelads.core.v1_21_1.feature.qa.mixin.MouseHandlerQaInvoker;
import com.thelads.core.v1_21_1.mixin.hud.BossBarAccessor;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
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
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * U3 in-world QA of the 1.21.1 HUD pipeline, after the menu access probe. Draw calls through a recording buffer source and
 * GuiGraphics.innerBlit prove the mechanisms: the Autohide scope (shader alpha, including a mod's colour reset, and the
 * unbatched source), SmoothHotbar, BossBar, Scoreboard, centered-text shadow, armor items, CPS press events and the bridge data.
 * Three real frames, read back at forced opacities 1, 0 and 0.5, prove the result on screen with every pack mod (ImmediatelyFast
 * HUD batching, Xaero, AppleSkin): the hotbar and a Lads plate blend halfway, and a hidden HUD also hides Xaero's minimap.
 */
public final class NativeHudProbe {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    /** GuiGraphicsQaMixin: sprite blits recorded as {width, height, pose x, shader alpha} while armed. */
    public static boolean recording;
    private static final List<float[]> BLITS = new ArrayList<>();
    private static int step, passed, capture = -1, width, height, plateX, plateY;
    private static boolean finished;
    private static long wakeFrame, iconDeadline;
    private static final int[][] FRAMES = new int[3][];
    private static JsonObject snapshot;
    private static LadsGameBridge bridge;
    private static String pixels = "";
    private NativeHudProbe() {}

    public static void blit(GuiGraphics graphics, int w, int h) {
        BLITS.add(new float[] {w, h, graphics.pose().last().pose().m30(), RenderSystem.getShaderColor()[3]});
    }

    static void tick() {
        if (finished || !NativeWorldVerification.active() || !NativeMenuAccessProbe.finished()
            || capture >= 0 || NativeWorldVerification.frames() < wakeFrame) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            switch (step) {
                case 0 -> {
                    if (!NativeWorldVerification.worldReady()) return;
                    snapshot = ConfigManager.toJson(); bridge = LadsGameBridge.get();
                    try { mechanisms(mc); } finally { NativeAutohide.forced = Float.NaN; LadsGameBridge.set(bridge); ConfigManager.applyJson(snapshot); }
                    fixture(mc);
                    request(0, 1);
                }
                case 1 -> request(1, 0);
                case 2 -> request(2, .5f);
                case 3 -> {
                    try { frames(mc); } finally { restore(); }
                    iconDeadline = System.nanoTime() + 10_000_000_000L;
                }
                default -> {
                    // Mod icons load off-thread; the Lads menu draws the real icon once its texture is registered.
                    if (!com.thelads.core.v1_21_1.gui.ModIcons.draw(new GuiGraphics(mc, mc.renderBuffers().bufferSource()), "fabric-api", 0, 0, 24)) {
                        check(System.nanoTime() < iconDeadline, "the Fabric API mod icon loads within 10 seconds");
                        return;
                    }
                    passed++;
                    finished = true;
                    LOGGER.info("Lads HUD pipeline probe END: {} passed, 0 failed; Autohide shader-alpha scope on the real hotbar and Lads HUD, "
                        + "real frames at opacity 1/0/0.5 with the production pack ({}), SmoothHotbar, BossBar, Scoreboard, CPS, mod icons, adapter and bridge data; fixture restored", passed, pixels);
                    return;
                }
            }
            step++;
        } catch (Throwable failure) {
            finished = true;
            restore();
            LOGGER.error("Lads HUD pipeline probe FAILED after {} checks", passed, failure);
        }
    }

    /** At GameRenderer.render TAIL: the completed frame (world, GUI) for a requested opacity. */
    static void frame(RenderTarget target) {
        if (capture < 0 || NativeWorldVerification.frames() < wakeFrame) return;
        try (NativeImage image = Screenshot.takeScreenshot(target)) {
            width = image.getWidth(); height = image.getHeight(); FRAMES[capture] = image.getPixelsRGBA();
            image.writeToFile(NativeWorldVerification.qaScreenshot("native-autohide-" + List.of("shown", "hidden", "half").get(capture) + "-"));
        } catch (RuntimeException | java.io.IOException failure) { LOGGER.error("Lads HUD pipeline probe frame readback failed", failure); }
        capture = -1;
    }

    /** Forces an opacity, then reads back a frame a few frames later (motion blur and Dynamic FPS settle). */
    private static void request(int index, float opacity) {
        NativeAutohide.forced = opacity;
        capture = index;
        wakeFrame = NativeWorldVerification.frames() + 8;
    }

    private static void mechanisms(Minecraft mc) throws Exception {
        for (String name : List.of("Autohide", "SmoothHotbar", "BossBar", "Scoreboard", "ArmorHUD")) check(ModuleSupport.isBuiltIn(name), name + " is a built-in module");
        check(!FabricLoader.getInstance().isModLoaded("autohidehud"), "the retired Auto Hide HUD jar is not loaded");
        check(FabricLoader.getInstance().isModLoaded("xaerominimap") == ModuleSupport.isBuiltIn("Minimap"), "Minimap is built in exactly when Xaero's minimap is loaded");
        check(Files.exists(ClientPaths.getBaseDir().resolve(".lads-adopted-autohidehud")), "the retired jar's on/off choice was adopted once for this profile");
        module("Autohide").setEnabled(true);
        ((BoolOption) module("Autohide").getOption("Show when hurt or hungry")).set(false);
        ((BoolOption) module("Autohide").getOption("Show while moving")).set(false);
        ((SliderOption) module("Autohide").getOption("Fade milliseconds")).setValue(0);
        NativeAutohide.update(); NativeAutohide.activity = System.nanoTime() - 60_000_000_000L;
        check(NativeAutohide.update() == 0, "idle Autohide reaches fully hidden");
        // Fade back in from fully hidden with calls microseconds apart (far above 143 FPS, where 1.3.4 snapped every step back to 0).
        ((SliderOption) module("Autohide").getOption("Fade milliseconds")).setValue(350);
        NativeAutohide.activity = NativeAutohide.frame = System.nanoTime();
        long until = System.nanoTime() + 2_000_000_000L; int calls = 0; float shown = 0;
        while (shown < 1 && System.nanoTime() < until) { shown = NativeAutohide.update(); calls++; }
        check(shown == 1 && calls > 1000, "fade-in from hidden completes with back-to-back frames (" + calls + " calls)");

        var boots = mc.player.getItemBySlot(EquipmentSlot.FEET).copy(); int selected = mc.player.getInventory().selected;
        var bosses = ((BossBarAccessor) mc.gui.getBossOverlay()).ladsEvents(); UUID boss = UUID.randomUUID();
        var board = mc.level.getScoreboard(); var sidebar = board.getDisplayObjective(DisplaySlot.SIDEBAR);
        var objective = board.addObjective("lads_qa_u3", ObjectiveCriteria.DUMMY, Component.literal("Lads QA"), ObjectiveCriteria.RenderType.INTEGER, false, null);
        boolean hadSpeed = mc.player.hasEffect(MobEffects.MOVEMENT_SPEED);
        var buffer = new ByteBufferBuilder(256);
        var record = new Recording(buffer);
        var graphics = new GuiGraphics(mc, record);
        try {
            LadsGameBridge.set(new VanillaGameBridge121());
            mc.player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
            recording = true;
            // A hidden HUD draws nothing: the whole hotbar layer (Xaero's map included) and the Lads HUD are skipped.
            NativeAutohide.forced = 0;
            ((GuiQaInvoker) mc.gui).ladsQaHotbar(graphics, mc.getTimer());
            NativeAutohide.renderLadsHud(graphics);
            check(record.buffers == 0 && BLITS.isEmpty(), "a hidden HUD skips the real hotbar layer and every Lads draw");
            // Half faded: the hotbar and highlight sprites draw at half shader alpha, through the game's own buffer source, and the scope
            // closes. (The whole layer, with Xaero's HEAD draw, is proven on real frames below.)
            NativeAutohide.forced = Float.NaN;
            NativeAutohide.begin(graphics, .5f);
            try { ((GuiQaInvoker) mc.gui).ladsQaItemHotbar(graphics, mc.getTimer()); } finally { NativeAutohide.end(graphics); }
            check(BLITS.stream().anyMatch(b -> b[0] == 182 && b[1] == 22) && BLITS.stream().filter(b -> b[0] == 182 || b[0] == 24).allMatch(b -> Math.abs(b[3] - .5f) < 1e-3),
                "the real hotbar and highlight sprites draw at half shader alpha");
            check(record.buffers == 0 && graphics.bufferSource() == record, "a faded scope draws unbatched and restores the caller's buffer source");
            check(NativeAutohide.scopeOpacity == 1 && RenderSystem.getShaderColor()[3] == 1, "the scope restores full opacity for later GUI draws");
            NativeAutohide.begin(graphics, .5f); RenderSystem.setShaderColor(1, 1, 1, 1);
            float reset = RenderSystem.getShaderColor()[3]; NativeAutohide.end(graphics);
            check(Math.abs(reset - .5f) < 1e-3 && RenderSystem.getShaderColor()[3] == 1, "a mod resetting the shader colour inside the scope stays faded");
            NativeAutohide.forced = Float.NaN; BLITS.clear(); record.clear();

            var adapter = new GuiGraphicsLadsAdapter(graphics, mc.font);
            adapter.drawCenteredText("Shadow", 50, 50, 0xFFFFFFFF, false);
            check(!record.colors.isEmpty() && record.colors.stream().allMatch(c -> (c & 0xFFFFFF) == 0xFFFFFF), "centered HUD text without shadow draws no shadow pass");
            record.clear(); adapter.drawCenteredText("Shadow", 50, 50, 0xFFFFFFFF, true);
            check(record.colors.stream().anyMatch(c -> (c >> 16 & 255) < 100), "centered HUD text with shadow draws its dimmed shadow pass");
            record.clear();
            Object key = adapter.textMetricsKey();
            check(key != null && key == adapter.textMetricsKey(), "HUD text widths are cached per font");
            GuiGraphicsLadsAdapter.invalidateMetrics(); check(adapter.textMetricsKey() != key, "a font reload invalidates cached widths");
            adapter.drawArmorItem(0, 5, 5, false); check(record.buffers > 0, "ArmorHUD draws the equipped boots as an item"); record.clear();
            adapter.drawBossBars(10, 10, 5, true, true); check(!record.colors.isEmpty(), "the BossBar preview draws its bar name"); record.clear();

            bosses.put(boss, new LerpingBossEvent(boss, Component.literal("QA boss"), .5f, BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS, true, false, false));
            check(new VanillaGameBridge121().bossBarCount() == 1, "the bridge counts live boss bars");
            module("BossBar").setEnabled(true); mc.gui.getBossOverlay().render(graphics);
            check(record.buffers == 0, "BossBar replaces vanilla's boss bars");
            ((BoolOption) module("BossBar").getOption("Darken sky")).set(false);
            check(!mc.gui.getBossOverlay().shouldDarkenScreen(), "Darken sky off stops a boss darkening the sky");
            module("BossBar").setEnabled(false); mc.gui.getBossOverlay().render(graphics);
            check(record.buffers > 0 && mc.gui.getBossOverlay().shouldDarkenScreen(), "vanilla boss bars and sky return with BossBar off");
            bosses.remove(boss); record.clear();

            board.setDisplayObjective(DisplaySlot.SIDEBAR, objective);
            board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("QA"), objective).set(7);
            LadsGameBridge.set(new VanillaGameBridge121());
            var sidebarSnapshot = LadsGameBridge.get().getScoreboard();
            check(sidebarSnapshot != null && sidebarSnapshot.title().equals("Lads QA") && sidebarSnapshot.lines().contains(new LadsGameBridge.ScoreLine("QA", "7")),
                "Scoreboard reads the real sidebar objective");
            module("Scoreboard").setEnabled(true); ((GuiQaInvoker) mc.gui).ladsQaSidebar(graphics, objective);
            check(record.buffers == 0, "Scoreboard replaces vanilla's sidebar");
            module("Scoreboard").setEnabled(false); ((GuiQaInvoker) mc.gui).ladsQaSidebar(graphics, objective);
            check(record.buffers > 0, "vanilla's sidebar returns with Scoreboard off"); record.clear();

            mc.player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600));
            var data = new VanillaGameBridge121();
            var effects = data.getActivePotionEffects();
            check(effects.contains(Component.translatable("effect.minecraft.speed").getString() + " (30s)") && effects.stream().noneMatch(e -> e.startsWith("effect.")),
                "effect names are translated, got " + effects);
            check(data.getActivePotionEffects() == effects, "effect names are cached within a tick");
            check(data.getBiomeId() != null && data.getBiomeId().contains(":"), "the biome ID is namespaced: " + data.getBiomeId());
            check(data.getYaw() >= 0 && data.getYaw() < 360, "yaw is normalized to [0, 360)");
            check(data.getAbsorption() >= 0 && data.getSaturation() >= 0, "absorption and integrated-server saturation are real values");
            String bootsName = new ItemStack(Items.DIAMOND_BOOTS).getHoverName().getString();
            check(data.getArmor().stream().anyMatch(piece -> piece.name().equals(bootsName) && piece.maximum() > 0), "ArmorHUD lists the equipped boots with durability");

            module("SmoothHotbar").setEnabled(true);
            var hotbar = (GuiQaInvoker) mc.gui;
            mc.player.getInventory().selected = 0; hotbar.ladsQaItemHotbar(graphics, mc.getTimer()); BLITS.clear();
            mc.player.getInventory().selected = 4; hotbar.ladsQaItemHotbar(graphics, mc.getTimer());
            float moving = selectionOffset(); BLITS.clear();
            check(moving < -40, "SmoothHotbar slides the highlight from the previous slot (" + moving + ")");
            module("SmoothHotbar").setEnabled(false); hotbar.ladsQaItemHotbar(graphics, mc.getTimer());
            check(Math.abs(selectionOffset()) < 1e-3, "without SmoothHotbar the highlight jumps to the slot"); BLITS.clear();

            // CPS counts press events, several between two frames included (1.21.1 counted rendered frames).
            check(mc.player.getMainHandItem().isEmpty() && mc.player.getOffhandItem().isEmpty(), "the QA player uses empty hands for synthetic clicks");
            int before = CpsTracker.get().rightCps();
            NativeWorldVerification.syntheticInput(true);
            try {
                for (int i = 0; i < 3; i++) {
                    ((MouseHandlerQaInvoker) mc.mouseHandler).ladsQaPress(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_RIGHT, GLFW.GLFW_PRESS, 0);
                    ((MouseHandlerQaInvoker) mc.mouseHandler).ladsQaPress(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_RIGHT, GLFW.GLFW_RELEASE, 0);
                }
            } finally { NativeWorldVerification.syntheticInput(false); mc.options.keyUse.setDown(false); }
            check(CpsTracker.get().rightCps() == before + 3, "three right clicks within one tick count 3 CPS");
            com.thelads.core.v1_21_1.gui.ModIcons.draw(graphics, "fabric-api", 0, 0, 24);
        } finally {
            recording = false; BLITS.clear();
            NativeAutohide.forced = Float.NaN;
            bosses.remove(boss);
            board.setDisplayObjective(DisplaySlot.SIDEBAR, sidebar); board.removeObjective(objective);
            if (!hadSpeed) mc.player.removeEffect(MobEffects.MOVEMENT_SPEED);
            mc.player.getInventory().selected = selected;
            mc.player.setItemSlot(EquipmentSlot.FEET, boots);
            buffer.close();
        }
    }

    /** Real frames: only the Day plate (constant text, opaque magenta, over the static ground) and Xaero's map, Autohide on. */
    private static void fixture(Minecraft mc) {
        HudSettings.getInstance().replaceGroups(List.of()); HudSettings.getInstance().clearPositions();
        for (var element : HudManager.getInstance().getElements()) module(element.getModuleName()).setEnabled(false);
        for (String name : List.of("Day", "Autohide", "Minimap")) module(name).setEnabled(true);
        HudSettings.getInstance().setBackgrounds(true); HudSettings.getInstance().setGlobalBackground(0xFFFF00FF);
        ((ColorOption) module("Day").getOption("Background")).setUseGlobal(true);
        plateX = mc.getWindow().getGuiScaledWidth() - 120; plateY = mc.getWindow().getGuiScaledHeight() - 80;
        HudSettings.getInstance().setPosition("Day", plateX, plateY);
        mc.player.getInventory().selected = 0;
    }

    private static void frames(Minecraft mc) {
        for (int[] frame : FRAMES) check(frame != null && frame.length == width * height, "three completed frames were read back");
        int[] shown = FRAMES[0], hidden = FRAMES[1], half = FRAMES[2];
        double scale = mc.getWindow().getGuiScale();
        // The Lads plate: opaque magenta at full opacity, the world when hidden, halfway at 0.5.
        int plate = 0, plateBlend = 0;
        for (int y = (int) (plateY * scale); y < Math.min(height, (plateY + 30) * scale); y++)
            for (int x = (int) (plateX * scale); x < Math.min(width, (plateX + 80) * scale); x++) {
                int i = y * width + x;
                if (!magenta(shown[i])) continue;
                plate++;
                if (!magenta(hidden[i]) && halfway(shown[i], hidden[i], half[i])) plateBlend++;
            }
        check(plate >= 50 && plateBlend >= plate * .85, "the Lads plate blends halfway at 0.5 and is gone when hidden (" + plateBlend + "/" + plate + " pixels)");
        // The hotbar: bottom 22 GUI rows around the centre, past the selected first slot.
        int bar = 0, barBlend = 0;
        for (int y = (int) (height - 22 * scale); y < height; y++)
            for (int x = (int) (width / 2 - 60 * scale); x < width / 2 + 60 * scale; x++) {
                int i = y * width + x;
                if (distance(shown[i], hidden[i]) < 90) continue;
                bar++;
                if (halfway(shown[i], hidden[i], half[i])) barBlend++;
            }
        check(bar >= 200 && barBlend >= bar * .85, "the real hotbar blends halfway at 0.5 and is gone when hidden (" + barBlend + "/" + bar + " pixels)");
        if (MinimapIntegration.available()) {
            int[] size = MinimapIntegration.size();
            int mapX = mc.getWindow().getGuiScaledWidth() - size[0] - 5, mapY = 44, map = 0, gone = 0;
            for (int y = (int) ((mapY + size[1] / 4) * scale); y < Math.min(height, (mapY + size[1] * 3 / 4) * scale); y++)
                for (int x = (int) ((mapX + size[0] / 4) * scale); x < Math.min(width, (mapX + size[0] * 3 / 4) * scale); x++) {
                    map++;
                    if (distance(shown[y * width + x], hidden[y * width + x]) >= 90) gone++;
                }
            check(map > 0 && gone >= map * .3, "a hidden HUD hides Xaero's minimap (" + gone + "/" + map + " pixels change)");
            pixels = "plate " + plateBlend + "/" + plate + " and hotbar " + barBlend + "/" + bar + " pixels halfway, minimap " + gone + "/" + map + " pixels gone when hidden";
        } else pixels = "plate " + plateBlend + "/" + plate + " and hotbar " + barBlend + "/" + bar + " pixels halfway";
    }

    private static void restore() {
        NativeAutohide.forced = Float.NaN; capture = -1; recording = false;
        if (bridge != null) LadsGameBridge.set(bridge);
        if (snapshot != null) ConfigManager.applyJson(snapshot);
        snapshot = null;
    }

    /** The selected-slot highlight (24 wide, 23 tall or 24 with HoveringHotbar) and its X translation. */
    private static float selectionOffset() {
        float offset = Float.NaN;
        for (float[] b : BLITS) if (b[0] == 24 && (b[1] == 23 || b[1] == 24)) offset = b[2];
        check(!Float.isNaN(offset), "the hotbar drew its selected-slot highlight");
        return offset;
    }
    private static int channel(int abgr, int shift) { return abgr >> shift & 255; }
    private static boolean magenta(int abgr) { return channel(abgr, 0) > 230 && channel(abgr, 8) < 30 && channel(abgr, 16) > 230; }
    private static int distance(int a, int b) {
        int sum = 0;
        for (int shift = 0; shift < 24; shift += 8) sum += Math.abs(channel(a, shift) - channel(b, shift));
        return sum;
    }
    private static boolean halfway(int shown, int hidden, int half) {
        for (int shift = 0; shift < 24; shift += 8)
            if (Math.abs(channel(half, shift) - (channel(shown, shift) + channel(hidden, shift)) / 2.0) > 16) return false;
        return true;
    }
    private static Module module(String name) { return ModuleManager.getInstance().getModule(name); }
    private static void check(boolean result, String description) {
        if (!result) throw new IllegalStateException("HUD pipeline QA: " + description);
        passed++;
    }

    /** Keeps the vertex colours GuiGraphics sends and draws nothing. */
    private static final class Recording extends MultiBufferSource.BufferSource {
        final List<Integer> colors = new ArrayList<>();
        int buffers;
        private final VertexConsumer consumer = new VertexConsumer() {
            public VertexConsumer addVertex(float x, float y, float z) { return this; }
            public VertexConsumer setColor(int r, int g, int b, int a) { colors.add(a << 24 | r << 16 | g << 8 | b); return this; }
            public VertexConsumer setUv(float u, float v) { return this; }
            public VertexConsumer setUv1(int u, int v) { return this; }
            public VertexConsumer setUv2(int u, int v) { return this; }
            public VertexConsumer setNormal(float x, float y, float z) { return this; }
        };
        Recording(ByteBufferBuilder buffer) { super(buffer, new LinkedHashMap<>()); }
        @Override public VertexConsumer getBuffer(RenderType type) { buffers++; return consumer; }
        @Override public void endLastBatch() {}
        @Override public void endBatch() {}
        @Override public void endBatch(RenderType type) {}
        void clear() { colors.clear(); buffers = 0; }
    }
}
