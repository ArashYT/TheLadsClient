package com.thelads.core.v1_21_1.embedded.entityculling.mixin;

import com.thelads.core.v1_21_1.embedded.entityculling.Cullable;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BlockEntity.class)
public abstract class BlockEntityCullableMixin implements Cullable {
    @Unique private volatile int lads$hiddenVersion;
    @Unique private volatile long lads$checkedAt;
    @Unique private volatile long lads$seenAt;
    @Unique private volatile boolean lads$tracked;

    @Override public int lads$hiddenVersion() { return lads$hiddenVersion; }
    @Override public long lads$checkedAt() { return lads$checkedAt; }

    @Override
    public void lads$cullResult(int hiddenVersion, long checkedAt) {
        lads$checkedAt = checkedAt;
        lads$hiddenVersion = hiddenVersion;
    }

    @Override public long lads$seenAt() { return lads$seenAt; }
    @Override public void lads$seen(long now) { lads$seenAt = now; }
    @Override public boolean lads$tracked() { return lads$tracked; }
    @Override public void lads$tracked(boolean tracked) { lads$tracked = tracked; }
}
