/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
 *  net.minecraft.world.item.ItemStack
 */
package dev.ultimatchamp.enhancedtooltips.tooltip;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;

public class TooltipComponentManager {
    private static final List<TooltipComponentEvent> CALLBACKS = new ArrayList<TooltipComponentEvent>();

    public static void register(TooltipComponentEvent callback) {
        CALLBACKS.add(callback);
    }

    public static void invoke(List<ClientTooltipComponent> components, ItemStack stack) {
        for (TooltipComponentEvent callback : CALLBACKS) {
            callback.of(components, stack);
        }
    }

    public static interface TooltipComponentEvent {
        public void of(List<ClientTooltipComponent> var1, ItemStack var2);
    }
}

