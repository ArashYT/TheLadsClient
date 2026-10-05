package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.EntityCulling189;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Entity Culling: blocks that change on the client (placed, broken, a chunk resent) may take a few frames to be drawn, so
 * they stop hiding anything for a second: the block itself, or a whole chunk's sections when its data arrives.
 */
@Mixin(World.class)
public abstract class WorldCullMixin {
    @Shadow @Final public boolean isRemote;

    @Inject(method = "markBlockForUpdate", at = @At("HEAD"), require = 1)
    private void ladsBlockChanged(BlockPos pos, CallbackInfo ci) {
        if (isRemote) EntityCulling189.blockChanged(pos.getX(), pos.getY(), pos.getZ());
    }

    /** A chunk's blocks arriving (or arriving again): the whole column. Light updates mark single columns and move no block. */
    @Inject(method = "markBlockRangeForRenderUpdate(IIIIII)V", at = @At("HEAD"), require = 1)
    private void ladsRangeChanged(int x1, int y1, int z1, int x2, int y2, int z2, CallbackInfo ci) {
        if (isRemote && x2 - x1 >= 15 && z2 - z1 >= 15) EntityCulling189.blocksChanged(x1, y1, z1, x2, y2, z2);
    }
}
