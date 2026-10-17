package com.thelads.core.v1_8_9.feature.jade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.hud.HudCover;
import com.thelads.core.client.jade.JadeMath;
import com.thelads.core.config.ActionOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.v1_8_9.feature.GlState189;
import com.thelads.core.v1_8_9.feature.Options189;
import com.thelads.core.v1_8_9.feature.jade.JadeLines189.Line;
import com.thelads.core.v1_8_9.feature.jade.JadeLines189.Panel;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

/**
 * The Jade module on 1.8.9: the Lads "look-at info" panel for the block, fluid or entity under the crosshair (JadeFeatures1201's
 * port). Stands down while Waila is loaded. The lines are rebuilt every client tick and the panel is drawn with a fade after
 * Forge's whole overlay (Post ALL), as NativeHud draws the Lads HUD.
 */
public final class Jade189 {
    public static final String MODULE = "Jade";
    private static final Logger LOG = LogManager.getLogger("TheLadsCore-1.8.9");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final float FADE_MS = 150f;
    private static boolean registered, failed;
    private static Config config;
    private static Panel panel = Panel.EMPTY;
    private static boolean hasTarget;
    private static float alpha;
    private static long lastFrame;
    private static int drawn;

    private Jade189() {}

    public static void register() {
        if (registered) return;
        registered = true;
        Module module = Options189.module(MODULE);
        ActionOption settings = module != null && module.getOption("Settings and addons") instanceof ActionOption
            ? (ActionOption) module.getOption("Settings and addons") : null;
        if (Loader.isModLoaded("Waila")) {
            ModuleSupport.registerExternal(MODULE, "Waila", "Waila", true);
            LOG.info("Waila is installed: the Lads Jade overlay stands down");
            return;
        }
        ModuleSupport.registerBuiltIn(MODULE);
        if (settings != null) settings.setAction(() -> {
            Minecraft mc = Minecraft.getMinecraft();
            mc.displayGuiScreen(new JadeSettingsScreen189(mc.currentScreen));
        });
        MinecraftForge.EVENT_BUS.register(new Jade189());
    }

    // ---- QA entry points (client thread) ----

    /**
     * Plain text of the lines the panel would show now for the crosshair target (display name first, mod name last; hearts read
     * "Health: 15/20"); empty with no target. Ignores the module switch, the fade and the screen/F3/Hypixel/sneak/creative gates.
     */
    public static List<String> lines(Minecraft mc) {
        Panel shown = describe(mc, config());
        return shown == null ? Collections.<String>emptyList() : shown.plain();
    }

    /** Frames in which the HUD panel was drawn (alpha above zero) since start. */
    public static int drawnFrames() {
        return drawn;
    }

