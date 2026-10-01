package com.thelads.core.v1_21_1.embedded.emf.mixin.mixins;

import net.minecraft.client.ResourceLoadStateTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_1.embedded.emf.EMF;
import com.thelads.core.v1_21_1.embedded.emf.EMFManager;


@Mixin(ResourceLoadStateTracker.class)
public abstract class MixinResourceReloadEnd {


    @Inject(method = "finishReload", at = @At("HEAD"))
    private void emf$reloadFinish(final CallbackInfo ci) {
        if (EMF.testForForgeLoadingError()) return;
        EMFManager.getInstance().modifyEBEIfRequired();
        EMFManager.getInstance().reloadEnd();
        EMF.isLoadingPhase = false;
    }
}


