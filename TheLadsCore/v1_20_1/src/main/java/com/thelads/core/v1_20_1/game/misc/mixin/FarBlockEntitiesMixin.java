package com.thelads.core.v1_20_1.game.misc.mixin;

import com.thelads.core.v1_20_1.game.misc.FarBlockDistance1201;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Far block entities: the inherited distance predicate of BlockEntityRenderer.shouldRender (64 blocks, or getViewDistance()) takes
 * the module's Distance when that is larger. Frustum checks and any renderer-specific shouldRender override stay intact.
 */
@Mixin(BlockEntityRenderer.class)
public interface FarBlockEntitiesMixin {
    @ModifyArg(method = "shouldRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;closerThan(Lnet/minecraft/core/Position;D)Z"),
        index = 1, require = 1)
    private double lads$loadedRenderDistance(double vanilla) {
        return FarBlockDistance1201.resolve(vanilla);
    }
}
