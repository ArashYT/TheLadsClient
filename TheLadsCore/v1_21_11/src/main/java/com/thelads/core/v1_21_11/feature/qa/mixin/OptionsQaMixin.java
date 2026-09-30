package com.thelads.core.v1_21_11.feature.qa.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.thelads.core.v1_21_11.feature.NativeWorldVerification;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;

/** As on 26.x: the QA-only pauseOnLostFocus override never reaches options.txt. */
@Mixin(Options.class)
public class OptionsQaMixin {
    @WrapMethod(method = "save()V")
    private void ladsQaSave(Operation<Void> original) {
        Options options = (Options) (Object) this;
        boolean qaPause = NativeWorldVerification.active(), pauseBefore = options.pauseOnLostFocus;
        if (qaPause) options.pauseOnLostFocus = NativeWorldVerification.originalPauseOnLostFocus();
        try { original.call(); }
        finally { if (qaPause) options.pauseOnLostFocus = pauseBefore; }
    }
}
