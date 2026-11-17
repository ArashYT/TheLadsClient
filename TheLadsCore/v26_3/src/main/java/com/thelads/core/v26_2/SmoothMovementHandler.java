package com.thelads.core.v26_2;

import com.thelads.core.modules.SmoothMovementModule;
import net.minecraft.core.PositionAndRotation;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.entity.SteppedInterpolationHandler;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Holds the 26.2 linear and 26.3 stepped handlers and forwards to the one Smooth Movement picks; a switch cancels the old one. */
public final class SmoothMovementHandler implements InterpolationHandler {
    private final InterpolationHandler linear;
    private final InterpolationHandler stepped;
    private InterpolationHandler active;

    public SmoothMovementHandler(LivingEntity entity) {
        this.linear = LinearInterpolationHandler.create(entity);
        this.stepped = SteppedInterpolationHandler.create(entity);
        this.active = selected();
    }

    private InterpolationHandler selected() {
        return SmoothMovementModule.active() ? linear : stepped;
    }

    private InterpolationHandler active() {
        InterpolationHandler s = selected();
        if (s != active) {
            active.cancel();
            active = s;
        }
        return active;
    }

    @Override public @Nullable PositionAndRotation target() { return active().target(); }
    @Override public boolean interpolateTo(@Nullable PositionPath position, float yRot, float xRot, boolean hasRotation) { return active().interpolateTo(position, yRot, xRot, hasRotation); }
    @Override public void interpolate() { active().interpolate(); }
    @Override public void applyPredictedMovement(Vec3 delta) { active().applyPredictedMovement(delta); }
    @Override public boolean hasActiveInterpolation() { return active().hasActiveInterpolation(); }
    @Override public void cancel() { active().cancel(); }
}
