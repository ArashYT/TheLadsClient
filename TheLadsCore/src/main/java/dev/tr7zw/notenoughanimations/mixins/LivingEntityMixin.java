/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.entity.LivingEntity
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package dev.tr7zw.notenoughanimations.mixins;

import dev.tr7zw.notenoughanimations.access.PlayerData;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={LivingEntity.class})
public class LivingEntityMixin {
    @Inject(method={"tickHeadTurn"}, at={@At(value="HEAD")}, cancellable=true)
    protected void tickHeadTurn(float g, CallbackInfo info) {
        if (!com.thelads.core.config.ModuleManager.getInstance().getModule("NotEnoughAnimations").isEnabled()) {
            return;
        }
        if ((Object)this instanceof net.minecraft.client.player.AbstractClientPlayer player) {
            PlayerData data = (PlayerData) player;
            if (data.isDisableBodyRotation()) {
                data.setDisableBodyRotation(false);
                info.cancel();
                return;
            }
            boolean isCameraEntity = player == net.minecraft.client.Minecraft.getInstance().getCameraEntity();
            boolean applyLock = true;
            if (!isCameraEntity && !dev.tr7zw.notenoughanimations.versionless.NEABaseMod.config.applyRotationLockToEveryone) {
                applyLock = false;
            }
            if (isCameraEntity && dev.tr7zw.notenoughanimations.versionless.NEABaseMod.config.limitRotationLockToFP && net.minecraft.client.Minecraft.getInstance().options.getCameraType() != net.minecraft.client.CameraType.FIRST_PERSON) {
                applyLock = false;
            }
            if (applyLock) {
                var lock = dev.tr7zw.notenoughanimations.versionless.NEABaseMod.config.rotationLock;
                if (lock == dev.tr7zw.notenoughanimations.versionless.RotationLock.FIXED && player.getVehicle() == null) {
                    player.yBodyRot = g;
                    info.cancel();
                    return;
                } else if (lock == dev.tr7zw.notenoughanimations.versionless.RotationLock.SMOOTH && player.getVehicle() == null) {
                    player.yBodyRot = net.minecraft.util.Mth.rotLerp(0.3f, player.yBodyRot, g);
                    info.cancel();
                    return;
                }
            }
        }
    }
}

