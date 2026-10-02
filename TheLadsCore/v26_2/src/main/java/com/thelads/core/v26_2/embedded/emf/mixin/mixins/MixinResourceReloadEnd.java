package com.thelads.core.v26_2.embedded.emf.mixin.mixins;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v26_2.embedded.emf.EMF;
import com.thelads.core.v26_2.embedded.emf.EMFManager;


@Mixin(Minecraft.class)
public abstract class MixinResourceReloadEnd {


    @Inject(method = "onResourceLoadFinished", at = @At("HEAD"))
    private void emf$reloadFinish(final CallbackInfo ci) {
        if (EMF.testForForgeLoadingError()) return;
        EMFManager.getInstance().modifyEBEIfRequired();
        EMFManager.getInstance().reloadEnd();
        EMF.isLoadingPhase = false;
    }
}


