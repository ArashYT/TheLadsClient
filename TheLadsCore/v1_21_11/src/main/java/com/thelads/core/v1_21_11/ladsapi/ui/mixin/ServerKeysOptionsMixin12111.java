package com.thelads.core.v1_21_11.ladsapi.ui.mixin;

import com.thelads.core.v1_21_11.ladsapi.ui.ServerKeys12111;
import java.util.Arrays;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Server keys are temporary: options.txt is written without them (the array is swapped for the duration of save()). */
@Mixin(Options.class)
public abstract class ServerKeysOptionsMixin12111 implements ServerKeys12111.OptionsKeys {
    @Shadow @Final @Mutable public KeyMapping[] keyMappings;
    @Unique private KeyMapping[] lads$all;

    @Override
    public void lads$setKeys(KeyMapping[] keys) {
        keyMappings = keys;
    }

    @Inject(method = "save()V", at = @At("HEAD"))
    private void lads$hideServerKeys(CallbackInfo ci) {
        lads$all = keyMappings;
        keyMappings = Arrays.stream(keyMappings).filter(k -> !ServerKeys12111.isServerKey(k)).toArray(KeyMapping[]::new);
    }

    @Inject(method = "save()V", at = @At("RETURN"))
    private void lads$restoreKeys(CallbackInfo ci) {
        if (lads$all != null) keyMappings = lads$all;
        lads$all = null;
    }
}
