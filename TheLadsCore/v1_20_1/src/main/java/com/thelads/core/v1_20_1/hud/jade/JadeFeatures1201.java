package com.thelads.core.v1_20_1.hud.jade;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.config.ActionOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.v1_20_1.gui.ExternalModSettings1201;
import com.thelads.core.v1_20_1.hud.Hud1201;
import com.thelads.core.v1_20_1.hud.Mods1201;
import com.thelads.core.v1_20_1.hud.jade.JadeLines1201.Line;
import com.thelads.core.v1_20_1.hud.jade.JadeLines1201.Panel;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Jade module: an original Lads "look-at info" panel for the block, fluid or entity under the crosshair.
 * Stands down while the real Jade mod is loaded (Zero SMP ships it); takes over when its jar is removed.
 * The target is tracked every client tick, its lines are rebuilt on change and otherwise 5 times a second, and the cached
 * panel is drawn every frame from Fabric's HUD callback (fired on Forge too by Forgified Fabric API, where ForgeGui replaces
 * {@code Gui.render}).
 */
public final class JadeFeatures1201 {
    public static final String MODULE = "Jade";
    private static final ResourceLocation ICONS = new ResourceLocation("textures/gui/icons.png");
    private static final float FADE_MS = 150f, RESIZE_MS = 60f;
    private static final int REFRESH_TICKS = 4;
    private static boolean registered, standDown, failed;

    // Target, written by the client tick.
    private static Entity targetEntity;
    private static BlockPos targetPos;
    private static BlockState targetState;
    private static boolean targetFluid, hasTarget;
    private static int age;
    private static Panel panel = Panel.EMPTY;

    // Animation, written by the render thread (same thread as the tick on the client).
    private static float alpha, width, height;
    private static long lastFrame;

    private JadeFeatures1201() {}

