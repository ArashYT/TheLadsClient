package com.thelads.core.v1_8_9.feature;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.thelads.core.client.ItemsQa;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;

/**
 * QA only (LADS_VERIFY_189_ONLY=items, a CoreProbe step in its QA world): the 1.9.1 items sequence (common ItemsQa) on 1.8.9.
 * The pointer is moved by WM_MOUSEMOVE messages the game posts to its own window (LWJGL's window procedure, never the OS cursor),
 * so frames hover what a real mouse would; buttons and keys go into LWJGL's queues (CoreProbe), read by GuiScreen.handleInput.
 * Frames: lads-qa/screenshots/items-*.png.
 */
final class ItemsProbe189 implements ItemsQa.Driver {
    private static final ItemsProbe189 DRIVER = new ItemsProbe189();
    private static final int WM_MOUSEMOVE = 0x0200;
    private final Map<KeyBinding, Integer> bindsWere = new LinkedHashMap<>();
    private final List<Entity> shown = new ArrayList<>();
    private final Map<BlockPos, IBlockState> blocksWere = new LinkedHashMap<>();
    private int pointerX, pointerY;
    private net.minecraft.world.EnumDifficulty difficultyWas;
    /** The pointer is held over a slot: posted again every tick, so a move Windows itself sends the window never wins a frame. */
    private Slot hovered;

    private ItemsProbe189() {}

    private static int titleTicks;
    private static boolean titleDrawing;

    /**
     * CoreProbe step on the title screen (no world): chest, trapped chest and ender chest items drawn over a black box the probe
     * fills, through RenderItem as any menu draws them; over a quarter of each item's square must be drawn (the world-less chests
     * were skipped, leaving it black).
     */
    static boolean title(Minecraft mc) throws Exception {
        if (titleTicks++ == 0) {
            CoreProbe.check(mc.theWorld == null, "items: the title screen has no world");
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(TITLE);
            titleDrawing = true;
            return false;
        }
        if (titleTicks < 20) return false;
        CoreProbe.screenshot(mc, "items-0-title-chests");
        java.awt.image.BufferedImage shot = CoreProbe.readShot(mc, "items-0-title-chests");
        titleDrawing = false;
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(TITLE);
        int scale = new net.minecraft.client.gui.ScaledResolution(mc).getScaleFactor();
        String[] names = {"chest", "trapped chest", "ender chest"};
        for (int i = 0; i < 3; i++) {
            int lit = 0, x0 = (8 + i * 20) * scale, y0 = 8 * scale, size = 16 * scale;
            for (int y = y0; y < y0 + size; y++)
                for (int x = x0; x < x0 + size; x++) {
                    if ((shot.getRGB(x, y) & 0xFFFFFF) != 0) lit++; // the box is pure black, the dark ender chest is not
                }
            CoreProbe.check(lit > size * size / 4, "items: a " + names[i] + " item drawn on the title screen (no world) is visible (" + lit + " of " + size * size + " pixels drawn)");
        }
        return CoreProbe.after(5);
    }

    private static final TitleChests TITLE = new TitleChests();

    /** Draws the three chest items over a black box at the top left of every screen while the title check runs. */
    public static final class TitleChests {
        private final ItemStack[] stacks = {new ItemStack(Blocks.chest), new ItemStack(Blocks.trapped_chest), new ItemStack(Blocks.ender_chest)};

        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
        public void draw(net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent.Post event) {
            if (!titleDrawing) return;
            net.minecraft.client.gui.Gui.drawRect(4, 4, 4 + 3 * 20 + 4, 28, 0xFF000000);
            net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
            for (int i = 0; i < 3; i++) Minecraft.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(stacks[i], 8 + i * 20, 8);
            net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
        }
    }

    /** The CoreProbe step: the whole sequence (one ItemsQa tick per client tick). */
    static boolean run(Minecraft mc) {
        if (DRIVER.hovered != null && mc.currentScreen instanceof GuiContainer) DRIVER.pointer(DRIVER.hovered);
        if (!ItemsQa.tick(DRIVER)) return false;
        CoreProbe.check(ItemsQa.failed() == 0, "items: every check passed (" + ItemsQa.failed() + " failed, see Lads items check FAIL)");
        return CoreProbe.after(10);
    }

    @Override public List<String> specialItems() {
        return Arrays.asList("chest", "trapped_chest", "ender_chest", "banner:1", "skull:0", "skull:1", "skull:2", "skull:3", "skull:4", "bed", "sign");
    }

    @Override public void creative(boolean creative) {
        server(p -> p.setGameType(creative ? WorldSettings.GameType.CREATIVE : WorldSettings.GameType.SURVIVAL));
    }

