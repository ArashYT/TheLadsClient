package com.thelads.core.v1_20_1.feature;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.client.ItemsQa;
import com.thelads.core.v1_20_1.game.misc.mixin.ContainerAccess1201;
import com.thelads.core.v1_20_1.game.misc.mixin.MouseAccess1201;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * QA script step "items" (plain Fabric and Zero SMP): the 1.9.1 items sequence (common ItemsQa) on 1.20.1. The pointer is
 * MouseHandler's own position (never the OS cursor); buttons and keys go through MouseHandler.onPress and KeyboardHandler.keyPress.
 * Frames: screenshots/items-*.png.
 */
final class ItemsQa1201 implements ItemsQa.Driver {
    private static final ItemsQa1201 DRIVER = new ItemsQa1201();
    private final Map<KeyMapping, String> bindsWere = new LinkedHashMap<>();
    private final List<Entity> shown = new ArrayList<>();
    private final Map<BlockPos, BlockState> blocksWere = new LinkedHashMap<>();
    private net.minecraft.world.Difficulty difficultyWas;

    private ItemsQa1201() {}

    static void register() {
        if (Boolean.getBoolean("thelads.verifyAutoWorld") || System.getProperty("thelads.qaScript") != null)
            QaProbe1201.registerStep("items", args -> {
                if (!ItemsQa.tick(DRIVER)) return false;
                if (ItemsQa.failed() > 0) throw new IllegalStateException(ItemsQa.failed() + " items checks failed (Lads items check FAIL)");
                return true;
            });
    }

    @Override public List<String> specialItems() {
        return List.of("chest", "trapped_chest", "ender_chest", "shulker_box", "red_shulker_box", "red_bed", "white_banner", "shield",
            "skeleton_skull", "wither_skeleton_skull", "zombie_head", "player_head", "creeper_head", "dragon_head", "piglin_head", "conduit",
            "decorated_pot", "trident", "bell", "oak_sign", "oak_hanging_sign");
    }

    @Override public void creative(boolean creative) { server(p -> p.setGameMode(creative ? GameType.CREATIVE : GameType.SURVIVAL)); }

    @Override public void prepare(boolean on) {
        if (on) {
            mc().player.setYRot(180F);
            mc().player.setXRot(0F);
        }
        server(p -> {
            if (on) p.serverLevel().setDayTime(6000);
            if (on && difficultyWas == null) difficultyWas = p.server.overworld().getDifficulty();
            if (on || difficultyWas != null) p.server.setDifficulty(on ? net.minecraft.world.Difficulty.PEACEFUL : difficultyWas, true);
            if (!on) difficultyWas = null;
        });
    }

    @Override public void effects(boolean on) {
        server(p -> {
            p.removeAllEffects();
            if (on) {
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 6000));
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
        stack.setHoverName(Component.literal("QA tooltip"));
        ListTag lore = new ListTag();
        for (int i = 0; i < lines; i++) lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(ItemsQa.loreLine(i, length)))));
        stack.getOrCreateTagElement("display").put("Lore", lore);
        server(p -> p.getInventory().setItem(slot, stack));
    }

    @Override public String item(int slot) { return id(mc().player.getInventory().getItem(slot)); }

    @Override public void select(int hotbar) { mc().player.getInventory().selected = hotbar; }

    @Override public void view(int view) { mc().options.setCameraType(view == 0 ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_FRONT); }

    @Override public void showcase(List<String> items) {
        server(p -> {
            ServerLevel level = p.serverLevel();
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
                int x = i % 11 - 5, row = i / 11;
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
                    CompoundTag tag = stand.saveWithoutId(new CompoundTag());
                    tag.putBoolean("ShowArms", true);
                    stand.load(tag);
                    stand.setItemSlot(EquipmentSlot.MAINHAND, stack(items.get(i)));
                    stand.setItemSlot(EquipmentSlot.HEAD, stack(items.get(i + 1)));
                    spawn(level, stand);
                }
            }
        });
    }

    @Override public void openInventory() { mc().setScreen(new InventoryScreen(mc().player)); }

    @Override public void openChest(String item) {
        server(p -> {
            BlockPos pos = p.blockPosition().above(2);
            block(p.serverLevel(), pos, Blocks.CHEST.defaultBlockState());
            if (!(p.serverLevel().getBlockEntity(pos) instanceof ChestBlockEntity chest)) throw new IllegalStateException("no chest at " + pos);
            chest.setItem(0, stack(item));
            p.openMenu(chest);
        });
    }

    @Override public void closeScreen() {
        if (mc().screen instanceof AbstractContainerScreen<?>) mc().player.closeContainer();
        mc().setScreen(null);
    }

    @Override public void hoverInventory(int index) {
        for (Slot slot : screen().getMenu().slots)
            if (slot.container == mc().player.getInventory() && slot.getContainerSlot() == index) { pointer(slot); return; }
        throw new IllegalStateException("no slot for inventory slot " + index + " on " + mc().screen);
    }

    @Override public String hoverMenu(int index) {
        Slot slot = screen().getMenu().slots.get(index);
        pointer(slot);
        return id(slot.getItem());
    }

    @Override public void mouse(int button) {
        long window = mc().getWindow().getWindow();
        ((MouseAccess1201) mc().mouseHandler).lads$onPress(window, button - 1, GLFW.GLFW_PRESS, 0);
        ((MouseAccess1201) mc().mouseHandler).lads$onPress(window, button - 1, GLFW.GLFW_RELEASE, 0);
    }

    @Override public void key(String key) {
        int code = InputConstants.getKey("key.keyboard." + key).getValue();
        long window = mc().getWindow().getWindow();
        mc().keyboardHandler.keyPress(window, code, 0, GLFW.GLFW_PRESS, 0);
        mc().keyboardHandler.keyPress(window, code, 0, GLFW.GLFW_RELEASE, 0);
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

    @Override public void shot(String name) {
        try { QaProbe1201.shot(mc(), name); }
        catch (java.io.IOException failure) { throw new IllegalStateException("shot " + name, failure); }
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------

    private static Minecraft mc() { return Minecraft.getInstance(); }

    private static AbstractContainerScreen<?> screen() {
        if (!(mc().screen instanceof AbstractContainerScreen<?> screen)) throw new IllegalStateException("no container screen open (" + mc().screen + ")");
        return screen;
    }

    /** The pointer a real mouse would be at, over a slot's centre (MouseHandler's position, never the OS cursor). */
    private static void pointer(Slot slot) {
        var access = (ContainerAccess1201) screen();
        var window = mc().getWindow();
        var mouse = (MouseAccess1201) mc().mouseHandler;
        mouse.lads$xpos((access.lads$leftPos() + slot.x + 8) * (double) window.getScreenWidth() / window.getGuiScaledWidth());
        mouse.lads$ypos((access.lads$topPos() + slot.y + 8) * (double) window.getScreenHeight() / window.getGuiScaledHeight());
    }

    private static ItemStack stack(String item) {
        var it = BuiltInRegistries.ITEM.get(new ResourceLocation("minecraft", item));
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
