/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.KeyMapping
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.Options
 *  net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
 *  net.minecraft.client.input.KeyEvent
 *  net.minecraft.client.input.MouseButtonEvent
 *  net.minecraft.network.chat.Component
 *  net.minecraft.world.entity.player.Inventory
 *  net.minecraft.world.inventory.AbstractContainerMenu
 *  net.minecraft.world.inventory.ContainerInput
 *  net.minecraft.world.inventory.Slot
 *  net.minecraft.world.item.ItemStack
 *  org.jetbrains.annotations.Nullable
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package com.thelads.core.mixin.alwayson.clientsort.client;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.config.ClassPolicy;
import com.thelads.core.features.alwayson.clientsort.config.Config;
import com.thelads.core.features.alwayson.clientsort.gui.screen.edit.EditorScreen;
import com.thelads.core.features.alwayson.clientsort.gui.screen.edit.SelectorScreen;
import com.thelads.core.features.alwayson.clientsort.inventory.helper.ContainerScreenHelper;
import com.thelads.core.features.alwayson.clientsort.inventory.operator.SingleUseOperator;
import com.thelads.core.features.alwayson.clientsort.order.SortOrder;
import com.thelads.core.features.alwayson.clientsort.util.KeybindManager;
import com.thelads.core.features.alwayson.clientsort.util.PolicyManager;
import com.thelads.core.mixin.alwayson.clientsort.client.accessor.AbstractContainerScreenAccessor;
import com.thelads.core.features.alwayson.clientsort.util.inject.ISlot;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={AbstractContainerScreen.class})
public abstract class AbstractContainerScreenMixin<T extends AbstractContainerMenu>
extends Screen {
    @Shadow
    @Final
    protected AbstractContainerMenu menu;
    @Shadow
    protected Slot hoveredSlot;
    @Shadow
    private ItemStack draggingItem;

    protected AbstractContainerScreenMixin(Component title) {
        super(title);
    }

    @Shadow
    public abstract T getMenu();

    @Inject(method={"slotClicked"}, at={@At(value="HEAD")}, cancellable=true)
    private void beforeSlotClicked(Slot slot, int slotId, int mouseButton, ContainerInput input, CallbackInfo ci) {
        if (slotId < 0 && com.thelads.core.features.alwayson.clientsort.ClientSortClient.operatingClient) {
            ci.cancel();
        }
    }

    @Inject(method={"mouseClicked"}, at={@At(value="HEAD")}, cancellable=true)
    private void beforeMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        Supplier<Boolean> op = this.clientsort$getOperation(keyMapping -> keyMapping.matchesMouse(event));
        if (op != null && op.get().booleanValue()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method={"keyPressed"}, at={@At(value="HEAD")}, cancellable=true)
    private void beforeKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        Supplier<Boolean> op = this.clientsort$getOperation(keyMapping -> keyMapping.matches(event));
        if (op != null && op.get().booleanValue()) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    @Nullable
    private Supplier<Boolean> clientsort$getOperation(Function<KeyMapping, Boolean> inputMatcher) {
        boolean isEditKey = inputMatcher.apply(KeybindManager.EDIT_KEY);
        if (!isEditKey && this.hoveredSlot == null) {
            return null;
        }
        Options options = this.minecraft.options;
        if (inputMatcher.apply(options.keyPickItem).booleanValue() && this.minecraft.player.hasInfiniteMaterials() && (this.hoveredSlot.hasItem() || !this.draggingItem.isEmpty() || !this.menu.getCarried().isEmpty())) {
            return null;
        }
        if (inputMatcher.apply(options.keyDrop).booleanValue() && this.hoveredSlot.hasItem()) {
            return null;
        }
        if (inputMatcher.apply(options.keySwapOffhand).booleanValue()) {
            return null;
        }
        for (int i = 0; i < 9; ++i) {
            if (!inputMatcher.apply(options.keyHotbarSlots[i]).booleanValue()) continue;
            return null;
        }
        if (isEditKey) {
            return this::clientsort$openEditor;
        }
        if (inputMatcher.apply(KeybindManager.SORT_KEY).booleanValue()) {
            return this::clientsort$sort;
        }
        if (inputMatcher.apply(KeybindManager.STACK_FILL_KEY).booleanValue()) {
            return this::clientsort$fillStacks;
        }
        if (inputMatcher.apply(KeybindManager.MATCH_TRANSFER_KEY).booleanValue()) {
            return this::clientsort$transferMatching;
        }
        if (inputMatcher.apply(KeybindManager.TRANSFER_KEY).booleanValue()) {
            return this::clientsort$transfer;
        }
        return null;
    }

    @Unique
    private boolean clientsort$openEditor() {
        Minecraft.getInstance().setScreen((Screen)new SelectorScreen((AbstractContainerScreen<?>)(Object)this));
        return true;
    }

    @Unique
    private boolean clientsort$sort() {
        if (this.hoveredSlot == null) {
            return false;
        }
        SortOrder sortOrder = Config.options().sortOrder;
        if (KeybindManager.hasShiftDown()) {
            sortOrder = Config.options().shiftSortOrder;
        } else if (KeybindManager.hasControlDown()) {
            sortOrder = Config.options().ctrlSortOrder;
        } else if (KeybindManager.hasAltDown()) {
            sortOrder = Config.options().altSortOrder;
        }
        return SingleUseOperator.sort((AbstractContainerScreen<?>)(Object)this, this.hoveredSlot, false, sortOrder);
    }

    @Unique
    private boolean clientsort$fillStacks() {
        if (this.hoveredSlot == null) {
            return false;
        }
        return SingleUseOperator.fillStacks((AbstractContainerScreen<?>)(Object)this, this.hoveredSlot, false);
    }

    @Unique
    private boolean clientsort$transferMatching() {
        if (this.hoveredSlot == null) {
            return false;
        }
        return SingleUseOperator.transferMatching((AbstractContainerScreen<?>)(Object)this, this.hoveredSlot, false);
    }

    @Unique
    private boolean clientsort$transfer() {
        if (this.hoveredSlot == null) {
            return false;
        }
        return SingleUseOperator.transfer((AbstractContainerScreen<?>)(Object)this, this.hoveredSlot, false);
    }

    @Inject(method={"extractRenderState"}, at={@At(value="TAIL")})
    private void afterRender(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!((Object)((Object)this)).equals((Object)Minecraft.getInstance().screen)) {
            return;
        }
        if (com.thelads.core.features.alwayson.clientsort.ClientSortClient.overlayMessage != null) {
            graphics.pose().pushMatrix();
            com.thelads.core.features.alwayson.clientsort.ClientSortClient.overlayMessage.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
            graphics.pose().popMatrix();
        }
        if (!ClientSortClient.debug()) {
            return;
        }
        ContainerScreenHelper<AbstractContainerScreen<?>> helper = ContainerScreenHelper.of((AbstractContainerScreen<?>)(Object)this);
        float scale = 0.7f;
        graphics.pose().pushMatrix();
        graphics.pose().scale(scale, scale);
        for (Slot slot : this.menu.slots) {
            int slotId = ((ISlot)slot).clientsort$getIndexInMenu();
            int slotIdx = ((ISlot)slot).clientsort$getIndexInContainer();
            if (!(Minecraft.getInstance().screen instanceof EditorScreen)) {
                Object object = ClientSort.getObj(slot, this.getMenu());
                if (object == null) continue;
                boolean isPlayerInv = slot.container instanceof Inventory;
                Component invTitle = isPlayerInv ? ((AbstractContainerScreenAccessor)((Object)this)).clientsort$getPlayerInventoryTitle() : this.getTitle();
                @Nullable ClassPolicy policy = PolicyManager.getPolicy(object.getClass(), invTitle.getString());
                if (policy != null && policy.ignoredSlots().contains(slotIdx)) {
                    graphics.text(Minecraft.getInstance().font, "\u274c", (int)((float)(((AbstractContainerScreenAccessor)((Object)this)).clientsort$getLeftPos() + slot.x) / scale), (int)((float)(((AbstractContainerScreenAccessor)((Object)this)).clientsort$getTopPos() + slot.y) / scale), -65536);
                }
            }
            graphics.text(Minecraft.getInstance().font, String.valueOf(KeybindManager.hasShiftDown() ? slotIdx : slotId), (int)((float)(((AbstractContainerScreenAccessor)((Object)this)).clientsort$getLeftPos() + slot.x) / scale), (int)((float)(((AbstractContainerScreenAccessor)((Object)this)).clientsort$getTopPos() + slot.y + 12) / scale), -1);
            graphics.text(Minecraft.getInstance().font, String.valueOf(helper.getScope(slot).ordinal()), (int)((float)(((AbstractContainerScreenAccessor)((Object)this)).clientsort$getLeftPos() + slot.x + 12) / scale), (int)((float)(((AbstractContainerScreenAccessor)((Object)this)).clientsort$getTopPos() + slot.y + 12) / scale), -1);
        }
        graphics.pose().popMatrix();
    }
}