    @Override public void prepare(boolean on) {
        if (on) {
            mc().thePlayer.rotationYaw = 180F;
            mc().thePlayer.rotationPitch = 0F;
        }
        server(p -> {
            if (on) p.worldObj.setWorldTime(6000);
            net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
            if (on && difficultyWas == null) difficultyWas = p.worldObj.getDifficulty();
            if (on || difficultyWas != null) server.setDifficultyForAllWorlds(on ? net.minecraft.world.EnumDifficulty.PEACEFUL : difficultyWas);
            if (!on) difficultyWas = null;
        });
    }

    @Override public void effects(boolean on) {
        server(p -> {
            p.clearActivePotions();
            if (on) {
                p.addPotionEffect(new PotionEffect(Potion.moveSpeed.id, 6000));
                p.addPotionEffect(new PotionEffect(Potion.nightVision.id, 6000));
            }
        });
    }

    @Override public void clear() {
        mc().thePlayer.inventory.setItemStack(null);
        server(p -> {
            Arrays.fill(p.inventory.mainInventory, null);
            Arrays.fill(p.inventory.armorInventory, null);
            p.inventory.setItemStack(null);
        });
    }

    @Override public void give(int slot, String item) {
        ItemStack stack = stack(item);
        server(p -> p.inventory.mainInventory[slot] = stack);
    }

    @Override public void giveTall(int slot, int lines, int length) {
        ItemStack stack = new ItemStack(Blocks.stone);
        NBTTagCompound display = new NBTTagCompound();
        display.setString("Name", "QA tooltip");
        NBTTagList lore = new NBTTagList();
        for (int i = 0; i < lines; i++) lore.appendTag(new NBTTagString(ItemsQa.loreLine(i, length)));
        display.setTag("Lore", lore);
        stack.setTagInfo("display", display);
        server(p -> p.inventory.mainInventory[slot] = stack);
    }

    @Override public String item(int slot) {
        return slot < 36 ? id(mc().thePlayer.inventory.mainInventory[slot]) : "";
    }

    @Override public void select(int hotbar) { mc().thePlayer.inventory.currentItem = hotbar; }

    @Override public void view(int view) { mc().gameSettings.thirdPersonView = view; }

    @Override public void showcase(List<String> items) {
        server(p -> {
            World world = p.worldObj;
            for (Entity entity : shown) entity.setDead();
            shown.clear();
            blocksWere.forEach((pos, state) -> world.setBlockState(pos, state, 3));
            blocksWere.clear();
            if (items == null) return;
            // Facing north: a stone wall 8 blocks ahead with two rows of item frames, the chests on armour stands (skulls and
            // banners worn) 5 ahead, every item dropped 3 ahead.
            BlockPos feet = new BlockPos(p);
            p.playerNetServerHandler.setPlayerLocation(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 180F, 15F);
            for (int x = -7; x <= 7; x++)
                for (int y = 0; y < 5; y++) {
                    BlockPos wall = feet.add(x, y, -8);
                    blocksWere.put(wall, world.getBlockState(wall));
                    world.setBlockState(wall, Blocks.stone.getDefaultState(), 3);
                }
            for (int i = 0; i < items.size(); i++) {
                EntityItemFrame frame = new EntityItemFrame(world, feet.add(i % 6 * 2 - 5, 3 - i / 6, -7), EnumFacing.SOUTH);
                frame.setDisplayedItem(stack(items.get(i)));
                spawn(world, frame);
                EntityItem dropped = new EntityItem(world, feet.getX() + 0.5 + (i - items.size() / 2) * 0.9, feet.getY() + 0.1, feet.getZ() - 2.5, stack(items.get(i)));
                dropped.setInfinitePickupDelay();
                dropped.setNoDespawn();
                dropped.motionX = dropped.motionY = dropped.motionZ = 0;
                spawn(world, dropped);
                if (i < 3) {
                    EntityArmorStand stand = new EntityArmorStand(world, feet.getX() + 0.5 + (i - 1) * 2, feet.getY(), feet.getZ() - 4.5);
                    NBTTagCompound tag = new NBTTagCompound();
                    stand.writeToNBT(tag);
                    tag.setBoolean("ShowArms", true);
                    stand.readFromNBT(tag);
                    stand.setCurrentItemOrArmor(0, stack(items.get(i)));
                    stand.setCurrentItemOrArmor(4, stack(items.get((i + 3) % items.size())));
                    spawn(world, stand);
                }
            }
        });
    }

    @Override public void openInventory() {
        hovered = null;
        mc().displayGuiScreen(new GuiInventory(mc().thePlayer));
    }

