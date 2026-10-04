package com.thelads.core.v26_2.feature;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.CustomFovModule;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Custom FOV on 26.x: CustomFovPlayerMixin and CustomFovCameraMixin hand each of the game's FOV changes here, where the game
 * makes it, to keep CustomFovModule's share of it. A Custom FOV jar left in the mods folder keeps the FOV to itself.
 */
public final class NativeCustomFov {
    private static final Identifier SPRINTING = Identifier.withDefaultNamespace("sprinting"),
        SPEED = Identifier.withDefaultNamespace("effect.speed"), SLOWNESS = Identifier.withDefaultNamespace("effect.slowness");
    private static boolean active;
    private NativeCustomFov() {}

    public static void register() {
        if (active || FabricLoader.getInstance().isModLoaded("customfov")) return;
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

    /** Movement speed as the FOV reads it, with its sprint and Speed/Slowness parts kept at their shares. */
    public static double speed(LivingEntity player, double speed) {
        AttributeInstance attribute = active ? player.getAttribute(Attributes.MOVEMENT_SPEED) : null;
        if (attribute == null) return speed;
        return CustomFovModule.speed(speed, multiplier(attribute, SPRINTING), multiplier(attribute, SPEED) * multiplier(attribute, SLOWNESS),
            share(CustomFovModule.SPRINTING), share(CustomFovModule.EFFECTS));
    }

    private static double multiplier(AttributeInstance attribute, Identifier id) {
        AttributeModifier modifier = attribute.getModifier(id);
        return modifier != null && modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL ? 1 + modifier.amount() : 1;
    }
}
