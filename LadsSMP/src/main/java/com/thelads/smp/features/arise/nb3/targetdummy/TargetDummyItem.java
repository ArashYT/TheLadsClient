package com.thelads.smp.features.arise.nb3.targetdummy;

import com.thelads.smp.features.arise.nb3.AriseNb3Mod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Placeable Target Dummy item allowing players to deploy combat testing dummies.
 */
public class TargetDummyItem extends Item {
    public TargetDummyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Direction direction = context.getClickedFace();
        if (direction == Direction.DOWN) {
            return InteractionResult.FAIL;
        }

        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        BlockPos spawnPos = clickedPos.relative(direction);

        if (!level.isClientSide()) {
            TargetDummyEntity dummy = new TargetDummyEntity(AriseNb3Mod.TARGET_DUMMY_ENTITY_TYPE, level);
            dummy.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
            dummy.setYRot(context.getRotation());
            level.addFreshEntity(dummy);
            level.playSound(null, spawnPos, SoundEvents.ARMOR_STAND_PLACE, SoundSource.BLOCKS, 0.75f, 0.8f);

            if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
