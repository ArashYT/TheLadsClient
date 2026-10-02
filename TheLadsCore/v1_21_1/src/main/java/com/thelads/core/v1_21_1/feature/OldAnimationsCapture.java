package com.thelads.core.v1_21_1.feature;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.modules.OldAnimationsModule;
import com.thelads.core.modules.OldAnimationsModule.Feature;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-capture-oldanim" from the harness's LADS_VERIFY_CAPTURE_OLDANIM): poses the player for each
 * 1.7 Animations scene with client-side held items, use, swing and hurt time (nothing is sent to the server), holds the pose
 * every tick and saves the completed game frame as screenshots/oldanim-*.png. Everything is restored afterwards.
 */
final class OldAnimationsCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final String[] SHOTS = {"idle-sword", "block-swing", "block-third-person", "bow-drawn", "fishing-rod", "eating", "dropped-2d", "red-armour"};
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final Item[] ARMOUR = {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
    private static final int FIRST_ID = Integer.MAX_VALUE - 96;
    private static final ItemStack[] HELD = new ItemStack[SLOTS.length];
    private static final boolean[] OPTIONS = new boolean[Feature.values().length];
    private static int step = -1, saved;
    private static long due, modifiedBefore;
    private static boolean enabledBefore;
    private static CameraType cameraBefore;
    private OldAnimationsCapture() {}

    static boolean busy() { return step >= 0 && step < SHOTS.length; }

    /** Each client tick of the auto-world run: starts once the world is ready and the request exists, then holds the pose. */
    static void tick(Path game, boolean ready) {
        if (busy()) {
            pose();
            return;
        }
        // After the native feature probe when it runs: it drives the same player state.
        boolean probed = NativeQualityProbe.done() || !(Boolean.getBoolean("thelads.verifyNativeFeatures") || Boolean.getBoolean("thelads.verifyIntegrations"));
        if (step >= 0 || !ready || !probed) return;
        Path request = game.resolve(".lads-qa-capture-oldanim");
        if (!Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
        try { Files.delete(request); } catch (Exception failure) { LOGGER.error("Lads 1.7 animations capture FAILED: request", failure); return; }
        Minecraft mc = Minecraft.getInstance();
        OldAnimationsModule module = NativeOldAnimations.module();
        enabledBefore = module.isEnabled();
        modifiedBefore = module.getLastModified();
        for (Feature feature : Feature.values()) {
            OPTIONS[feature.ordinal()] = module.option(feature).get();
            module.option(feature).set(true);
        }
        module.setEnabled(true);
        for (int i = 0; i < SLOTS.length; i++) HELD[i] = mc.player.getItemBySlot(SLOTS[i]);
        cameraBefore = mc.options.getCameraType();
        LOGGER.info("Lads 1.7 animations capture BEGIN: {} frames; held items, use, swing and hurt time are client-side only", SHOTS.length);
        step = 0;
        setUp();
    }

    /** Each completed game frame (NativeWorldVerification.renderedFrame); 1.21.1 reads it back at once. */
    static void frame(RenderTarget target, Path game) {
        if (!busy()) return;
        // Another QA menu over the view: wait until it is gone, then let the pose settle again.
        if (Minecraft.getInstance().screen != null) { due = Math.max(due, System.nanoTime() + 600_000_000L); return; }
        if (System.nanoTime() < due) return;
        String name = "oldanim-" + SHOTS[step];
        try (NativeImage image = Screenshot.takeScreenshot(target)) {
            Path folder = game.resolve("screenshots");
            Files.createDirectories(folder);
            if (!folder.toRealPath().startsWith(game)) throw new java.io.IOException("QA screenshot folder is outside the isolated game directory");
            Path output = folder.resolve(name + ".png");
            image.writeToFile(output);
            saved++;
            LOGGER.info("Lads 1.7 animations frame {}", output);
        } catch (Exception failure) {
            LOGGER.error("Lads 1.7 animations capture FAILED: {}", name, failure);
        }
        next();
    }

    private static void next() {
        if (++step < SHOTS.length) { setUp(); return; }
        reset();
        OldAnimationsModule module = NativeOldAnimations.module();
        for (Feature feature : Feature.values()) module.option(feature).set(OPTIONS[feature.ordinal()]);
        module.setEnabled(enabledBefore);
        module.setLastModified(modifiedBefore);
        LOGGER.info("Lads 1.7 animations capture END: {} passed, {} failed", saved, SHOTS.length - saved);
    }

    private static void setUp() {
        reset();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        String shot = SHOTS[step];
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(switch (shot) {
            case "bow-drawn" -> Items.BOW;
            case "fishing-rod" -> Items.FISHING_ROD;
            case "eating" -> Items.APPLE;
            case "dropped-2d" -> Items.AIR;
            default -> Items.DIAMOND_SWORD;
        }));
        player.setItemInHand(InteractionHand.OFF_HAND, shot.startsWith("block") ? new ItemStack(Items.SHIELD) : ItemStack.EMPTY);
        if (shot.equals("red-armour")) for (int i = 0; i < ARMOUR.length; i++) player.setItemSlot(SLOTS[i + 2], new ItemStack(ARMOUR[i]));
        if (shot.equals("block-third-person") || shot.equals("red-armour")) mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        if (shot.equals("dropped-2d")) {
            // An apple (flat, 1.7's camera-facing icon) beside a stone block (stays 3D), in front of the view.
            Vec3 look = player.getViewVector(1.0f), side = look.cross(new Vec3(0, 1, 0));
            side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
            Vec3 centre = player.getEyePosition().add(look.scale(1.6)).add(0, -0.35, 0);
            drop(FIRST_ID, centre.add(side.scale(-0.45)), Items.APPLE);
            drop(FIRST_ID + 1, centre.add(side.scale(0.45)), Items.STONE);
        }
        pose();
        due = System.nanoTime() + 1_200_000_000L;
    }

    /** Every tick: re-applies the use, swing and hurt time the player's own tick wears down. */
    private static void pose() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        switch (SHOTS[step]) {
            case "block-swing", "block-third-person" -> {
                using(player, InteractionHand.OFF_HAND, 72000);
                player.swinging = true; // mid-swing (3 of 6) once the player's tick advances it
                player.swingTime = 2;
                player.swingingArm = InteractionHand.MAIN_HAND;
            }
            case "bow-drawn" -> using(player, InteractionHand.MAIN_HAND, 72000 - 20);
            case "eating" -> using(player, InteractionHand.MAIN_HAND, 16);
            case "red-armour" -> player.hurtTime = 10;
            default -> {}
        }
    }

    private static void using(LocalPlayer player, InteractionHand hand, int remaining) {
        if (!player.isUsingItem()) player.startUsingItem(hand);
        player.useItemRemaining = remaining;
        // handleKeybinds releases an item in use once the use key is up; held down, it only keeps the client-side use going.
        Minecraft.getInstance().options.keyUse.setDown(true);
    }

    private static void drop(int id, Vec3 at, Item item) {
        var level = Minecraft.getInstance().level;
        ItemEntity entity = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(item));
        entity.setId(id);
        entity.setNoGravity(true);
        entity.setDeltaMovement(Vec3.ZERO);
        level.addEntity(entity);
    }

    /** Back to the player's own items, camera and state, with the client-only items removed. */
    private static void reset() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        mc.options.keyUse.setDown(false);
        if (mc.level != null) for (int id = FIRST_ID; id < FIRST_ID + 2; id++) mc.level.removeEntity(id, Entity.RemovalReason.DISCARDED);
        mc.options.setCameraType(cameraBefore);
        if (player == null) return;
        player.stopUsingItem();
        player.swinging = false;
        player.swingTime = 0;
        player.hurtTime = 0;
        for (int i = 0; i < SLOTS.length; i++) player.setItemSlot(SLOTS[i], HELD[i]);
    }
}
