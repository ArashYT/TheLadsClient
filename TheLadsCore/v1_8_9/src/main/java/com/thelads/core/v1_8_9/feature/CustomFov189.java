package com.thelads.core.v1_8_9.feature;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.CustomFovModule;
import java.util.UUID;
import net.minecraft.block.material.Material;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.potion.Potion;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Custom FOV on 1.8.9: AbstractClientPlayerMixin hands flying, the sprint and Speed/Slowness parts of movement speed and the
 * bow here; water's narrower FOV is in Forge's FOVModifier event. 1.8.9 has no spyglass and no FOV Effects slider.
 */
public final class CustomFov189 {
    /** EntityLivingBase's sprint boost modifier (a private constant there). */
    private static final UUID SPRINTING = UUID.fromString("662A6B8D-DA3E-4C1C-8813-96EA6097278D");
    private static final float WATER = 60.0F / 70.0F;
    private static boolean active;
    private CustomFov189() {}

    public static void register() {
        if (Loader.isModLoaded("customfov")) {
            ModuleSupport.registerExternal(CustomFovModule.NAME, "Custom FOV", "customfov", true);
            return;
        }
        active = true;
        ModuleSupport.registerBuiltIn(CustomFovModule.NAME);
        MinecraftForge.EVENT_BUS.register(new CustomFov189());
    }

    /** The share (0 to 1) of an FOV change to keep. */
    public static double share(String change) {
        Module module = ModuleManager.getInstance().getModule(CustomFovModule.NAME);
        return active && module instanceof CustomFovModule ? ((CustomFovModule) module).share(change) : 1;
    }

    public static float scaled(float change, String name) {
        return (float) CustomFovModule.scaled(change, share(name));
    }

    /** Movement speed as the FOV reads it, with its sprint and Speed/Slowness parts kept at their shares. */
    public static double speed(IAttributeInstance attribute) {
        double speed = attribute.getAttributeValue();
        if (!active) return speed;
        return CustomFovModule.speed(speed, multiplier(attribute, SPRINTING),
            multiplier(attribute, potion(Potion.moveSpeed)) * multiplier(attribute, potion(Potion.moveSlowdown)),
            share(CustomFovModule.SPRINTING), share(CustomFovModule.EFFECTS));
    }

    private static UUID potion(Potion potion) {
        return potion.getAttributeModifierMap().get(SharedMonsterAttributes.movementSpeed).getID();
    }

    private static double multiplier(IAttributeInstance attribute, UUID id) {
        AttributeModifier modifier = attribute.getModifier(id);
        return modifier != null && modifier.getOperation() == 2 ? 1 + modifier.getAmount() : 1;
    }

    /** 1.8.9 multiplies water's 60/70 in just before this event; HIGH: before Lads Zoom's handler reads the FOV. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void fov(EntityViewRenderEvent.FOVModifier event) {
        if (event.block.getMaterial() == Material.water) event.setFOV(event.getFOV() / WATER * scaled(WATER, CustomFovModule.UNDERWATER));
    }
}
