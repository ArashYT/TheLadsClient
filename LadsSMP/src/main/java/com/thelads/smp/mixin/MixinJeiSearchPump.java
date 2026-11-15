package com.thelads.smp.mixin;

import com.thelads.smp.client.LoadingPump;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** See {@link MixinJeiHotLoopPump}: JEI's search index gets one document per ingredient. */
@Pseudo
@Mixin(targets = "mezz.jei.common.search.LuceneSearchStorage", remap = false)
public class MixinJeiSearchPump {
    @Inject(method = "put", at = @At("HEAD"), remap = false, require = 0)
    private void ladssmp$pump(CallbackInfo ci) {
        LoadingPump.poll();
    }
}
