package com.thelads.smp.features.arise.nb9.entity;

import com.thelads.smp.features.arise.nb9.AriseNB9Features;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity for wall-mounted lanterns.
 * Stores gentle swing physics without per-tick heap allocations.
 */
public class WallLanternBlockEntity extends BlockEntity {
    private float swingAngle = 0.0F;
    private float prevSwingAngle = 0.0F;
    private float swingVelocity = 0.0F;

    public WallLanternBlockEntity(BlockPos pos, BlockState state) {
        super(AriseNB9Features.WALL_LANTERN_BE, pos, state);
    }

    public float getSwingAngle() {
        return swingAngle;
    }

    public float getPrevSwingAngle() {
        return prevSwingAngle;
    }

    public void addImpulse(float impulse) {
        this.swingVelocity += impulse;
    }

    public void tickPhysics() {
        this.prevSwingAngle = this.swingAngle;
        this.swingVelocity *= 0.92F;
        this.swingAngle += this.swingVelocity;
        this.swingVelocity -= this.swingAngle * 0.08F;

        // Clamp very small oscillations to 0
        if (Math.abs(this.swingAngle) < 0.001F && Math.abs(this.swingVelocity) < 0.001F) {
            this.swingAngle = 0.0F;
            this.swingVelocity = 0.0F;
        }
    }
}
