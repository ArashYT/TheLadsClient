package com.thelads.core.v26_2.feature.async.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.thelads.core.v26_2.feature.async.AsyncTicking;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Mixin;

/** Starting, replacing or stopping a path updates the level's set of navigating mobs (Lithium keeps that set). */
@Mixin(PathNavigation.class)
abstract class PathNavigationAsyncMixin {
    @WrapMethod(method = "moveTo(Lnet/minecraft/world/level/pathfinder/Path;D)Z")
    private boolean lads$lockMoveTo(Path path, double speed, Operation<Boolean> original) {
        return AsyncTicking.locked(original, path, speed);
    }

    @WrapMethod(method = "stop")
    private void lads$lockStop(Operation<Void> original) {
        AsyncTicking.locked(original);
    }

    @WrapMethod(method = "recomputePath")
    private void lads$lockRecompute(Operation<Void> original) {
        AsyncTicking.locked(original);
    }
}
