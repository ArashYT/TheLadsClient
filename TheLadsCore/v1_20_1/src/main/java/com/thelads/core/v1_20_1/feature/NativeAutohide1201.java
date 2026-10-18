package com.thelads.core.v1_20_1.feature;

import com.thelads.core.client.hud.AutohideFade;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import com.thelads.core.v1_20_1.gui.DraggableHudScreen1201;
import com.thelads.core.v1_20_1.hud.Hud1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Auto Hide on 1.20.1: independent native implementation. The hotbar, the bars on it, the XP bar and the selected item name fade
 * with {@link #update()} (HudParts1201 draws them through the shader colour's alpha), and so does the Lads HUD
 * ({@link #renderLadsHud}), whose adapter reads {@link #scopeOpacity}.
 */
public final class NativeAutohide1201 {
    private static Object player;
    private static long activity, frame;
    private static float health, opacity = 1;
    private static int food, air, slot, xp;
    /** The opacity everything drawn right now is faded to; 1 outside an Auto Hide scope. */
    public static float scopeOpacity = 1;

    private NativeAutohide1201() {}

    /** Renders the Lads HUD (after vanilla's HUD), faded with the hotbar. */
    public static void renderLadsHud(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof DraggableHudScreen1201) return;
        float previous = scopeOpacity;
        try {
            float opacity = update();
            scopeOpacity = Hud1201.bool("Autohide", "Fade Lads HUD", true) ? opacity : 1;
            if (scopeOpacity > 0) {
                GuiGraphicsLadsAdapter1201 adapter = new GuiGraphicsLadsAdapter1201(graphics, mc.font);
                adapter.managed(() -> HudManager.getInstance().render(adapter));
            }
            scopeOpacity = 1;
            if (com.thelads.core.client.qa.QaProgress.on()) {
                GuiGraphicsLadsAdapter1201 adapter = new GuiGraphicsLadsAdapter1201(graphics, mc.font);
                adapter.managed(() -> com.thelads.core.client.qa.QaProgress.draw(adapter)); // QA only (no-op for players)
            }
        } finally {
            scopeOpacity = previous;
        }
    }

    /** QA only: the QA progress bar over the screens that show without a world (with one, the HUD draws it). */
    public static void registerQaBarScreens() {
        if (!com.thelads.core.client.qa.QaProgress.on()) return;
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, delta) -> {
                if (client.level != null) return;
                GuiGraphicsLadsAdapter1201 adapter = new GuiGraphicsLadsAdapter1201(graphics, client.font);
                adapter.managed(() -> com.thelads.core.client.qa.QaProgress.draw(adapter));
            }));
    }

    /** QA: idle for a minute, at this opacity now. */
    public static void idle(float now) {
        activity = System.nanoTime() - 60_000_000_000L;
        frame = System.nanoTime();
        opacity = now;
    }

    public static float level() { return opacity; }

    /** Called by the hotbar group and again by the Lads HUD each frame; each call steps by its own elapsed time, so a frame steps once. */
    public static float update() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        if (mc.player == null || !Hud1201.enabled("Autohide")) {
            player = null;
            activity = frame = now;
            return opacity = 1;
        }
        var p = mc.player;
        int nextFood = p.getFoodData().getFoodLevel(), nextSlot = p.getInventory().selected;
        boolean changed = player != p || health != p.getHealth() || food != nextFood || air != p.getAirSupply() || slot != nextSlot || xp != p.totalExperience;
        player = p;
        health = p.getHealth();
        food = nextFood;
        air = p.getAirSupply();
        slot = nextSlot;
        xp = p.totalExperience;
        boolean action = mc.screen != null;
        // Swinging, placing and using items bring the HUD back only when asked to.
        if (Hud1201.bool("Autohide", "Show when swinging or placing", false))
            action |= p.isUsingItem() || mc.options.keyAttack.isDown() || mc.options.keyUse.isDown();
        if (Hud1201.bool("Autohide", "Show while moving", false)) action |= p.getDeltaMovement().lengthSqr() > .001;
        if (Hud1201.bool("Autohide", "Show when hurt or hungry", true)) action |= health < p.getMaxHealth() || food < 20 || air < p.getMaxAirSupply();
        if (changed || action || activity == 0) activity = now;
        float target = (now - activity) / 1e9 < Hud1201.number("Autohide", "Hide after seconds", 4) ? 1 : (float) Hud1201.number("Autohide", "Faded opacity", 0) / 100f;
        double duration = Hud1201.number("Autohide", "Fade milliseconds", 350) / 1000;
        opacity = AutohideFade.step(opacity, target, (now - frame) / 1e9, duration);
        frame = now;
        return opacity;
    }
}
