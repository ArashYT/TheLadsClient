package com.thelads.core.v1_21_11.embedded.emf.mixin.mixins;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.thelads.core.v1_21_11.embedded.emf.EMF;
import com.thelads.core.v1_21_11.embedded.emf.EMFManager;

@Mixin(Minecraft.class)
public abstract class MixinResourceReloadStart {

    @Inject(
            method = "reloadResourcePacks(ZLnet/minecraft/client/Minecraft$GameLoadCookie;)Ljava/util/concurrent/CompletableFuture;",
            at = @At("HEAD"))
    private void emf$reloadStart(CallbackInfoReturnable<Float> cir) {
        EMF.isLoadingPhase = true;
        if (EMF.testForForgeLoadingError()) return;
        EMFManager.resetInstance();
    }

}


