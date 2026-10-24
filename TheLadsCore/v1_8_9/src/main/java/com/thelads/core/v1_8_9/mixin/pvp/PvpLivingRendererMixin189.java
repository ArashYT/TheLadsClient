package com.thelads.core.v1_8_9.mixin.pvp;

import com.thelads.core.client.PvpVisuals;
import com.thelads.core.modules.HitColorModule;
import com.thelads.core.v1_8_9.feature.PvpVisuals189;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hit Color: setBrightness puts the hurt flash's red, green, blue and strength into the brightness buffer, its first
 * four puts. Supports per-damage-type flash colors. Mount Opacity: renderModel takes its see-through path (an invisible mob a spectator sees)
 * for the player's mount, at the module's opacity instead of 15%.
 */
@Mixin(RendererLivingEntity.class)
public class PvpLivingRendererMixin189 {
    private static final String PUT = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;";
    private static EntityLivingBase lads$currentEntity;

    @Inject(method = "setBrightness", at = @At("HEAD"), require = 0)
    private void lads$captureEntity(EntityLivingBase entity, float partialTicks, boolean combineTextures, CallbackInfoReturnable<Boolean> cir) {
        lads$currentEntity = entity;
    }

    @Inject(method = "setBrightness", at = @At("RETURN"), require = 0)
    private void lads$releaseEntity(EntityLivingBase entity, float partialTicks, boolean combineTextures, CallbackInfoReturnable<Boolean> cir) {
        lads$currentEntity = null;
    }

    private static int lads$currentColor() {
        HitColorModule hit = PvpVisuals.HIT_COLOR;
        if (!hit.on) return hit.argb;
        HitColorModule.DamageType type = HitColorModule.DamageType.DEFAULT;
        if (lads$currentEntity != null) {
            type = HitColorModule.getDamageType(lads$currentEntity.getEntityId());
            if (type == HitColorModule.DamageType.DEFAULT) {
                if (lads$currentEntity.isBurning()) type = HitColorModule.DamageType.FIRE;
                else if (lads$currentEntity.isInWater() && lads$currentEntity.getAir() <= 0) type = HitColorModule.DamageType.DROWNING;
                else if (lads$currentEntity.fallDistance > 2.0F) type = HitColorModule.DamageType.FALL;
            }
        }
        return hit.getColor(type);
    }

    @ModifyArg(method = "setBrightness", at = @At(value = "INVOKE", target = PUT, ordinal = 0, remap = false), require = 0)
    private float lads$hitRed(float red) {
        HitColorModule hit = PvpVisuals.HIT_COLOR;
        return hit.on ? (lads$currentColor() >> 16 & 0xFF) / 255F : red;
    }

    @ModifyArg(method = "setBrightness", at = @At(value = "INVOKE", target = PUT, ordinal = 1, remap = false), require = 0)
    private float lads$hitGreen(float green) {
        HitColorModule hit = PvpVisuals.HIT_COLOR;
        return hit.on ? (lads$currentColor() >> 8 & 0xFF) / 255F : green;
    }

    @ModifyArg(method = "setBrightness", at = @At(value = "INVOKE", target = PUT, ordinal = 2, remap = false), require = 0)
    private float lads$hitBlue(float blue) {
        HitColorModule hit = PvpVisuals.HIT_COLOR;
        return hit.on ? (lads$currentColor() & 0xFF) / 255F : blue;
    }

    @ModifyArg(method = "setBrightness", at = @At(value = "INVOKE", target = PUT, ordinal = 3, remap = false), require = 0)
    private float lads$hitStrength(float strength) {
        HitColorModule hit = PvpVisuals.HIT_COLOR;
        return hit.on ? (lads$currentColor() >>> 24) / 255F : strength;
    }

    /** Mount Opacity's flag lives as long as one renderModel call (doRender catches what it throws: the next call sets it first). */
    @Inject(method = "renderModel", at = @At("HEAD"), require = 0)
    private void lads$mountStart(EntityLivingBase entity, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch,
                                 float scale, CallbackInfo ci) {
        PvpVisuals189.mountNow = PvpVisuals189.isMount(entity);
    }

    @Inject(method = "renderModel", at = @At("RETURN"), require = 0)
    private void lads$mountEnd(EntityLivingBase entity, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch,
                               float scale, CallbackInfo ci) {
        PvpVisuals189.mountNow = false;
    }

    @Redirect(method = "renderModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;isInvisible()Z"), require = 0)
    private boolean lads$mountInvisible(EntityLivingBase entity) {
        return PvpVisuals189.mountNow || entity.isInvisible();
    }

    @Redirect(method = "renderModel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/entity/EntityLivingBase;isInvisibleToPlayer(Lnet/minecraft/entity/player/EntityPlayer;)Z"), require = 0)
    private boolean lads$mountSeen(EntityLivingBase entity, EntityPlayer player) {
        return !PvpVisuals189.mountNow && entity.isInvisibleToPlayer(player);
    }

    @ModifyArg(method = "renderModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;color(FFFF)V"),
        index = 3, require = 0)
    private float lads$mountOpacity(float alpha) {
        return PvpVisuals189.mountNow ? PvpVisuals.MOUNT_OPACITY.alpha : alpha;
    }
}
