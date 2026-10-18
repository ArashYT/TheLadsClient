package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.DynamicLights189;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks OptiFine's DynamicLights.getLightLevel(Entity) to provide dynamic lighting
 * for ItemFrames holding glowing items and FallingBlocks of glowing blocks.
 */
@Pseudo
@Mixin(targets = "net.optifine.DynamicLights", remap = false)
public abstract class OptiFineDynamicLightsMixin {
    @Inject(method = "getLightLevel(Lnet/minecraft/entity/Entity;)I", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void ladsGetEntityLight(Entity entity, CallbackInfoReturnable<Integer> cir) {
        if (entity instanceof EntityItemFrame) {
            ItemStack stack = ((EntityItemFrame) entity).getDisplayedItem();
            if (stack != null && stack.getItem() != null) {
                int lum = DynamicLights189.itemLuminance(stack);
                if (lum > cir.getReturnValue()) cir.setReturnValue(lum);
            }
        } else if (entity instanceof EntityFallingBlock) {
            IBlockState state = ((EntityFallingBlock) entity).getBlock();
            if (state != null && state.getBlock() != null) {
                int lum = state.getBlock().getLightValue();
                if (lum > cir.getReturnValue()) cir.setReturnValue(lum);
            }
        }
    }
}
