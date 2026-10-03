package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import net.minecraft.client.settings.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lads Zoom is the only zoom: while it is on, Essential's zoom key ("keybind.name.ZOOM", also C) reads as up. Essential creates
 * that key only when OptiFine is not loaded (the 1.8.9 pack loads OptiFine), so this covers a game without it. On the vanilla
 * KeyBinding Essential polls, as on the other versions: Essential is downloaded at runtime.
 */
@Mixin(KeyBinding.class)
public abstract class EssentialZoomMixin189 {
    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsOnlyZoom(CallbackInfoReturnable<Boolean> cir) {
        if (!"keybind.name.ZOOM".equals(((KeyBinding) (Object) this).getKeyDescription())) return;
        Module zoom = ModuleManager.getInstance().getModule("Zoom");
        if (zoom != null && zoom.isEnabled()) cir.setReturnValue(false);
    }
}
