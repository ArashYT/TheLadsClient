package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.hud.AutohideFade;
import java.util.EnumSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Autohide on 1.8.9 (1.7.0; the module was listed since 1.4.1 but drew nothing): as NativeAutohide on 26.x, the hotbar (frame,
 * selection, items, counts, durability bars), the status bars, the experience or jump bar and the Lads HUD fade out together while
 * idle. 1.8.9 draws items with opaque vertex colours, so a GL colour cannot fade them: while fading, each of these overlay elements
 * draws into an offscreen framebuffer that is then blended in at the fade's opacity. Hidden elements are skipped; shown ones draw directly.
 */
public final class Autohide189 {
    private static final EnumSet<ElementType> FADED = EnumSet.of(ElementType.HOTBAR, ElementType.HEALTH, ElementType.ARMOR,
        ElementType.FOOD, ElementType.AIR, ElementType.HEALTHMOUNT, ElementType.JUMPBAR, ElementType.EXPERIENCE);
    private static Object player;
    private static long activity, frame;
    private static float health, opacity = 1;
    private static int food, air, slot, xp;
    /** This frame's opacity, stepped once per frame at the start of Forge's overlay. */
    public static float shown = 1;
    /** Kept while fades come and go, given back after FREE_AFTER without one (it is window-size). */
    private static final HudBuffer189 BUFFER = new HudBuffer189(true);
    private static final long FREE_AFTER = 10_000_000_000L;
    private static long faded;
    private static ElementType capturing;

    /** QA (Probe170Hud): idle for a minute, at this opacity now. */
    static void idle(float now) {
        activity = System.nanoTime() - 60_000_000_000L;
        frame = System.nanoTime();
        opacity = now;
    }

    /** Same signals as NativeAutohide.update on 26.x. */
    static float update() {
        Minecraft mc = Minecraft.getMinecraft();
        long now = System.nanoTime();
        if (mc.thePlayer == null || !Options189.enabled("Autohide")) {
            player = null;
            activity = frame = now;
            return opacity = 1;
        }
        EntityPlayerSP p = mc.thePlayer;
        int nextFood = p.getFoodStats().getFoodLevel(), nextSlot = p.inventory.currentItem;
        boolean changed = player != p || health != p.getHealth() || food != nextFood || air != p.getAir() || slot != nextSlot || xp != p.experienceTotal;
        player = p;
        health = p.getHealth();
        food = nextFood;
        air = p.getAir();
        slot = nextSlot;
        xp = p.experienceTotal;
        boolean action = mc.currentScreen != null || p.isUsingItem() || mc.gameSettings.keyBindAttack.isKeyDown() || mc.gameSettings.keyBindUseItem.isKeyDown();
        if (Options189.bool("Autohide", "Show while moving", false)) action |= p.motionX * p.motionX + p.motionZ * p.motionZ > .001 || !p.onGround;
        if (Options189.bool("Autohide", "Show when hurt or hungry", true)) action |= health < p.getMaxHealth() || food < 20 || air < 300;
        if (changed || action || activity == 0) activity = now;
        float target = (now - activity) / 1e9 < Options189.number("Autohide", "Hide after seconds", 4) ? 1 : 0;
        opacity = AutohideFade.step(opacity, target, (now - frame) / 1e9, Options189.number("Autohide", "Fade milliseconds", 350) / 1000);
        frame = now;
        return opacity;
    }

    /** Last, so a mod that cancels an element first leaves nothing captured. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void pre(RenderGameOverlayEvent.Pre event) {
        if (event.type == ElementType.ALL) {
            end(null);
            shown = update();
            long now = System.nanoTime();
            if (shown > 0 && shown < 1) faded = now;
            else if (BUFFER.allocated() && now - faded > FREE_AFTER) BUFFER.free();
        } else if (FADED.contains(event.type)) {
            end(null);
            if (shown <= 0) event.setCanceled(true);
            else if (shown < 1 && begin()) capturing = event.type;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void post(RenderGameOverlayEvent.Post event) {
        if (event.type == capturing || event.type == ElementType.ALL) end(event);
    }

    /** Draws what follows into the cleared offscreen framebuffer; false without framebuffers (the element then draws unfaded). */
    public static boolean begin() {
        return BUFFER.begin();
    }

    /** Out of a world: the buffer goes back at once. */
    public static void free() {
        BUFFER.free();
    }

    /** Ends an element capture whose Post never came (a mod cancelled its Pre after this one ran). */
    public static void flush() { end(null); }

    private static void end(RenderGameOverlayEvent event) {
        if (capturing == null) return;
        capturing = null;
        Minecraft mc = Minecraft.getMinecraft();
        net.minecraft.client.gui.ScaledResolution res = event != null ? event.resolution : new net.minecraft.client.gui.ScaledResolution(mc);
        end(shown, res.getScaledWidth_double(), res.getScaledHeight_double());
    }

    /** Back to the framebuffer drawn before begin(), with the captured pixels blended in at alpha over the scaled GUI area. */
    public static void end(float alpha, double width, double height) {
        BUFFER.end();
        BUFFER.draw(alpha, width, height);
    }
}
