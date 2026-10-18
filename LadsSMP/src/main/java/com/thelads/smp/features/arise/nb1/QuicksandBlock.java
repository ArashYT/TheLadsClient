package com.thelads.smp.features.arise.nb1;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Quicksand hazard block: entities sink in slowly, movement is heavily restricted,
 * and players suffer suffocation if completely submerged.
 */
public class QuicksandBlock extends Block {

    public QuicksandBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier applier, boolean inside) {
        double sinkSpeed = AriseNB1Config.get().quicksand.sinkSpeed;
        entity.makeStuckInBlock(state, new Vec3(0.5D, sinkSpeed, 0.5D));

        if (!level.isClientSide() && entity instanceof LivingEntity living && level instanceof ServerLevel serverLevel) {
            // Suffocation check: if entity head/eyes are inside quicksand block
            if (pos.getY() == Math.floor(living.getEyeY())) {
                living.hurtServer(serverLevel, level.damageSources().inWall(), 1.0F);
            }
        }
    }
}
