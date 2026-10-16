package com.thelads.smp.mixin;

import com.thelads.smp.config.LadsConfig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Consolidates 'noscaffoldingdown': Requires looking down (>70 degrees pitch) while sneaking to descend through scaffolding.
 */
@Mixin(ScaffoldingBlock.class)
public abstract class MixinScaffoldingBlock {

    @Redirect(
            method = "getCollisionShape",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/shapes/CollisionContext;isDescending()Z"
            )
    )
    private boolean ladssmp$requireLookingDownToDescend(CollisionContext context) {
        if (!LadsConfig.get().features.noScaffoldingDown) {
            return context.isDescending();
        }
        return context.isDescending()
                && context instanceof EntityCollisionContext ecc
                && ecc.getEntity() instanceof Player player
                && player.getXRot() > 70.0f;
    }
}
