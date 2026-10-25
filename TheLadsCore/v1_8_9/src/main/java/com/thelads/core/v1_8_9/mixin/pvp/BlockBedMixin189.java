package com.thelads.core.v1_8_9.mixin.pvp;

import com.thelads.core.v1_8_9.feature.BedColors189;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.material.Material;
import net.minecraft.util.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockBed.class)
public abstract class BlockBedMixin189 extends Block {
    public BlockBedMixin189(Material materialIn) {
        super(materialIn);
    }

    @Override
    public int colorMultiplier(IBlockAccess worldIn, BlockPos pos, int renderPass) {
        int color = BedColors189.getBedColor(worldIn, pos);
        return color != -1 ? color : super.colorMultiplier(worldIn, pos, renderPass);
    }
}
