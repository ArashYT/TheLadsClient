package com.thelads.core.v26_2.mixin;
import com.thelads.core.v26_2.feature.NativeFeatures;
import com.thelads.core.v26_2.feature.NativeMenuKey;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class ClientTickMixin {
    @Inject(method = "tick()V", at = @At("HEAD"), require = 1)
    private void ladsTickFeatures(CallbackInfo ci) {
        NativeMenuKey.tick();
        NativeFeatures.tick();
        com.thelads.core.v26_2.feature.NativeQualityOfLife.tick();
    }
    @Inject(method="tick()V",at=@At("TAIL"),require=1)
    private void ladsLegacyCameraTick(CallbackInfo ci){
        com.thelads.core.v26_2.feature.NativeVerticalBob.tick();
        com.thelads.core.v26_2.feature.NativeOldAnimations.tick(((Minecraft) (Object) this).player);
    }

    // 1.7 Animations, Swing while using items: an attack click that vanilla drops while an item is in use swings the arm (drawn only).
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/KeyMapping;consumeClick()Z"), require = 1)
    private boolean ladsSwingWhileUsing(net.minecraft.client.KeyMapping key, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Boolean> original) {
        boolean clicked = original.call(key);
        Minecraft mc = (Minecraft) (Object) this;
        if (clicked && key == mc.options.keyAttack && mc.player != null && mc.player.isUsingItem())
            com.thelads.core.v26_2.feature.NativeOldAnimations.attackClicked(mc.player);
        return clicked;
    }

    // 1.7 Animations: the use key blocks, draws a bow or eats while the attack key mines a block, as in 1.7 (vanilla waits).
    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(method = "startUseItem", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;isDestroying()Z"), require = 1)
    private boolean ladsUseWhileMining(boolean destroying) {
        return destroying && !com.thelads.core.v26_2.feature.NativeOldAnimations.useWhileMining(((Minecraft) (Object) this).player);
    }
}
