package com.thelads.core.v26_2.feature;

import com.thelads.core.client.ClientTools;
import com.thelads.core.config.ActionOption;
import com.thelads.core.config.ModuleSupport;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import java.util.Set;

/** Client observations only: never sends chat, inventory edits or interaction packets. */
public final class NativeClientTools {
    private static final ClientTools.Warning durability = new ClientTools.Warning(), full = new ClientTools.Warning();
    private static final ClientTools.ParticleBudget particles = new ClientTools.ParticleBudget();
    private static final Set<String> DECORATIVE = Set.of("smoke", "large_smoke", "white_smoke", "campfire_cosy_smoke",
        "campfire_signal_smoke", "ash", "white_ash", "cherry_leaves", "pale_oak_leaves", "falling_spore_blossom", "spore_blossom_air");
    private static final EquipmentSlot[] GEAR = { EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
        EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
    private static LocalPlayer player;
    private static Object level;
    private static KeyMapping copy, timer, reset;
    private static int countTick = Integer.MIN_VALUE, countSelection = -1;
    private static String countText = "Items: 0";
    private static long hitAt;
    private static String hitText = "Reach: --";
    private NativeClientTools() {}

    public static void register() {
        ModuleSupport.registerBuiltIn("Clock", "Stopwatch", "ItemCounter", "ReachDisplay", "ServerAddress", "PortalCoordinates", "ClientTools", "ParticleBudget");
        copy = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.thelads.copy_coordinates", com.mojang.blaze3d.platform.InputConstants.UNKNOWN.getValue(), NativeKeyBindings.CATEGORY));
        timer = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.thelads.stopwatch", com.mojang.blaze3d.platform.InputConstants.UNKNOWN.getValue(), NativeKeyBindings.CATEGORY));
        reset = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.thelads.stopwatch_reset", com.mojang.blaze3d.platform.InputConstants.UNKNOWN.getValue(), NativeKeyBindings.CATEGORY));
        ((ActionOption)NativeQualityOfLife.module("ClientTools").getOption("Copy coordinates")).setAction(NativeClientTools::copyCoordinates);
        ((ActionOption)NativeQualityOfLife.module("Stopwatch").getOption("Start or pause")).setAction(ClientTools.STOPWATCH::toggle);
        ((ActionOption)NativeQualityOfLife.module("Stopwatch").getOption("Reset")).setAction(ClientTools.STOPWATCH::reset);
    }
    public static void tick() {
        var mc = Minecraft.getInstance();
        if (player != mc.player || level != mc.level) {
            player = mc.player; level = mc.level;
            durability.reset(); full.reset(); particles.reset(); countTick = Integer.MIN_VALUE; hitAt = 0; hitText = "Reach: --";
        }
        boolean playing = player != null && mc.gui.screen() == null;
        while (copy.consumeClick()) if (playing && NativeQualityOfLife.enabled("ClientTools")) copyCoordinates();
        while (timer.consumeClick()) if (playing && NativeQualityOfLife.enabled("Stopwatch")) ClientTools.STOPWATCH.toggle();
        while (reset.consumeClick()) if (playing && NativeQualityOfLife.enabled("Stopwatch")) ClientTools.STOPWATCH.reset();
        if (player == null || !NativeQualityOfLife.enabled("ClientTools")) { durability.reset(); full.reset(); return; }
        long now = System.nanoTime() / 1_000_000;
        ItemStack lowest = ItemStack.EMPTY;
        double remaining = 101;
        if (NativeQualityOfLife.bool("ClientTools", "Low durability warning", true)) {
            for (var slot : GEAR) {
                var stack = player.getItemBySlot(slot);
                if (stack.isDamageableItem()) {
                    double percent = 100.0 * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage();
                    if (percent < remaining) { remaining = percent; lowest = stack; }
                }
            }
        }
        if (durability.update(remaining <= NativeQualityOfLife.number("ClientTools", "Durability percent", 10), now, 30_000))
            notify("Low durability: " + lowest.getHoverName().getString() + " (" + Math.round(remaining) + "%)");
        boolean inventoryFull = NativeQualityOfLife.bool("ClientTools", "Inventory full warning", true) && player.getInventory().getFreeSlot() == -1;
        if (full.update(inventoryFull, now, 10_000)) notify("Inventory is full");
    }
    public static void copyCoordinates() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        var p = mc.player.blockPosition();
        mc.keyboardHandler.setClipboard("X: " + p.getX() + " Y: " + p.getY() + " Z: " + p.getZ() + " (" + mc.level.dimension().identifier() + ")");
        notify("Coordinates copied");
    }
    private static void notify(String text) {
        var current = Minecraft.getInstance().player;
        if (current != null) Minecraft.getInstance().gui.hud.setOverlayMessage(Component.literal("Lads: " + text), false);
    }
    public static String itemCount(int selection) {
        var current = Minecraft.getInstance().player;
        if (current == null) return "Items: 0";
        if (current == player && countTick == current.tickCount && countSelection == selection) return countText;
        countTick = current.tickCount; countSelection = selection;
        Item target = switch (selection) { case 1 -> Items.ARROW; case 2 -> Items.TOTEM_OF_UNDYING; case 3 -> Items.FIREWORK_ROCKET; default -> current.getMainHandItem().getItem(); };
        int count = 0;
        // Count the 36 storage slots and offhand, not worn armor.
        for (int i = 0; i < 37; i++) {
            var stack = i == 36 ? current.getOffhandItem() : current.getInventory().getItem(i);
            if (!stack.isEmpty() && (stack.is(target) || selection == 1 && (stack.is(Items.SPECTRAL_ARROW) || stack.is(Items.TIPPED_ARROW)))) count += stack.getCount();
        }
        String label = switch (selection) { case 1 -> "Arrows"; case 2 -> "Totems"; case 3 -> "Rockets"; default -> current.getMainHandItem().isEmpty() ? "Held item" : current.getMainHandItem().getHoverName().getString(); };
        return countText = label + ": " + count;
    }
    public static void attacked(Entity entity) {
        var mc = Minecraft.getInstance();
        if (!NativeQualityOfLife.enabled("ReachDisplay") || mc.player == null) return;
        if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity() == entity) {
            hitAt = System.nanoTime();
            hitText = String.format(java.util.Locale.ROOT, "Reach: %.2f blocks", mc.player.getEyePosition().distanceTo(hit.getLocation()));
        }
    }
    public static String reachText() { return hitAt != 0 && System.nanoTime() - hitAt < 3_000_000_000L ? hitText : "Reach: --"; }
    public static boolean allowParticle(ParticleOptions options, double x, double y, double z) {
        var mc = Minecraft.getInstance();
        if (!NativeQualityOfLife.enabled("ParticleBudget") || mc.player == null || mc.level == null) return true;
        var id = BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType());
        if (id == null || !id.getNamespace().equals("minecraft") || !DECORATIVE.contains(id.getPath())) return true;
        return particles.allow(mc.level.getGameTime(), mc.player.distanceToSqr(x, y, z),
            NativeQualityOfLife.number("ParticleBudget", "Distance", 48), (int)NativeQualityOfLife.number("ParticleBudget", "Particles per tick", 64));
    }
    public static Component timestamp(Component original) {
        if (!NativeQualityOfLife.enabled("Chat") || !NativeQualityOfLife.bool("Chat", "Timestamps", false)) return original;
        var prefix = Component.literal("[" + java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) + "] ")
            .withStyle(net.minecraft.ChatFormatting.GRAY);
        // Use an unstyled root so the original message keeps its colour/click/hover style.
        return Component.empty().append(prefix).append(original);
    }
}
