package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.ContainerSorting;
import com.thelads.core.client.InventoryTweaks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import com.thelads.core.v1_8_9.feature.ChatTweaks189;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The container screens' parity-1023 tweaks on 1.8.9 (common InventoryTweaks): Inventory Search's box above the container (its
 * matches are worked out once a tick, the slots only read them), Slot Lock's Lock key and tint, and Armor Swap (gated) in the
 * player's own inventory screen only. The creative inventory keeps its own search and gets none of these.
 */
@Mixin(GuiContainer.class)
public abstract class InventoryTweaksMixin189 extends GuiScreen implements ChatTweaks189.Typing {
    @Shadow protected int guiLeft;
    @Shadow protected int guiTop;
    @Shadow protected int xSize;
    @Shadow public Container inventorySlots;
    @Shadow private Slot theSlot;
    @Shadow private Slot getSlotAtPosition(int x, int y) { throw new AssertionError(); }
    @Shadow protected abstract void handleMouseClick(Slot slot, int slotId, int button, int mode);
    @Unique private GuiTextField ladsSearch;
    /** Container slots Inventory Search dims (worked out each tick while a search is typed). */
    @Unique private boolean[] ladsDim = new boolean[0];
    /** Rate-limited swap queue: processes at most 1 swap per client tick to prevent anticheat packet bursts. */
    @Unique private final Queue<int[]> lads$sortQueue = new ArrayDeque<>();

    @Override public boolean lads$typing() { return ladsSearch != null && ladsSearch.isFocused(); }

    @Override public boolean lads$overSearch(int x, int y) {
        return ladsSearch != null && x >= ladsSearch.xPosition && x < ladsSearch.xPosition + ladsSearch.width
            && y >= ladsSearch.yPosition && y < ladsSearch.yPosition + ladsSearch.height;
    }

    @Unique
    private boolean ladsTweaked() {
        return !((Object) this instanceof GuiContainerCreative);
    }

    @Inject(method = "initGui", at = @At("TAIL"), require = 1)
    private void ladsInit(CallbackInfo ci) {
        lads$sortQueue.clear();
        ladsSearch = null;
        if (!InventoryTweaks.searchOn || !ladsTweaked()) return;
        ladsSearch = new GuiTextField(0, fontRendererObj, guiLeft + xSize - InventoryTweaks.SEARCH_WIDTH, guiTop - InventoryTweaks.SEARCH_HEIGHT - 2,
            InventoryTweaks.SEARCH_WIDTH, InventoryTweaks.SEARCH_HEIGHT);
        ladsSearch.setMaxStringLength(64);
        ladsSearch.setText(InventoryTweaks.query());
    }

    @Inject(method = "onGuiClosed", at = @At("HEAD"))
    private void ladsOnClosed(CallbackInfo ci) {
        lads$sortQueue.clear();
    }

    @Inject(method = "updateScreen", at = @At("HEAD"), require = 1)
    private void ladsSearchTick(CallbackInfo ci) {
        if (!lads$sortQueue.isEmpty()) {
            if (mc.thePlayer == null || mc.thePlayer.inventory == null || mc.thePlayer.inventory.getItemStack() != null) {
                lads$sortQueue.clear();
            } else {
                int[] swap = lads$sortQueue.poll();
                if (swap != null) {
                    Slot from = inventorySlots.getSlot(swap[0]);
                    Slot to = inventorySlots.getSlot(swap[1]);
                    if (from != null && to != null) {
                        lads$swapSlots(from, to);
                    }
                }
            }
        }
        if (ladsSearch != null) ladsSearch.updateCursorCounter();
        int size = inventorySlots.inventorySlots.size();
        if (ladsDim.length != size) ladsDim = new boolean[size];
        boolean searching = ladsSearch != null && !InventoryTweaks.query().isEmpty();
        for (int i = 0; i < size; i++) {
            ItemStack stack = inventorySlots.inventorySlots.get(i).getStack();
            ladsDim[i] = searching && stack != null
                && !InventoryTweaks.matches(stack.getDisplayName(), String.valueOf(Item.itemRegistry.getNameForObject(stack.getItem())));
        }
    }

