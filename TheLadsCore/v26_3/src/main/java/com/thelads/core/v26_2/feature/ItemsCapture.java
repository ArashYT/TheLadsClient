package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.client.ItemsQa;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-capture-items" from the harness's LADS_VERIFY_CAPTURE_ITEMS): the 1.9.1 items sequence (common
 * ItemsQa) in the QA world. The pointer is MouseHandler's own position (never the OS cursor); buttons and keys go through
 * MouseHandler.onButton and KeyboardHandler.keyPress. Frames: screenshots/items-*.png.
 */
final class ItemsCapture implements ItemsQa.Driver {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final ItemsCapture DRIVER = new ItemsCapture();
    private static boolean started, done;
    private static String pending;
    private final Map<KeyMapping, String> bindsWere = new LinkedHashMap<>();
    private final List<Entity> shown = new ArrayList<>();
    private final Map<BlockPos, BlockState> blocksWere = new LinkedHashMap<>();
    private net.minecraft.world.Difficulty difficultyWas;

    private ItemsCapture() {}

    static boolean busy() { return started && !done; }

    /** Each client tick of the auto-world run: starts once the world is ready and the request exists, then steps the sequence. */
    static void tick(Path gameDirectory, boolean ready) {
        if (done) return;
        if (!started) {
            if (!ready) return;
            Path request = gameDirectory.resolve(".lads-qa-capture-items");
            if (!Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
            try { Files.delete(request); } catch (Exception failure) { LOGGER.error("Lads items check FAIL: request", failure); done = true; return; }
            started = true;
        }
        done = ItemsQa.tick(DRIVER);
    }

    /** Each completed game frame (NativeWorldVerification.renderedFrame): writes a requested shot. */
    static void frame(RenderTarget target, Path gameDirectory) {
        if (pending == null) return;
        String name = pending;
        pending = null;
        try {
            Path folder = gameDirectory.resolve("screenshots");
            Files.createDirectories(folder);
            if (!folder.toRealPath().startsWith(gameDirectory)) throw new java.io.IOException("QA screenshot folder is outside the isolated game directory");
            Path output = folder.resolve(name + ".png");
            net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
                try { image.writeToFile(output); LOGGER.info("Lads items saved {}", output); }
                catch (Exception failure) { LOGGER.error("Lads items frame {} not saved", name, failure); }
                finally { image.close(); }
            });
        } catch (Exception failure) {
            LOGGER.error("Lads items frame {} not saved", name, failure);
        }
    }

    @Override public List<String> specialItems() {
        return List.of("chest", "trapped_chest", "ender_chest", "copper_chest", "shulker_box", "red_shulker_box", "red_bed", "white_banner",
            "shield", "skeleton_skull", "wither_skeleton_skull", "zombie_head", "player_head", "creeper_head", "dragon_head", "piglin_head",
            "conduit", "decorated_pot", "trident", "bell", "copper_golem_statue", "oak_sign", "oak_hanging_sign");
    }

    @Override public void creative(boolean creative) { server(p -> p.setGameMode(creative ? GameType.CREATIVE : GameType.SURVIVAL)); }

    @Override public void prepare(boolean on) {
        if (on) {
            mc().player.setYRot(180F);
            mc().player.setXRot(0F);
        }
        server(p -> {
            if (on) p.level().getServer().getCommands().performPrefixedCommand(p.level().getServer().createCommandSourceStack(), "time set 6000");
            var server = mc().getSingleplayerServer();
            if (on && difficultyWas == null) difficultyWas = server.overworld().getDifficulty();
            if (on || difficultyWas != null) server.setDifficulty(on ? net.minecraft.world.Difficulty.PEACEFUL : difficultyWas, true);
            if (!on) difficultyWas = null;
        });
    }

    @Override public void effects(boolean on) {
        server(p -> {
            p.removeAllEffects();
            if (on) {
                p.addEffect(new MobEffectInstance(MobEffects.SPEED, 6000));
                p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 6000));
            }
        });
    }

    @Override public void clear() {
        mc().player.containerMenu.setCarried(ItemStack.EMPTY);
        server(p -> {
            p.getInventory().clearContent();
            p.containerMenu.setCarried(ItemStack.EMPTY);
        });
    }

    @Override public void give(int slot, String item) {
        ItemStack stack = item == null ? ItemStack.EMPTY : stack(item);
        server(p -> p.getInventory().setItem(slot, stack));
    }

    @Override public void giveTall(int slot, int lines, int length) {
        ItemStack stack = new ItemStack(Items.STONE);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("QA tooltip"));
        List<Component> lore = new ArrayList<>();
        for (int i = 0; i < lines; i++) lore.add(Component.literal(ItemsQa.loreLine(i, length)));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        server(p -> p.getInventory().setItem(slot, stack));
    }

    @Override public String item(int slot) { return id(mc().player.getInventory().getItem(slot)); }

    @Override public void select(int hotbar) { mc().player.getInventory().setSelectedSlot(hotbar); }

    @Override public void view(int view) { mc().options.setCameraType(view == 0 ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_FRONT); }

    @Override public void showcase(List<String> items) {
        server(p -> {
            ServerLevel level = p.level();
            shown.forEach(Entity::discard);
            shown.clear();
            blocksWere.forEach((pos, state) -> level.setBlock(pos, state, 3));
            blocksWere.clear();
            if (items == null) return;
            // Facing north: a stone wall 6 blocks ahead with rows of item frames, armour stands 3 ahead, the items dropped 2 ahead.
            BlockPos feet = p.blockPosition();
            p.connection.teleport(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 180F, 15F);
            for (int x = -7; x <= 7; x++)
                for (int y = 0; y < 5; y++) block(level, feet.offset(x, y, -6), Blocks.STONE.defaultBlockState());
            for (int i = 0; i < items.size(); i++) {
                int x = i % 12 - 6, row = i / 12;
                ItemFrame frame = new ItemFrame(level, feet.offset(x, 3 - row, -5), Direction.SOUTH);
                frame.setItem(stack(items.get(i)), false);
                spawn(level, frame);
                ItemEntity dropped = new ItemEntity(level, feet.getX() + x + 0.5 + row * 0.5, feet.getY() + 0.1, feet.getZ() - 1.5 - row, stack(items.get(i)));
                dropped.setNeverPickUp();
                dropped.setUnlimitedLifetime();
                dropped.setDeltaMovement(Vec3.ZERO);
                spawn(level, dropped);
                if (i < 6) {
                    ArmorStand stand = new ArmorStand(level, feet.getX() + i * 2 - 5 + 0.5, feet.getY(), feet.getZ() - 3.5);
                    stand.setShowArms(true);
                    stand.setItemSlot(EquipmentSlot.MAINHAND, stack(items.get(i)));
                    stand.setItemSlot(EquipmentSlot.HEAD, stack(items.get(i + 1)));
                    spawn(level, stand);
                }
            }
        });
    }

    @Override public void openInventory() { mc().setScreenAndShow(new InventoryScreen(mc().player)); }

    @Override public void openChest(String item) {
        server(p -> {
            BlockPos pos = p.blockPosition().above(2);
            block(p.level(), pos, Blocks.CHEST.defaultBlockState());
            if (!(p.level().getBlockEntity(pos) instanceof ChestBlockEntity chest)) throw new IllegalStateException("no chest at " + pos);
            chest.setItem(0, stack(item));
            p.openMenu(chest);
        });
    }

    @Override public void closeScreen() {
        if (mc().gui.screen() instanceof AbstractContainerScreen<?>) mc().player.closeContainer();
        if (mc().gui.screen() != null) mc().setScreenAndShow(null);
    }

    @Override public void hoverInventory(int index) {
        for (Slot slot : screen().getMenu().slots)
            if (slot.container == mc().player.getInventory() && slot.getContainerSlot() == index) { pointer(slot); return; }
        throw new IllegalStateException("no slot for inventory slot " + index + " on " + mc().gui.screen());
    }

    @Override public String hoverMenu(int index) {
        Slot slot = screen().getMenu().slots.get(index);
        pointer(slot);
        return id(slot.getItem());
    }

    @Override public void mouse(int button) { // 26.3 numbers buttons from 1 (InputConstants.MOUSE_BUTTON_4 = 4)
        long window = mc().getWindow().handle();
        mc().mouseHandler.onButton(window, new MouseButtonInfo(button, 0), InputConstants.PRESS);
        mc().mouseHandler.onButton(window, new MouseButtonInfo(button, 0), InputConstants.RELEASE);
    }

    @Override public void key(String key) {
        int code = InputConstants.getKey("key.keyboard." + key).getValue();
        long window = mc().getWindow().handle();
        mc().keyboardHandler.keyPress(window, InputConstants.PRESS, new KeyEvent(code, 0, 0));
        mc().keyboardHandler.keyPress(window, InputConstants.RELEASE, new KeyEvent(code, 0, 0));
    }

    @Override public void bind(int hotbar, String key) {
        if (key == null) {
            bindsWere.forEach((mapping, was) -> mapping.setKey(InputConstants.getKey(was)));
            bindsWere.clear();
        } else {
            KeyMapping mapping = hotbar < 0 ? mc().options.keySwapOffhand : mc().options.keyHotbarSlots[hotbar];
            bindsWere.putIfAbsent(mapping, mapping.saveString());
            mapping.setKey(InputConstants.getKey(key.startsWith("mouse") ? "key.mouse." + key.substring(5) : "key.keyboard." + key));
        }
        KeyMapping.resetMapping();
    }

    @Override public boolean offhand() { return true; }

    @Override public void shot(String name) { pending = name; }

    // ---- helpers ---------------------------------------------------------------------------------------------------

    private static Minecraft mc() { return Minecraft.getInstance(); }

    private static AbstractContainerScreen<?> screen() {
        if (!(mc().gui.screen() instanceof AbstractContainerScreen<?> screen)) throw new IllegalStateException("no container screen open (" + mc().gui.screen() + ")");
        return screen;
    }

    /** The pointer a real mouse would be at, over a slot's centre (MouseHandler's own position, never the OS cursor). */
    private static void pointer(Slot slot) {
        try {
            AbstractContainerScreen<?> screen = screen();
            var left = AbstractContainerScreen.class.getDeclaredField("leftPos");
            var top = AbstractContainerScreen.class.getDeclaredField("topPos");
            var x = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            var y = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            for (var field : List.of(left, top, x, y)) field.setAccessible(true);
            var window = mc().getWindow();
            x.setDouble(mc().mouseHandler, (left.getInt(screen) + slot.x + 8) * (double) window.getScreenWidth() / window.getGuiScaledWidth());
            y.setDouble(mc().mouseHandler, (top.getInt(screen) + slot.y + 8) * (double) window.getScreenHeight() / window.getGuiScaledHeight());
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    private static ItemStack stack(String item) {
        var it = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(item));
        if (it == Items.AIR) throw new IllegalArgumentException("no item " + item);
        return new ItemStack(it);
    }

    private static String id(ItemStack stack) { return stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(); }

    private void block(ServerLevel level, BlockPos pos, BlockState state) {
        blocksWere.putIfAbsent(pos, level.getBlockState(pos));
        level.setBlock(pos, state, 3);
    }

    private void spawn(ServerLevel level, Entity entity) {
        level.addFreshEntity(entity);
        shown.add(entity);
    }

    private static void server(Consumer<ServerPlayer> task) {
        var server = mc().getSingleplayerServer();
        var id = mc().player.getUUID();
        server.execute(() -> task.accept(server.getPlayerList().getPlayer(id)));
    }
}
