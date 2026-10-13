package com.thelads.core.v1_8_9.mixin;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(World.class)
public abstract class WorldEntitiesMixin {
    /**
     * Eliminates O(N*M) ArrayList.removeAll comparisons during entity and tile entity unloads by converting
     * collections with > 2 elements into a HashSet before removal, preserving exact elements and list ordering.
     */
    @Redirect(method = "updateEntities", at = @At(value = "INVOKE",
        target = "Ljava/util/List;removeAll(Ljava/util/Collection;)Z"), require = 1)
    private boolean ladsFastRemoveAll(List<?> list, Collection<?> toRemove) {
        if (list.isEmpty() || toRemove.isEmpty()) return false;
        if (toRemove.size() <= 2) return list.removeAll(toRemove);
        return list.removeAll(new HashSet<>(toRemove));
    }
}
