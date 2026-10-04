package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.screenshot;

import com.google.gson.JsonElement;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.config.Option;
import com.thelads.core.modules.MouseTweaksModule;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * QA only: Lads Mouse Tweaks (MouseTweaks189), run by CoreProbe in its QA world. A chest the integrated server opens gets real
 * mouse events through LWJGL's queue (GuiScreen.handleInput, GuiContainer's handlers and Forge's mouse input event): right-drag,
 * left-drag gathering, shift-drag and the wheel, each checked slot by slot after the server answered; then every slot and the
 * cursor must match the server's own container (no desync). Shift for the tweak's drags comes from MouseTweaks189.qaShift, as
 * a synthetic key never reaches Keyboard.isKeyDown. Inventory, block and module are put back.
 * Screenshots: 170-mousetweaks-1-before, 170-mousetweaks-2-after.
 */
final class MouseTweaksProbe189 {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    private static final int PLAYER = 27, HOTBAR = 54; // single chest: 27 chest slots, then main inventory, then hotbar
    static final List<CoreProbe.Step> STEPS = Arrays.<CoreProbe.Step>asList(MouseTweaksProbe189::start, MouseTweaksProbe189::opened,
        MouseTweaksProbe189::rightDrag, MouseTweaksProbe189::putBack, MouseTweaksProbe189::leftDrag, MouseTweaksProbe189::putDown,
        MouseTweaksProbe189::shiftDrag, MouseTweaksProbe189::wheelDown, MouseTweaksProbe189::wheelUp, MouseTweaksProbe189::wheelThree,
        MouseTweaksProbe189::compared, MouseTweaksProbe189::stop);
    private static final Map<Option, JsonElement> optionsWere = new LinkedHashMap<>();
    private static ItemStack[] mainWas, armorWas;
    private static boolean started, wasEnabled, asked;
    private static long modifiedWas;
    private static volatile BlockPos chestPos;
    private static volatile IBlockState blockWas;
    private static volatile String serverState;

    private MouseTweaksProbe189() {}

    private static boolean start(Minecraft mc) {
        MouseTweaksModule module = module();
        check(ModuleSupport.isBuiltIn(MouseTweaksModule.NAME), "Mouse Tweaks is built in on 1.8.9");
        wasEnabled = module.isEnabled();
        modifiedWas = module.getLastModified();
        for (Option option : module.getOptions()) optionsWere.put(option, option.save());
        module.getOptions().forEach(Option::reset);
        module.setEnabled(true);
        started = true;
        EntityPlayerMP player = player(mc);
        mc.getIntegratedServer().addScheduledTask(() -> {
            World world = player.worldObj;
            BlockPos pos = new BlockPos(player).up(2);
            blockWas = world.getBlockState(pos);
            world.setBlockState(pos, Blocks.chest.getDefaultState(), 3);
            chestPos = pos;
            TileEntityChest chest = (TileEntityChest) world.getTileEntity(pos);
            chest.setInventorySlotContents(0, new ItemStack(Blocks.dirt, 16));
            chest.setInventorySlotContents(1, new ItemStack(Blocks.cobblestone, 10));
            chest.setInventorySlotContents(2, new ItemStack(Blocks.cobblestone, 20));
            chest.setInventorySlotContents(3, new ItemStack(Blocks.stone, 5));
            chest.setInventorySlotContents(4, new ItemStack(Blocks.cobblestone, 30));
            for (int i = 9; i < 12; i++) chest.setInventorySlotContents(i, new ItemStack(Blocks.sand, 8));
            chest.setInventorySlotContents(18, new ItemStack(Blocks.planks, 3));
            mainWas = copy(player.inventory.mainInventory);
            armorWas = copy(player.inventory.armorInventory);
            Arrays.fill(player.inventory.mainInventory, null);
            player.inventory.mainInventory[0] = new ItemStack(Blocks.planks, 12);
            player.displayGUIChest(chest);
        });
        return after(20);
    }

    private static boolean opened(Minecraft mc) {
        if (!(mc.currentScreen instanceof GuiChest)) return false;
        state("before", mc);
        screenshot(mc, "170-mousetweaks-1-before");
        return after(10);
    }

    // Clicks on one slot stay 250 ms apart (a step), or GuiContainer would take them for a double click.
    private static boolean rightDrag(Minecraft mc) throws Exception {
        click(mc, 0); // 16 dirt picked up
        drag(mc, 1, 5, 6, 7, 5); // one into 5, 6 and 7, a second into 5
        return after(10);
    }

