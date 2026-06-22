package com.thelads.core.mixin.alwayson.advancementsreloaded;

import com.thelads.core.features.alwayson.advancementsreloaded.AdvancementTreePositioning;
import java.util.Set;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementTree;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AdvancementTree.class)
public class AdvancementTreeAdvrMixin {
    @Shadow
    @Final
    private Set<AdvancementNode> roots;

    @Inject(method = "tryInsert", at = @At("RETURN"))
    private void recalculateTreePositions(AdvancementHolder advancement, CallbackInfoReturnable<Boolean> cir) {
        if (!com.thelads.core.config.ModuleManager.getInstance().getModule("AdvancementsReloaded").isEnabled()) {
            return;
        }
        if (cir.getReturnValue() && advancement.value().parent().isEmpty()) {
            for (AdvancementNode root : this.roots) {
                if (!root.holder().equals(advancement) || !root.advancement().display().isPresent()) continue;
                AdvancementTreePositioning.run(root);
                break;
            }
        }
    }
}
