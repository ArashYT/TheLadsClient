package com.thelads.smp.features.arise.nb4;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entrypoint and event dispatcher for Native Batch NB4 features:
 * - Config loading and persistence (config/ladssmp/arise-nb4.json)
 * - Culinary items and recipes registration (unconditional registration for registry safety)
 * - Animal tallow drop butchery integration (Delightful Slaughter)
 * - Melon juicing in-world and hand interaction (Delightful)
 */
public final class AriseNB4FeatureManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/AriseNB4");
    private static boolean INITIALIZED = false;

    private AriseNB4FeatureManager() {}

    public static synchronized void init() {
        if (INITIALIZED) {
            return;
        }
        INITIALIZED = true;

        long start = System.currentTimeMillis();
        AriseNB4Config config = AriseNB4Config.load();

        // Always register items to maintain registry integrity across world loads
        AriseNB4Items.registerAll();

        if (config.foodsEnabled) {
            if (config.melonJuicing) {
                registerMelonJuicing();
            }
            if (config.butcheryTallowDrops) {
                registerButcheryTallow();
            }
        }

        LOGGER.info("[Arise NB4] Initialized in {} ms (Advancements={}, Foods={}, MelonJuicing={}, TallowDrops={})",
                System.currentTimeMillis() - start,
                config.advancementsEnabled,
                config.foodsEnabled,
                config.melonJuicing,
                config.butcheryTallowDrops);
    }

    private static void registerMelonJuicing() {
        // In-world block interaction: right-clicking a melon block with a glass bottle
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!AriseNB4Config.get().melonJuicing) {
                return InteractionResult.PASS;
            }

            ItemStack held = player.getItemInHand(hand);
            if (!held.is(Items.GLASS_BOTTLE)) {
                return InteractionResult.PASS;
            }

            if (!world.getBlockState(hitResult.getBlockPos()).is(Blocks.MELON)) {
                return InteractionResult.PASS;
            }

            if (!world.isClientSide()) {
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                // Destroy/carve the melon block so it cannot be infinitely juiced
                world.destroyBlock(hitResult.getBlockPos(), false);
                ItemStack juice = new ItemStack(AriseNB4Items.MELON_JUICE);
                if (!player.getInventory().add(juice)) {
                    player.spawnAtLocation((ServerLevel) world, juice);
                }
                world.playSound(null, hitResult.getBlockPos(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1.0f, 1.0f);
            }

            return InteractionResult.SUCCESS;
        });

        // Item-based interaction: right-clicking bottle with melon slice in offhand
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!AriseNB4Config.get().melonJuicing) {
                return InteractionResult.PASS;
            }

            ItemStack held = player.getItemInHand(hand);
            if (!held.is(Items.GLASS_BOTTLE)) {
                return InteractionResult.PASS;
            }

            InteractionHand otherHand = (hand == InteractionHand.MAIN_HAND) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack other = player.getItemInHand(otherHand);
            if (!other.is(Items.MELON_SLICE)) {
                return InteractionResult.PASS;
            }

            if (!world.isClientSide()) {
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                    other.shrink(1);
                }
                ItemStack juice = new ItemStack(AriseNB4Items.MELON_JUICE);
                if (!player.getInventory().add(juice)) {
                    player.spawnAtLocation((ServerLevel) world, juice);
                }
                world.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1.0f, 1.0f);
            }

            return InteractionResult.SUCCESS;
        });
    }

    private static void registerButcheryTallow() {
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((level, entity, killedEntity, damageSource) -> {
            if (!AriseNB4Config.get().butcheryTallowDrops) {
                return;
            }
            if (!(entity instanceof ServerPlayer)) {
                return;
            }
            if (killedEntity instanceof Animal || killedEntity instanceof Hoglin) {
                if (killedEntity.getRandom().nextDouble() < AriseNB4Config.get().tallowDropChance) {
                    killedEntity.spawnAtLocation(level, AriseNB4Items.ANIMAL_TALLOW);
                }
            }
        });
    }
}
