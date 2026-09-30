package com.thelads.core.v1_21_11.feature;

import com.thelads.core.client.hud.AutohideFade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.renderer.RenderPipelines;

/** Independent native implementation (the 26.x one on the 1.21.11 GUI render state). No Auto Hide HUD code or engine is included. */
public final class NativeAutohide {
    private static Object player;
    static long activity, frame;
    static float health, opacity = 1;
    private static int food, air, slot, xp;
    public static float scopeOpacity = 1;
    /** Picture-in-picture states (Xaero's minimap) submitted while faded, with their opacity for the blit; weak, so skipped blits never pile up. */
    public static final java.util.Map<Object, Float> PICTURES = new java.util.WeakHashMap<>();
    private NativeAutohide() {}

    /** Client init, after the config loads: a profile that had turned the retired Auto Hide HUD jar off keeps Autohide off. */
    public static void register() {
        com.thelads.core.mods.RetiredModChoice.adopt(com.thelads.core.client.util.ClientPaths.getBaseDir(), "autohidehud", "WfEV6RRi", NativeQualityOfLife.module("Autohide"));
    }
    /** Share the hotbar fade with every native Lads draw, including deferred text/items. */
    public static void renderLadsHud(GuiGraphics graphics) {
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof com.thelads.core.v1_21_11.gui.DraggableHudScreen12111) return;
        scoped(() -> com.thelads.core.client.hud.HudManager.getInstance().render(new com.thelads.core.v1_21_11.adapter.GuiGraphicsLadsAdapter(graphics, mc.font)));
    }
    /** Runs a HUD draw at the current Autohide opacity: its submissions fade, and a hidden HUD submits nothing. */
    public static void scoped(Runnable draw) {
        float previous = scopeOpacity;
        try {
            scopeOpacity = update();
            if (scopeOpacity > 0) draw.run();
        } finally { scopeOpacity = previous; }
    }
    public static float update() {
        var mc = Minecraft.getInstance(); long now = System.nanoTime();
        if (mc.player == null || !NativeQualityOfLife.enabled("Autohide")) { player = null; activity = frame = now; return opacity = 1; }
        var p = mc.player;
        int nextFood = p.getFoodData().getFoodLevel(), nextSlot = p.getInventory().getSelectedSlot();
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
    /** A blit at the given opacity; premultiplied-alpha blits (item atlas, picture-in-picture) scale every channel. */
    public static BlitRenderState fade(BlitRenderState b, float alpha) {
        int color = b.pipeline() == RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA ? AutohideFade.tintPremultiplied(b.color(), alpha) : AutohideFade.tint(b.color(), alpha);
        return new BlitRenderState(b.pipeline(), b.textureSetup(), b.pose(), b.x0(), b.y0(), b.x1(), b.y1(), b.u0(), b.u1(), b.v0(), b.v1(), color, b.scissorArea(), b.bounds());
    }
}
