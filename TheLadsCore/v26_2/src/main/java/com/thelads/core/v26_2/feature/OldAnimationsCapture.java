package com.thelads.core.v26_2.feature;

import com.google.gson.JsonElement;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;
import com.thelads.core.modules.OldAnimationsModule;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
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
 * QA only (auto-world, ".lads-qa-capture-oldanim" from the harness's LADS_VERIFY_CAPTURE_OLDANIM): poses 1.7 Animations in the QA
 * world and saves the completed game frame of each pose as oldanim-*.png. Held items, use, swing and hurt are set on this client
 * only (the server never sees them) and everything is restored afterwards.
 */
final class OldAnimationsCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    /** One frame: held items, the hand being used (null: none), third person (front, wearing armour), ms to wait before capturing. */
    private record Shot(String name, Item main, Item off, InteractionHand use, boolean thirdPerson, long delayMs) {}
    private static final Shot[] SHOTS = {
        new Shot("idle-sword", Items.DIAMOND_SWORD, Items.AIR, null, false, 900),
        new Shot("idle-sword-vanilla", Items.DIAMOND_SWORD, Items.AIR, null, false, 900), // 1.7 Animations off, for comparison
        new Shot("idle-item", Items.APPLE, Items.AIR, null, false, 900),
        new Shot("held-block", Items.STONE, Items.AIR, null, false, 900),
        new Shot("sword-block", Items.DIAMOND_SWORD, Items.SHIELD, InteractionHand.OFF_HAND, false, 900),
        new Shot("blockhit", Items.DIAMOND_SWORD, Items.SHIELD, InteractionHand.OFF_HAND, false, 120), // swings, captured mid-swing
        new Shot("bow-drawn", Items.BOW, Items.AIR, InteractionHand.MAIN_HAND, false, 1100),
        new Shot("fishing-rod", Items.FISHING_ROD, Items.AIR, null, false, 900),
        new Shot("eating", Items.COOKED_BEEF, Items.AIR, InteractionHand.MAIN_HAND, false, 700),
        new Shot("dropped-2d", Items.AIR, Items.AIR, null, false, 900), // a flat apple beside a 3D stone block
        new Shot("red-armour", Items.DIAMOND_SWORD, Items.AIR, null, true, 600), // hurt every tick
        new Shot("third-person-block", Items.DIAMOND_SWORD, Items.SHIELD, InteractionHand.OFF_HAND, true, 900),
        // Legacy Swing on, held half way through the swing a block placement plays; "-off" with 1.7 Animations off must match.
        new Shot("legacy-place", Items.STONE, Items.AIR, null, false, 600),
        new Shot("legacy-place-off", Items.STONE, Items.AIR, null, false, 600),
        new Shot("legacy-torch", Items.TORCH, Items.AIR, null, false, 600)};
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
        EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final int FIRST_ID = Integer.MAX_VALUE - 128;
    private static final Map<Item, ItemStack> STACKS = new HashMap<>(); // one instance per item, so a repeated item never re-equips
    private static int step = -1, saved, spawned;
    private static long due;
    private static boolean capturing, held, enabledBefore, legacyBefore, useBefore;
    private static long modifiedBefore, legacyModified;
    private static CameraType cameraBefore;
    private static final Map<Option, JsonElement> OPTIONS = new LinkedHashMap<>();
    private static final Map<EquipmentSlot, ItemStack> WORN = new LinkedHashMap<>();
    private OldAnimationsCapture() {}

    /** Started and not yet finished: another capture waits so the frames do not mix. */
    static boolean busy() { return step >= 0 && step < SHOTS.length; }

    /** Each client tick of the auto-world run: starts once the world is ready and the request exists, then keeps the pose going. */
    static void tick(Path game, boolean ready) {
        if (busy()) { hold(); return; }
        if (step >= 0 || !ready) return;
        Path request = game.resolve(".lads-qa-capture-oldanim");
        if (!Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
        try { Files.delete(request); } catch (Exception failure) { LOGGER.error("Lads 1.7 animations capture FAILED: request", failure); return; }
        Minecraft mc = Minecraft.getInstance();
        OldAnimationsModule module = NativeOldAnimations.module();
        Module legacy = NativeQualityOfLife.module("LegacySwing");
        enabledBefore = module.isEnabled();
        modifiedBefore = module.getLastModified();
        legacyBefore = legacy.isEnabled();
        legacyModified = legacy.getLastModified();
        for (Option option : module.getOptions()) OPTIONS.put(option, option.save().deepCopy());
        for (EquipmentSlot slot : SLOTS) WORN.put(slot, mc.player.getItemBySlot(slot));
        cameraBefore = mc.options.getCameraType();
        useBefore = mc.options.keyUse.isDown();
        module.getOptions().forEach(Option::reset); // every option on
        module.setEnabled(true);
        legacy.setEnabled(false); // the pure 1.7 swing
        LOGGER.info("Lads 1.7 animations capture BEGIN: {} frames, client-only items and use, all restored", SHOTS.length);
        step = 0;
        fire();
    }

    /** Each completed game frame (NativeWorldVerification.renderedFrame). */
    static void frame(RenderTarget target, Path game) {
        if (!busy() || capturing) return;
        // Another QA menu over the world hides the pose: hold this shot and pose it again once the screen is gone.
        if (Minecraft.getInstance().gui.screen() != null) { held = true; return; }
        if (held) { held = false; fire(); return; }
        if (System.nanoTime() < due) return;
        capturing = true;
        String name = "oldanim-" + SHOTS[step].name();
        try {
            Path output = game.resolve("screenshots").resolve(name + ".png");
            Files.createDirectories(output.getParent());
            net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
                try { image.writeToFile(output); saved++; LOGGER.info("Lads 1.7 animations frame {}", output); }
                catch (Exception failure) { LOGGER.error("Lads 1.7 animations capture FAILED: {}", name, failure); }
                finally { image.close(); Minecraft.getInstance().execute(OldAnimationsCapture::next); }
            });
        } catch (Exception failure) {
            LOGGER.error("Lads 1.7 animations capture FAILED: {}", name, failure);
            next();
        }
    }

    private static void next() {
        capturing = false;
        if (++step < SHOTS.length) { fire(); return; }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        mc.options.keyUse.setDown(useBefore);
        player.stopUsingItem();
        WORN.forEach(player::setItemSlot);
        player.hurtTime = 0;
        mc.options.setCameraType(cameraBefore);
        removeDropped();
        OldAnimationsModule module = NativeOldAnimations.module();
        OPTIONS.forEach(Option::load);
        module.setEnabled(enabledBefore);
        module.setLastModified(modifiedBefore);
        Module legacy = NativeQualityOfLife.module("LegacySwing");
        legacy.setEnabled(legacyBefore);
        legacy.setLastModified(legacyModified);
        LOGGER.info("Lads 1.7 animations capture END: {} passed, {} failed", saved, SHOTS.length - saved);
    }

    /** Poses the current shot. */
    private static void fire() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Shot shot = SHOTS[step];
        Shot previous = step > 0 ? SHOTS[step - 1] : null;
        boolean sameUse = previous != null && previous.use() == shot.use() && previous.main() == shot.main() && previous.off() == shot.off();
        if (!sameUse) { // a blockhit keeps blocking; anything else starts its use afresh
            mc.options.keyUse.setDown(useBefore);
            player.stopUsingItem();
        }
        removeDropped();
        NativeOldAnimations.module().setEnabled(!shot.name().endsWith("-vanilla") && !shot.name().equals("legacy-place-off"));
        NativeQualityOfLife.module("LegacySwing").setEnabled(shot.name().startsWith("legacy"));
        for (EquipmentSlot slot : SLOTS) player.setItemSlot(slot, worn(shot, slot));
        mc.options.setCameraType(shot.thirdPerson() ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
        if (shot.name().equals("blockhit")) player.swing(InteractionHand.MAIN_HAND);
        if (shot.name().equals("dropped-2d")) {
            Vec3 eye = player.getEyePosition(), look = player.getViewVector(1);
            Vec3 side = new Vec3(-look.z, 0, look.x).normalize().scale(0.4);
            Vec3 at = eye.add(look.scale(1.6)).add(0, -0.45, 0);
            drop(Items.APPLE, at.subtract(side));
            drop(Items.STONE, at.add(side));
        }
        hold();
        due = System.nanoTime() + shot.delayMs() * 1_000_000L;
    }

    /** Every tick of a shot: the use stays held (no real right-click can start) and the red-armour shot stays hurt. */
    private static void hold() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Shot shot = SHOTS[step];
        // The items are this client's only: a slot update from the server (another QA probe changing the real inventory) puts the
        // real items back. Wear the shot's items again and give the pose its full time once more.
        boolean replaced = false;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack expected = worn(shot, slot), actual = player.getItemBySlot(slot);
            if (actual != expected && !(actual.isEmpty() && expected.isEmpty())) {
                player.setItemSlot(slot, expected);
                replaced = true;
            }
        }
        if (replaced) {
            player.stopUsingItem();
            due = Math.max(due, System.nanoTime() + shot.delayMs() * 1_000_000L);
        }
        if (shot.use() != null) {
            try { // no right-click of our own may start while the use key is held down
                var delay = Minecraft.class.getDeclaredField("rightClickDelay");
                delay.setAccessible(true);
                delay.setInt(mc, 20);
            } catch (ReflectiveOperationException failure) { LOGGER.warn("Lads 1.7 animations capture: right-click delay unavailable", failure); }
            mc.options.keyUse.setDown(true); // vanilla releases a use whose key is up
            if (!player.isUsingItem()) player.startUsingItem(shot.use());
        }
        if (shot.name().equals("red-armour")) player.hurtTime = player.hurtDuration = 10;
        if (shot.name().startsWith("legacy")) { // this tick's updateSwingTime makes it 3 of 6: the swing stays half way
            player.swingingArm = InteractionHand.MAIN_HAND;
            player.swinging = true;
            player.swingTime = 2;
        }
    }

    /** What the shot wears in this slot: its held items, iron armour in third person, otherwise the player's own armour. */
    private static ItemStack worn(Shot shot, EquipmentSlot slot) {
        if (slot == EquipmentSlot.MAINHAND) return stack(shot.main());
        if (slot == EquipmentSlot.OFFHAND) return stack(shot.off());
        return shot.thirdPerson() ? stack(armour(slot)) : WORN.get(slot);
    }

    private static ItemStack stack(Item item) {
        return item == Items.AIR ? ItemStack.EMPTY : STACKS.computeIfAbsent(item, ItemStack::new);
    }

    private static Item armour(EquipmentSlot slot) {
        return slot == EquipmentSlot.HEAD ? Items.IRON_HELMET : slot == EquipmentSlot.CHEST ? Items.IRON_CHESTPLATE
            : slot == EquipmentSlot.LEGS ? Items.IRON_LEGGINGS : Items.IRON_BOOTS;
    }

    /** A client-only dropped item hovering in view (never sent to or from the server). */
    private static void drop(Item item, Vec3 at) {
        var level = Minecraft.getInstance().level;
        ItemEntity entity = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(item), 0, 0, 0);
        entity.setNoGravity(true);
        entity.setId(FIRST_ID + spawned++);
        level.addEntity(entity);
    }

    private static void removeDropped() {
        var level = Minecraft.getInstance().level;
        for (int i = 0; i < spawned; i++) level.removeEntity(FIRST_ID + i, Entity.RemovalReason.DISCARDED);
        spawned = 0;
    }
}