    @Inject(method = "drawSlot", at = @At("TAIL"), require = 1)
    private void ladsOverlay(Slot slot, CallbackInfo ci) {
        boolean dim = slot.slotNumber < ladsDim.length && ladsDim[slot.slotNumber];
        boolean locked = slot.inventory instanceof InventoryPlayer && InventoryTweaks.locked(slot.getSlotIndex());
        if (!dim && !locked) return;
        int x = slot.xDisplayPosition, y = slot.yDisplayPosition;
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        if (dim) Gui.drawRect(x, y, x + 16, y + 16, InventoryTweaks.DIM);
        if (locked) {
            Gui.drawRect(x, y, x + 16, y + 16, InventoryTweaks.LOCK_TINT);
            Gui.drawRect(x, y + 15, x + 16, y + 16, InventoryTweaks.LOCK_EDGE);
        }
        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
    }

    @Inject(method = "drawScreen", at = @At("TAIL"), require = 1)
    private void ladsDrawSearch(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (ladsSearch != null) {
            net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting(); // drawScreen ends with item lighting on
            ladsSearch.drawTextBox();
            if (!ladsSearch.isFocused() && ladsSearch.getText().isEmpty())
                fontRendererObj.drawString("Search", ladsSearch.xPosition + 4, ladsSearch.yPosition + 2, 0xFF606060);
        }
        if (ContainerSorting.isAllowed() && ladsTweaked()) {
            int bx = ContainerSorting.getButtonX(guiLeft, xSize, InventoryTweaks.searchOn);
            int by = ContainerSorting.getButtonY(guiTop);
            boolean over = ContainerSorting.isOverButton(mouseX, mouseY, bx, by);
            net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
            Gui.drawRect(bx, by, bx + ContainerSorting.BUTTON_WIDTH, by + ContainerSorting.BUTTON_HEIGHT, over ? 0x90404040 : 0x80000000);
            drawCenteredString(fontRendererObj, ContainerSorting.SORT_LABEL, bx + ContainerSorting.BUTTON_WIDTH / 2, by + 2, over ? 0xFFFFA0 : 0xFFFFFF);
        }
    }

