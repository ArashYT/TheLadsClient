package com.thelads.core.v1_20_1.game.misc.mixin;

import com.thelads.core.client.ContainerSorting;
import com.thelads.core.client.InventoryTweaks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import com.thelads.core.v1_20_1.hud.chat.ChatTweaks1201;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The container screens' parity-1023 tweaks on 1.20.1 (common InventoryTweaks): Inventory Search's box above the container (its
 * matches are worked out once a tick, the slots only read them), Slot Lock's Lock key and tint, and Armor Swap (gated) in the
 * player's own inventory screen only. The creative inventory keeps its own search and gets none of these.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class InventoryTweaksMixin1201 extends Screen {
    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow protected int imageWidth;
    @Shadow protected Slot hoveredSlot;
    @Shadow @Final protected AbstractContainerMenu menu;
    @Shadow private Slot findSlot(double x, double y) { throw new AssertionError(); }
    @Shadow protected abstract void slotClicked(Slot slot, int slotId, int button, ClickType type);
    @Unique private EditBox ladsSearch;
    /** Menu slots Inventory Search dims (worked out each tick while a search is typed). */
    @Unique private boolean[] ladsDim = new boolean[0];
    /** Rate-limited swap queue: processes at most 1 swap per client tick to prevent anticheat packet bursts. */
    @Unique private final Queue<int[]> lads$sortQueue = new ArrayDeque<>();

    private InventoryTweaksMixin1201(Component title) { super(title); }

    @Unique
    private boolean lads$isStorageMenu() {
        return this.menu instanceof ChestMenu 
            || this.menu instanceof ShulkerBoxMenu 
            || this.menu instanceof InventoryMenu;
    }

    @Unique
    private boolean lads$hasFocusedTextBox() {
        if (ladsSearch != null && ladsSearch.isFocused()) return true;
        GuiEventListener focused = getFocused();
        if (focused instanceof EditBox editBox) {
            return editBox.isFocused();
        }
        return false;
    }

    @Unique
    private boolean lads$tweaked() {
        return !((Object) this instanceof CreativeModeInventoryScreen) && lads$isStorageMenu();
    }

    @Inject(method = "init", at = @At("TAIL"), require = 1)
    private void lads$init(CallbackInfo ci) {
        lads$sortQueue.clear();
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

    @Inject(method = "removed", at = @At("HEAD"))
    private void lads$onRemoved(CallbackInfo ci) {
        lads$sortQueue.clear();
    }

    /** The box sits on the container's top right corner, following it when the recipe book moves the screen. */
    @Unique
    private void lads$place() {
        ladsSearch.setX(leftPos + imageWidth - InventoryTweaks.SEARCH_WIDTH);
        ladsSearch.setY(topPos - InventoryTweaks.SEARCH_HEIGHT - 2);
    }

    @Inject(method = "render", at = @At("HEAD"), require = 1)
    private void lads$follow(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (ladsSearch != null) lads$place();
    }

    @Inject(method = "render", at = @At("TAIL"), require = 1)
    private void lads$renderSort(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (ContainerSorting.isAllowed() && lads$tweaked()) {
            int bx = ContainerSorting.getButtonX(leftPos, imageWidth, InventoryTweaks.searchOn);
            int by = ContainerSorting.getButtonY(topPos);
            boolean over = ContainerSorting.isOverButton(mouseX, mouseY, bx, by);
            graphics.fill(RenderType.guiOverlay(), bx, by, bx + ContainerSorting.BUTTON_WIDTH, by + ContainerSorting.BUTTON_HEIGHT, over ? 0x90404040 : 0x80000000);
            graphics.drawCenteredString(font, ContainerSorting.SORT_LABEL, bx + ContainerSorting.BUTTON_WIDTH / 2, by + 2, over ? 0xFFFFA0 : 0xFFFFFF);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), require = 1)
    private void lads$search(CallbackInfo ci) {
        if (!lads$sortQueue.isEmpty()) {
            if (minecraft == null || minecraft.player == null || (menu != null && !menu.getCarried().isEmpty())) {
                lads$sortQueue.clear();
            } else {
                int[] swap = lads$sortQueue.poll();
                if (swap != null) {
                    Slot from = menu.getSlot(swap[0]);
                    Slot to = menu.getSlot(swap[1]);
                    if (from != null && to != null) {
                        lads$swapSlots(from, to);
                    }
                }
            }
        }
        int size = menu.slots.size();
        if (ladsDim.length != size) ladsDim = new boolean[size];
        boolean searching = ladsSearch != null && !InventoryTweaks.query().isEmpty();
        for (int i = 0; i < size; i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            ladsDim[i] = searching && !stack.isEmpty()
                && !InventoryTweaks.matches(stack.getHoverName().getString(), BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
    }

    @Inject(method = "renderSlot", at = @At("TAIL"), require = 1)
    private void lads$overlay(GuiGraphics graphics, Slot slot, CallbackInfo ci) {
        int x = slot.x, y = slot.y;
        if (slot.index < ladsDim.length && ladsDim[slot.index]) graphics.fill(RenderType.guiOverlay(), x, y, x + 16, y + 16, InventoryTweaks.DIM);
        if (slot.container instanceof Inventory && InventoryTweaks.locked(slot.getContainerSlot())) {
            graphics.fill(RenderType.guiOverlay(), x, y, x + 16, y + 16, InventoryTweaks.LOCK_TINT);
            graphics.fill(RenderType.guiOverlay(), x, y + 15, x + 16, y + 16, InventoryTweaks.LOCK_EDGE);
        }
    }

    /** Typing in the search box: every key but Escape goes to it (E, Q and the number keys type instead of acting; Enter leaves it); the Lock key. */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$keys(int key, int scancode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (ladsSearch != null && ladsSearch.isFocused() && key != GLFW.GLFW_KEY_ESCAPE) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                ladsSearch.setFocused(false);
                setFocused(null);
            } else ladsSearch.keyPressed(key, scancode, modifiers);
            cir.setReturnValue(true);
            return;
        }
        if (key == GLFW.GLFW_KEY_R && !lads$hasFocusedTextBox() && ContainerSorting.isAllowed() && lads$tweaked()) {
            lads$sortContainer();
            cir.setReturnValue(true);
            return;
        }
        if (InventoryTweaks.lockOn && lads$tweaked() && ChatTweaks1201.LOCK.matches(key, scancode) && hoveredSlot != null
            && hoveredSlot.container instanceof Inventory) {
            InventoryTweaks.toggleLock(hoveredSlot.getContainerSlot());
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$click(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (ladsSearch != null && ladsSearch.isFocused() && !ladsSearch.isMouseOver(mouseX, mouseY)) {
            ladsSearch.setFocused(false);
            setFocused(null);
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && ContainerSorting.isAllowed() && lads$tweaked()) {
            int bx = ContainerSorting.getButtonX(leftPos, imageWidth, InventoryTweaks.searchOn);
            int by = ContainerSorting.getButtonY(topPos);
            if (ContainerSorting.isOverButton(mouseX, mouseY, bx, by)) {
                lads$sortContainer();
                cir.setReturnValue(true);
                return;
            }
        }
        if (!lads$sortQueue.isEmpty()) lads$sortQueue.clear();
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && lads$armorSwap(findSlot(mouseX, mouseY))) cir.setReturnValue(true);
    }

    /**
     * Armor Swap (gated): middle-clicking armor in the player's own inventory screen wears it, the piece worn before goes where it
     * was: two or three ordinary pickup clicks on that screen's own slots (InventoryMenu: 5 head .. 8 feet). Not in creative, not with
     * an item on the cursor, never with a locked slot.
     */
    @Unique
    private boolean lads$armorSwap(Slot from) {
        if (!InventoryTweaks.armorSwapOn || !((Object) this instanceof InventoryScreen) || minecraft == null || minecraft.player == null
            || minecraft.player.isCreative() || from == null || !(from.container instanceof Inventory) || from.index < 9
            || !menu.getCarried().isEmpty() || !from.hasItem()) return false;
        EquipmentSlot type = LivingEntity.getEquipmentSlotForItem(from.getItem());
        if (type.getType() != EquipmentSlot.Type.ARMOR) return false;
        Slot to = menu.getSlot(InventoryTweaks.armorMenuSlot(3 - type.getIndex()));
        if (InventoryTweaks.locked(from.getContainerSlot()) || InventoryTweaks.locked(to.getContainerSlot())) return false;
        boolean wearing = to.hasItem();
        slotClicked(from, from.index, 0, ClickType.PICKUP);
        slotClicked(to, to.index, 0, ClickType.PICKUP);
        if (wearing) slotClicked(from, from.index, 0, ClickType.PICKUP);
        return true;
    }

    @Unique
    private void lads$sortContainer() {
        if (!ContainerSorting.isAllowed() || !lads$isStorageMenu() || !lads$sortQueue.isEmpty() || minecraft == null || minecraft.player == null || !menu.getCarried().isEmpty()) return;

        List<Slot> containerSlots = new ArrayList<>();
        List<Slot> playerStorageSlots = new ArrayList<>();

        if (this.menu instanceof ChestMenu || this.menu instanceof ShulkerBoxMenu) {
            for (Slot slot : menu.slots) {
                if (!(slot.container instanceof Inventory)) {
                    containerSlots.add(slot);
                } else if (slot.getContainerSlot() >= 9 && slot.getContainerSlot() < 36) {
                    playerStorageSlots.add(slot);
                }
            }
        } else if (this.menu instanceof InventoryMenu) {
            // Player inventory: main inventory only (slots 9..35); never crafting grid, result, armor, offhand
            for (Slot slot : menu.slots) {
                if (slot.container instanceof Inventory && slot.getContainerSlot() >= 9 && slot.getContainerSlot() < 36) {
                    playerStorageSlots.add(slot);
                }
            }
        } else {
            return;
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
        lads$sortQueue.addAll(swaps);
    }

    @Unique
    private void lads$swapSlots(Slot from, Slot to) {
        boolean hasFrom = from.hasItem();
        boolean hasTo = to.hasItem();
        if (!hasFrom && !hasTo) return;
        if (hasFrom && !hasTo) {
            slotClicked(from, from.index, 0, ClickType.PICKUP);
            slotClicked(to, to.index, 0, ClickType.PICKUP);
        } else if (!hasFrom && hasTo) {
            slotClicked(to, to.index, 0, ClickType.PICKUP);
            slotClicked(from, from.index, 0, ClickType.PICKUP);
        } else {
            slotClicked(from, from.index, 0, ClickType.PICKUP);
            slotClicked(to, to.index, 0, ClickType.PICKUP);
            if (menu != null && !menu.getCarried().isEmpty()) {
                slotClicked(from, from.index, 0, ClickType.PICKUP);
            }
        }
    }

}
