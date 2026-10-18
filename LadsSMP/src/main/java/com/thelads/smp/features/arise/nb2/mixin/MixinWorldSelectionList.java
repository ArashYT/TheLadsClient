package com.thelads.smp.features.arise.nb2.mixin;

import com.thelads.smp.features.arise.nb2.OWorld2CreateManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Mixin to WorldSelectionList to keep singleplayer world list open instead of
 * automatically launching the create world wizard when no saves exist (OWorld2Create).
 */
@Environment(EnvType.CLIENT)
@Mixin(WorldSelectionList.class)
public abstract class MixinWorldSelectionList {

    @Inject(
            method = "handleNewLevels(Ljava/util/List;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/worldselection/CreateWorldScreen;openFresh(Lnet/minecraft/client/Minecraft;Ljava/lang/Runnable;)V"
            ),
            cancellable = true
    )
    private void onOpenFreshCreateWorld(List<LevelSummary> levels, CallbackInfo ci) {
        if (OWorld2CreateManager.isBypassEnabled()) {
            ci.cancel();
        }
    }
}
