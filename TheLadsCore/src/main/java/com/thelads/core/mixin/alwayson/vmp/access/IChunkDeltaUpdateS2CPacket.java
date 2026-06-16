/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.SectionPos
 *  net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
 *  net.minecraft.world.level.block.state.BlockState
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Mutable
 *  org.spongepowered.asm.mixin.gen.Accessor
 */
package com.thelads.core.mixin.alwayson.vmp.access;

import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={ClientboundSectionBlocksUpdatePacket.class})
public interface IChunkDeltaUpdateS2CPacket {
    @Accessor(value="sectionPos")
    public SectionPos getSectionPos();

    @Mutable
    @Accessor(value="sectionPos")
    public void setSectionPos(SectionPos var1);

    @Mutable
    @Accessor(value="positions")
    public void setPositions(short[] var1);

    @Mutable
    @Accessor(value="states")
    public void setBlockStates(BlockState[] var1);
}