    private static boolean putBack(Minecraft mc) throws Exception {
        click(mc, 0);
        return after(10);
    }

    private static boolean leftDrag(Minecraft mc) throws Exception {
        expect(mc, "right-drag places one item per slot entered, twice on a second visit", 0, "dirt", 12, 5, "dirt", 2, 6, "dirt", 1, 7, "dirt", 1);
        drag(mc, 0, 1, 2, 3, 4); // 10 cobblestone picked up, 20 and 30 gathered, the stone in between left
        return after(10);
    }

    private static boolean putDown(Minecraft mc) throws Exception {
        click(mc, 1);
        return after(10);
    }

    private static boolean shiftDrag(Minecraft mc) throws Exception {
        expect(mc, "left-drag gathers matching stacks onto the cursor", 1, "cobblestone", 60, 2, null, 0, 3, "stone", 5, 4, null, 0);
        MouseTweaks189.qaShift = true;
        drag(mc, 0, 12, 9, 10, 11); // from an empty slot over the three sand stacks
        return after(10);
    }

    private static boolean wheelDown(Minecraft mc) throws Exception {
        MouseTweaks189.qaShift = false;
        expect(mc, "shift-drag moves every stack entered", 9, null, 0, 10, null, 0, 11, null, 0);
        int sand = 0;
        Container menu = ((GuiContainer) mc.currentScreen).inventorySlots;
        for (int i = PLAYER; i < HOTBAR + 9; i++) {
            ItemStack stack = menu.getSlot(i).getStack();
            if (stack != null && stack.getItem() == Item.getItemFromBlock(Blocks.sand)) sand += stack.stackSize;
        }
        check(sand == 24, "the 24 sand reached the player's inventory (" + sand + ")");
        wheel(mc, HOTBAR, -1); // the planks in the hotbar: one to the chest's planks
        return after(6);
    }

    private static boolean wheelUp(Minecraft mc) throws Exception {
        expect(mc, "scrolling down sends one item to the matching chest stack", HOTBAR, "planks", 11, 18, "planks", 4);
        wheel(mc, HOTBAR, 1);
        return after(6);
    }

    private static boolean wheelThree(Minecraft mc) throws Exception {
        expect(mc, "scrolling up pulls one item back", HOTBAR, "planks", 12, 18, "planks", 3);
        wheel(mc, HOTBAR, -3);
        return after(8);
    }

    private static boolean compared(Minecraft mc) {
        if (!asked) {
            asked = true;
            expect(mc, "three notches send three items", HOTBAR, "planks", 9, 18, "planks", 6);
            state("after", mc);
            screenshot(mc, "170-mousetweaks-2-after");
            EntityPlayerMP player = player(mc);
            mc.getIntegratedServer().addScheduledTask(() -> { serverState = describe(player.openContainer, player.inventory.getItemStack()); });
            return CoreProbe.retry(10);
        }
        if (serverState == null) return CoreProbe.retry(5);
        String client = describe(((GuiContainer) mc.currentScreen).inventorySlots, mc.thePlayer.inventory.getItemStack());
        if (!client.equals(serverState)) LOG.error("Lads mouse tweaks server state: {}", serverState);
        check(client.equals(serverState), "the server's chest container matches the client's slot for slot (no desync)");
        return after(2);
    }

    /** Also CoreProbe.finish: puts the inventory, block and module back whether or not the checks passed. */
    static boolean stop(Minecraft mc) {
        MouseTweaks189.qaShift = false;
        if (!started) return true;
        started = false;
        if (mc.thePlayer != null && mc.currentScreen instanceof GuiContainer) mc.thePlayer.closeScreen();
        EntityPlayerMP player = player(mc);
        if (player != null) mc.getIntegratedServer().addScheduledTask(() -> {
            if (chestPos != null) {
                if (player.worldObj.getTileEntity(chestPos) instanceof TileEntityChest) ((TileEntityChest) player.worldObj.getTileEntity(chestPos)).clear();
                player.worldObj.setBlockState(chestPos, blockWas, 3);
            }
            if (mainWas != null) System.arraycopy(mainWas, 0, player.inventory.mainInventory, 0, mainWas.length);
            if (armorWas != null) System.arraycopy(armorWas, 0, player.inventory.armorInventory, 0, armorWas.length);
        });
        MouseTweaksModule module = module();
        optionsWere.forEach(Option::load);
        module.setEnabled(wasEnabled);
        module.setLastModified(modifiedWas);
        return after(10);
    }

