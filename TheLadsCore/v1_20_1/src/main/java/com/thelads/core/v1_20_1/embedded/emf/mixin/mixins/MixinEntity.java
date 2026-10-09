package com.thelads.core.v1_20_1.embedded.emf.mixin.mixins;


import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.thelads.core.v1_20_1.embedded.emf.EMFManager;
import com.thelads.core.v1_20_1.embedded.emf.models.animation.state.EMFState;
import com.thelads.core.v1_20_1.embedded.emf.utils.EMFEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mixin(Entity.class)
public abstract class MixinEntity implements EMFEntity {

    @Unique
    private final Map<String, Float> emf$variableMap = new HashMap<>();
    @Unique
    private @Nullable Map<String, Float> emf$variableMapGuiCopy = null;
    @Unique
    private @Nullable Map<String, Float> emf$variableMapGuiCopyFirstPerson = null;


    @Shadow
    public abstract double getX();

    @Shadow
    public abstract double getY();

    @Shadow
    public abstract double getZ();



    @Shadow
    public abstract boolean isOnFire();





    @Shadow
    public abstract boolean isAlive();



    @Shadow
    public abstract boolean isInLava();

    @Shadow
    public abstract boolean isInvisible();


    @Shadow
    public abstract boolean isSprinting();


    @Shadow
    public abstract EntityType<?> getType();

    @Shadow public abstract boolean isCrouching();

    @Shadow public double zo;

    @Shadow public double yo;

    @Shadow public double xo;

    @Shadow public float xRotO;

    @Shadow private float xRot;

    @Shadow private float yRot;

    @Shadow public int tickCount;

    @Shadow public abstract boolean isInWater();

    @Shadow public abstract boolean isPassenger();

    @Shadow public abstract boolean onGround();

    @Shadow public abstract boolean hasGlowingTag();

    @Shadow public abstract List<Entity> getPassengers();

    @Shadow public abstract boolean isInWaterRainOrBubble();

    @Shadow public abstract Vec3 getDeltaMovement();

    @Shadow
    public abstract boolean equals(final Object object);

    @Shadow
    public abstract Vec3 position();


    @Shadow
    public double xOld;

    @Shadow
    public double yOld;

    @Shadow
    public double zOld;

    @Shadow public abstract boolean isInWaterOrRain();

    @ModifyReturnValue(method = "getLeashOffset()Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"))
    private Vec3 emf$leash(Vec3 vec) {
        var map = emf$getVariableMap();
        var x = map.getOrDefault("render.leash_offset_x", 0.0f);
        var y = map.getOrDefault("render.leash_offset_y", 0.0f);
        var z = map.getOrDefault("render.leash_offset_z", 0.0f);

        if (x != 0 || y != 0 || z != 0) {
            return vec.add(x, y, z);
        }
        return vec;
    }

    @Override
    public double emf$prevX() {
        return xo;
    }

    @Override
    public double emf$getX() {
        return getX();
    }

    @Override
    public double emf$prevY() {
        return yo;
    }

    @Override
    public double emf$getY() {
        return getY();
    }

    @Override
    public double emf$prevZ() {
        return zo;
    }

    @Override
    public double emf$getZ() {
        return getZ();
    }

    @Override
    public float emf$prevPitch() {
        return xRotO;
    }

    @Override
    public float emf$getPitch() {
        return xRot;
    }

    @Override
    public boolean emf$isTouchingWater() {
        return isInWater();
    }

    @Override
    public boolean emf$isOnFire() {
        return isOnFire();
    }

    @Override
    public boolean emf$hasVehicle() {
        return isPassenger();
    }

    @Override
    public boolean emf$isOnGround() {
        return onGround();
    }

    @Override
    public boolean emf$isAlive() {
        return isAlive();
    }

    @Override
    public boolean emf$isGlowing() {
        return hasGlowingTag();
    }

    @Override
    public boolean emf$isInLava() {
        return isInLava();
    }

    @Override
    public boolean emf$isInvisible() {
        return isInvisible();
    }

    @Override
    public boolean emf$hasPassengers() {
        return !getPassengers().isEmpty();
    }

    @Override
    public boolean emf$isSneaking() {
        return isCrouching();
    }

    @Override
    public boolean emf$isSprinting() {
        return isSprinting();
    }

    @Override
    public boolean emf$isWet() {
        return isInWaterRainOrBubble();
    }

    @Override
    public float emf$age() {
        return tickCount;
    }

    @Override
    public float emf$getYaw() {
        return yRot;
    }

    @Override
    public Vec3 emf$getVelocity() {
        //noinspection EqualsBetweenInconvertibleTypes
        if (equals(Minecraft.getInstance().player)) {
            return getDeltaMovement();//ez get
        }
        return position().subtract(
                    new Vec3(xOld,yOld,zOld)
        );
    }

    @Unique private String emf$typeString = null;

    @Override
    public String emf$getTypeString() {
        if (emf$typeString == null) emf$typeString = getType().toString();
        return emf$typeString;
    }

    @Unique private int varHash = 0;

    @Override
    public Map<String, Float> emf$getVariableMap() {
        int managerHash = EMFManager.getManagerInstanceHash();
        if (varHash != managerHash) {
            varHash = managerHash;
            emf$variableMap.clear();
            emf$variableMapGuiCopy = null;
            emf$variableMapGuiCopyFirstPerson = null;
        }

        // Create separate variable instances for both gui and first person hand contexts
        var state = EMFState.state();
        if (state != null && state.isFirstPersonHand()) {
            // First person hand variables can mess up other gui rendering entities, specifically known to break
            // paper doll renderers arm animations as they may have at some point read the first person values
            if (emf$variableMapGuiCopyFirstPerson == null) emf$variableMapGuiCopyFirstPerson = new HashMap<>(emf$variableMap);
            return emf$variableMapGuiCopyFirstPerson;
        }

        if (EMFState.isInGui) {
            if (emf$variableMapGuiCopy == null) emf$variableMapGuiCopy = new HashMap<>(emf$variableMap);
            return emf$variableMapGuiCopy;
        }

        return emf$variableMap;
    }
}