    public static void register() {
        if (registered) return;
        registered = true;
        standDown = Mods1201.loaded("jade");
        ActionOption settings = settingsAction();
        if (standDown) {
            ModuleSupport.registerExternal(MODULE, "Jade", "jade", true);
            if (settings != null) settings.setAction(() -> ExternalModSettings1201.open("jade", Minecraft.getInstance().screen));
            Hud1201.LOG.info("Jade is installed: the Lads Jade overlay stands down");
            return;
        }
        ModuleSupport.registerBuiltIn(MODULE);
        if (settings != null) settings.setAction(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new JadeSettingsScreen1201(mc.screen));
        });
        ClientTickEvents.END_CLIENT_TICK.register(JadeFeatures1201::tick);
        HudRenderCallback.EVENT.register(JadeFeatures1201::renderHud);
    }

    private static ActionOption settingsAction() {
        Module module = Hud1201.module(MODULE);
        return module != null && module.getOption("Settings and addons") instanceof ActionOption action ? action : null;
    }

    // ---- QA / addon entry points (client thread) ----

    /** Plain text of the panel lines (display name first, mod name last; hearts read "Health: 15/20"); empty with no target. */
    public static List<String> lastLines() {
        return hasTarget ? panel.plain() : List.of();
    }

    /** Whether the panel was drawn on the last frame (alpha above zero). */
    public static boolean visible() {
        return alpha > 0;
    }

    /** True when the real Jade mod is loaded and this overlay does nothing. */
    public static boolean standingDown() {
        return standDown;
    }

    /** "entity minecraft:cow", "block minecraft:wheat @ x,y,z", "fluid minecraft:water @ x,y,z" or "none". */
    public static String target() {
        if (!hasTarget) return "none";
        if (targetEntity != null) return "entity " + BuiltInRegistries.ENTITY_TYPE.getKey(targetEntity.getType());
        return (targetFluid ? "fluid " : "block ") + BuiltInRegistries.BLOCK.getKey(targetState.getBlock())
            + " @ " + targetPos.getX() + "," + targetPos.getY() + "," + targetPos.getZ();
    }

    /** Re-reads the target and rebuilds the lines now instead of at the next 5 Hz refresh (call on the client thread). */
    public static void refreshNow() {
        age = REFRESH_TICKS;
        tick(Minecraft.getInstance());
    }

    // ---- Tracking ----

    private static void tick(Minecraft mc) {
        JadeConfig1201 config = JadeConfig1201.get();
        if (mc.player == null || mc.level == null || !Hud1201.enabled(MODULE)
            || (config.onlySneaking && !mc.player.isShiftKeyDown()) || (config.hideCreative && mc.player.isCreative())) {
            lose();
            return;
        }
        boolean changed;
        HitResult hit = mc.hitResult;
        BlockHitResult fluid = config.displayFluids ? fluidHit(mc, hit) : null;
        if (fluid != null) {
            BlockPos pos = fluid.getBlockPos();
            BlockState state = mc.level.getFluidState(pos).createLegacyBlock();
            changed = !targetFluid || state != targetState || !pos.equals(targetPos);
            setBlock(pos, state, true);
        } else if (hit instanceof EntityHitResult entityHit && !entityHit.getEntity().isRemoved()) {
            changed = entityHit.getEntity() != targetEntity;
            targetEntity = entityHit.getEntity();
            targetPos = null;
            targetState = null;
            targetFluid = false;
        } else if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = blockHit.getBlockPos();
            BlockState state = mc.level.getBlockState(pos);
            if (state.isAir()) { lose(); return; }
            changed = targetFluid || targetEntity != null || state != targetState || !pos.equals(targetPos);
            setBlock(pos, state, false);
        } else {
            lose();
            return;
        }
        hasTarget = true;
        if (changed || ++age >= REFRESH_TICKS) rebuild(mc, config);
    }

    private static void setBlock(BlockPos pos, BlockState state, boolean fluid) {
        targetEntity = null;
        targetPos = pos;
        targetState = state;
        targetFluid = fluid;
    }

    /** A fluid source in reach that is nearer than vanilla's crosshair target (vanilla picking ignores fluids). */
    private static BlockHitResult fluidHit(Minecraft mc, HitResult vanilla) {
        Entity camera = mc.getCameraEntity();
        if (camera == null || mc.gameMode == null) return null;
        Vec3 eye = camera.getEyePosition(1f);
        Vec3 end = eye.add(camera.getViewVector(1f).scale(mc.gameMode.getPickRange()));
        BlockHitResult hit = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, camera));
        if (hit.getType() != HitResult.Type.BLOCK || mc.level.getFluidState(hit.getBlockPos()).isEmpty()) return null;
        boolean nearer = vanilla == null || vanilla.getType() == HitResult.Type.MISS
            || eye.distanceToSqr(hit.getLocation()) < eye.distanceToSqr(vanilla.getLocation()) - 1.0E-4;
        return nearer ? hit : null;
    }

    private static void lose() {
        hasTarget = false;
        targetEntity = null;
        targetPos = null;
        targetState = null;
        targetFluid = false;
    }

    private static void rebuild(Minecraft mc, JadeConfig1201 config) {
        age = 0;
        try {
            panel = targetEntity != null ? JadeLines1201.entity(mc, targetEntity, config)
                : JadeLines1201.block(mc, targetPos, targetState, targetFluid, config);
        } catch (RuntimeException e) {
            if (!failed) Hud1201.LOG.warn("Jade: could not describe {}", target(), e);
            failed = true;
            panel = Panel.EMPTY;
        }
    }

    // ---- Rendering ----

    private static void renderHud(GuiGraphics graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        long now = Util.getMillis();
        float elapsed = lastFrame == 0 ? 0 : Math.min(100, now - lastFrame);
        lastFrame = now;
        JadeConfig1201 config = JadeConfig1201.get();
        boolean hidden = mc.screen != null || mc.options.hideGui || (config.hideWithDebug && mc.options.renderDebug);
        alpha = hidden ? 0 : JadeMath1201.approach(alpha, hasTarget ? 1 : 0, elapsed / FADE_MS);
        if (alpha <= 0 || panel.lines().isEmpty()) {
            if (!hasTarget) panel = Panel.EMPTY;
            width = height = 0;
            return;
        }
        if (width == 0) {
            width = panel.width();
            height = panel.height();
        } else {
            float k = Math.min(1f, elapsed / RESIZE_MS);
            width += (panel.width() - width) * k;
            height += (panel.height() - height) * k;
        }
        draw(graphics, mc, config, panel, alpha, Math.round(width), Math.round(height));
    }

    /** The settings screen's live preview: the current panel at full opacity and final size. */
    static boolean drawPreview(GuiGraphics graphics) {
        Panel shown = panel;
        if (shown.lines().isEmpty()) return false;
        draw(graphics, Minecraft.getInstance(), JadeConfig1201.get(), shown, 1f, shown.width(), shown.height());
        return true;
    }

    static void markDirty() {
        age = REFRESH_TICKS;
    }

    private static void draw(GuiGraphics g, Minecraft mc, JadeConfig1201 config, Panel shown, float fade, int w, int h) {
        float scale = config.scale;
        int screenW = Math.round(w * scale), screenH = Math.round(h * scale);
        int x = JadeMath1201.place(config.anchor % 3, g.guiWidth(), screenW, config.offsetX);
        int y = JadeMath1201.place(config.anchor / 3, g.guiHeight(), screenH, config.offsetY);
        // Under the tab list or the debug text the panel steps aside with the Lads HUD (HudCover).
        if (com.thelads.core.client.hud.HudCover.coveredBefore(x, y, x + screenW, y + screenH)) return;
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1);
        RenderSystem.setShaderColor(1f, 1f, 1f, fade);
        background(g, w, h, config.opacity);
        boolean clip = w < shown.width() || h < shown.height();
        if (clip) g.enableScissor(x, y, x + screenW, y + screenH);
        ItemStack icon = shown.icon();
        int textX = JadeLines1201.PAD + (icon.isEmpty() ? 0 : 20);
        int lineY = JadeLines1201.PAD;
        for (Line line : shown.lines()) {
            switch (line.kind()) {
                case JadeLines1201.HEARTS -> hearts(g, textX, lineY, line.value(), line.max());
                case JadeLines1201.ARMOR -> armor(g, textX, lineY, (int) line.value());
                case JadeLines1201.ITEMS -> items(g, mc, textX, lineY, line.items());
                default -> g.drawString(mc.font, line.seq(), textX, lineY, 0xFFFFFF, true);
            }
            lineY += line.height();
        }
        // Item models are cut out, not blended: show the icon once the fade is past half instead of popping at full alpha.
        if (!icon.isEmpty() && fade >= 0.5f) {
            int textHeight = shown.height() - JadeLines1201.PAD * 2;
            g.renderItem(icon, JadeLines1201.PAD, JadeLines1201.PAD + Math.max(0, Math.min(textHeight, 19) - 16) / 2);
        }
        if (clip) g.disableScissor();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        pose.popPose();
    }

    /** Dark panel with a 1px outline and cut corners. */
    private static void background(GuiGraphics g, int w, int h, float opacity) {
        int fill = Math.round(opacity * 255) << 24 | 0x100F16;
        int edge = Math.round(Math.min(1f, opacity * 1.25f) * 255) << 24 | 0x5A5A78;
        g.fill(1, 1, w - 1, h - 1, fill);
        g.fill(2, 0, w - 2, 1, edge);
        g.fill(2, h - 1, w - 2, h, edge);
        g.fill(0, 2, 1, h - 2, edge);
        g.fill(w - 1, 2, w, h - 2, edge);
        g.fill(1, 1, 2, 2, edge);
        g.fill(w - 2, 1, w - 1, 2, edge);
        g.fill(1, h - 2, 2, h - 1, edge);
        g.fill(w - 2, h - 2, w - 1, h - 1, edge);
    }

    /** Vanilla heart sprites (icons.png: container 16,0; full 52,0; half 61,0), 10 per row. */
    private static void hearts(GuiGraphics g, int x, int y, float health, float max) {
        sprites(true);
        int containers = Math.max(1, (int) Math.ceil(max / 2));
        int halves = (int) Math.ceil(health);
        for (int i = 0; i < containers; i++) {
            int hx = x + (i % 10) * 8, hy = y + (i / 10) * JadeLines1201.LINE;
            g.blit(ICONS, hx, hy, 16, 0, 9, 9);
            int left = halves - i * 2;
            if (left >= 2) g.blit(ICONS, hx, hy, 52, 0, 9, 9);
            else if (left == 1) g.blit(ICONS, hx, hy, 61, 0, 9, 9);
        }
        sprites(false);
    }

    /** Vanilla armor sprites (icons.png: full 34,9; half 25,9). */
    private static void armor(GuiGraphics g, int x, int y, int armor) {
        sprites(true);
        for (int i = 0; i * 2 < armor; i++) g.blit(ICONS, x + i * 8, y, armor - i * 2 >= 2 ? 34 : 25, 9, 9, 9);
        sprites(false);
    }

    /** Plain blits do not set blending (fills and text do, through their render types), so the fade needs it here. */
    private static void sprites(boolean start) {
        if (start) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        } else {
            RenderSystem.disableBlend();
        }
    }

    private static void items(GuiGraphics g, Minecraft mc, int x, int y, List<ItemStack> stacks) {
        for (int i = 0; i < stacks.size(); i++) {
            int ix = x + (i % JadeLines1201.ITEMS_PER_ROW) * JadeLines1201.SLOT, iy = y + (i / JadeLines1201.ITEMS_PER_ROW) * JadeLines1201.SLOT;
            g.renderItem(stacks.get(i), ix, iy);
            g.renderItemDecorations(mc.font, stacks.get(i), ix, iy);
        }
    }
}