    // ---- Tracking ----

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        Config config = config();
        if (mc.thePlayer == null || mc.theWorld == null || !Options189.enabled(MODULE) || HypixelSafetyManager.isDisallowed(MODULE)
            || (config.onlySneaking && !mc.thePlayer.isSneaking())
            || (config.hideCreative && mc.playerController != null && mc.playerController.isInCreativeMode())) {
            hasTarget = false;
            return;
        }
        // ponytail: rebuilt every tick (1.20.1: on change and at 5 Hz); a handful of strings, cheap next to one frame.
        Panel next = describe(mc, config);
        hasTarget = next != null;
        if (next != null) panel = next;
    }

    /** The panel for what the crosshair is on (a nearer fluid source first when fluids are shown), or null with no target. */
    private static Panel describe(Minecraft mc, Config config) {
        if (mc.theWorld == null) return null;
        MovingObjectPosition hit = mc.objectMouseOver;
        try {
            MovingObjectPosition fluid = config.displayFluids ? fluidHit(mc, hit) : null;
            if (fluid != null) return JadeLines189.block(mc, fluid, true, config);
            if (hit == null) return null;
            if (hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && hit.entityHit != null && !hit.entityHit.isDead) {
                return JadeLines189.entity(mc, hit.entityHit, hit, config);
            }
            if (hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && !mc.theWorld.isAirBlock(hit.getBlockPos())) {
                return JadeLines189.block(mc, hit, false, config);
            }
        } catch (RuntimeException e) {
            if (!failed) LOG.warn("Jade: could not describe the crosshair target", e);
            failed = true;
        }
        return null;
    }

    /** A fluid source in reach that is nearer than vanilla's crosshair target (vanilla picking ignores fluids). */
    private static MovingObjectPosition fluidHit(Minecraft mc, MovingObjectPosition vanilla) {
        net.minecraft.entity.Entity camera = mc.getRenderViewEntity();
        if (camera == null || mc.playerController == null) return null;
        Vec3 eye = camera.getPositionEyes(1f), look = camera.getLook(1f);
        double reach = mc.playerController.getBlockReachDistance();
        Vec3 end = eye.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
        MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eye, end, true); // stops on liquid sources only (BlockLiquid level 0)
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
            || !mc.theWorld.getBlockState(hit.getBlockPos()).getBlock().getMaterial().isLiquid()) return null;
        boolean nearer = vanilla == null || vanilla.typeOfHit == MovingObjectPosition.MovingObjectType.MISS || vanilla.hitVec == null
            || eye.squareDistanceTo(hit.hitVec) < eye.squareDistanceTo(vanilla.hitVec) - 1.0E-4;
        return nearer ? hit : null;
    }

    // ---- Rendering ----

    @SubscribeEvent
    public void overlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        long now = Minecraft.getSystemTime();
        float elapsed = lastFrame == 0 ? 0 : Math.min(100, now - lastFrame);
        lastFrame = now;
        Config config = config();
        boolean hidden = mc.currentScreen != null || mc.gameSettings.hideGUI || (config.hideWithDebug && mc.gameSettings.showDebugInfo)
            || HypixelSafetyManager.isDisallowed(MODULE);
        alpha = hidden ? 0 : JadeMath.approach(alpha, hasTarget ? 1 : 0, elapsed / FADE_MS);
        if (alpha <= 0 || panel.lines.isEmpty()) {
            if (!hasTarget) panel = Panel.EMPTY;
            return;
        }
        if (draw(mc, config, panel, alpha, event.resolution.getScaledWidth(), event.resolution.getScaledHeight())) drawn++;
    }

    /** The settings screen's live preview: the current panel at full opacity. */
    static boolean drawPreview(int screenWidth, int screenHeight) {
        Panel shown = panel;
        return !shown.lines.isEmpty() && draw(Minecraft.getMinecraft(), config(), shown, 1f, screenWidth, screenHeight);
    }

    private static boolean draw(Minecraft mc, Config config, Panel shown, float fade, int screenWidth, int screenHeight) {
        float scale = config.scale;
        int w = shown.width, h = shown.height, screenW = Math.round(w * scale), screenH = Math.round(h * scale);
        int x = JadeMath.place(config.anchor % 3, screenWidth, screenW, config.offsetX);
        int y = JadeMath.place(config.anchor / 3, screenHeight, screenH, config.offsetY);
        // Under the tab list or the debug text the panel steps aside with the Lads HUD (HudCover).
        if (HudCover.coveredBefore(x, y, x + screenW, y + screenH)) return false;
        boolean blend = GlState189.blend(), depth = GlState189.depth();
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y, 0);
            GlStateManager.scale(scale, scale, 1);
            GlStateManager.disableDepth();
            GlStateManager.disableAlpha(); // the alpha test would cut the fill and text early in the fade
            background(w, h, config.opacity, fade);
            int textX = JadeLines189.PAD + (shown.icon == null ? 0 : 20), lineY = JadeLines189.PAD;
            int textAlpha = Math.round(fade * 255);
            for (Line line : shown.lines) {
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
                if (line.kind == JadeLines189.HEARTS) hearts(mc, textX, lineY, line.value, line.max, fade);
                else if (line.kind == JadeLines189.ARMOR) armor(mc, textX, lineY, (int) line.value, fade);
                // FontRenderer draws a colour whose alpha is under 4 fully opaque.
                else if (textAlpha >= 4) mc.fontRendererObj.drawStringWithShadow(line.text, textX, lineY, textAlpha << 24 | 0xFFFFFF);
                lineY += line.height;
            }
            // Item models are cut out, not blended: show the icon once the fade is past half instead of popping at full alpha.
            if (shown.icon != null && fade >= 0.5f) {
                int textHeight = h - JadeLines189.PAD * 2;
                GlStateManager.color(1f, 1f, 1f, 1f);
                GlStateManager.enableDepth(); // block models need it
                RenderHelper.enableGUIStandardItemLighting();
                mc.getRenderItem().renderItemAndEffectIntoGUI(shown.icon, JadeLines189.PAD, JadeLines189.PAD + Math.max(0, Math.min(textHeight, 19) - 16) / 2);
                RenderHelper.disableStandardItemLighting();
            }
        } finally {
            GlStateManager.popMatrix();
            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.disableLighting();
            GlStateManager.enableAlpha();
            GlStateManager.enableTexture2D();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
            if (depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
        }
        return true;
    }

    /** Dark panel with a 1px outline and cut corners. Gui.drawRect takes its alpha from the colour, so the fade goes in there. */
    private static void background(int w, int h, float opacity, float fade) {
        int fill = Math.round(opacity * fade * 255) << 24 | 0x100F16;
        int edge = Math.round(Math.min(1f, opacity * 1.25f) * fade * 255) << 24 | 0x5A5A78;
        Gui.drawRect(1, 1, w - 1, h - 1, fill);
        Gui.drawRect(2, 0, w - 2, 1, edge);
        Gui.drawRect(2, h - 1, w - 2, h, edge);
        Gui.drawRect(0, 2, 1, h - 2, edge);
        Gui.drawRect(w - 1, 2, w, h - 2, edge);
        Gui.drawRect(1, 1, 2, 2, edge);
        Gui.drawRect(w - 2, 1, w - 1, 2, edge);
        Gui.drawRect(1, h - 2, 2, h - 1, edge);
        Gui.drawRect(w - 2, h - 2, w - 1, h - 1, edge);
    }

    /** Vanilla heart sprites (icons.png: container 16,0; full 52,0; half 61,0), 10 per row. */
    private static void hearts(Minecraft mc, int x, int y, float health, float max, float fade) {
        icons(mc, fade);
        int containers = Math.max(1, (int) Math.ceil(max / 2));
        int halves = (int) Math.ceil(health);
        for (int i = 0; i < containers; i++) {
            int hx = x + (i % 10) * 8, hy = y + (i / 10) * JadeLines189.LINE;
            sprite(hx, hy, 16, 0);
            int left = halves - i * 2;
            if (left >= 2) sprite(hx, hy, 52, 0);
            else if (left == 1) sprite(hx, hy, 61, 0);
        }
    }

    /** Vanilla armor sprites (icons.png: full 34,9; half 25,9). */
    private static void armor(Minecraft mc, int x, int y, int armor, float fade) {
        icons(mc, fade);
        for (int i = 0; i * 2 < armor; i++) sprite(x + i * 8, y, armor - i * 2 >= 2 ? 34 : 25, 9);
    }

    private static void icons(Minecraft mc, float fade) {
        GlStateManager.enableTexture2D();
        mc.getTextureManager().bindTexture(Gui.icons);
        GlStateManager.color(1f, 1f, 1f, fade);
    }

    private static void sprite(int x, int y, int u, int v) {
        Gui.drawModalRectWithCustomSizedTexture(x, y, u, v, 9, 9, 256, 256);
    }

    // ---- Settings ----

    static Config config() {
        if (config == null) config = Config.load();
        return config;
    }

    /** Settings of the overlay, kept in {@code config/theladscore-jade.json} with 1.20.1's keys. */
    static final class Config {
        // Info lines ("containers" is the item frame line here: 1.8.9 clients get no container contents).
        boolean icon = true, modName = true, health = true, armor = true, growth = true, containers = true, redstone = true,
            blockStates = true, spawner = true, signs = true, banners = true, harvest = true, animals = true;
        // Behaviour
        boolean displayFluids, onlySneaking, hideCreative, hideWithDebug = true;
        // Look: anchor is a 3x3 grid cell (row * 3 + column), 1 = top centre.
        int anchor = 1, offsetX, offsetY;
        float scale = 1.0f, opacity = 0.8f;

        private static File file() {
            return new File(Loader.instance().getConfigDir(), "theladscore-jade.json");
        }

        private static Config load() {
            File file = file();
            if (file.isFile()) {
                try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                    Config loaded = GSON.fromJson(reader, Config.class);
                    if (loaded != null) return loaded.clamp();
                } catch (IOException | RuntimeException e) {
                    LOG.warn("Jade: could not read {}, using defaults", file, e);
                }
            }
            return new Config();
        }

        void save() {
            clamp();
            File file = file();
            try {
                Files.createDirectories(file.getParentFile().toPath());
                try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                    GSON.toJson(this, writer);
                }
            } catch (IOException e) {
                LOG.warn("Jade: could not save {}", file, e);
            }
        }

        private Config clamp() {
            anchor = Math.max(0, Math.min(8, anchor));
            scale = Math.max(0.5f, Math.min(2.0f, scale));
            opacity = Math.max(0f, Math.min(1f, opacity));
            offsetX = Math.max(-400, Math.min(400, offsetX));
            offsetY = Math.max(-400, Math.min(400, offsetY));
            return this;
        }
    }
}
