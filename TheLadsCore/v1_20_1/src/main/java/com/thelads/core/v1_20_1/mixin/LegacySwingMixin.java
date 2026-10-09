package com.thelads.core.v1_20_1.mixin;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.OldAnimationsModule;
import com.thelads.core.v1_20_1.feature.NativeFeatures1201;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class LegacySwingMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;getAttackStrengthScale(F)F"), require = 0)
    private float ladsNoCooldownDip(LocalPlayer player, float partial) {
        if (NativeFeatures1201.enabled("LegacySwing")) return 1.0f;
        Module mod = ModuleManager.getInstance().getModule(OldAnimationsModule.NAME);
        if (mod instanceof OldAnimationsModule oldAnims && oldAnims.isEnabled()
                && oldAnims.active(OldAnimationsModule.Feature.NO_COOLDOWN_DIP, OldAnimationsModule.Platform.MODERN)) {
            return 1.0f;
        }
        return player.getAttackStrengthScale(partial);
    }

    @Inject(method = "itemUsed", at = @At("HEAD"), cancellable = true, require = 0)
    private void ladsNoUsePop(InteractionHand hand, CallbackInfo ci) {
        if (NativeFeatures1201.enabled("LegacySwing")) ci.cancel();
    }
}
