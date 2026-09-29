package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeKeyBindings;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Arrays;

@Mixin(Options.class)
public class OptionsMixin {
    @Shadow @Final @Mutable public KeyMapping[] keyMappings;

    @Inject(method = "load()V", at = @At("HEAD"), require = 1)
    private void ladsRegisterControls(CallbackInfo ci) {
        // Register before native options parsing, so saved bindings load on the first launch.
        // Reloads and upgrades add only missing entries, retaining every other mod's binding.
        boolean zoom = false, modules = false;
        for (KeyMapping binding : keyMappings) {
            zoom |= binding == NativeKeyBindings.ZOOM;
            modules |= binding == NativeKeyBindings.MODULES;
        }
        if (zoom && modules) return;
        int next = keyMappings.length;
        keyMappings = Arrays.copyOf(keyMappings, next + (zoom ? 0 : 1) + (modules ? 0 : 1));
        if (!zoom) keyMappings[next++] = NativeKeyBindings.ZOOM;
        if (!modules) keyMappings[next] = NativeKeyBindings.MODULES;
    }
}
