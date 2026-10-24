package com.thelads.core.v26_2.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.client.ContainerSorting;
import com.thelads.core.client.InventoryTweaks;
import java.util.ArrayList;
import java.util.List;
import com.thelads.core.v26_2.feature.ChatTweaks26;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The container screens' parity-1023 tweaks on 26.3 (common InventoryTweaks): Inventory Search's box above the container (its
 * matches are worked out once a tick, the slots only read them), Slot Lock's Lock key and tint, and Armor Swap (gated) in the
 * player's own inventory screen only. The creative inventory keeps its own search and gets none of these.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class InventoryTweaksMixin26 extends Screen {
    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow @Final protected int imageWidth;
    @Shadow protected Slot hoveredSlot;
    @Shadow @Final protected AbstractContainerMenu menu;
    @Shadow private Slot getHoveredSlot(double x, double y) { throw new AssertionError(); }
    @Shadow protected abstract void slotClicked(Slot slot, int slotId, int button, ContainerInput input);
    @Unique private EditBox ladsSearch;
    /** Menu slots Inventory Search dims (worked out each tick while a search is typed). */
    @Unique private boolean[] ladsDim = new boolean[0];

    private InventoryTweaksMixin26(Component title) { super(title); }

    @Unique
    private boolean lads$tweaked() {
        return !((Object) this instanceof CreativeModeInventoryScreen);
    }

    @Inject(method = "init", at = @At("TAIL"), require = 1)
    private void lads$init(CallbackInfo ci) {
        ladsSearch = null;
        if (!InventoryTweaks.searchOn || !lads$tweaked()) return;
        Component hint = Component.literal("Search");
        ladsSearch = new EditBox(font, 0, 0, InventoryTweaks.SEARCH_WIDTH, InventoryTweaks.SEARCH_HEIGHT, hint);
        ladsSearch.setHint(hint.copy().withStyle(ChatFormatting.DARK_GRAY));
        ladsSearch.setValue(InventoryTweaks.query());
        ladsSearch.setResponder(InventoryTweaks::setQuery);
        lads$place();
        addRenderableWidget(ladsSearch);
    }

    /** The box sits on the container's top right corner, following it when the recipe book moves the screen. */
    @Unique
    private void lads$place() {
        ladsSearch.setX(leftPos + imageWidth - InventoryTweaks.SEARCH_WIDTH);
        ladsSearch.setY(topPos - InventoryTweaks.SEARCH_HEIGHT - 2);
    }

    @Inject(method = "extractContents", at = @At("HEAD"), require = 1)
    private void lads$follow(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (ladsSearch != null) lads$place();
    }

    @Inject(method = "extractContents", at = @At("TAIL"), require = 1)
    private void lads$renderSort(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (ContainerSorting.isAllowed() && lads$tweaked()) {
            int bx = ContainerSorting.getButtonX(leftPos, imageWidth, InventoryTweaks.searchOn);
            int by = ContainerSorting.getButtonY(topPos);
            boolean over = ContainerSorting.isOverButton(mouseX, mouseY, bx, by);
            graphics.fill(bx, by, bx + ContainerSorting.BUTTON_WIDTH, by + ContainerSorting.BUTTON_HEIGHT, over ? 0x90404040 : 0x80000000);
            graphics.centeredText(font, ContainerSorting.SORT_LABEL, bx + ContainerSorting.BUTTON_WIDTH / 2, by + 2, over ? 0xFFFFA0 : 0xFFFFFF);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), require = 1)
    private void lads$search(CallbackInfo ci) {
        int size = menu.slots.size();
        if (ladsDim.length != size) ladsDim = new boolean[size];
        boolean searching = ladsSearch != null && !InventoryTweaks.query().isEmpty();
        for (int i = 0; i < size; i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            ladsDim[i] = searching && !stack.isEmpty()
                && !InventoryTweaks.matches(stack.getHoverName().getString(), BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
    }

    @Inject(method = "extractSlot", at = @At("TAIL"), require = 1)
    private void lads$overlay(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        int x = slot.x, y = slot.y;
        if (slot.index < ladsDim.length && ladsDim[slot.index]) graphics.fill(x, y, x + 16, y + 16, InventoryTweaks.DIM);
        if (slot.container instanceof Inventory && InventoryTweaks.locked(slot.getContainerSlot())) {
            graphics.fill(x, y, x + 16, y + 16, InventoryTweaks.LOCK_TINT);
            graphics.fill(x, y + 15, x + 16, y + 16, InventoryTweaks.LOCK_EDGE);
        }
    }

    /** Typing in the search box: every key but Escape goes to it (E, Q and the number keys type instead of acting; Enter leaves it); the Lock key. */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$keys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (ladsSearch != null && ladsSearch.isFocused() && !event.isEscape()) {
            if (event.isConfirmation()) {
                ladsSearch.setFocused(false);
                setFocused(null);
            } else ladsSearch.keyPressed(event);
            cir.setReturnValue(true);
            return;
        }
        if (event.key() == InputConstants.KEY_R && (ladsSearch == null || !ladsSearch.isFocused()) && ContainerSorting.isAllowed() && lads$tweaked()) {
            lads$sortContainer();
            cir.setReturnValue(true);
            return;
        }
        if (InventoryTweaks.lockOn && lads$tweaked() && ChatTweaks26.LOCK.matches(event) && hoveredSlot != null
            && hoveredSlot.container instanceof Inventory) {
            InventoryTweaks.toggleLock(hoveredSlot.getContainerSlot());
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$click(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (ladsSearch != null && ladsSearch.isFocused() && !ladsSearch.isMouseOver(event.x(), event.y())) {
            ladsSearch.setFocused(false);
            setFocused(null);
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && ContainerSorting.isAllowed() && lads$tweaked()) {
            int bx = ContainerSorting.getButtonX(leftPos, imageWidth, InventoryTweaks.searchOn);
            int by = ContainerSorting.getButtonY(topPos);
            if (ContainerSorting.isOverButton(event.x(), event.y(), bx, by)) {
                lads$sortContainer();
                cir.setReturnValue(true);
                return;
            }
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_MIDDLE && lads$armorSwap(getHoveredSlot(event.x(), event.y()))) cir.setReturnValue(true);
    }

    /**
     * Armor Swap (gated): middle-clicking armor in the player's own inventory screen wears it, the piece worn before goes where it
     * was: two or three ordinary pickup clicks on that screen's own slots (InventoryMenu: 5 head .. 8 feet). Not in creative, not with
     * an item on the cursor, never with a locked slot.
     */
    @Unique
    private boolean lads$armorSwap(Slot from) {
        if (!InventoryTweaks.armorSwapOn || !((Object) this instanceof InventoryScreen) || minecraft.player == null
            || minecraft.player.hasInfiniteMaterials() || from == null || !(from.container instanceof Inventory) || from.index < 9
            || !menu.getCarried().isEmpty() || !from.hasItem()) return false;
        EquipmentSlot type = minecraft.player.getEquipmentSlotForItem(from.getItem());
        if (type.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) return false;
        Slot to = menu.getSlot(InventoryTweaks.armorMenuSlot(3 - type.getIndex()));
        if (InventoryTweaks.locked(from.getContainerSlot()) || InventoryTweaks.locked(to.getContainerSlot())) return false;
        boolean wearing = to.hasItem();
        slotClicked(from, from.index, 0, ContainerInput.PICKUP);
        slotClicked(to, to.index, 0, ContainerInput.PICKUP);
        if (wearing) slotClicked(from, from.index, 0, ContainerInput.PICKUP);
        return true;
    }

    @Unique
    private void lads$sortContainer() {
        if (!ContainerSorting.isAllowed() || minecraft == null || minecraft.player == null || !menu.getCarried().isEmpty()) return;

        List<Slot> containerSlots = new ArrayList<>();
        List<Slot> playerStorageSlots = new ArrayList<>();

        for (Slot slot : menu.slots) {
            if (!(slot.container instanceof Inventory)) {
                containerSlots.add(slot);
            } else {
                if (slot.getContainerSlot() >= 9 && slot.getContainerSlot() < 36) {
                    playerStorageSlots.add(slot);
                }
            }
        }

        List<Slot> targetSlots = null;
        if (!containerSlots.isEmpty() && ContainerSorting.shouldSortContainer()) {
            targetSlots = containerSlots;
        } else if (ContainerSorting.shouldSortInventory() || (containerSlots.isEmpty() && ContainerSorting.isAllowed())) {
            targetSlots = playerStorageSlots;
        }

        if (targetSlots == null || targetSlots.size() <= 1) return;

        List<ContainerSorting.SlotItem> items = new ArrayList<>();
        for (Slot slot : targetSlots) {
            ItemStack stack = slot.getItem();
            boolean locked = (slot.container instanceof Inventory) && InventoryTweaks.locked(slot.getContainerSlot());
            if (stack.isEmpty()) {
                items.add(new ContainerSorting.SlotItem(slot.index, "", 0, 0, 0, locked));
            } else {
                String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                items.add(new ContainerSorting.SlotItem(slot.index, itemId, stack.getCount(), stack.getMaxStackSize(), stack.getDamageValue(), locked));
            }
        }

        List<int[]> swaps = ContainerSorting.planSwaps(items);
        for (int[] swap : swaps) {
            Slot from = menu.getSlot(swap[0]);
            Slot to = menu.getSlot(swap[1]);
            if (from != null && to != null) {
                lads$swapSlots(from, to);
            }
        }
    }

    @Unique
    private void lads$swapSlots(Slot from, Slot to) {
        boolean hasFrom = from.hasItem();
        boolean hasTo = to.hasItem();
        if (!hasFrom && !hasTo) return;
        if (hasFrom && !hasTo) {
            slotClicked(from, from.index, 0, ContainerInput.PICKUP);
            slotClicked(to, to.index, 0, ContainerInput.PICKUP);
        } else if (!hasFrom && hasTo) {
            slotClicked(to, to.index, 0, ContainerInput.PICKUP);
            slotClicked(from, from.index, 0, ContainerInput.PICKUP);
        } else {
            slotClicked(from, from.index, 0, ContainerInput.PICKUP);
            slotClicked(to, to.index, 0, ContainerInput.PICKUP);
            if (menu != null && !menu.getCarried().isEmpty()) {
                slotClicked(from, from.index, 0, ContainerInput.PICKUP);
            }
        }
    }

}