    private static MouseTweaksModule module() {
        return (MouseTweaksModule) ModuleManager.getInstance().getModule(MouseTweaksModule.NAME);
    }

    private static EntityPlayerMP player(Minecraft mc) {
        return mc.thePlayer == null || mc.getIntegratedServer() == null ? null
            : mc.getIntegratedServer().getConfigurationManager().getPlayerByUUID(mc.thePlayer.getUniqueID());
    }

    private static ItemStack[] copy(ItemStack[] stacks) {
        ItemStack[] out = new ItemStack[stacks.length];
        for (int i = 0; i < stacks.length; i++) out[i] = ItemStack.copyItemStack(stacks[i]);
        return out;
    }

    /** GUI position of a slot's centre in the open container screen. */
    private static int[] at(Minecraft mc, int slot) {
        GuiContainer gui = (GuiContainer) mc.currentScreen;
        int left = ReflectionHelper.getPrivateValue(GuiContainer.class, gui, "guiLeft", "field_147003_i");
        int top = ReflectionHelper.getPrivateValue(GuiContainer.class, gui, "guiTop", "field_147009_r");
        Slot s = gui.inventorySlots.getSlot(slot);
        return new int[] {left + s.xDisplayPosition + 8, top + s.yDisplayPosition + 8};
    }

    private static void click(Minecraft mc, int slot) throws Exception {
        int[] at = at(mc, slot);
        CoreProbe.mouse(0, true, at[0], at[1]);
        CoreProbe.mouse(0, false, at[0], at[1]);
    }

    /** Press on the first slot, move over the rest (GuiScreen turns each into mouseClickMove), release on the last. */
    private static void drag(Minecraft mc, int button, int... slots) throws Exception {
        int[] at = at(mc, slots[0]);
        CoreProbe.mouse(button, true, at[0], at[1]);
        for (int i = 1; i < slots.length; i++) {
            at = at(mc, slots[i]);
            CoreProbe.mouse(-1, false, at[0], at[1]);
        }
        CoreProbe.mouse(button, false, at[0], at[1]);
    }

    private static void wheel(Minecraft mc, int slot, int notches) throws Exception {
        int[] at = at(mc, slot);
        CoreProbe.wheel(at[0], at[1], notches);
    }

    /** Slot, item path, count triples; the cursor must be empty. */
    private static void expect(Minecraft mc, String what, Object... triples) {
        Container menu = ((GuiContainer) mc.currentScreen).inventorySlots;
        List<String> wrong = new ArrayList<>();
        for (int i = 0; i < triples.length; i += 3) {
            ItemStack stack = menu.getSlot((Integer) triples[i]).getStack();
            String want = triples[i + 1] == null ? "empty" : triples[i + 2] + " " + triples[i + 1];
            if (!want.equals(name(stack))) wrong.add("slot " + triples[i] + " has " + name(stack) + ", not " + want);
        }
        if (mc.thePlayer.inventory.getItemStack() != null) wrong.add("cursor holds " + name(mc.thePlayer.inventory.getItemStack()));
        state(what, mc);
        check(wrong.isEmpty(), "Mouse Tweaks: " + what + (wrong.isEmpty() ? "" : " " + wrong));
    }

    private static void state(String label, Minecraft mc) {
        LOG.info("Lads mouse tweaks state ({}): {}", label,
            describe(((GuiContainer) mc.currentScreen).inventorySlots, mc.thePlayer.inventory.getItemStack()));
    }

    private static String describe(Container menu, ItemStack cursor) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < menu.inventorySlots.size(); i++) {
            ItemStack stack = menu.getSlot(i).getStack();
            if (stack != null) out.append(i < PLAYER ? "chest " : i < HOTBAR ? "main " : "hotbar ").append(i).append('=').append(name(stack)).append(", ");
        }
        return out.append("cursor=").append(name(cursor)).toString();
    }

    private static String name(ItemStack stack) {
        return stack == null || stack.stackSize <= 0 ? "empty" : stack.stackSize + " " + Item.itemRegistry.getNameForObject(stack.getItem()).getResourcePath();
    }
}
