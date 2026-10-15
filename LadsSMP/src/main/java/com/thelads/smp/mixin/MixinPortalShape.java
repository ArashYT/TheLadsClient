package com.thelads.smp.mixin;

import com.thelads.smp.config.LadsConfig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Consolidates 'cryingportals': Allows Crying Obsidian in Nether Portal frames when enabled in LadsConfig.
 */
@Mixin(PortalShape.class)
public abstract class MixinPortalShape {

    @Inject(method = "lambda$static$0", at = @At("HEAD"), cancellable = true)
    private static void ladssmp$allowCryingObsidianFrame(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (LadsConfig.get().features.cryingPortals && state != null && state.is(Blocks.CRYING_OBSIDIAN)) {
            cir.setReturnValue(true);
        }
    }
}
