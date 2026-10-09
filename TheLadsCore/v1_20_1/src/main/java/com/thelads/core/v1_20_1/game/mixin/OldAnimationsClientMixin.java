package com.thelads.core.v1_20_1.game.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v1_20_1.game.anim.NativeOldAnimations1201;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public class OldAnimationsClientMixin {
    // 1.7 Animations, Swing while using items: an attack click that vanilla drops while an item is in use swings the arm (drawn only).
    @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;consumeClick()Z"), require = 1)
    private boolean lads$swingWhileUsing(KeyMapping key, Operation<Boolean> original) {
        boolean clicked = original.call(key);
        Minecraft mc = (Minecraft) (Object) this;
        if (clicked && key == mc.options.keyAttack && mc.player != null && mc.player.isUsingItem()) NativeOldAnimations1201.attackClicked(mc.player);
        return clicked;
    }

    // 1.7 Animations: the use key blocks, draws a bow or eats while the attack key mines a block, as in 1.7 (vanilla waits).
    @ModifyExpressionValue(method = "startUseItem", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;isDestroying()Z"), require = 1)
    private boolean lads$useWhileMining(boolean destroying) {
        return destroying && !NativeOldAnimations1201.useWhileMining(((Minecraft) (Object) this).player);
    }
}
