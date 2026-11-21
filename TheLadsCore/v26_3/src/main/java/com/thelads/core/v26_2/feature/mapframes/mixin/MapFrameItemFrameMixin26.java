package com.thelads.core.v26_2.feature.mapframes.mixin;

import com.thelads.core.v26_2.feature.mapframes.MapFrameCache26;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemFrame.class)
abstract class MapFrameItemFrameMixin26 implements MapFrameCache26 {
    @Unique private long lads$mapStamp = Long.MIN_VALUE / 2;
    @Unique private boolean lads$mapHidden;

    @Override public long lads$mapStamp() { return lads$mapStamp; }
    @Override public boolean lads$mapHidden() { return lads$mapHidden; }
    @Override public void lads$mapSet(long stamp, boolean hidden) { lads$mapStamp = stamp; lads$mapHidden = hidden; }
}
