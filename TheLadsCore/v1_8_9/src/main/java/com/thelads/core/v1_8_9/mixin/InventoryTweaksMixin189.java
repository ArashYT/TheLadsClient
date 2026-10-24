package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.InventoryTweaks;
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
import net.minecraft.inventory.ContainerChest;
import com.thelads.core.client.hypixel.TerminalSolver;
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
        ladsSearch = null;
        if (!InventoryTweaks.searchOn || !ladsTweaked()) return;
        ladsSearch = new GuiTextField(0, fontRendererObj, guiLeft + xSize - InventoryTweaks.SEARCH_WIDTH, guiTop - InventoryTweaks.SEARCH_HEIGHT - 2,
            InventoryTweaks.SEARCH_WIDTH, InventoryTweaks.SEARCH_HEIGHT);
        ladsSearch.setMaxStringLength(64);
        ladsSearch.setText(InventoryTweaks.query());
    }

    @Inject(method = "updateScreen", at = @At("HEAD"), require = 1)
    private void ladsSearchTick(CallbackInfo ci) {
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
        int highlight = 0;
        if (slot.getHasStack()) {
            ItemStack stack = slot.getStack();
            String title = null;
            if (inventorySlots instanceof ContainerChest) {
                title = ((ContainerChest) inventorySlots).getLowerChestInventory().getDisplayName().getUnformattedText();
            } else if (slot.inventory != null && !(slot.inventory instanceof InventoryPlayer)) {
                title = slot.inventory.getDisplayName().getUnformattedText();
            }
            if (title != null) {
                highlight = TerminalSolver.getSlotHighlight(title, slot.slotNumber, stack.stackSize, stack.getDisplayName(), inventorySlots.inventorySlots.size());
            }
        }
        if (!dim && !locked && highlight == 0) return;
        int x = slot.xDisplayPosition, y = slot.yDisplayPosition;
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        if (dim) Gui.drawRect(x, y, x + 16, y + 16, InventoryTweaks.DIM);
        if (locked) {
            Gui.drawRect(x, y, x + 16, y + 16, InventoryTweaks.LOCK_TINT);
            Gui.drawRect(x, y + 15, x + 16, y + 16, InventoryTweaks.LOCK_EDGE);
        }
        if (highlight != 0) {
            Gui.drawRect(x, y, x + 16, y + 16, highlight);
        }
        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
    }

    @Inject(method = "drawScreen", at = @At("TAIL"), require = 1)
    private void ladsDrawSearch(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (ladsSearch == null) return;
        net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting(); // drawScreen ends with item lighting on
        ladsSearch.drawTextBox();
        if (!ladsSearch.isFocused() && ladsSearch.getText().isEmpty())
            fontRendererObj.drawString("Search", ladsSearch.xPosition + 4, ladsSearch.yPosition + 2, 0xFF606060);
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
}
