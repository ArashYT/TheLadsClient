package com.thelads.core.v1_21_11.mixin;
import com.thelads.core.v1_21_11.feature.NativeFeatures;
import com.thelads.core.v1_21_11.feature.NativeMenuKey;
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
        com.thelads.core.v1_21_11.feature.NativeQualityOfLife.tick();
        com.thelads.core.mods.CoreCatalogExporter.exportIfChanged();
    }

    /** 1.7 Animations: an attack click consumed while an item is in use still swings (Blockhitting, Swing while using items). */
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "handleKeybinds",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;consumeClick()Z"), require = 1)
    private boolean lads$swingWhileUsing(net.minecraft.client.KeyMapping key, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Boolean> original) {
        boolean click = original.call(key);
        Minecraft mc = (Minecraft) (Object) this;
        if (click && key == mc.options.keyAttack && mc.player != null && mc.player.isUsingItem())
            com.thelads.core.v1_21_11.feature.NativeOldAnimations.attackWhileUsing(mc.player);
        return click;
    }
}
