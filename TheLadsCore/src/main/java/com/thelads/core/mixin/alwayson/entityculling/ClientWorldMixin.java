/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.tr7zw.transition.mc.GeneralUtil
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.multiplayer.ClientLevel
 *  net.minecraft.sounds.SoundEvents
 *  net.minecraft.util.Mth
 *  net.minecraft.world.entity.Display
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.monster.warden.AngerLevel
 *  net.minecraft.world.entity.monster.warden.Warden
 *  net.minecraft.world.entity.vehicle.minecart.AbstractMinecart
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.alwayson.entityculling;

import com.thelads.core.features.alwayson.entityculling.EntityCullingModBase;
import com.thelads.core.features.alwayson.entityculling.NMSCullingHelper;
import com.thelads.core.mixin.alwayson.entityculling.DisplayAccessor;
import com.thelads.core.mixin.alwayson.entityculling.access.Cullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.warden.AngerLevel;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ClientLevel.class})
public class ClientWorldMixin {
    private Minecraft mc = Minecraft.getInstance();

    @Inject(method={"tickNonPassenger"}, at={@At(value="HEAD")}, cancellable=true)
    public void tickEntity(Entity entity, CallbackInfo info) {
        if (!EntityCullingModBase.instance.config.tickCulling || EntityCullingModBase.instance.config.skipEntityCulling) {
            ++EntityCullingModBase.instance.tickedEntities;
            return;
        }
        if (EntityCullingModBase.instance.config.forceDisplayCulling && entity instanceof Display) {
            Display display = (Display)entity;
            this.processDisplay(display);
        }
		if (NMSCullingHelper.ignoresCulling(entity) || entity == this.mc.player || entity == this.mc.getCameraEntity() || entity.isPassenger() || entity.isVehicle() || entity instanceof AbstractMinecart) {
            ++EntityCullingModBase.instance.tickedEntities;
            return;
        }
        if (EntityCullingModBase.instance.tickCullWhitelists.contains(entity.getType()) || EntityCullingModBase.instance.entityWhitelist.contains(entity.getType())) {
            ++EntityCullingModBase.instance.tickedEntities;
            return;
        }
        if (entity instanceof Cullable) {
            Cullable cull = (Cullable)entity;
            if (cull.isCulled() || cull.isOutOfCamera()) {
                this.basicTick(entity);
                ++EntityCullingModBase.instance.skippedEntityTicks;
                info.cancel();
                return;
            }
            cull.setOutOfCamera(true);
        }
        ++EntityCullingModBase.instance.tickedEntities;
    }

    private void processDisplay(Display display) {
        if (display.getBoundingBoxForCulling().getSize() == 0.0 && display instanceof DisplayAccessor) {
            DisplayAccessor accessor = (DisplayAccessor)display;
            accessor.invokeSetWidth(3.0f);
            accessor.invokeSetHeight(3.0f);
            display.setPos(display.getX(), display.getY(), display.getZ());
        }
    }

    private void basicTick(Entity entity) {
        entity.setOldPosAndRot();
        ++entity.tickCount;
        if (entity instanceof LivingEntity) {
            LivingEntity living = (LivingEntity)entity;
            living.aiStep();
            if (living.hurtTime > 0) {
                --living.hurtTime;
            }
        }
        if (entity instanceof Warden) {
            Warden warden = (Warden)entity;
            if (this.mc.level.isClientSide() && !warden.isSilent() && warden.tickCount % this.getWardenHeartBeatDelay(warden) == 0) {
                this.mc.level.playLocalSound(warden.getX(), warden.getY(), warden.getZ(), SoundEvents.WARDEN_HEARTBEAT, warden.getSoundSource(), 5.0f, warden.getVoicePitch(), false);
            }
        }
    }

    private int getWardenHeartBeatDelay(Warden warden) {
        float f = warden.getClientAngerLevel() / AngerLevel.ANGRY.getMinimumAnger();
        return 40 - Mth.floor((float)(Mth.clamp((float)f, (float)0.0f, (float)1.0f) * 30.0f));
    }
}

