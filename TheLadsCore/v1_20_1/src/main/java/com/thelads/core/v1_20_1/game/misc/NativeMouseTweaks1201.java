package com.thelads.core.v1_20_1.game.misc;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.MouseTweaksModule;
import com.thelads.core.v1_20_1.compat.ModPresence1201;
import com.thelads.core.v1_20_1.game.misc.mixin.CreativeTabAccessor1201;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * Lads Mouse Tweaks (MouseTweaksModule) on every container screen, modded ones built on it included; MouseTweaksMixin feeds the
 * screen's own mouse events. Clicks go through the screen's slotClicked, as a player's do. Stands down while the Mouse Tweaks mod
 * is loaded. Creative: the inventory tab only (the item tabs keep their scrolling and picking).
 */
public final class NativeMouseTweaks1201 implements MouseTweaksModule.Menu<Slot, ItemStack> {
    /** What MouseTweaksMixin adds to AbstractContainerScreen. */
    public interface Screen1201 {
        Slot lads$slotAt(double x, double y);
        void lads$click(Slot slot, int button, ClickType type);
    }

    private static Boolean external;
    private static AbstractContainerScreen<?> dragScreen;
    /** QA: shift as the probe holds it (windows that are never focused have no keyboard state to read); null: ask the keyboard. */
    public static volatile Boolean shiftOverride;
    /** QA: slot clicks the tweak sent, as "slot:button:type" (read by the QA probe). */
    public static final List<String> CLICKS = new ArrayList<>();
    private final AbstractContainerScreen<?> screen;
    private final boolean ownInventory;

    private NativeMouseTweaks1201(AbstractContainerScreen<?> screen) {
        this.screen = screen;
        ownInventory = screen.getMenu() instanceof InventoryMenu || screen instanceof CreativeModeInventoryScreen;
    }

    /** The Mouse Tweaks mod is installed (on Forge through Connector too): it keeps the job and this stays off. */
    public static boolean externalMod() {
        if (external == null) external = ModPresence1201.loaded("mousetweaks");
        return external;
    }

    private static MouseTweaksModule module(AbstractContainerScreen<?> screen) {
        if (externalMod() || screen instanceof CreativeModeInventoryScreen && !inventoryTab()) return null;
        return ModuleManager.getInstance().getModule(MouseTweaksModule.NAME) instanceof MouseTweaksModule mt && mt.isEnabled() ? mt : null;
    }

    private static boolean inventoryTab() {
        CreativeModeTab tab = CreativeTabAccessor1201.lads$selectedTab();
        return tab != null && tab.getType() == CreativeModeTab.Type.INVENTORY;
    }

    /**
     * The slot under the mouse, or null when the tweak must leave it alone. On creative's inventory tab only the player's own
     * slots count: the tab also lists its Destroy Item slot, where any click wipes the whole cursor stack.
     */
    private static Slot slotAt(AbstractContainerScreen<?> screen, double x, double y) {
        Slot slot = ((Screen1201) screen).lads$slotAt(x, y);
        boolean playerSlot = slot != null && slot.container instanceof Inventory;
        return screen instanceof CreativeModeInventoryScreen && !playerSlot ? null : slot;
    }

    /** True: the press is the tweak's and the screen must not handle it. {@code button}: 0 left, 1 right. */
    public static boolean press(AbstractContainerScreen<?> screen, double x, double y, int button) {
        MouseTweaksModule mt = module(screen);
        dragScreen = screen;
        return mt != null && mt.press(new NativeMouseTweaks1201(screen), button, slotAt(screen, x, y));
    }

    public static void drag(AbstractContainerScreen<?> screen, double x, double y, int button) {
        MouseTweaksModule mt = module(screen);
        if (mt == null || screen != dragScreen) return;
        mt.drag(new NativeMouseTweaks1201(screen), button, slotAt(screen, x, y), shift());
    }

    public static boolean release(AbstractContainerScreen<?> screen, int button) {
        MouseTweaksModule mt = module(screen);
        return mt != null && screen == dragScreen && mt.release(button);
    }

    public static boolean scroll(AbstractContainerScreen<?> screen, double x, double y, double amount) {
        MouseTweaksModule mt = module(screen);
        Slot slot = slotAt(screen, x, y);
        return mt != null && amount != 0 && mt.scroll(new NativeMouseTweaks1201(screen), slot, amount > 0);
    }

    private static boolean shift() {
        Boolean forced = shiftOverride;
        return forced != null ? forced : Screen.hasShiftDown(); // shift may come after the press
    }

    @Override public List<Slot> slots() {
        List<Slot> slots = new ArrayList<>();
        for (Slot slot : screen.getMenu().slots) if (slot.isActive()) slots.add(slot);
        return slots;
    }
    @Override public ItemStack item(Slot slot) { return slot.getItem(); }
    @Override public ItemStack carried() { return screen.getMenu().getCarried(); }
    @Override public int count(ItemStack item) { return item.isEmpty() ? 0 : item.getCount(); }
    @Override public boolean same(ItemStack a, ItemStack b) { return !a.isEmpty() && !b.isEmpty() && ItemStack.isSameItemSameTags(a, b); }
    @Override public boolean mayPlace(Slot slot, ItemStack item) { return slot.mayPlace(item); }
    @Override public int limit(Slot slot, ItemStack item) { return slot.getMaxStackSize(item); }
    @Override public int y(Slot slot) { return slot.y; }
    @Override public void click(Slot slot, int button, boolean quickMove) {
        ClickType type = quickMove ? ClickType.QUICK_MOVE : ClickType.PICKUP;
        synchronized (CLICKS) {
            if (CLICKS.size() < 512) CLICKS.add(slot.index + ":" + button + ":" + type);
        }
        ((Screen1201) screen).lads$click(slot, button, type);
    }

    /** In the player's own inventory: hotbar and main inventory. Elsewhere: the player's 36 slots and the container's. */
    @Override public int part(Slot slot) {
        if (ownInventory) {
            // InventoryMenu's own numbering; the creative tab's wrappers carry it as their container slot.
            int i = screen instanceof CreativeModeInventoryScreen ? slot.getContainerSlot() : slot.index;
            return i >= InventoryMenu.USE_ROW_SLOT_START && i < InventoryMenu.USE_ROW_SLOT_END ? 1
                : i >= InventoryMenu.INV_SLOT_START && i < InventoryMenu.INV_SLOT_END ? 2 : 0;
        }
        return slot.container instanceof Inventory && slot.getContainerSlot() < Inventory.INVENTORY_SIZE ? 1 : 2;
    }
}
