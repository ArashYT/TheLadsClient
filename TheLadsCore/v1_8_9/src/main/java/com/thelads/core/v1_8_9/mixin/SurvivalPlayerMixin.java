package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.SurvivalTweaks189;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** SurvivalTweaks Faster obsidian mining: a tool that mines obsidian does so 1.6x faster (before Efficiency), as 26.x's Tool speed. */
@Mixin(EntityPlayer.class)
public abstract class SurvivalPlayerMixin {
    @Redirect(method = "getBreakSpeed", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/Item;getDigSpeed(Lnet/minecraft/item/ItemStack;Lnet/minecraft/block/state/IBlockState;)F", remap = false), remap = false, require = 1)
    private float ladsFasterObsidian(Item item, ItemStack stack, IBlockState state) {
        float speed = item.getDigSpeed(stack, state);
        return speed > 1.0F && state.getBlock() == Blocks.obsidian && SurvivalTweaks189.fasterObsidian((EntityPlayer) (Object) this) ? speed * 1.6F : speed;
    }
}
