package com.thelads.core.mixin.alwayson.advancementsreloaded;

import com.thelads.core.features.alwayson.advancementsreloaded.AdvancementTreePositioning;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.TreeNodePosition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TreeNodePosition.class)
public class TreeNodePositionAdvrMixin {
    @Inject(method = "run", at = @At("HEAD"), cancellable = true)
    private static void replaceTreePositioning(AdvancementNode rootNode, CallbackInfo ci) {
        AdvancementTreePositioning.run(rootNode);
        ci.cancel();
    }
}
