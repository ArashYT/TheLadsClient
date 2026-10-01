// Adapted from World Play Time 1.2.2 by Khajiitos (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.playtime.mixin;

import com.thelads.core.v1_21_1.embedded.playtime.util.IWithPlayTime;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LevelSummary.class)
public class LevelSummaryMixin implements IWithPlayTime {
    @Unique
    private int worldplaytime$playTimeTicks = -1;

    @Override
    public void setPlayTimeTicks(int playTimeTicks) {
        this.worldplaytime$playTimeTicks = playTimeTicks;
    }

    @Override
    public int getPlayTimeTicks() {
        return this.worldplaytime$playTimeTicks;
    }
}