    @Override public void openChest(String item) {
        server(p -> {
            BlockPos pos = new BlockPos(p).up(2);
            blocksWere.putIfAbsent(pos, p.worldObj.getBlockState(pos));
            p.worldObj.setBlockState(pos, Blocks.chest.getDefaultState(), 3);
            TileEntityChest chest = (TileEntityChest) p.worldObj.getTileEntity(pos);
            chest.setInventorySlotContents(0, stack(item));
            p.displayGUIChest(chest);
        });
    }

    @Override public void closeScreen() {
        hovered = null;
        if (mc().currentScreen instanceof GuiContainer) mc().thePlayer.closeScreen();
        mc().displayGuiScreen(null);
    }

    @Override public void hoverInventory(int slot) {
        for (Slot s : screen().inventorySlots.inventorySlots)
            if (s.inventory == mc().thePlayer.inventory && s.getSlotIndex() == slot) { pointer(s); return; }
        throw new IllegalStateException("no slot for inventory slot " + slot + " on " + screen());
    }

    @Override public String hoverMenu(int index) {
        Slot slot = screen().inventorySlots.getSlot(index);
        pointer(slot);
        return id(slot.getStack());
    }

    @Override public void mouse(int button) {
        try {
            CoreProbe.mouse(button - 1, true, pointerX, pointerY);
            CoreProbe.mouse(button - 1, false, pointerX, pointerY);
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    @Override public void key(String key) {
        try { CoreProbe.tap(Keyboard.getKeyIndex(key.toUpperCase(java.util.Locale.ROOT)), key.charAt(0)); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    @Override public void bind(int hotbar, String key) {
        if (key == null) {
            bindsWere.forEach(KeyBinding::setKeyCode);
            bindsWere.clear();
        } else {
            if (hotbar < 0) return; // no offhand
            KeyBinding binding = mc().gameSettings.keyBindsHotbar[hotbar];
            bindsWere.putIfAbsent(binding, binding.getKeyCode());
            binding.setKeyCode(key.startsWith("mouse") ? Integer.parseInt(key.substring(5)) - 1 - 100 : Keyboard.getKeyIndex(key.toUpperCase(java.util.Locale.ROOT)));
        }
        KeyBinding.resetKeyBindingArrayAndHash();
    }

    @Override public boolean offhand() { return false; }

    @Override public void shot(String name) { CoreProbe.screenshot(mc(), name); }

    // ---- helpers ---------------------------------------------------------------------------------------------------

    private static Minecraft mc() { return Minecraft.getMinecraft(); }

    private static GuiContainer screen() {
        if (!(mc().currentScreen instanceof GuiContainer)) throw new IllegalStateException("no container screen open (" + mc().currentScreen + ")");
        return (GuiContainer) mc().currentScreen;
    }

    /** The pointer over a slot's centre: a WM_MOUSEMOVE in window pixels (y down), which LWJGL turns into Mouse.getX/getY. */
    private void pointer(Slot slot) {
        hovered = slot;
        GuiContainer gui = screen();
        int left = ReflectionHelper.getPrivateValue(GuiContainer.class, gui, "guiLeft", "field_147003_i");
        int top = ReflectionHelper.getPrivateValue(GuiContainer.class, gui, "guiTop", "field_147009_r");
        pointerX = left + slot.xDisplayPosition + 8;
        pointerY = top + slot.yDisplayPosition + 8;
        Minecraft mc = mc();
        int x = (pointerX * mc.displayWidth + mc.displayWidth / 2) / gui.width, y = (pointerY * mc.displayHeight + mc.displayHeight / 2) / gui.height;
        try { User32.INSTANCE.PostMessage(Borderless189.hwnd(), WM_MOUSEMOVE, new WPARAM(0), new LPARAM((long) y << 16 | x & 0xFFFF)); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    /** "chest" or "skull:3" (metadata 3). */
    private static ItemStack stack(String item) {
        String[] parts = item.split(":");
        Item it = Item.getByNameOrId("minecraft:" + parts[0]);
        if (it == null) throw new IllegalArgumentException("no item " + item);
        return new ItemStack(it, 1, parts.length > 1 ? Integer.parseInt(parts[1]) : 0);
    }

    private static String id(ItemStack stack) {
        return stack == null || stack.getItem() == null ? "" : Item.itemRegistry.getNameForObject(stack.getItem()).getResourcePath();
    }

    private void spawn(World world, Entity entity) {
        world.spawnEntityInWorld(entity);
        shown.add(entity);
    }

    private static void server(Consumer<EntityPlayerMP> task) {
        Minecraft mc = mc();
        java.util.UUID id = mc.thePlayer.getUniqueID();
        mc.getIntegratedServer().addScheduledTask(() -> task.accept(mc.getIntegratedServer().getConfigurationManager().getPlayerByUUID(id)));
    }
}