    /** Typing in the search box: every key but Escape goes to it (E, Q and the number keys type instead of acting; Enter leaves it); the Lock key. */
    @Inject(method = "keyTyped", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsKeys(char typed, int key, CallbackInfo ci) {
        if (ladsSearch != null && ladsSearch.isFocused() && key != Keyboard.KEY_ESCAPE) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) ladsSearch.setFocused(false);
            else if (ladsSearch.textboxKeyTyped(typed, key)) InventoryTweaks.setQuery(ladsSearch.getText());
            ci.cancel();
            return;
        }
        if (key == Keyboard.KEY_R && !lads$typing() && ContainerSorting.isAllowed() && ladsTweaked()) {
            lads$sortContainer();
            ci.cancel();
            return;
        }
        int lock = ChatTweaks189.LOCK.getKeyCode();
        if (InventoryTweaks.lockOn && ladsTweaked() && lock > 0 && key == lock && theSlot != null && theSlot.inventory instanceof InventoryPlayer) {
            InventoryTweaks.toggleLock(theSlot.getSlotIndex());
            ci.cancel();
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsClick(int mouseX, int mouseY, int button, CallbackInfo ci) {
        if (ladsSearch != null) {
            ladsSearch.mouseClicked(mouseX, mouseY, button); // focuses it when clicked, unfocuses it otherwise
            if (ladsSearch.isFocused()) {
                ci.cancel();
                return;
            }
        }
        if (button == 0 && ContainerSorting.isAllowed() && ladsTweaked()) {
            int bx = ContainerSorting.getButtonX(guiLeft, xSize, InventoryTweaks.searchOn);
            int by = ContainerSorting.getButtonY(guiTop);
            if (ContainerSorting.isOverButton(mouseX, mouseY, bx, by)) {
                lads$sortContainer();
                ci.cancel();
                return;
            }
        }
        if (!lads$sortQueue.isEmpty()) lads$sortQueue.clear();
        if (button == 2 && ladsArmorSwap(getSlotAtPosition(mouseX, mouseY))) ci.cancel();
    }

    /**
     * Armor Swap (gated): middle-clicking armor in the player's own inventory screen wears it, the piece worn before goes where it
     * was: two or three ordinary pickup clicks on that screen's own slots (ContainerPlayer: 5 helmet .. 8 boots). Not in creative,
     * not with an item on the cursor, never with a locked slot.
     */
    @Unique
    private boolean ladsArmorSwap(Slot from) {
        if (!InventoryTweaks.armorSwapOn || !((Object) this instanceof GuiInventory) || mc.thePlayer == null || mc.playerController.isInCreativeMode()
            || from == null || !(from.inventory instanceof InventoryPlayer) || from.slotNumber < 9 || mc.thePlayer.inventory.getItemStack() != null
            || from.getStack() == null || !(from.getStack().getItem() instanceof ItemArmor)) return false;
        Slot to = inventorySlots.getSlot(InventoryTweaks.armorMenuSlot(((ItemArmor) from.getStack().getItem()).armorType));
        if (InventoryTweaks.locked(from.getSlotIndex()) || InventoryTweaks.locked(to.getSlotIndex())) return false;
        boolean wearing = to.getHasStack();
        handleMouseClick(from, from.slotNumber, 0, InventoryTweaks.PICKUP);
        handleMouseClick(to, to.slotNumber, 0, InventoryTweaks.PICKUP);
        if (wearing) handleMouseClick(from, from.slotNumber, 0, InventoryTweaks.PICKUP);
        return true;
    }

    @Unique
    private void lads$sortContainer() {
        if (!ContainerSorting.isAllowed() || !lads$sortQueue.isEmpty() || mc.thePlayer == null || mc.thePlayer.inventory.getItemStack() != null) return;

        List<Slot> containerSlots = new ArrayList<Slot>();
        List<Slot> playerStorageSlots = new ArrayList<Slot>();

        for (Object obj : inventorySlots.inventorySlots) {
            if (!(obj instanceof Slot)) continue;
            Slot slot = (Slot) obj;
            if (!(slot.inventory instanceof InventoryPlayer)) {
                containerSlots.add(slot);
            } else {
                if (slot.getSlotIndex() >= 9 && slot.getSlotIndex() < 36) {
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

        List<ContainerSorting.SlotItem> items = new ArrayList<ContainerSorting.SlotItem>();
        for (Slot slot : targetSlots) {
            ItemStack stack = slot.getStack();
            boolean locked = (slot.inventory instanceof InventoryPlayer) && InventoryTweaks.locked(slot.getSlotIndex());
            if (stack == null || stack.getItem() == null) {
                items.add(new ContainerSorting.SlotItem(slot.slotNumber, "", 0, 0, 0, locked));
            } else {
                String itemId = String.valueOf(Item.itemRegistry.getNameForObject(stack.getItem()));
                items.add(new ContainerSorting.SlotItem(slot.slotNumber, itemId, stack.stackSize, stack.getMaxStackSize(), stack.getMetadata(), locked));
            }
        }

        List<int[]> swaps = ContainerSorting.planSwaps(items);
        lads$sortQueue.addAll(swaps);
    }

    @Unique
    private void lads$swapSlots(Slot from, Slot to) {
        boolean hasFrom = from.getHasStack();
        boolean hasTo = to.getHasStack();
        if (!hasFrom && !hasTo) return;
        if (hasFrom && !hasTo) {
            handleMouseClick(from, from.slotNumber, 0, InventoryTweaks.PICKUP);
            handleMouseClick(to, to.slotNumber, 0, InventoryTweaks.PICKUP);
        } else if (!hasFrom && hasTo) {
            handleMouseClick(to, to.slotNumber, 0, InventoryTweaks.PICKUP);
            handleMouseClick(from, from.slotNumber, 0, InventoryTweaks.PICKUP);
        } else {
            handleMouseClick(from, from.slotNumber, 0, InventoryTweaks.PICKUP);
            handleMouseClick(to, to.slotNumber, 0, InventoryTweaks.PICKUP);
            if (mc.thePlayer != null && mc.thePlayer.inventory.getItemStack() != null) {
                handleMouseClick(from, from.slotNumber, 0, InventoryTweaks.PICKUP);
            }
        }
    }

}
