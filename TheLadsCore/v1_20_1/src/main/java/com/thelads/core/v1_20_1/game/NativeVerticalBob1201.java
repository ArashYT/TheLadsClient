package com.thelads.core.v1_20_1.game;

import com.thelads.core.client.LegacyVerticalBob;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Vertical Bobbing (module "VerticalBobbing"): the view tips with the player's vertical speed, as 1.8 did. The motion is sampled on
 * every completed game tick, so frames between ticks only interpolate and the hand and world passes read the same value.
 */
public final class NativeVerticalBob1201 {
    public static final String MODULE = "VerticalBobbing";
    private static final LegacyVerticalBob MOTION = new LegacyVerticalBob();
    private static Object level, player;
    /** QA: the displacement of the last frame, in degrees about X. */
    public static volatile float lastDisplacement;

    private NativeVerticalBob1201() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (level != mc.level || player != mc.player) {
            MOTION.reset();
            level = mc.level;
            player = mc.player;
        }
        if (mc.player != null && mc.level != null && !mc.isPaused()) {
            LocalPlayer p = mc.player;
            // No bob while flying, swimming or riding: the pitch eases back to level.
            boolean bobbing = !p.isSpectator() && !p.isPassenger() && !p.isSleeping() && !p.isFallFlying() && !p.isSwimming() && !p.getAbilities().flying;
            MOTION.sample(p.tickCount, p.getDeltaMovement().y, p.onGround() || p.isDeadOrDying(), bobbing, 1, true);
        }
    }

    /** GameRenderer.bobView, in degrees about X (the world and the hand are both tipped by it). */
    public static float displacement(float partial) {
        Minecraft mc = Minecraft.getInstance();
        Module module = ModuleManager.getInstance().getModule(MODULE);
        LocalPlayer self = mc.player;
        double accessibility = Math.min(mc.options.screenEffectScale().get(), mc.options.fovEffectScale().get());
        boolean active = module != null && module.isEnabled() && self != null && mc.getCameraEntity() == self && mc.level != null
            && !mc.isPaused() && mc.options.getCameraType().isFirstPerson() && mc.options.bobView().get() && accessibility > 0;
        double intensity = module != null && module.getOption("Intensity") instanceof DropdownOption choice
            ? switch (choice.getIndex()) { case 0 -> .5; case 2 -> 1.5; default -> 1; } : 1;
        float value = active ? MOTION.interpolate(partial) * (float) (intensity * accessibility) : 0;
        lastDisplacement = value;
        return value;
    }
}
