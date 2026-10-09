package com.thelads.core.v26_2.feature.fastquit.mixin;

import com.thelads.core.v26_2.feature.fastquit.FastQuit26;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Create World loads data packs and registries: only once the last server has fully stopped. */
@Mixin(CreateWorldScreen.class)
abstract class CreateWorldScreenFastQuitMixin {
    @Inject(method = "openCreateWorldScreen", at = @At("HEAD"))
    private static void lads$awaitSaves(CallbackInfo ci) {
        FastQuit26.awaitSaves(true);
    }
}
