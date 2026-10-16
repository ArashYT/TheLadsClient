package com.thelads.smp.mixin;

import com.thelads.smp.config.LadsConfig;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Consolidates 'letmedespawn': Allows Endermen carrying blocks to despawn normally.
 */
@Mixin(Enderman.class)
public abstract class MixinEnderman {

    @Redirect(
            method = "requiresCustomPersistence",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/monster/Enderman;getCarriedBlock()Lnet/minecraft/world/level/block/state/BlockState;"
            )
    )
    private BlockState ladssmp$ignoreCarriedBlockForPersistence(Enderman instance) {
        if (LadsConfig.get().features.letMeDespawn) {
            return null;
        }
        return instance.getCarriedBlock();
    }
}
