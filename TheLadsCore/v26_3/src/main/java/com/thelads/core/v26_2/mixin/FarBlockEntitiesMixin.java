package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.FarBlockDistance;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(BlockEntityRenderer.class)
public interface FarBlockEntitiesMixin {
    // Only the inherited distance predicate changes. Loaded-section extraction,
    // frustum checks and any renderer-specific shouldRender overrides stay intact.
    @ModifyArg(method = "shouldRender", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/phys/Vec3;closerThan(Lnet/minecraft/core/Position;D)Z"), index = 1, require = 1)
    private double lads$loadedRenderDistance(double vanilla) {
        return FarBlockDistance.resolve(vanilla, NativeQualityOfLife.number("FarBlockEntities", "Distance", 128),
            NativeQualityOfLife.enabled("FarBlockEntities"));
    }
}
