package com.thelads.core.v26_2.feature;

import com.google.gson.JsonElement;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.modules.MouseTweaksModule;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-capture-mousetweaks" from the harness's LADS_VERIFY_CAPTURE_MOUSETWEAKS): Lads Mouse Tweaks in a real
 * chest the integrated server opens, through the chest screen's own mouseClicked, mouseDragged, mouseReleased and mouseScrolled
 * (MouseHandler sends nothing to an unfocused window). Right-drag, left-drag gathering, shift-drag and the wheel, each checked
 * slot by slot after the server answered, then every slot and the cursor compared with the server's own menu (no desync).
 * Then the wheel between hotbar and main inventory in the player's own inventory (the creative inventory tab for a creative
 * player), compared with the server the same way.
 * Saves mousetweaks-1-before, -2-after and -3-inventory; logs each state. Inventory, block and module are put back.
 */
final class MouseTweaksCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final int PLAYER = 27, HOTBAR = 54; // single chest: 27 chest slots, then main inventory, then hotbar
    private static final int OWN_HOTBAR = 36; // the player's inventory: result, crafting, armour, main (9-35), hotbar
    private static final List<String> FAILURES = new ArrayList<>();
    private static final Map<Option, JsonElement> OPTIONS = new LinkedHashMap<>();
    private static final List<ItemStack> INVENTORY = new ArrayList<>();
    private static int step = -1, wait, passed, tries;
    private static boolean enabledBefore, shooting;
    private static long modifiedBefore;
    private static String shot;
    private static volatile BlockPos chestPos;
    private static volatile BlockState blockBefore;
    private static volatile String serverState;
    private MouseTweaksCapture() {}

    static boolean busy() { return step >= 0 && step < 99; }

    /** Each client tick of the auto-world run: starts once the world is ready and the request exists, then one step per tick. */
    static void tick(Path game, boolean ready) {
        if (!busy()) {
            if (step >= 0 || !ready) return;
            Path request = game.resolve(".lads-qa-capture-mousetweaks");
            if (!Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
            try { Files.delete(request); } catch (Exception failure) { LOGGER.error("Lads mouse tweaks capture FAILED: request", failure); return; }
            step = 0;
        }
        if (shooting || wait-- > 0) return;
        try { run(Minecraft.getInstance()); }
        catch (Exception failure) { fail("step " + step + ": " + failure); finish(); }
    }

    /** Each completed game frame (NativeWorldVerification.renderedFrame): a requested screenshot. */
    static void frame(RenderTarget target, Path game) {
        if (shot == null || shooting) return;
        shooting = true;
        String name = shot;
        try {
            Path output = game.resolve("screenshots").resolve(name + ".png");
            Files.createDirectories(output.getParent());
            net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
                try { image.writeToFile(output); LOGGER.info("Lads mouse tweaks frame {}", output); }
                catch (Exception failure) { fail(name + ": " + failure); }
                finally { image.close(); Minecraft.getInstance().execute(() -> { shot = null; shooting = false; }); }
            });
        } catch (Exception failure) {
            fail(name + ": " + failure);
            shot = null;
            shooting = false;
        }
    }

    private static void run(Minecraft mc) throws Exception {
        AbstractContainerScreen<?> screen = mc.gui.screen() instanceof AbstractContainerScreen<?> s && s.getMenu() instanceof ChestMenu ? s : null;
        switch (step) {
            case 0 -> { start(mc); next(1); }
            case 1 -> {
                if (screen == null) { if (++tries > 100) throw new IllegalStateException("the chest screen did not open"); return; }
                state("before", screen);
                shot = "mousetweaks-1-before";
                next(10);
            }
            // Clicks on one slot stay 250 ms apart (a step), or the screen would take them for a double click.
            case 2 -> { // right-drag: 16 dirt picked up, then one into slots 5, 6, 7 and a second into 5
                click(screen, 0, InputConstants.MOUSE_BUTTON_LEFT, false);
                drag(screen, InputConstants.MOUSE_BUTTON_RIGHT, false, 5, 6, 7, 5);
                next(8);
            }
            case 3 -> { click(screen, 0, InputConstants.MOUSE_BUTTON_LEFT, false); next(8); } // vanilla puts the rest back
            case 4 -> {
                expect(screen, "right-drag places one item per slot entered, twice on a second visit", 0, "dirt", 12, 5, "dirt", 2, 6, "dirt", 1, 7, "dirt", 1);
                // left-drag gathering: 10 cobblestone picked up, then 20 and 30 gathered; the stone in between stays
                drag(screen, InputConstants.MOUSE_BUTTON_LEFT, false, 1, 2, 3, 4);
                next(8);
            }
            case 5 -> { click(screen, 1, InputConstants.MOUSE_BUTTON_LEFT, false); next(8); }
            case 6 -> {
                expect(screen, "left-drag gathers matching stacks onto the cursor", 1, "cobblestone", 60, 2, null, 0, 3, "stone", 5, 4, null, 0);
                // shift-drag from an empty slot over the three sand stacks
                drag(screen, InputConstants.MOUSE_BUTTON_LEFT, true, 12, 9, 10, 11);
                next(8);
            }
            case 7 -> {
                expect(screen, "shift-drag moves every stack entered", 9, null, 0, 10, null, 0, 11, null, 0);
                int sand = 0;
                for (int i = PLAYER; i < HOTBAR + 9; i++) if (screen.getMenu().getSlot(i).getItem().is(Items.SAND)) sand += screen.getMenu().getSlot(i).getItem().getCount();
                check(sand == 24, "the 24 sand reached the player's inventory (" + sand + ")");
                // the wheel over 12 planks in the hotbar: down sends one to the chest's planks, up pulls it back, then three down
                scroll(screen, HOTBAR, -1);
                next(6);
            }
            case 8 -> { expect(screen, "scrolling down sends one item to the matching chest stack", HOTBAR, "oak_planks", 11, 18, "oak_planks", 4); scroll(screen, HOTBAR, 1); next(6); }
            case 9 -> {
                expect(screen, "scrolling up pulls one item back", HOTBAR, "oak_planks", 12, 18, "oak_planks", 3);
                for (int i = 0; i < 3; i++) scroll(screen, HOTBAR, -1);
                next(8);
            }
            case 10 -> {
                expect(screen, "three notches send three items", HOTBAR, "oak_planks", 9, 18, "oak_planks", 6);
                state("after", screen);
                serverState = null;
                ServerPlayer player = player(mc);
                mc.getSingleplayerServer().execute(() -> serverState = describe(player.containerMenu));
                shot = "mousetweaks-2-after";
                next(10);
            }
            case 11 -> {
                if (serverState == null) return;
                matchesServer(screen, "the server's chest menu matches the client's slot for slot (no desync)");
                mc.player.closeContainer();
                // The wheel between hotbar and main inventory; a creative player gets the creative screen's inventory tab.
                mc.setScreenAndShow(new InventoryScreen(mc.player));
                next(10);
            }
            case 12 -> {
                if (mc.gui.screen() instanceof CreativeModeInventoryScreen creative && !creative.isInventoryOpen()) {
                    var select = CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab", CreativeModeTab.class);
                    select.setAccessible(true);
                    for (CreativeModeTab tab : CreativeModeTabs.allTabs()) if (tab.getType() == CreativeModeTab.Type.INVENTORY) select.invoke(creative, tab);
                }
                if (inventory(mc) == null) { if (++tries > 200) throw new IllegalStateException("the inventory did not open"); return; }
                LOGGER.info("Lads mouse tweaks: the wheel in {}", mc.gui.screen().getClass().getSimpleName());
                state("inventory before", inventory(mc));
                scroll(inventory(mc), OWN_HOTBAR, -1);
                next(6);
            }
            case 13 -> {
                expect(inventory(mc), "in the inventory, scrolling down on the hotbar sends one item to the main inventory", OWN_HOTBAR, "oak_planks", 8, 9, "oak_planks", 1);
                scroll(inventory(mc), 9, 1);
                next(6);
            }
            case 14 -> {
                expect(inventory(mc), "scrolling up on the main inventory pulls one back from the hotbar", OWN_HOTBAR, "oak_planks", 7, 9, "oak_planks", 2);
                serverState = null;
                ServerPlayer player = player(mc);
                mc.getSingleplayerServer().execute(() -> serverState = describe(player.containerMenu));
                shot = "mousetweaks-3-inventory";
                next(10);
            }
            case 15 -> {
                if (serverState == null) return;
                matchesServer(inventory(mc), "the server's inventory menu matches the client's slot for slot (no desync)");
                finish();
            }
            default -> { }
        }
    }

    /** The player's own inventory screen (the creative one on its inventory tab), else null. */
    private static AbstractContainerScreen<?> inventory(Minecraft mc) {
        if (mc.gui.screen() instanceof InventoryScreen screen) return screen;
        return mc.gui.screen() instanceof CreativeModeInventoryScreen creative && creative.isInventoryOpen() ? creative : null;
    }

    private static void matchesServer(AbstractContainerScreen<?> screen, String what) {
        String client = describe(screen.getMenu());
        check(client.equals(serverState), what);
        if (!client.equals(serverState)) LOGGER.error("Lads mouse tweaks server state: {}", serverState);
    }

    private static void start(Minecraft mc) {
        MouseTweaksModule module = (MouseTweaksModule) ModuleManager.getInstance().getModule(MouseTweaksModule.NAME);
        enabledBefore = module.isEnabled();
        modifiedBefore = module.getLastModified();
        for (Option option : module.getOptions()) OPTIONS.put(option, option.save().deepCopy());
        module.getOptions().forEach(Option::reset);
        module.setEnabled(true);
        LOGGER.info("Lads mouse tweaks capture BEGIN: a server chest through the screen's own mouse handlers; Mouse Tweaks mod {}",
            net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("mousetweaks") ? "LOADED (the native tweak stands down)" : "not loaded");
        ServerPlayer player = player(mc);
        mc.getSingleplayerServer().execute(() -> {
            try {
                var level = player.level();
                chestPos = player.blockPosition().above(2);
                blockBefore = level.getBlockState(chestPos);
                level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
                ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
                chest.setItem(0, new ItemStack(Items.DIRT, 16));
                chest.setItem(1, new ItemStack(Items.COBBLESTONE, 10));
                chest.setItem(2, new ItemStack(Items.COBBLESTONE, 20));
                chest.setItem(3, new ItemStack(Items.STONE, 5));
                chest.setItem(4, new ItemStack(Items.COBBLESTONE, 30));
                for (int i = 9; i < 12; i++) chest.setItem(i, new ItemStack(Items.SAND, 8));
                chest.setItem(18, new ItemStack(Items.OAK_PLANKS, 3));
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) INVENTORY.add(player.getInventory().getItem(i).copy());
                player.getInventory().clearContent();
                player.getInventory().setItem(0, new ItemStack(Items.OAK_PLANKS, 12));
                player.openMenu(chest);
            } catch (Exception failure) { fail("setup: " + failure); }
        });
    }

    private static void finish() {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.player != null && mc.gui.screen() instanceof AbstractContainerScreen<?>) mc.player.closeContainer();
            ServerPlayer player = player(mc);
            if (player != null && chestPos != null) mc.getSingleplayerServer().execute(() -> {
                var level = player.level();
                if (level.getBlockEntity(chestPos) instanceof Container chest) chest.clearContent();
                level.setBlock(chestPos, blockBefore, 3);
                player.getInventory().clearContent();
                for (int i = 0; i < INVENTORY.size(); i++) player.getInventory().setItem(i, INVENTORY.get(i));
            });
        } catch (Exception failure) { fail("restore: " + failure); }
        MouseTweaksModule module = (MouseTweaksModule) ModuleManager.getInstance().getModule(MouseTweaksModule.NAME);
        OPTIONS.forEach(Option::load);
        module.setEnabled(enabledBefore);
        module.setLastModified(modifiedBefore);
        step = 99;
        if (FAILURES.isEmpty()) LOGGER.info("Lads mouse tweaks capture END: {} passed, 0 failed; right-drag, left-drag, shift-drag and wheel in a server chest", passed);
        else LOGGER.error("Lads mouse tweaks capture FAILED: {} passed; {}", passed, String.join("; ", FAILURES));
    }

    private static void next(int ticks) { step++; wait = ticks; }

    private static ServerPlayer player(Minecraft mc) {
        return mc.player == null || mc.getSingleplayerServer() == null ? null : mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
    }

    /** Screen position of a menu slot's centre: the screen's own leftPos and topPos. */
    private static double[] at(AbstractContainerScreen<?> screen, int slot) throws Exception {
        var left = AbstractContainerScreen.class.getDeclaredField("leftPos");
        var top = AbstractContainerScreen.class.getDeclaredField("topPos");
        left.setAccessible(true);
        top.setAccessible(true);
        var s = screen.getMenu().getSlot(slot);
        return new double[] {left.getInt(screen) + s.x + 8, top.getInt(screen) + s.y + 8};
    }

    private static MouseButtonEvent event(double[] at, int button, boolean shift) {
        return new MouseButtonEvent(at[0], at[1], new MouseButtonInfo(button, shift ? InputConstants.MOD_SHIFT : 0));
    }

    private static void click(AbstractContainerScreen<?> screen, int slot, int button, boolean shift) throws Exception {
        double[] at = at(screen, slot);
        screen.mouseClicked(event(at, button, shift), false);
        screen.mouseReleased(event(at, button, shift));
    }

    /** Press on the first slot, move through the rest (MouseHandler sends one drag per moved frame), release on the last. */
    private static void drag(AbstractContainerScreen<?> screen, int button, boolean shift, int... slots) throws Exception {
        double[] at = at(screen, slots[0]);
        screen.mouseClicked(event(at, button, shift), false);
        for (int i = 1; i < slots.length; i++) {
            double[] to = at(screen, slots[i]);
            screen.mouseDragged(event(to, button, shift), to[0] - at[0], to[1] - at[1]);
            at = to;
        }
        screen.mouseReleased(event(at, button, shift));
    }

    private static void scroll(AbstractContainerScreen<?> screen, int slot, double notches) throws Exception {
        double[] at = at(screen, slot);
        check(screen.mouseScrolled(at[0], at[1], 0, notches), "the wheel over slot " + slot + " is taken by the tweak");
    }

    /** Slot, item path, count triples. */
    private static void expect(AbstractContainerScreen<?> screen, String what, Object... triples) {
        List<String> wrong = new ArrayList<>();
        for (int i = 0; i < triples.length; i += 3) {
            ItemStack stack = screen.getMenu().getSlot((Integer) triples[i]).getItem();
            String want = triples[i + 1] == null ? "empty" : triples[i + 2] + " " + triples[i + 1];
            if (!want.equals(name(stack))) wrong.add("slot " + triples[i] + " has " + name(stack) + ", not " + want);
        }
        if (!screen.getMenu().getCarried().isEmpty()) wrong.add("cursor holds " + name(screen.getMenu().getCarried()));
        check(wrong.isEmpty(), what + (wrong.isEmpty() ? "" : " " + wrong));
        state(what, screen);
    }

    private static void state(String label, AbstractContainerScreen<?> screen) {
        LOGGER.info("Lads mouse tweaks state ({}): {}", label, describe(screen.getMenu()));
    }

    private static String describe(AbstractContainerMenu menu) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < menu.slots.size(); i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty()) out.append(!(menu instanceof ChestMenu) ? "slot " : i < PLAYER ? "chest " : i < HOTBAR ? "main " : "hotbar ")
                .append(i).append('=').append(name(stack)).append(", ");
        }
        return out.append("cursor=").append(name(menu.getCarried())).toString();
    }

    private static String name(ItemStack stack) {
        return stack.isEmpty() ? "empty" : stack.getCount() + " " + BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }

    private static void check(boolean ok, String what) {
        if (ok) { passed++; LOGGER.info("Lads mouse tweaks PASS {}: {}", passed, what); }
        else fail(what);
    }

    private static void fail(String what) {
        FAILURES.add(what);
        LOGGER.error("Lads mouse tweaks check failed: {}", what);
    }
}
