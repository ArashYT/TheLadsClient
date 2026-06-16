/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.components.MultiLineTextWidget
 *  net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
 *  net.minecraft.core.registries.BuiltInRegistries
 *  net.minecraft.network.chat.Component
 *  net.minecraft.world.item.Item
 *  org.jetbrains.annotations.Nullable
 */
package com.thelads.core.features.alwayson.clientsort;

import com.thelads.core.features.alwayson.clientsort.config.Config;
import com.thelads.core.features.alwayson.clientsort.interaction.InteractionManager;
import com.thelads.core.features.alwayson.clientsort.order.SortOrder;
import com.thelads.core.features.alwayson.clientsort.util.PolicyManager;
import com.thelads.core.features.alwayson.clientsort.util.TaskManager;
import com.thelads.core.mixin.alwayson.clientsort.client.accessor.AbstractContainerScreenAccessor;
import com.thelads.core.features.alwayson.clientsort.util.ModLogger;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

public class ClientSortClient {
    public static final String MOD_ID = "clientsort";
    public static final String MOD_NAME = "ClientSort";
    public static final ModLogger LOG = com.thelads.core.features.alwayson.clientsort.ClientSort.LOG;
    public static final TaskManager taskManager = new TaskManager();
    @Nullable
    public static MultiLineTextWidget overlayMessage = null;
    private static Runnable clearOverlayMessage = null;
    public static volatile boolean searchOrderUpdated;
    public static volatile boolean emiReloading;
    public static volatile boolean updateBlockedByEmi;
    public static volatile boolean operatingClient;
    public static BlockingQueue<Runnable> clientOpQueue;

    public static boolean debug() {
        return com.thelads.core.features.alwayson.clientsort.ClientSort.debug();
    }

    public static void init() {
        Config.getAndSave();
    }

    public static void afterClientTick(Minecraft mc) {
        taskManager.tick();
    }

    public static void afterConfigSaved(Config config) {
        @Nullable Minecraft mc = Minecraft.getInstance();
        Config.Options options = config.options;
        options.sortOrder = SortOrder.SORT_ORDERS.get(options.sortOrderStr);
        options.shiftSortOrder = SortOrder.SORT_ORDERS.get(options.shiftSortOrderStr);
        options.ctrlSortOrder = SortOrder.SORT_ORDERS.get(options.ctrlSortOrderStr);
        options.altSortOrder = SortOrder.SORT_ORDERS.get(options.altSortOrderStr);
        options.sortSoundLoc = Identifier.tryParse(options.interactionSound);
        InteractionManager.setTickRate(options.interactionInterval);
        PolicyManager.reloadPolicyClasses(options.classPolicies.keySet());
        if (mc != null && mc.getConnection() != null && mc.getConnection().isAcceptingMessages()) {
            ClientSortClient.updateItemTags(options);
            ClientSortClient.updateItemSets(options);
        }
    }

    public static void updateItemTags(Config.Options options) {
        options.typeMatchItemCache.clear();
        BuiltInRegistries.ITEM.getTags().forEach(named -> {
            if (options.typeMatchTags.contains(named.key().location().getPath())) {
                named.forEach(itemHolder -> options.typeMatchItemCache.add((Item)itemHolder.value()));
            }
        });
    }

    public static void updateItemSets(Config.Options options) {
        options.startOverrideMap.clear();
        int i = 0;
        for (String s : options.startOverrideItems) {
            final int idx = i++;
            BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(s)).ifPresent(item -> options.startOverrideMap.put((Item)item, idx));
        }
        options.endOverrideMap.clear();
        i = 0;
        for (String s : options.endOverrideItems) {
            final int idx = i++;
            BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(s)).ifPresent(item -> options.endOverrideMap.put((Item)item, idx));
        }
    }

    public static void setOverlayMessage(AbstractContainerScreen<?> screen, Component message, int clearAfterTicks) {
        if (overlayMessage != null) {
            clearOverlayMessage.run();
        }
        Minecraft mc = Minecraft.getInstance();
        MultiLineTextWidget newMessage = new MultiLineTextWidget(message, mc.font);
        newMessage.setMaxWidth(((AbstractContainerScreenAccessor)screen).clientsort$getImageWidth());
        newMessage.setCentered(true);
        newMessage.setX(screen.width / 2 - newMessage.getWidth() / 2);
        newMessage.setY(screen.height / 2 - newMessage.getHeight() / 2);
        overlayMessage = newMessage;
        clearOverlayMessage = () -> {
            if (overlayMessage == newMessage) {
                overlayMessage = null;
                clearOverlayMessage = null;
            }
        };
        taskManager.schedule(clearAfterTicks, clearOverlayMessage);
    }

    static {
        emiReloading = false;
        updateBlockedByEmi = false;
        operatingClient = false;
        clientOpQueue = new ArrayBlockingQueue<Runnable>(2);
    }
}
