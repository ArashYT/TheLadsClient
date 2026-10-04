package com.thelads.core.v1_8_9.feature;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.MouseTweaksModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;

/**
 * Lads Mouse Tweaks (MouseTweaksModule) on every GuiContainer: GuiContainerMouseTweaksMixin feeds press, drag and release, and
 * Forge's mouse input event the wheel (GuiContainer has no wheel handler of its own). Clicks go through the screen's
 * handleMouseClick, as a player's do. Stands down while the Mouse Tweaks mod is loaded. Not in the creative screen, whose
 * inventory tab re-numbers the player's slots.
 */
public final class MouseTweaks189 implements MouseTweaksModule.Menu<Slot, ItemStack> {
    /** What GuiContainerMouseTweaksMixin adds to GuiContainer. */
    public interface Screen {
        Slot ladsSlotAt(int x, int y);
        void ladsClick(Slot slot, int button, boolean quickMove);
    }

    /** QA (MouseTweaksProbe189): shift held for the tweak's own drags, as synthetic key events never reach Keyboard.isKeyDown. */
    static boolean qaShift;
    private static boolean external;
    private static GuiContainer dragScreen;
    private final GuiContainer screen;

    private MouseTweaks189(GuiContainer screen) { this.screen = screen; }

    public static void register() {
        external = Loader.isModLoaded("MouseTweaks") || Loader.isModLoaded("mousetweaks");
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new Wheel());
    }

    private static MouseTweaksModule module(GuiContainer screen) {
        if (external || screen instanceof GuiContainerCreative) return null;
        Object module = ModuleManager.getInstance().getModule(MouseTweaksModule.NAME);
        return module instanceof MouseTweaksModule && ((MouseTweaksModule) module).isEnabled() ? (MouseTweaksModule) module : null;
    }

    /** True: the press is the tweak's and the screen must not handle it. */
    public static boolean press(GuiContainer screen, int x, int y, int button) {
        MouseTweaksModule mt = module(screen);
        dragScreen = screen;
        return mt != null && mt.press(new MouseTweaks189(screen), button, ((Screen) screen).ladsSlotAt(x, y));
    }

    public static void drag(GuiContainer screen, int x, int y, int button) {
        MouseTweaksModule mt = module(screen);
        if (mt != null && screen == dragScreen)
            mt.drag(new MouseTweaks189(screen), button, ((Screen) screen).ladsSlotAt(x, y), GuiScreen.isShiftKeyDown() || qaShift);
    }

    public static boolean release(GuiContainer screen, int button) {
        MouseTweaksModule mt = module(screen);
        return mt != null && screen == dragScreen && mt.release(button);
    }

    /** The wheel, at the event's own position (as GuiScreen.handleMouseInput reads it). */
    public static final class Wheel {
        @SubscribeEvent
        public void mouse(GuiScreenEvent.MouseInputEvent.Pre event) {
            int wheel = Mouse.getEventDWheel();
            if (wheel == 0 || !(event.gui instanceof GuiContainer)) return;
            GuiContainer screen = (GuiContainer) event.gui;
            MouseTweaksModule mt = module(screen);
            if (mt == null) return;
            Minecraft mc = Minecraft.getMinecraft();
            int x = Mouse.getEventX() * screen.width / mc.displayWidth;
            int y = screen.height - Mouse.getEventY() * screen.height / mc.displayHeight - 1;
            if (mt.scroll(new MouseTweaks189(screen), ((Screen) screen).ladsSlotAt(x, y), wheel > 0)) event.setCanceled(true);
        }
    }

    @Override public List<Slot> slots() {
        List<Slot> slots = new ArrayList<>();
        for (Slot slot : screen.inventorySlots.inventorySlots) if (slot.canBeHovered()) slots.add(slot);
        return slots;
    }
    @Override public ItemStack item(Slot slot) { return slot.getStack(); }
    @Override public ItemStack carried() { return Minecraft.getMinecraft().thePlayer.inventory.getItemStack(); }
    @Override public int count(ItemStack item) { return item == null ? 0 : Math.max(0, item.stackSize); }
    /** As Container.slotClick merges stacks: item, metadata and NBT. */
    @Override public boolean same(ItemStack a, ItemStack b) {
        return count(a) > 0 && count(b) > 0 && a.getItem() == b.getItem() && a.getMetadata() == b.getMetadata() && ItemStack.areItemStackTagsEqual(a, b);
    }
    @Override public boolean mayPlace(Slot slot, ItemStack item) { return slot.isItemValid(item); }
    @Override public int limit(Slot slot, ItemStack item) {
        return item == null ? slot.getSlotStackLimit() : Math.min(slot.getItemStackLimit(item), item.getMaxStackSize());
    }
    @Override public int y(Slot slot) { return slot.yDisplayPosition; }
    @Override public void click(Slot slot, int button, boolean quickMove) { ((Screen) screen).ladsClick(slot, button, quickMove); }

    /** In the player's own inventory: hotbar and main inventory. Elsewhere: the player's 36 slots and the container's. */
    @Override public int part(Slot slot) {
        boolean own = screen.inventorySlots instanceof ContainerPlayer;
        if (slot.inventory instanceof InventoryPlayer && slot.getSlotIndex() < 36) return own ? (slot.getSlotIndex() < 9 ? 1 : 2) : 1;
        return own ? 0 : 2;
    }
}
