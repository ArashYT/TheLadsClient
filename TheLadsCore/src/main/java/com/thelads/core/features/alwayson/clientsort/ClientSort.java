/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.Container
 *  net.minecraft.world.SimpleContainer
 *  net.minecraft.world.inventory.AbstractContainerMenu
 *  net.minecraft.world.inventory.Slot
 *  net.minecraft.world.ticks.ContainerSingleItem
 *  org.jetbrains.annotations.Nullable
 */
package com.thelads.core.features.alwayson.clientsort;

import com.thelads.core.features.alwayson.clientsort.config.ServerConfig;
import com.thelads.core.features.alwayson.clientsort.network.handler.validate.PolicyManager;
import com.thelads.core.features.alwayson.clientsort.util.ModLogger;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.ticks.ContainerSingleItem;
import org.jetbrains.annotations.Nullable;
import com.thelads.core.features.alwayson.clientsort.config.Config;

public class ClientSort {
    public static final String MOD_ID = "clientsort";
    public static final String MOD_NAME = "ClientSort";
    public static final ModLogger LOG = new ModLogger("ClientSort");
    public static boolean debugEnabled;

    public static boolean debug() {
        return debugEnabled;
    }

    public static void init() {
        ServerConfig.getAndSave();
    }

    public static void onConfigSaved(ServerConfig config) {
        ServerConfig.Options options = config.options;
        PolicyManager.reloadPolicyClasses(options.classPolicies.keySet());
    }

    public static void afterConfigSaved(Config config) {
        // Client config saved callback
    }

    @Nullable
    public static Object getObj(Slot slot, AbstractContainerMenu menu) {
        return ClientSort.getObj(slot.container, menu);
    }

    @Nullable
    public static Object getObj(@Nullable Container container, AbstractContainerMenu menu) {
        return switch (container) {
            case null -> null;
            case ContainerSingleItem ignored -> menu;
            case SimpleContainer ignored -> menu;
            default -> container;
        };
    }
}
