package com.thelads.core.v1_20_1.hud.chat;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.client.ClientTools;
import com.thelads.core.config.ActionOption;
import com.thelads.core.v1_20_1.feature.NativeKeyBindings1201;
import com.thelads.core.v1_20_1.hud.Hud1201;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

/**
 * ClientTools on 1.20.1 (the 26.3 NativeClientTools): coordinate copying, the low durability and inventory full warnings, the
 * Stopwatch keys and actions, and what the ItemCounter and ReachDisplay HUD elements show. Client observations only: it never
 * sends chat, inventory edits or interaction packets. The owner wires {@code LadsGameBridge.getItemCountText} to
 * {@link #itemCount} and {@code getRecentReachText} to {@link #reachText}.
 */
public final class NativeClientTools1201 {
    private static final ClientTools.Warning durability = new ClientTools.Warning(), full = new ClientTools.Warning();
    private static final EquipmentSlot[] GEAR = { EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
        EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static LocalPlayer player;
    private static Object level;
    private static KeyMapping copy, timer, reset;
    private static int countTick = Integer.MIN_VALUE, countSelection = -1;
    private static String countText = "Items: 0";
    private static long hitAt;
    private static String hitText = "Reach: --";

    private NativeClientTools1201() {}

    public static void register() {
        // The 26.x key names, so a shared options.txt keeps the bindings.
        copy = key("key.thelads.copy_coordinates");
        timer = key("key.thelads.stopwatch");
        reset = key("key.thelads.stopwatch_reset");
        action("ClientTools", "Copy coordinates", NativeClientTools1201::copyCoordinates);
        action("Stopwatch", "Start or pause", ClientTools.STOPWATCH::toggle);
        action("Stopwatch", "Reset", ClientTools.STOPWATCH::reset);
    }

    private static KeyMapping key(String name) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(name, InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), NativeKeyBindings1201.CATEGORY));
    }

    private static void action(String module, String option, Runnable run) {
        var m = Hud1201.module(module);
        if (m != null && m.getOption(option) instanceof ActionOption action) action.setAction(run);
    }

    public static void tick() {
        var mc = Minecraft.getInstance();
        if (player != mc.player || level != mc.level) {
            player = mc.player;
            level = mc.level;
            durability.reset();
            full.reset();
            countTick = Integer.MIN_VALUE;
            hitAt = 0;
            hitText = "Reach: --";
        }
        boolean playing = player != null && mc.screen == null;
        while (copy.consumeClick()) if (playing && Hud1201.enabled("ClientTools")) copyCoordinates();
        while (timer.consumeClick()) if (playing && Hud1201.enabled("Stopwatch")) ClientTools.STOPWATCH.toggle();
        while (reset.consumeClick()) if (playing && Hud1201.enabled("Stopwatch")) ClientTools.STOPWATCH.reset();
        if (player == null || !Hud1201.enabled("ClientTools")) {
            durability.reset();
            full.reset();
            return;
        }
        long now = System.nanoTime() / 1_000_000;
        ItemStack lowest = ItemStack.EMPTY;
        double remaining = 101;
        if (Hud1201.bool("ClientTools", "Low durability warning", true)) {
            for (var slot : GEAR) {
                var stack = player.getItemBySlot(slot);
                if (stack.isDamageableItem()) {
                    double percent = 100.0 * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage();
                    if (percent < remaining) {
                        remaining = percent;
                        lowest = stack;
                    }
                }
            }
        }
        if (durability.update(remaining <= Hud1201.number("ClientTools", "Durability percent", 10), now, 30_000))
            notify("Low durability: " + lowest.getHoverName().getString() + " (" + Math.round(remaining) + "%)");
        boolean inventoryFull = Hud1201.bool("ClientTools", "Inventory full warning", true) && player.getInventory().getFreeSlot() == -1;
        if (full.update(inventoryFull, now, 10_000)) notify("Inventory is full");
    }

    public static void copyCoordinates() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        var p = mc.player.blockPosition();
        mc.keyboardHandler.setClipboard("X: " + p.getX() + " Y: " + p.getY() + " Z: " + p.getZ() + " (" + mc.level.dimension().location() + ")");
        notify("Coordinates copied");
    }

    private static void notify(String text) {
        var mc = Minecraft.getInstance();
        if (mc.player != null) mc.gui.setOverlayMessage(Component.literal("Lads: " + text), false);
    }

    /** The ItemCounter HUD text for its Item option (0 held item, 1 arrows, 2 totems, 3 rockets): the inventory's 36 slots and the offhand. */
    public static String itemCount(int selection) {
        var current = Minecraft.getInstance().player;
        if (current == null) return "Items: 0";
        if (current == player && countTick == current.tickCount && countSelection == selection) return countText;
        countTick = current.tickCount;
        countSelection = selection;
        Item target = switch (selection) {
            case 1 -> Items.ARROW;
            case 2 -> Items.TOTEM_OF_UNDYING;
            case 3 -> Items.FIREWORK_ROCKET;
            case 4 -> Items.GOLDEN_APPLE;
            case 5 -> Items.ENDER_PEARL;
            case 6 -> Items.POTION;
            case 7 -> Items.MUSHROOM_STEW;
            default -> current.getMainHandItem().getItem();
        };
        int count = 0;
        // The 36 storage slots and the offhand, not worn armor.
        for (int i = 0; i < 37; i++) {
            var stack = i == 36 ? current.getOffhandItem() : current.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                if (selection == 1 && (stack.is(Items.ARROW) || stack.is(Items.SPECTRAL_ARROW) || stack.is(Items.TIPPED_ARROW))) count += stack.getCount();
                else if (selection == 4 && (stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE))) count += stack.getCount();
                else if (selection == 6 && (stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION))) count += stack.getCount();
                else if (selection == 7 && (stack.is(Items.MUSHROOM_STEW) || stack.is(Items.SUSPICIOUS_STEW) || stack.is(Items.RABBIT_STEW))) count += stack.getCount();
                else if (stack.is(target)) count += stack.getCount();
            }
        }
        String label = switch (selection) {
            case 1 -> "Arrows";
            case 2 -> "Totems";
            case 3 -> "Rockets";
            case 4 -> "Golden Apples";
            case 5 -> "Ender Pearls";
            case 6 -> "Potions";
            case 7 -> "Soups";
            default -> current.getMainHandItem().isEmpty() ? "Held item" : current.getMainHandItem().getHoverName().getString();
        };
        return countText = label + ": " + count;
    }

    /** The local player attacked {@code entity} (ClientToolsAttackMixin): ReachDisplay keeps the distance for three seconds. */
    public static void attacked(Entity entity) {
        var mc = Minecraft.getInstance();
        if (!Hud1201.enabled("ReachDisplay") || mc.player == null) return;
        if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity() == entity) {
            hitAt = System.nanoTime();
            hitText = String.format(Locale.ROOT, "Reach: %.2f blocks", mc.player.getEyePosition().distanceTo(hit.getLocation()));
        }
    }

    public static String reachText() {
        return hitAt != 0 && System.nanoTime() - hitAt < 3_000_000_000L ? hitText : "Reach: --";
    }

    /** Chat's Timestamps: "[HH:mm] " in gray before a new message, on an unstyled root so the message keeps its own style. */
    public static Component timestamp(Component original) {
        if (!Hud1201.enabled("Chat") || !Hud1201.bool("Chat", "Timestamps", false)) return original;
        var prefix = Component.literal("[" + LocalTime.now().format(CLOCK) + "] ").withStyle(ChatFormatting.GRAY);
        return Component.empty().append(prefix).append(original);
    }
}
