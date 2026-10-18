package com.thelads.smp.features.arise.nb9;

import com.thelads.smp.features.arise.nb9.block.DyedCauldronBlock;
import com.thelads.smp.features.arise.nb9.block.PotionCauldronBlock;
import com.thelads.smp.features.arise.nb9.entity.DyedCauldronBlockEntity;
import com.thelads.smp.features.arise.nb9.entity.PotionCauldronBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Handles all cauldron interactions: water dyeing, leather dipping, potion pouring,
 * potion mixing, glass bottle filling, and tipped arrow dipping.
 */
public final class CauldronInteractionHelper {

    public static InteractionResult handleWaterCauldron(
            ItemStack held,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand
    ) {
        AriseNB9Config config = AriseNB9Config.get();
        if (!config.enhancedCauldron) {
            return InteractionResult.PASS;
        }

        // 1. Water Cauldron + Dye -> Dyed Cauldron (only valid on water cauldrons)
        if (config.dyeWater && held.getItem() instanceof DyeItem && state.is(Blocks.WATER_CAULDRON)) {
            DyeColor dyeColor = held.get(DataComponents.DYE);
            if (dyeColor != null) {
                if (!level.isClientSide()) {
                    int currentLevel = state.getValue(LayeredCauldronBlock.LEVEL);
                    int colorRgb = dyeColor.getTextureDiffuseColor();

                    BlockState dyedState = AriseNB9Features.DYED_CAULDRON.defaultBlockState()
                            .setValue(DyedCauldronBlock.LEVEL, currentLevel);
                    level.setBlock(pos, dyedState, 3);

                    if (level.getBlockEntity(pos) instanceof DyedCauldronBlockEntity be) {
                        be.setColor(colorRgb);
                        be.setUsesLeft(config.dyeRecipesPerLayer);
                    }

                    if (!player.getAbilities().instabuild) {
                        held.shrink(1);
                    }
                    level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
                }
                return InteractionResult.SUCCESS;
            }
        }

        // 2. Empty Cauldron + Potion -> Potion Cauldron (only valid on empty cauldrons to preserve fluid)
        if ((held.is(Items.POTION) || held.is(Items.SPLASH_POTION) || held.is(Items.LINGERING_POTION)) && state.is(Blocks.CAULDRON)) {
            PotionContents contents = held.get(DataComponents.POTION_CONTENTS);
            if (contents != null && contents.hasEffects()) {
                if (!level.isClientSide()) {
                    BlockState potionState = AriseNB9Features.POTION_CAULDRON.defaultBlockState()
                            .setValue(PotionCauldronBlock.LEVEL, 1);
                    level.setBlock(pos, potionState, 3);

                    if (level.getBlockEntity(pos) instanceof PotionCauldronBlockEntity be) {
                        be.setPotionContents(contents);
                        be.setUsesLeft(config.potionRecipesPerLayer);
                    }

                    if (!player.getAbilities().instabuild) {
                        player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.GLASS_BOTTLE)));
                    }
                    level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                }
                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.PASS;
    }

    public static InteractionResult handleDyedCauldron(
            ItemStack held,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand
    ) {
        AriseNB9Config config = AriseNB9Config.get();
        if (!config.enhancedCauldron || !config.dyeWater) {
            return InteractionResult.PASS;
        }

        if (!(level.getBlockEntity(pos) instanceof DyedCauldronBlockEntity be)) {
            return InteractionResult.PASS;
        }

        // 1. DyeItem -> Blend colors
        if (held.getItem() instanceof DyeItem) {
            DyeColor dyeColor = held.get(DataComponents.DYE);
            if (dyeColor != null) {
                if (!level.isClientSide()) {
                    int incomingColor = dyeColor.getTextureDiffuseColor();
                    int blended = PotionMixingHelper.blendColors(be.getColor(), incomingColor);
                    be.setColor(blended);

                    if (!player.getAbilities().instabuild) {
                        held.shrink(1);
                    }
                    level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
                }
                return InteractionResult.SUCCESS;
            }
        }

        // 2. Dyeable Item -> Dye item
        if (isDyeableItem(held)) {
            if (!level.isClientSide()) {
                held.set(DataComponents.DYED_COLOR, new DyedItemColor(be.getColor()));

                int uses = be.getUsesLeft() - 1;
                if (uses <= 0) {
                    int currentLevel = state.getValue(DyedCauldronBlock.LEVEL);
                    if (currentLevel <= 1) {
                        level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
                    } else {
                        level.setBlock(pos, state.setValue(DyedCauldronBlock.LEVEL, currentLevel - 1), 3);
                        be.setUsesLeft(config.dyeRecipesPerLayer);
                    }
                } else {
                    be.setUsesLeft(uses);
                }

                level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }

        // 3. Glass Bottle -> Scoop water bottle
        if (held.is(Items.GLASS_BOTTLE)) {
            if (!level.isClientSide()) {
                ItemStack waterBottle = PotionContents.createItemStack(Items.POTION, Potions.WATER);
                player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, waterBottle));

                int currentLevel = state.getValue(DyedCauldronBlock.LEVEL);
                if (currentLevel <= 1) {
                    level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
                } else {
                    level.setBlock(pos, state.setValue(DyedCauldronBlock.LEVEL, currentLevel - 1), 3);
                }
                level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }

        // 4. Empty Bucket -> Scoop water bucket
        if (held.is(Items.BUCKET)) {
            if (!level.isClientSide()) {
                player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.WATER_BUCKET)));
                level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
                level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    public static InteractionResult handlePotionCauldron(
            ItemStack held,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand
    ) {
        AriseNB9Config config = AriseNB9Config.get();
        if (!config.enhancedCauldron) {
            return InteractionResult.PASS;
        }

        if (!(level.getBlockEntity(pos) instanceof PotionCauldronBlockEntity be)) {
            return InteractionResult.PASS;
        }

        // 1. Potion Bottle -> Add layer or mix
        if (held.is(Items.POTION) || held.is(Items.SPLASH_POTION) || held.is(Items.LINGERING_POTION)) {
            int currentLevel = state.getValue(PotionCauldronBlock.LEVEL);
            if (currentLevel < 3) {
                PotionContents incoming = held.get(DataComponents.POTION_CONTENTS);
                if (incoming != null && incoming.hasEffects()) {
                    if (!level.isClientSide()) {
                        PotionContents existing = be.getPotionContents();
                        PotionContents mixed = combinePotions(existing, incoming, config);
                        be.setPotionContents(mixed);

                        level.setBlock(pos, state.setValue(PotionCauldronBlock.LEVEL, currentLevel + 1), 3);

                        if (!player.getAbilities().instabuild) {
                            player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.GLASS_BOTTLE)));
                        }
                        level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                    }
                    return InteractionResult.SUCCESS;
                }
            }
        }

        // 2. Glass Bottle -> Scoop potion
        if (held.is(Items.GLASS_BOTTLE)) {
            if (!level.isClientSide()) {
                ItemStack filledPotion = new ItemStack(Items.POTION);
                filledPotion.set(DataComponents.POTION_CONTENTS, be.getPotionContents());
                player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, filledPotion));

                int currentLevel = state.getValue(PotionCauldronBlock.LEVEL);
                if (currentLevel <= 1) {
                    level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
                } else {
                    level.setBlock(pos, state.setValue(PotionCauldronBlock.LEVEL, currentLevel - 1), 3);
                }
                level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }

        // 3. Arrow -> Craft Tipped Arrow
        if (held.is(Items.ARROW)) {
            if (!level.isClientSide()) {
                int countToTip = Math.min(held.getCount(), 8);
                ItemStack tippedArrows = new ItemStack(Items.TIPPED_ARROW, countToTip);
                tippedArrows.set(DataComponents.POTION_CONTENTS, be.getPotionContents());

                if (!player.getAbilities().instabuild) {
                    held.shrink(countToTip);
                }

                if (held.isEmpty()) {
                    player.setItemInHand(hand, tippedArrows);
                } else {
                    if (!player.addItem(tippedArrows)) {
                        Block.popResource(level, pos, tippedArrows);
                    }
                }

                int uses = be.getUsesLeft() - 1;
                if (uses <= 0) {
                    int currentLevel = state.getValue(PotionCauldronBlock.LEVEL);
                    if (currentLevel <= 1) {
                        level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
                    } else {
                        level.setBlock(pos, state.setValue(PotionCauldronBlock.LEVEL, currentLevel - 1), 3);
                        be.setUsesLeft(config.potionRecipesPerLayer);
                    }
                } else {
                    be.setUsesLeft(uses);
                }

                level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    private static boolean isDyeableItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.has(DataComponents.DYED_COLOR)
                || stack.is(Items.LEATHER_HELMET)
                || stack.is(Items.LEATHER_CHESTPLATE)
                || stack.is(Items.LEATHER_LEGGINGS)
                || stack.is(Items.LEATHER_BOOTS)
                || stack.is(Items.LEATHER_HORSE_ARMOR)
                || stack.is(Items.WOLF_ARMOR);
    }

    private static PotionContents combinePotions(
            PotionContents existing,
            PotionContents incoming,
            AriseNB9Config config
    ) {
        if (existing == null || !existing.hasEffects()) {
            return incoming;
        }

        List<PotionMixingHelper.PotionEffectEntry> existingList = new ArrayList<>();
        for (MobEffectInstance inst : existing.getAllEffects()) {
            String id = BuiltInRegistries.MOB_EFFECT.getKey(inst.getEffect().value()).toString();
            existingList.add(new PotionMixingHelper.PotionEffectEntry(id, inst.getDuration(), inst.getAmplifier()));
        }

        List<PotionMixingHelper.PotionEffectEntry> incomingList = new ArrayList<>();
        for (MobEffectInstance inst : incoming.getAllEffects()) {
            String id = BuiltInRegistries.MOB_EFFECT.getKey(inst.getEffect().value()).toString();
            incomingList.add(new PotionMixingHelper.PotionEffectEntry(id, inst.getDuration(), inst.getAmplifier()));
        }

        PotionMixingHelper.MixedPotionResult result = PotionMixingHelper.mix(
                existingList,
                incomingList,
                config.inversePotions,
                config.potionMixingLimit,
                existing.getColor(),
                incoming.getColor()
        );

        List<MobEffectInstance> combinedEffects = new ArrayList<>();
        for (PotionMixingHelper.PotionEffectEntry entry : result.effects()) {
            BuiltInRegistries.MOB_EFFECT.get(net.minecraft.resources.Identifier.parse(entry.effectId()))
                    .ifPresent(holder -> combinedEffects.add(new MobEffectInstance(holder, entry.duration(), entry.amplifier())));
        }

        return new PotionContents(
                Optional.empty(),
                Optional.of(result.blendedColor()),
                combinedEffects,
                Optional.empty()
        );
    }
}
