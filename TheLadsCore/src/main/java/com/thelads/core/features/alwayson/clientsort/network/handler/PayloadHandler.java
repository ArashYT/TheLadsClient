/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type
 *  net.minecraft.server.MinecraftServer
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.inventory.AbstractContainerMenu
 *  net.minecraft.world.inventory.InventoryMenu
 *  net.minecraft.world.inventory.Slot
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.GameType
 *  org.jetbrains.annotations.NotNull
 */
package com.thelads.core.features.alwayson.clientsort.network.handler;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.config.ServerConfig;
import com.thelads.core.features.alwayson.clientsort.exception.PayloadHandlerException;
import com.thelads.core.mixin.alwayson.clientsort.accessor.ContainerAccessor;
import com.thelads.core.features.alwayson.clientsort.network.handler.validate.PayloadResult;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import org.jetbrains.annotations.NotNull;

public abstract class PayloadHandler {
    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void processPayload(MinecraftServer server, ServerPlayer player, int containerId, ThrowingConsumer<AbstractContainerMenu> contextValidator, ThrowingConsumer<AbstractContainerMenu> schemaValidator, ThrowingConsumer<AbstractContainerMenu> operator, CustomPacketPayload.Type<?> payloadType, CustomPacketPayload.Type<?> responseType, BiFunction<PayloadResult, String, CustomPacketPayload> responseProvider) {
        AbstractContainerMenu menu = null;
        PayloadResult result = PayloadResult.SUCCESS;
        String message = "";
        if (player.gameMode.getGameModeForPlayer().equals((Object)GameType.SPECTATOR)) {
            result = PayloadResult.UNSUPPORTED_OP;
            message = "This operation is not available in spectator mode.";
            if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, responseType)) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, responseProvider.apply(result, message));
            }
            return;
        }
        try {
            menu = PayloadHandler.getMenu(player, containerId);
            menu.suppressRemoteUpdates();
            contextValidator.accept(menu);
            schemaValidator.accept(menu);
            operator.accept(menu);
        }
        catch (Exception e) {
            if (e instanceof PayloadHandlerException) {
                PayloadHandlerException ex = (PayloadHandlerException)e;
                result = ex.result;
                message = ex.getMessage();
            } else {
                result = PayloadResult.FAILURE;
                message = PayloadHandlerException.GENERIC_MESSAGE;
                ClientSort.LOG.error("Encountered an unexpected exception while handling payload '{}' from player '{}': {}", payloadType.id(), player, e);
            }
        }
        finally {
            if (menu != null) {
                menu.resumeRemoteUpdates();
                menu.broadcastChanges();
                ((ContainerAccessor)((Slot)menu.slots.getFirst()).container).clientsort$setChanged();
                ((ContainerAccessor)((Slot)menu.slots.getLast()).container).clientsort$setChanged();
            }
            if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, responseType)) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, responseProvider.apply(result, message));
            }
        }
    }

    public static void validate(MinecraftServer server, ItemStack expected, ItemStack actual, Supplier<String> message, Consumer<String> policySetter) throws PayloadHandlerException.InconsistentStateException {
        boolean invalid = false;
        boolean log = false;
        boolean error = false;
        if (ServerConfig.serverOptions().alwaysLogUnexpectedResults) {
            log = true;
        }
        if (server.isDedicatedServer() && ServerConfig.serverOptions().validationActiveServer) {
            log = true;
            error = true;
        }
        if (!(!log && !ServerConfig.serverOptions().validateItemType || expected.isEmpty() || actual.isEmpty() || ItemStack.isSameItemSameComponents((ItemStack)expected, (ItemStack)actual))) {
            invalid = true;
        }
        if (log || ServerConfig.serverOptions().validateStackSize) {
            int sizeDifference;
            int n = sizeDifference = expected.getCount() > actual.getCount() ? expected.getCount() - actual.getCount() : actual.getCount() - expected.getCount();
            if (sizeDifference > 0 && sizeDifference >= ServerConfig.serverOptions().validateStackSizeThreshold) {
                invalid = true;
            }
        }
        if (invalid && log) {
            String msg = String.format("%s: Expected '%s' in destination after set, got '%s'!", message.get(), expected, actual);
            if (!error) {
                ClientSort.LOG.warn(msg, new Object[0]);
            } else {
                ClientSort.LOG.error(msg, new Object[0]);
                policySetter.accept(msg);
                throw new PayloadHandlerException.InconsistentStateException(msg);
            }
        }
    }

    @NotNull
    private static AbstractContainerMenu getMenu(ServerPlayer player, int containerId) throws PayloadHandlerException.InvalidDataException {
        AbstractContainerMenu menu;
        if (containerId == player.inventoryMenu.containerId) {
            menu = player.inventoryMenu;
        } else if (containerId == player.containerMenu.containerId) {
            menu = player.containerMenu;
        } else {
            throw new PayloadHandlerException.InvalidDataException(String.format("Container ID '%d' does not match player inventory or container!", containerId));
        }
        if (!menu.stillValid((Player)player)) {
            throw new PayloadHandlerException.InvalidDataException(String.format("Container ID '%d' is not valid for the player!", containerId));
        }
        return menu;
    }

    @FunctionalInterface
    public static interface ThrowingConsumer<T> {
        public void accept(T var1) throws Exception;
    }
}
