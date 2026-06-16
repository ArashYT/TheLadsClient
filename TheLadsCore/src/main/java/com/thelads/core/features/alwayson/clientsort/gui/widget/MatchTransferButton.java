/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.components.WidgetSprites
 *  net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
 *  net.minecraft.network.chat.Component
 *  net.minecraft.world.Container
 *  net.minecraft.world.inventory.Slot
 *  org.jetbrains.annotations.Nullable
 */
package com.thelads.core.features.alwayson.clientsort.gui.widget;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.config.ClassPolicy;
import com.thelads.core.features.alwayson.clientsort.config.Config;
import com.thelads.core.features.alwayson.clientsort.config.Operation;
import com.thelads.core.features.alwayson.clientsort.config.Policy;
import com.thelads.core.features.alwayson.clientsort.config.Vec2i;
import com.thelads.core.features.alwayson.clientsort.gui.widget.TriggerButton;
import com.thelads.core.features.alwayson.clientsort.inventory.operator.SingleUseOperator;
import java.util.Collection;
import java.util.TreeSet;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

public class MatchTransferButton
extends TriggerButton {
    private static final WidgetSprites SPRITES_UP = new WidgetSprites(Identifier.fromNamespaceAndPath("clientsort", "widget/match_transfer_up"), Identifier.fromNamespaceAndPath("clientsort", "widget/match_transfer_up_disabled"), Identifier.fromNamespaceAndPath("clientsort", "widget/match_transfer_up_highlighted"));
    private static final WidgetSprites SPRITES_DOWN = new WidgetSprites(Identifier.fromNamespaceAndPath("clientsort", "widget/match_transfer_down"), Identifier.fromNamespaceAndPath("clientsort", "widget/match_transfer_down_disabled"), Identifier.fromNamespaceAndPath("clientsort", "widget/match_transfer_down_highlighted"));

    public MatchTransferButton(AbstractContainerScreen<?> screen, Container container, Slot referenceSlot, boolean referenceLeft, boolean isPlayerInv, @Nullable ClassPolicy policy, String lowestPolicyKey, Vec2i offset, Component name) {
        super(screen, container, referenceSlot, referenceLeft, isPlayerInv, isPlayerInv ? SPRITES_UP : SPRITES_DOWN, name, policy == null ? null : policy.getKey(), lowestPolicyKey, offset, policy == null ? true : policy.offsetFromSlot(), policy == null || policy.canMatchTransfer(), policy != null && policy.showMatchTransferButton(), button -> SingleUseOperator.transferMatching(screen, referenceSlot, false));
    }

    @Override
    public boolean getPolicyStatus(ClassPolicy policy) {
        return policy.showMatchTransferButton();
    }

    @Override
    public void savePolicy(@Nullable Vec2i offset, boolean offsetFromSlot, @Nullable Operation autoOp, boolean autoOpOther, Collection<Integer> slots) {
        String key = this.activePolicyKey != null ? this.activePolicyKey : this.lowestPolicyKey;
        @Nullable ClassPolicy policy = Config.options().classPolicies.get(key);
        if (policy == null) {
            key = this.lowestPolicyKey;
            policy = Config.options().classPolicies.get(key);
        }
        if (policy == null) {
            Config.options().classPolicies.put(key, ClassPolicy.create(key, offset, offsetFromSlot, Policy.KEYBIND, Policy.KEYBIND, this.operationAllowed ? (this.active ? Policy.KEYBIND_BUTTON : Policy.KEYBIND) : Policy.NONE, Policy.KEYBIND, autoOp, autoOpOther, new TreeSet<Integer>(slots)));
        } else {
            Config.options().classPolicies.put(key, ClassPolicy.create(key, offset, offsetFromSlot, policy.sortPolicy(), policy.stackFillPolicy(), this.operationAllowed ? (this.active ? Policy.KEYBIND_BUTTON : Policy.KEYBIND) : Policy.NONE, policy.transferPolicy(), autoOp, autoOpOther, new TreeSet<Integer>(slots)));
        }
        Config.save();
    }
}
