package com.thelads.core.v1_20_1.game;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.CustomFovModule;
import com.thelads.core.v1_20_1.compat.ModPresence1201;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Custom FOV on 1.20.1: CustomFovPlayerMixin and CustomFovCameraMixin hand each of the game's FOV changes here, where the game makes
 * it, to keep CustomFovModule's share of it. A Custom FOV jar left in the mods folder keeps the FOV to itself.
 */
public final class NativeCustomFov1201 {
    /** The speed modifiers the FOV reads: sprinting (LivingEntity), and the Speed and Slowness effects. */
    private static final UUID SPRINTING = UUID.fromString("662A6B8D-DA3E-4C1C-8813-96EA6097278D"),
        SPEED = UUID.fromString("91AEAA56-376B-4498-935B-2F7F68070635"), SLOWNESS = UUID.fromString("7107DE5E-7CE8-4030-940E-514C1F160890");
    private static boolean active;

    private NativeCustomFov1201() {}

    public static void register() {
        if (active || ModPresence1201.loaded("customfov")) return;
        active = true;
        ModuleSupport.registerBuiltIn(CustomFovModule.NAME);
    }

    /** The share (0 to 1) of an FOV change to keep. */
    public static double share(String change) {
        return active && ModuleManager.getInstance().getModule(CustomFovModule.NAME) instanceof CustomFovModule fov ? fov.share(change) : 1;
    }

    /** A multiplicative FOV change (flying's 1.1, the spyglass's 0.1, water's 6/7) kept at its share. */
    public static float scaled(float change, String name) {
        return (float) CustomFovModule.scaled(change, share(name));
    }

    public static double scaled(double change, String name) {
        return CustomFovModule.scaled(change, share(name));
    }

    /** Movement speed as the FOV reads it, with its sprint and Speed/Slowness parts kept at their shares. */
    public static double speed(LivingEntity player, double speed) {
        AttributeInstance attribute = active ? player.getAttribute(Attributes.MOVEMENT_SPEED) : null;
        if (attribute == null) return speed;
        return CustomFovModule.speed(speed, multiplier(attribute, SPRINTING), multiplier(attribute, SPEED) * multiplier(attribute, SLOWNESS),
            share(CustomFovModule.SPRINTING), share(CustomFovModule.EFFECTS));
    }

    private static double multiplier(AttributeInstance attribute, UUID id) {
        AttributeModifier modifier = attribute.getModifier(id);
        return modifier != null && modifier.getOperation() == AttributeModifier.Operation.MULTIPLY_TOTAL ? 1 + modifier.getAmount() : 1;
    }
}
