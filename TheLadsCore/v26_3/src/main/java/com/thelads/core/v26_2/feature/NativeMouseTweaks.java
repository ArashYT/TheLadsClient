package com.thelads.core.v26_2.feature;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.MouseTweaksModule;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Lads Mouse Tweaks (MouseTweaksModule) on every container screen, modded ones built on it included; MouseTweaksMixin feeds the
 * screen's own mouse events. Clicks go through the screen's slotClicked, as a player's do. Stands down while the Mouse Tweaks mod
 * is loaded. Creative: the inventory tab only (the item tabs keep their scrolling and picking).
 */
public final class NativeMouseTweaks implements MouseTweaksModule.Menu<Slot, ItemStack> {
    /** What MouseTweaksMixin adds to AbstractContainerScreen. */
    public interface Screen {
        Slot lads$slotAt(double x, double y);
        void lads$click(Slot slot, int button, ContainerInput input);
        /** An item's own wheel action (a bundle's) owns scrolling over this slot. */
        boolean lads$itemScrolls(Slot slot);
    }

    private static final boolean EXTERNAL = FabricLoader.getInstance().isModLoaded("mousetweaks");
    private static AbstractContainerScreen<?> dragScreen;
    private final AbstractContainerScreen<?> screen;
    private final boolean ownInventory;

    private NativeMouseTweaks(AbstractContainerScreen<?> screen) {
        this.screen = screen;
        ownInventory = screen.getMenu() instanceof InventoryMenu || screen instanceof CreativeModeInventoryScreen;
    }

    private static MouseTweaksModule module(AbstractContainerScreen<?> screen) {
        if (EXTERNAL || screen instanceof CreativeModeInventoryScreen creative && !creative.isInventoryOpen()) return null;
        return ModuleManager.getInstance().getModule(MouseTweaksModule.NAME) instanceof MouseTweaksModule mt && mt.isEnabled() ? mt : null;
    }

    /** True: the press is the tweak's and the screen must not handle it. {@code button}: 0 left, 1 right. */
    public static boolean press(AbstractContainerScreen<?> screen, MouseButtonEvent event, int button) {
        MouseTweaksModule mt = module(screen);
        dragScreen = screen;
        return mt != null && mt.press(new NativeMouseTweaks(screen), button, ((Screen) screen).lads$slotAt(event.x(), event.y()));
    }

    public static void drag(AbstractContainerScreen<?> screen, MouseButtonEvent event, int button) {
        MouseTweaksModule mt = module(screen);
        if (mt == null || screen != dragScreen) return;
        boolean shift = event.hasShiftDown() || Minecraft.getInstance().hasShiftDown(); // shift may come after the press
        mt.drag(new NativeMouseTweaks(screen), button, ((Screen) screen).lads$slotAt(event.x(), event.y()), shift);
    }

    public static boolean release(AbstractContainerScreen<?> screen, int button) {
        MouseTweaksModule mt = module(screen);
        return mt != null && screen == dragScreen && mt.release(button);
    }

    public static boolean scroll(AbstractContainerScreen<?> screen, double x, double y, double amount) {
        MouseTweaksModule mt = module(screen);
        Slot slot = ((Screen) screen).lads$slotAt(x, y);
        return mt != null && amount != 0 && (slot == null || !((Screen) screen).lads$itemScrolls(slot))
            && mt.scroll(new NativeMouseTweaks(screen), slot, amount > 0);
    }

    @Override public List<Slot> slots() {
        List<Slot> slots = new ArrayList<>();
        for (Slot slot : screen.getMenu().slots) if (slot.isActive()) slots.add(slot);
        return slots;
    }
    @Override public ItemStack item(Slot slot) { return slot.getItem(); }
    @Override public ItemStack carried() { return screen.getMenu().getCarried(); }
    @Override public int count(ItemStack item) { return item.isEmpty() ? 0 : item.getCount(); }
    @Override public boolean same(ItemStack a, ItemStack b) { return !a.isEmpty() && !b.isEmpty() && ItemStack.isSameItemSameComponents(a, b); }
    @Override public boolean mayPlace(Slot slot, ItemStack item) { return slot.mayPlace(item); }
    @Override public int limit(Slot slot, ItemStack item) { return slot.getMaxStackSize(item); }
    @Override public int y(Slot slot) { return slot.y; }
    @Override public void click(Slot slot, int button, boolean quickMove) {
        ((Screen) screen).lads$click(slot, button, quickMove ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP);
    }

    /** In the player's own inventory: hotbar and main inventory. Elsewhere: the player's 36 slots and the container's. */
    @Override public int part(Slot slot) {
        if (slot.container instanceof Inventory && slot.getContainerSlot() < Inventory.INVENTORY_SIZE)
            return ownInventory ? (slot.getContainerSlot() < Inventory.SELECTION_SIZE ? 1 : 2) : 1;
        return ownInventory ? 0 : 2;
    }
}
