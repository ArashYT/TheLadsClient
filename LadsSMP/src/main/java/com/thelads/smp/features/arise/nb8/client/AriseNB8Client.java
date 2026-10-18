package com.thelads.smp.features.arise.nb8.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.smp.features.arise.nb8.AriseNB8Config;
import com.thelads.smp.features.arise.nb8.VeinMiningManager;
import com.thelads.smp.features.arise.nb8.VeinMiningNetwork;
import com.thelads.smp.features.arise.nb8.VeinShape;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Client entrypoint for Batch NB8: Vein Mining.
 * Handles keybindings, shape cycling, network state sync, and cached outline preview generation.
 */
public final class AriseNB8Client implements ClientModInitializer {
    public static KeyMapping KEY_VEIN_MINING;
    public static KeyMapping KEY_CYCLE_SHAPE;

    private static VeinShape currentShape = VeinShape.SHAPELESS;
    private static boolean wasActive = false;
    private static Direction lastHitFace = null;

    // Outline preview cache
    private static List<BlockPos> cachedPreviewBlocks = Collections.emptyList();
    private static BlockPos lastTargetPos = null;
    private static Direction lastTargetFace = null;
    private static VeinShape lastCachedShape = null;
    private static ItemStack lastHeldItem = ItemStack.EMPTY;

    @Override
    public void onInitializeClient() {
        KEY_VEIN_MINING = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.ladssmp.vein_mining",
                InputConstants.KEY_GRAVE,
                KeyMapping.Category.GAMEPLAY
        ));

        KEY_CYCLE_SHAPE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.ladssmp.cycle_shape",
                InputConstants.KEY_BACKSLASH,
                KeyMapping.Category.GAMEPLAY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(Minecraft client) {
        LocalPlayer player = client.player;
        ClientLevel level = client.level;
        if (player == null || level == null) {
            clearCache();
            return;
        }

        // Handle shape cycling keybind
        while (KEY_CYCLE_SHAPE.consumeClick()) {
            currentShape = currentShape.next();
            player.sendOverlayMessage(
                    Component.translatable("ladssmp.vein_mining.shape_changed", currentShape.getDisplayName())
            );
            sendStatePacket(KEY_VEIN_MINING.isDown(), lastHitFace);
            clearCache();
        }

        boolean isDown = KEY_VEIN_MINING.isDown();
        Direction hitFace = null;
        BlockPos targetPos = null;

        if (client.hitResult instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            hitFace = bhr.getDirection();
            targetPos = bhr.getBlockPos();
        }

        // Send network packet on state change
        if (isDown != wasActive || hitFace != lastHitFace) {
            wasActive = isDown;
            lastHitFace = hitFace;
            sendStatePacket(isDown, hitFace);
        }

        // Update outline preview cache while active
        if (isDown && targetPos != null && hitFace != null) {
            ItemStack held = player.getMainHandItem();
            if (!targetPos.equals(lastTargetPos)
                    || hitFace != lastTargetFace
                    || currentShape != lastCachedShape
                    || !ItemStack.matches(held, lastHeldItem)) {
                updatePreviewCache(level, player, targetPos, hitFace, held);
            }
        } else if (!isDown && !cachedPreviewBlocks.isEmpty()) {
            clearCache();
        }
    }

    private static void sendStatePacket(boolean active, Direction hitFace) {
        if (!ClientPlayNetworking.canSend(VeinMiningNetwork.VeinStatePayload.TYPE)) {
            return;
        }
        int hitDir = hitFace != null ? hitFace.get3DDataValue() : -1;
        ClientPlayNetworking.send(new VeinMiningNetwork.VeinStatePayload(active, currentShape.ordinal(), hitDir));
    }

    private static void updatePreviewCache(ClientLevel level, LocalPlayer player, BlockPos targetPos, Direction hitFace, ItemStack held) {
        lastTargetPos = targetPos;
        lastTargetFace = hitFace;
        lastCachedShape = currentShape;
        lastHeldItem = held.copy();

        AriseNB8Config config = AriseNB8Config.get();
        BlockState originState = level.getBlockState(targetPos);

        if (originState.isAir() || originState.getDestroySpeed(level, targetPos) < 0) {
            cachedPreviewBlocks = Collections.emptyList();
            return;
        }

        boolean isOre = VeinMiningManager.isOreBlock(originState);
        boolean isLog = VeinMiningManager.isLogBlock(originState);
        boolean matchesConfigTag = VeinMiningManager.matchesMergeTags(originState, config.mergeTags);

        if (currentShape == VeinShape.SHAPELESS && !isOre && !isLog && !matchesConfigTag) {
            cachedPreviewBlocks = Collections.emptyList();
            return;
        }

        boolean eyeLevel = targetPos.getY() >= player.getEyeY() - 0.5;
        int renderLimit = Math.min(config.maxBlocks, config.renderOutline);

        Predicate<BlockPos> filter = pos -> {
            BlockState candidate = level.getBlockState(pos);
            if (candidate.isAir()) return false;
            if (candidate.getDestroySpeed(level, pos) < 0) return false;

            if (!config.mineContainers && candidate.getBlock() instanceof BaseEntityBlock) {
                return false;
            }

            if (candidate.requiresCorrectToolForDrops() && !player.hasCorrectToolForDrops(candidate)) {
                return false;
            }

            if (currentShape != VeinShape.SHAPELESS && config.shapedAnyBlock) {
                return true;
            }

            return VeinMiningManager.areBlocksCompatible(originState, candidate, isOre, isLog, config.mergeTags);
        };

        cachedPreviewBlocks = currentShape.calculatePositions(
                level,
                targetPos,
                hitFace,
                player.getDirection(),
                eyeLevel,
                filter,
                renderLimit
        );
    }

    private static void clearCache() {
        cachedPreviewBlocks = Collections.emptyList();
        lastTargetPos = null;
        lastTargetFace = null;
        lastCachedShape = null;
        lastHeldItem = ItemStack.EMPTY;
    }

    public static boolean isVeinMiningActive() {
        return wasActive && !cachedPreviewBlocks.isEmpty();
    }

    public static List<BlockPos> getPreviewBlocks() {
        return cachedPreviewBlocks;
    }

    public static VeinShape getCurrentShape() {
        return currentShape;
    }
}
