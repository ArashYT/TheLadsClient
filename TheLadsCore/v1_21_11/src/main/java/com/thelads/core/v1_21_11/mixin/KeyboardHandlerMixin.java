package com.thelads.core.v1_21_11.mixin;
import com.thelads.core.v1_21_11.feature.NativeFeatures;
import com.thelads.core.v1_21_11.feature.NativeMenuKey;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
    @Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsKey(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (window != Minecraft.getInstance().getWindow().handle()) return;
        if (NativeMenuKey.key(event, action)) { ci.cancel(); return; }
        NativeFeatures.key(event, action);
    }
}
