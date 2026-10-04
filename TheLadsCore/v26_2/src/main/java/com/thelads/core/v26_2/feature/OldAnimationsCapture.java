package com.thelads.core.v26_2.feature;

import com.google.gson.JsonElement;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;
import com.thelads.core.modules.OldAnimationsModule;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-capture-oldanim" from the harness's LADS_VERIFY_CAPTURE_OLDANIM): poses 1.7 Animations in the QA
 * world and saves the completed game frame of each pose as oldanim-*.png. Held items, use, swing and hurt are set on this client
 * only (the server never sees them) and everything is restored afterwards.
 *
 * <p>1.7.0, after the poses, through the game's own input (held attack and use keys, survival on the integrated server, all put
 * back): the shield block, bow and food start at once while the attack key mines a block (oldanim-170-*-on-block), the sneak
 * camera per tick with 1.7 Animations on and off (oldanim-170-sneak.csv, third-person frames) and Vertical Bobbing's pitch as
 * flying stops a fall (oldanim-170-fly-cancel-bob.csv).
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
        new Shot("eat-click", Items.COOKED_BEEF, Items.AIR, InteractionHand.MAIN_HAND, false, 120), // clicked mid-meal: no swing
        new Shot("dropped-2d", Items.AIR, Items.AIR, null, false, 900), // a flat apple beside a 3D stone block
        new Shot("red-armour", Items.DIAMOND_SWORD, Items.AIR, null, true, 600), // hurt every tick
        new Shot("third-person-block", Items.DIAMOND_SWORD, Items.SHIELD, InteractionHand.OFF_HAND, true, 900),
        // Legacy Swing on, held half way through the swing a block placement plays; "-off" with 1.7 Animations off must match.
        new Shot("legacy-place", Items.STONE, Items.AIR, null, false, 600),
        new Shot("legacy-place-off", Items.STONE, Items.AIR, null, false, 600),
        new Shot("legacy-torch", Items.TORCH, Items.AIR, null, false, 600),
        new Shot("legacy-torch-off", Items.TORCH, Items.AIR, null, false, 600)}; // 1.7 Animations off: legacy-torch must match it
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
    static boolean busy() { return step >= 0 && step < SHOTS.length || input >= 0 && input < INPUT.size(); }

    /** Each client tick of the auto-world run: starts once the world is ready and the request exists, then keeps the pose going. */
    static void tick(Path game, boolean ready) {
        if (busy()) {
            if (step < SHOTS.length) hold();
            else inputTick(game);
            return;
        }
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
        if (step >= SHOTS.length) { inputFrame(target, game); return; }
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
        LOGGER.info("Lads 1.7 animations poses: {} of {} frames saved; 1.7.0 input checks next", saved, SHOTS.length);
        INPUT.clear();
        INPUT.addAll(inputSteps());
        input = 0;
        inputWait = 20;
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
        NativeOldAnimations.module().setEnabled(!shot.name().endsWith("-vanilla") && !shot.name().endsWith("-off"));
        NativeQualityOfLife.module("LegacySwing").setEnabled(shot.name().startsWith("legacy"));
        for (EquipmentSlot slot : SLOTS) player.setItemSlot(slot, worn(shot, slot));
        mc.options.setCameraType(shot.thirdPerson() ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
        if (shot.name().equals("blockhit") || shot.name().equals("eat-click")) player.swing(InteractionHand.MAIN_HAND);
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

    // ---- 1.7.0 input checks: one step per tick (its return: ticks to wait, or -1 to run it again next tick) ----
    private interface Step { int run(Minecraft mc) throws Exception; }
    private static final List<Step> INPUT = new ArrayList<>();
    private static int input = -1, inputWait, checks, failures, slotBefore, sample;
    private static String pendingFrame;
    private static boolean frameBusy, grabbing, grabbedBefore;
    private static float xRotBefore, yRotBefore;
    private static double[] posBefore;
    private static final List<ItemStack> INVENTORY = new ArrayList<>();
    private static GameType modeBefore;
    private static int foodBefore;
    private static final StringBuilder CSV = new StringBuilder();

    private static void inputTick(Path game) {
        if (pendingFrame != null || frameBusy) return; // a frame is being saved
        if (inputWait > 0) { inputWait--; return; }
        Minecraft mc = Minecraft.getInstance();
        try {
            int wait = INPUT.get(input).run(mc);
            if (wait < 0) return;
            inputWait = wait;
            if (++input == INPUT.size()) LOGGER.info("Lads 1.7 animations capture END: {} passed, {} failed; {} pose frames and the 1.7.0 "
                + "input checks", saved + checks, SHOTS.length - saved + failures, SHOTS.length);
        } catch (Throwable failure) {
            LOGGER.error("Lads 1.7 animations capture FAILED: 1.7.0 input step {}", input, failure);
            input = INPUT.size() - 1; // the last step puts everything back
            failures++;
        }
    }

    private static void inputFrame(RenderTarget target, Path game) {
        if (pendingFrame == null) return;
        String name = "oldanim-" + pendingFrame;
        pendingFrame = null;
        frameBusy = true;
        try {
            Path output = game.resolve("screenshots").resolve(name + ".png");
            net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
                try { image.writeToFile(output); LOGGER.info("Lads 1.7 animations frame {}", output); }
                catch (Exception failure) { LOGGER.error("Lads 1.7 animations capture FAILED: {}", name, failure); failures++; }
                finally { image.close(); frameBusy = false; }
            });
        } catch (Exception failure) { frameBusy = false; failures++; LOGGER.error("Lads 1.7 animations capture FAILED: {}", name, failure); }
    }

    private static void check(boolean ok, String what) {
        if (ok) { checks++; LOGGER.info("Lads 1.7 animations check PASS: {}", what); }
        else { failures++; LOGGER.error("Lads 1.7 animations check FAILED: {}", what); }
    }

    private static void onServer(Consumer<ServerPlayer> task) {
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        var id = mc.player.getUUID();
        server.execute(() -> task.accept(server.getPlayerList().getPlayer(id)));
    }

    private static List<Step> inputSteps() {
        List<Step> steps = new ArrayList<>();
        steps.add(mc -> { // survival with a sword, bow and food (shield in the off hand, an arrow), looking down at the block below
            LocalPlayer player = mc.player;
            if (!player.onGround()) throw new IllegalStateException("the QA player must stand on a block for survival");
            slotBefore = player.getInventory().getSelectedSlot();
            xRotBefore = player.getXRot();
            yRotBefore = player.getYRot();
            posBefore = new double[]{player.getX(), player.getY(), player.getZ()};
            modeBefore = mc.gameMode.getPlayerMode();
            onServer(server -> {
                INVENTORY.clear();
                for (int i = 0; i < server.getInventory().getContainerSize(); i++) INVENTORY.add(server.getInventory().getItem(i).copy());
                foodBefore = server.getFoodData().getFoodLevel();
                server.getInventory().clearContent();
                server.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
                server.getInventory().setItem(1, new ItemStack(Items.BOW));
                server.getInventory().setItem(2, new ItemStack(Items.COOKED_BEEF, 8));
                server.getInventory().setItem(9, new ItemStack(Items.ARROW));
                server.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                server.getFoodData().setFoodLevel(10);
                server.setGameMode(GameType.SURVIVAL);
            });
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            aimAtPlainBlock(player);
            NativeOldAnimations.module().getOptions().forEach(Option::reset);
            NativeQualityOfLife.module("LegacySwing").setEnabled(false);
            return 20;
        });
        for (boolean on : new boolean[]{true, false})
            for (int slot = 0; slot < 3; slot++) {
                int held = slot;
                String what = slot == 0 ? "block" : slot == 1 ? "bow" : "eat", tag = (on ? "" : "-off");
                String state = "1.7 Animations " + (on ? "on" : "off") + ", " + what + " on a block: ";
                steps.add(mc -> {
                    if (mc.gui.screen() != null) throw new IllegalStateException(state + mc.gui.screen() + " opened");
                    NativeOldAnimations.module().setEnabled(on);
                    mc.player.getInventory().setSelectedSlot(held);
                    return 10;
                });
                steps.add(mc -> {
                    check(mc.gameMode.getPlayerMode() == GameType.SURVIVAL, state + "survival on the integrated server");
                    HitResult hit = mc.hitResult;
                    check(hit instanceof BlockHitResult && hit.getType() == HitResult.Type.BLOCK, state + "looking at a block");
                    var pos = ((BlockHitResult) hit).getBlockPos();
                    float perTick = mc.level.getBlockState(pos).getDestroyProgress(mc.player, mc.level, pos);
                    if (perTick * 12 >= 1) throw new IllegalStateException(state + mc.level.getBlockState(pos) + " breaks within 12 ticks");
                    mouseGrabbed(mc, true); // QA: continueAttack mines only with the mouse grabbed by a focused window
                    mc.options.keyAttack.setDown(true);
                    return 4;
                });
                steps.add(mc -> {
                    check(mc.gameMode.isDestroying() && !mc.player.isUsingItem(), state + "the held attack key mines the block");
                    mc.options.keyUse.setDown(true);
                    return held == 1 ? 6 : 3;
                });
                steps.add(mc -> {
                    boolean using = mc.player.isUsingItem();
                    if (on) check(using, state + "the use starts at once while the attack key mines, as in 1.7");
                    else check(!using && mc.gameMode.isDestroying(), state + "vanilla ignores the use key while mining");
                    mc.options.keyAttack.setDown(false); // the mining stops here: the block never breaks
                    pendingFrame = "170-" + what + "-on-block" + tag;
                    return 0;
                });
                steps.add(mc -> {
                    mc.player.getInventory().setSelectedSlot(4); // an empty slot ends the use first: no arrow flies
                    return 2;
                });
                steps.add(mc -> {
                    mc.options.keyAttack.setDown(false);
                    mc.options.keyUse.setDown(false);
                    mouseGrabbed(mc, false);
                    return 6;
                });
                steps.add(mc -> {
                    check(!mc.player.isUsingItem() && !mc.gameMode.isDestroying(), state + "both keys up: no use, no mining");
                    return 2;
                });
            }
        steps.add(mc -> { // creative again, level, for the sneak camera
            onServer(server -> server.setGameMode(GameType.CREATIVE));
            mc.player.setXRot(0);
            mc.player.xRotO = 0;
            CSV.setLength(0);
            CSV.append("animations,press,tick,shift,crouching,pose,eyeHeight,cameraOld,camera,partial50\n");
            return 10;
        });
        // Sneak held 8 ticks (a third-person frame half way), and a 1-tick tap, whose crouch the server echoes after the key is up.
        for (boolean on : new boolean[]{true, false})
            for (int press : new int[]{8, 1}) {
                steps.add(mc -> {
                    NativeOldAnimations.module().setEnabled(on);
                    mc.options.keyShift.setDown(true);
                    sample = 0;
                    return 0;
                });
                steps.add(mc -> {
                    if (sample == press) mc.options.keyShift.setDown(false);
                    var camera = mc.gameRenderer.mainCamera();
                    float old = (float) field(camera, "eyeHeightOld"), now = (float) field(camera, "eyeHeight");
                    CSV.append(on ? "on," : "off,").append(press).append(',').append(sample).append(',').append(mc.player.isShiftKeyDown())
                        .append(',').append(mc.player.isCrouching()).append(',').append(mc.player.getPose()).append(',').append(mc.player.getEyeHeight())
                        .append(',').append(old).append(',').append(now).append(',').append(old + (now - old) * 0.5f).append('\n');
                    if (press == 8 && sample == 4) mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                    if (press == 8 && sample == 6) pendingFrame = "170-sneak-third-person" + (on ? "" : "-off");
                    if (press == 8 && sample == 7) mc.options.setCameraType(CameraType.FIRST_PERSON);
                    return ++sample < press + 10 ? -1 : 10;
                });
            }
        steps.add(mc -> {
            String text = CSV.toString();
            Files.writeString(mc.gameDirectory.toPath().resolve("screenshots").resolve("oldanim-170-sneak.csv"), text);
            LOGGER.info("Lads 1.7.0 sneak camera samples:\n{}", text);
            check(text.contains("on,8,2,true,true,CROUCHING") && text.contains("off,8,2,true,true,CROUCHING"), "the held sneak key crouches (sneak camera sampled)");
            // The 1.7 camera after a tap: down once, then up, and never down again (a crouch the server echoes late must not dip it).
            float previous = Float.NaN, redip = 0;
            boolean rising = false;
            for (String row : text.split("\n")) {
                String[] cell = row.split(",");
                if (!cell[0].equals("on") || !cell[1].equals("1")) continue;
                float camera = Float.parseFloat(cell[8]);
                if (rising) redip = Math.max(redip, previous - camera);
                if (camera > previous + 1e-4f) rising = true;
                previous = camera;
            }
            check(rising && redip < 1e-3f, "1.7 sneak camera: a quick tap dips once and rises; it never drops again (" + redip + ")");
            // Fly-cancel bob: up 24 blocks, falling, then flying stops the fall.
            mc.player.getAbilities().flying = false;
            mc.player.onUpdateAbilities();
            onServer(server -> server.teleportTo(posBefore[0], posBefore[1] + 24, posBefore[2]));
            CSV.setLength(0);
            CSV.append("tick,phase,velocityY,partial0,partial25,partial50,partial75\n");
            sample = 0;
            return 4;
        });
        steps.add(mc -> {
            if (sample == 10) {
                mc.player.getAbilities().flying = true; // as a double jump would
                mc.player.onUpdateAbilities();
            }
            CSV.append(sample).append(sample <= 10 ? ",fall," : ",flying,").append(String.format(java.util.Locale.ROOT, "%.4f", mc.player.getDeltaMovement().y));
            for (float partial : new float[]{0, 0.25f, 0.5f, 0.75f})
                CSV.append(',').append(String.format(java.util.Locale.ROOT, "%.4f", NativeVerticalBob.value(partial, true)));
            CSV.append('\n');
            return ++sample < 26 ? -1 : 0;
        });
        steps.add(mc -> {
            String text = CSV.toString();
            Files.writeString(mc.gameDirectory.toPath().resolve("screenshots").resolve("oldanim-170-fly-cancel-bob.csv"), text);
            LOGGER.info("Lads 1.7.0 fly-cancel bob samples (Vertical Bobbing pitch):\n{}", text);
            String[] rows = text.split("\n");
            float before = Float.parseFloat(rows[11].split(",")[6]), largest = 0, last = before;
            for (int row = 12; row < rows.length; row++)
                for (int column = 3; column <= 6; column++) {
                    float value = Float.parseFloat(rows[row].split(",")[column]);
                    largest = Math.max(largest, Math.abs(value - last));
                    last = value;
                }
            check(Math.abs(before) > 1 && largest < Math.abs(before) * 0.5f && Math.abs(last) < Math.abs(before) * 0.1f, "fly-cancel bob eases from "
                + before + " to " + last + " (largest quarter-tick step " + largest + "): no snap");
            return 0;
        });
        steps.add(mc -> { // everything back
            mc.options.keyAttack.setDown(false);
            mc.options.keyUse.setDown(false);
            mc.options.keyShift.setDown(false);
            mouseGrabbed(mc, false);
            if (mc.gui.screen() != null) mc.player.closeContainer();
            mc.player.getAbilities().flying = false;
            mc.player.onUpdateAbilities();
            if (modeBefore != null) {
                GameType mode = modeBefore;
                double[] at = posBefore;
                onServer(server -> {
                    server.setGameMode(mode);
                    if (!INVENTORY.isEmpty()) { // saved on the server thread before this task
                        server.getInventory().clearContent();
                        for (int i = 0; i < INVENTORY.size(); i++) server.getInventory().setItem(i, INVENTORY.get(i));
                        server.getFoodData().setFoodLevel(foodBefore);
                    }
                    server.teleportTo(at[0], at[1], at[2]);
                });
                mc.player.getInventory().setSelectedSlot(slotBefore);
                mc.player.setXRot(xRotBefore);
                mc.player.setYRot(yRotBefore);
            }
            mc.options.setCameraType(cameraBefore);
            OPTIONS.forEach(Option::load);
            NativeOldAnimations.module().setEnabled(enabledBefore);
            NativeOldAnimations.module().setLastModified(modifiedBefore);
            Module legacy = NativeQualityOfLife.module("LegacySwing");
            legacy.setEnabled(legacyBefore);
            legacy.setLastModified(legacyModified);
            return 10;
        });
        return steps;
    }

    /**
     * Looks down at a block within reach that has no block entity, so the use key can never open a container (the 26.3 QA player
     * stands on a shulker box): straight down, else 60 degrees down towards each side.
     */
    private static void aimAtPlainBlock(LocalPlayer player) {
        for (float pitch : new float[]{90, 60})
            for (float yaw = 0; yaw < 360; yaw += 90) {
                player.setXRot(pitch);
                player.xRotO = pitch;
                player.setYRot(yaw);
                player.yRotO = yaw;
                if (player.pick(player.blockInteractionRange(), 1, false) instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                    && player.level().getBlockEntity(hit.getBlockPos()) == null) return;
            }
        throw new IllegalStateException("no plain block within reach below the QA player");
    }

    /** QA only: the mouse counted as grabbed while the synthetic attack key mines (true), then as it was (false). */
    private static void mouseGrabbed(Minecraft mc, boolean grab) throws ReflectiveOperationException {
        var field = net.minecraft.client.MouseHandler.class.getDeclaredField("mouseGrabbed");
        field.setAccessible(true);
        if (grab && !grabbing) grabbedBefore = field.getBoolean(mc.mouseHandler);
        if (grab || grabbing) field.setBoolean(mc.mouseHandler, grab || grabbedBefore);
        grabbing = grab;
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
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
