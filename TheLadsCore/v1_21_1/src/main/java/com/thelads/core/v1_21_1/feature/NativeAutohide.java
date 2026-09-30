package com.thelads.core.v1_21_1.feature;

import com.mojang.blaze3d.systems.RenderSystem;
import com.thelads.core.client.hud.AutohideFade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Independent native implementation (26.x's Autohide on 1.21.1's immediate-mode GUI). No Auto Hide HUD code or engine is included.
 * A faded scope draws at a shader-colour alpha: every shader colour set inside it is multiplied (AutohideShaderColorMixin), so
 * mods that reset the colour (AppleSkin) still fade, and it draws unbatched, since a HUD batcher (ImmediatelyFast) would draw
 * deferred text and items after the scope. A hidden layer is not drawn at all.
 */
public final class NativeAutohide {
    private static Object player;
    static long activity, frame;
    static float health, opacity = 1;
    /** QA only (NativeHudProbe): an opacity every frame uses, so real frames can be read back at known opacities. */
    static float forced = Float.NaN;
    private static int food, air, slot, xp;
    /** The open scope's alpha (1 = none). */
    public static float scopeOpacity = 1;
    private static MultiBufferSource.BufferSource batching;
    private NativeAutohide() {}

    /** Client init, after the config loads: a profile that had turned the retired Auto Hide HUD jar off keeps Autohide off. */
    public static void register() {
        com.thelads.core.mods.RetiredModChoice.adopt(com.thelads.core.client.util.ClientPaths.getBaseDir(), "autohidehud", "WfEV6RRi", NativeQualityOfLife.module("Autohide"));
    }
    /** The Lads HUD at Gui.render TAIL, faded with the hotbar; the HUD editor draws its own opaque previews. */
    public static void renderLadsHud(GuiGraphics graphics) {
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof com.thelads.core.v1_21_1.gui.DraggableHudScreen121) return;
        float alpha = update();
        if (alpha <= 0) return;
        begin(graphics, alpha);
        try { com.thelads.core.client.hud.HudManager.getInstance().render(new com.thelads.core.v1_21_1.adapter.GuiGraphicsLadsAdapter(graphics, mc.font)); }
        finally { end(graphics); }
    }
    /** This frame's opacity, as the hotbar layer computed it. */
    public static float opacity() { return opacity; }
    /** Opens a faded scope; nothing to do at full opacity. */
    public static void begin(GuiGraphics graphics, float alpha) {
        if (alpha >= 1 || scopeOpacity < 1) return;
        graphics.flush();
        var direct = Minecraft.getInstance().renderBuffers().bufferSource();
        if (graphics.bufferSource() != direct) { batching = graphics.bufferSource(); ((com.thelads.core.v1_21_1.mixin.hud.GuiGraphicsAccessor) graphics).ladsSetBufferSource(direct); }
        scopeOpacity = alpha;
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }
    public static void end(GuiGraphics graphics) {
        if (scopeOpacity >= 1) return;
        graphics.flush();
        scopeOpacity = 1;
        RenderSystem.setShaderColor(1, 1, 1, 1);
        if (batching != null) { ((com.thelads.core.v1_21_1.mixin.hud.GuiGraphicsAccessor) graphics).ladsSetBufferSource(batching); batching = null; }
    }
    public static float update() {
        if (!Float.isNaN(forced)) return opacity = forced;
        var mc = Minecraft.getInstance(); long now = System.nanoTime();
        if (mc.player == null || !NativeQualityOfLife.enabled("Autohide")) { player = null; activity = frame = now; return opacity = 1; }
        var p = mc.player;
        int nextFood = p.getFoodData().getFoodLevel(), nextSlot = p.getInventory().selected;
        boolean changed = player != p || health != p.getHealth() || food != nextFood || air != p.getAirSupply() || slot != nextSlot || xp != p.totalExperience;
        player = p; health = p.getHealth(); food = nextFood; air = p.getAirSupply(); slot = nextSlot; xp = p.totalExperience;
        boolean action = mc.screen != null || p.isUsingItem() || mc.options.keyAttack.isDown() || mc.options.keyUse.isDown();
        if (NativeQualityOfLife.bool("Autohide", "Show while moving", false)) action |= p.getDeltaMovement().lengthSqr() > .001;
        if (NativeQualityOfLife.bool("Autohide", "Show when hurt or hungry", true)) action |= health < p.getMaxHealth() || food < 20 || air < p.getMaxAirSupply();
        if (changed || action || activity == 0) activity = now;
        float target = (now - activity) / 1e9 < NativeQualityOfLife.number("Autohide", "Hide after seconds", 4) ? 1 : 0;
        double duration = NativeQualityOfLife.number("Autohide", "Fade milliseconds", 350) / 1000;
        // Called by the hotbar and again by the Lads HUD each frame; each call steps by its own elapsed time, so a frame steps once.
        opacity = AutohideFade.step(opacity, target, (now - frame) / 1e9, duration); frame = now;
        return opacity;
    }
}
