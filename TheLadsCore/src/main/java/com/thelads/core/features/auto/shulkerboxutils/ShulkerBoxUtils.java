package com.thelads.core.features.auto.shulkerboxutils;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

/**
 * Client-side wiring for the shulkerboxutils recreation:
 * <ul>
 *   <li>Maps {@link ShulkerBoxTooltipData} to its graphical {@link ShulkerBoxTooltipComponent}.</li>
 *   <li>Tracks the last shulker box opened so its contents can feed the in-world icon cache.</li>
 * </ul>
 * Always-on (no config screen / keybind / disk persistence from the original mod).
 */
@Environment(EnvType.CLIENT)
public final class ShulkerBoxUtils {

    private ShulkerBoxUtils() {
    }

    public static void initClient() {
        ClientTooltipComponentCallback.EVENT.register(
                data -> data instanceof ShulkerBoxTooltipData tooltipData ? new ShulkerBoxTooltipComponent(tooltipData) : null);

        // Remember which shulker box the player interacted with, so the screen-close handler
        // knows the position to write its captured contents to.
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (level.isClientSide()) {
                BlockEntity be = level.getBlockEntity(hitResult.getBlockPos());
                if (be instanceof ShulkerBoxBlockEntity) {
                    ShulkerBoxUtilsCache.pendingPos = hitResult.getBlockPos().immutable();
                }
            }
            return InteractionResult.PASS;
        });

        // While a shulker box screen is open, capture its first item; on close, commit it to the
        // cache as screen-authoritative so the renderer won't clobber it with empty synced data.
        ScreenEvents.AFTER_INIT.register((minecraft, screen, w, h) -> {
            if (screen instanceof ShulkerBoxScreen shulkerScreen) {
                ItemStack[] lastFirst = new ItemStack[] { getFirstItem(shulkerScreen) };
                if (!lastFirst[0].isEmpty()) {
                    writeToCache(lastFirst[0]);
                }

                ScreenEvents.afterTick(screen).register(s -> {
                    ItemStack currentFirst = getFirstItem(shulkerScreen);
                    if (!ItemStack.isSameItem(currentFirst, lastFirst[0])) {
                        lastFirst[0] = currentFirst.copy();
                        writeToCache(lastFirst[0]);
                    }
                });

                ScreenEvents.remove(screen).register(s -> {
                    BlockPos pos = ShulkerBoxUtilsCache.pendingPos;
                    if (pos != null) {
                        if (lastFirst[0].isEmpty()) {
                            ShulkerBoxUtilsCache.ITEMS.remove(pos);
                            ShulkerBoxUtilsCache.SCREEN_AUTHORITATIVE.remove(pos);
                        } else {
                            ShulkerBoxUtilsCache.ITEMS.put(pos, lastFirst[0].copy());
                            ShulkerBoxUtilsCache.SCREEN_AUTHORITATIVE.add(pos);
                        }
                        ShulkerBoxUtilsCache.pendingPos = null;
                    }
                });
            }
        });
    }

    private static ItemStack getFirstItem(ShulkerBoxScreen screen) {
        return ((ShulkerBoxMenu) screen.getMenu())
                .slots
                .stream()
                .filter(s -> s.index < 27)
                .map(Slot::getItem)
                .filter(s -> !s.isEmpty())
                .findFirst()
                .orElse(ItemStack.EMPTY);
    }

    private static void writeToCache(ItemStack item) {
        BlockPos pos = ShulkerBoxUtilsCache.pendingPos;
        if (pos != null) {
            ShulkerBoxUtilsCache.SCREEN_AUTHORITATIVE.add(pos);
            if (item.isEmpty()) {
                ShulkerBoxUtilsCache.ITEMS.remove(pos);
            } else {
                ShulkerBoxUtilsCache.ITEMS.put(pos, item.copy());
            }
        }
    }
}
