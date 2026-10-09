package com.thelads.core.v1_20_1.mixin;

import com.mojang.authlib.GameProfile;
import com.thelads.core.v1_20_1.feature.NativeFeatures1201;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin extends AbstractClientPlayer {
    public LocalPlayerMixin(ClientLevel clientLevel, GameProfile gameProfile) {
        super(clientLevel, gameProfile);
    }

    @Shadow public abstract boolean hasEnoughImpulseToStartSprinting();
    @Shadow public abstract boolean hasEnoughFoodToStartSprinting();

    @Inject(method = "aiStep()V", at = @At("TAIL"))
    private void ladsSprintAiStep(CallbackInfo ci) {
        if (NativeFeatures1201.isSprintToggled()) {
            if (!this.isSprinting() && this.hasEnoughImpulseToStartSprinting() && this.hasEnoughFoodToStartSprinting()
                    && !this.isUsingItem() && !this.hasEffect(MobEffects.BLINDNESS)
                    && !this.isShiftKeyDown() && !this.horizontalCollision && !this.isFallFlying()) {
                this.setSprinting(true);
            }
        }
    }
}
