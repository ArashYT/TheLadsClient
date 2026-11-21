package com.thelads.core.v26_2.feature.mapframes;

import com.thelads.core.perf.MapFrames;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;

/** Frame-side cache of "is this map frame's own model invisible": solid block behind it and a map frame with the same facing on all four sides. 20 game ticks. */
public interface MapFrameCache26 {
    long lads$mapStamp();
    boolean lads$mapHidden();
    void lads$mapSet(long stamp, boolean hidden);

    static boolean hidden(ItemFrame frame) {
        if (!MapFrames.enabled() || !(frame instanceof MapFrameCache26 cache)) return false;
        Level level = frame.level();
        long now = level.getGameTime(), age = now - cache.lads$mapStamp();
        if (age < 0 || age >= 20) cache.lads$mapSet(now, test(frame, level));
        return cache.lads$mapHidden();
    }

    private static boolean hasMap(ItemFrame frame) {
        ItemStack stack = frame.getItem();
        return !stack.isEmpty() && frame.getFramedMapId(stack) != null;
    }

    /** True when the map data is known and no pixel is see-through (colour id 0 shows the frame model behind it). Runs inside the 20-tick cache only. */
    private static boolean opaque(ItemFrame frame, Level level) {
        MapItemSavedData data = level.getMapData(frame.getFramedMapId(frame.getItem()));
        if (data == null) return false;
        for (byte b : data.colors) if (((b & 0xFF) >> 2) == 0) return false;
        return true;
    }

    private static boolean test(ItemFrame frame, Level level) {
        if (!hasMap(frame) || !opaque(frame, level)) return false;
        Direction facing = frame.getDirection();
        BlockPos pos = frame.getPos(), behind = pos.relative(facing.getOpposite());
        if (!level.getBlockState(behind).isSolidRender()) return false;
        for (Direction side : Direction.values()) {
            if (side.getAxis() == facing.getAxis()) continue;
            if (level.getEntitiesOfClass(ItemFrame.class, new AABB(pos.relative(side)),
                other -> other.getDirection() == facing && hasMap(other)).isEmpty()) return false;
        }
        MapFrames.hidFrame();
        return true;
    }
}
