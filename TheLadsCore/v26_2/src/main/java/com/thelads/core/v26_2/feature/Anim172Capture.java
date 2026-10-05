package com.thelads.core.v26_2.feature;

import com.google.gson.JsonElement;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.client.DamageTilt;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import com.thelads.core.modules.OldAnimationsModule;
import com.thelads.core.modules.OldAnimationsModule.Feature;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-capture-anim172" from the harness's LADS_VERIFY_CAPTURE_ANIM172): the 1.7.2 animation lane, driven by
 * client ticks only, so it also runs while the QA game is minimized (26.x then draws no frames); frames it asks for are saved as
 * screenshots/anim172-*.png whenever the game renders. Survival on a stone platform 40 blocks above the QA player, everything put
 * back afterwards.
 *
 * <p>Swing while using items: eating, a bow draw and the 1.7 sword block (a sword, the shield blocking in the off hand), each clicked
 * once through the attack key's own click count (KeyMapping.click, as the mouse handler does), with 1.7 Animations on and then off
 * (vanilla, for comparison). Per tick: the use, its remaining ticks and the arm's swing (lads-qa/172-swing.csv); the swing the first-
 * person hand would draw over the use (NativeOldAnimations.firstPerson called as the renderer calls it); every packet the client sent
 * around the click; the food eaten on time, the bow shooting when released, the block ending when the key comes up.
 * Damage tilt (the OldDamageTilt module): a zombie hits the player from the left, right, front and back, arrows from the left and
 * right, at 100, 50 and 0%, with Directional off, fall damage and the module off. The camera's hurt rotation is GameRenderer.bobHurt's
 * own result (called with the player's hurt state at the tilt's peak, through the Lads redirects), its roll and nod per hit in
 * lads-qa/172-tilt.csv.
 */
final class Anim172Capture {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    /** One step per tick; its return: ticks to wait, or -1 to run it again next tick. */
    private interface Step { int run(Minecraft mc) throws Exception; }
    private static final List<Step> STEPS = new ArrayList<>();
    private static int step = -1, wait, checks, failures, sample, useStart, foodWas, arrowsWas, slotBefore, waited, arrowEntities;
    private static float maxSwing, xRotBefore, yRotBefore;
    private static boolean drawn;
    private static int hurtTicks;
    private static double[] posBefore, platform;
    private static GameType modeBefore;
    private static Difficulty difficultyBefore;
    private static int foodBefore;
    private static int timeBefore = Integer.MIN_VALUE;
    private static CameraType cameraBefore;
    private static float roll, nod;
    private static final List<ItemStack> INVENTORY = new ArrayList<>();
    private static ItemStack offhandBefore = ItemStack.EMPTY;
    private static final Map<BlockPos, BlockState> PLATFORM = new LinkedHashMap<>();
    private static final Map<Option, JsonElement> OPTIONS = new LinkedHashMap<>();
    private static final Map<Module, Boolean> ENABLED = new LinkedHashMap<>();
    private static final StringBuilder SWING = new StringBuilder("use,animations,sample,tick,using,remaining,swing,drawn\n");
    private static final StringBuilder TILT = new StringBuilder("hit,source,module,directional,intensity,hurtDir,yawUsed,roll,nod,expected\n");
    private static final List<String> SENT = Collections.synchronizedList(new ArrayList<>());
    private static final Map<String, String> PACKETS = new TreeMap<>();
    private static final Map<String, Integer> EATEN = new TreeMap<>();
    private static volatile boolean counting;
    private static Channel channel;
    private static volatile String pendingFrame;
    private static volatile boolean frameBusy;
    private static volatile int frames;

    private Anim172Capture() {}

    static boolean busy() { return step >= 0 && step < STEPS.size(); }

    /**
     * Each completed game frame (NativeWorldVerification.renderedFrame): saves the frame a step asked for as
     * screenshots/anim172-NAME.png. The steps never wait for frames (a minimized 26.x game draws none); a later request replaces one
     * not yet taken.
     */
    static void frame(RenderTarget target, Path game) {
        if (pendingFrame == null || frameBusy) return;
        String name = pendingFrame;
        pendingFrame = null;
        frameBusy = true;
        try {
            Path output = game.resolve("screenshots").resolve("anim172-" + name + ".png");
            Files.createDirectories(output.getParent());
            net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
                try { image.writeToFile(output); frames++; LOGGER.info("Lads anim 1.7.2 frame {}", output); }
                catch (Exception failure) { LOGGER.warn("Lads anim 1.7.2 frame {} not saved", name, failure); }
                finally { image.close(); frameBusy = false; }
            });
        } catch (Exception failure) {
            frameBusy = false;
            LOGGER.warn("Lads anim 1.7.2 frame {} not saved", name, failure);
        }
    }

    /** Each client tick of the auto-world run: starts once the world is ready and the request exists, then one step at a time. */
    static void tick(Path game, boolean ready) {
        if (busy()) { run(Minecraft.getInstance()); return; }
        if (step >= 0 || !ready) return;
        Path request = game.resolve(".lads-qa-capture-anim172");
        if (!Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
        try { Files.delete(request); } catch (Exception failure) { LOGGER.error("Lads anim 1.7.2 capture FAILED: request", failure); return; }
        build();
        step = 0;
        LOGGER.info("Lads anim 1.7.2 capture BEGIN: {} steps, tick-driven (no frames needed), all restored", STEPS.size());
    }

    private static void run(Minecraft mc) {
        if (wait > 0) { wait--; return; }
        try {
            int next = STEPS.get(step).run(mc);
            if (next < 0) return;
            wait = next;
            step++;
        } catch (Throwable failure) {
            LOGGER.error("Lads anim 1.7.2 check FAILED: step {} threw", step, failure);
            failures++;
            step = step == STEPS.size() - 1 ? STEPS.size() : STEPS.size() - 1; // the last step puts everything back
        }
        if (step == STEPS.size()) {
            LOGGER.info("Lads anim 1.7.2 capture END: {} passed, {} failed; swing while using (eat, bow, sword block) and damage tilt; {} frames "
                + "saved (none while minimized)", checks, failures, frames);
            if (failures > 0) LOGGER.error("Lads anim 1.7.2 capture FAILED: {} checks failed", failures);
        }
    }

    private static void check(boolean ok, String what) {
        if (ok) { checks++; LOGGER.info("Lads anim 1.7.2 check PASS: {}", what); }
        else { failures++; LOGGER.error("Lads anim 1.7.2 check FAILED: {}", what); }
    }

    private static void onServer(Consumer<ServerPlayer> task) {
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        var id = mc.player.getUUID();
        server.execute(() -> task.accept(server.getPlayerList().getPlayer(id)));
    }

    private static void build() {
        STEPS.clear();
        STEPS.add(Anim172Capture::setup);
        STEPS.add(mc -> {
            if (platform == null) throw new IllegalStateException("the QA platform was not built (see the server log)");
            check(platform != null && Math.abs(mc.player.getY() - platform[1]) < 0.01 && mc.gameMode.getPlayerMode() == GameType.SURVIVAL,
                "survival on the QA platform with a sword, bow, beef, arrows and a shield");
            return 0;
        });
        for (String use : new String[]{"eat", "bow", "block"})
            for (boolean on : new boolean[]{true, false}) swing(use, on);
        STEPS.add(mc -> {
            for (String use : new String[]{"eat", "bow", "block"})
                check(PACKETS.get(use).equals(PACKETS.get(use + "-off")), use + ": 1.7 Animations on and off sent the same packet kinds around the "
                    + "click " + PACKETS.get(use) + " / " + PACKETS.get(use + "-off"));
            check(EATEN.size() == 2 && Math.abs(EATEN.get("") - EATEN.get("-off")) <= 1, "the beef took as long with 1.7 Animations on as off " + EATEN);
            Files.createDirectories(mc.gameDirectory.toPath().resolve("lads-qa"));
            Files.writeString(mc.gameDirectory.toPath().resolve("lads-qa").resolve("172-swing.csv"), SWING.toString());
            LOGGER.info("Lads 1.7.2 swing while using samples:\n{}", SWING);
            NativeQualityOfLife.module(DamageTilt.MODULE).setEnabled(true);
            onServer(server -> server.level().getServer().setDifficulty(Difficulty.NORMAL, true)); // mobs hurt players on Normal
            LOGGER.info("Lads 1.7.2 damage tilt: Minecraft's Damage Tilt setting is {}", mc.options.damageTiltStrength().get());
            return 10;
        });
        //   hit      source    module directional intensity expected (roll: a hit from a side; nod: from the front or back)
        tilt("left", "zombie", true, true, 100, "roll-");
        tilt("right", "zombie", true, true, 100, "roll+");
        tilt("front", "zombie", true, true, 100, "nod+");
        tilt("back", "zombie", true, true, 100, "nod-");
        tilt("left", "arrow", true, true, 100, "roll-");
        tilt("right", "arrow", true, true, 100, "roll+");
        tilt("right", "zombie", true, true, 50, "roll+");
        tilt("right", "zombie", true, true, 0, "none");
        tilt("right", "zombie", true, false, 100, "roll-");
        tilt("none", "fall", true, true, 100, "roll-");
        tilt("right", "zombie", false, true, 100, "roll+"); // vanilla 26.x turns by the server's hurt direction itself
        STEPS.add(Anim172Capture::restore);
    }

    /** Survival on a 3x3 stone platform 40 blocks up, with a sword, bow, beef, arrows and a shield; 1.7 Animations on, packets counted. */
    private static int setup(Minecraft mc) throws Exception {
        LocalPlayer player = mc.player;
        // The tilt module as thelads_config.json gave it at startup (LADS_QA_TILT_EXPECT: "enabled,directional,intensity" to check).
        Module loadedTilt = NativeQualityOfLife.module(DamageTilt.MODULE);
        String loaded = loadedTilt.isEnabled() + "," + ((BoolOption) loadedTilt.getOption(DamageTilt.DIRECTIONAL)).get() + ","
            + ((SliderOption) loadedTilt.getOption(DamageTilt.INTENSITY)).getIntValue(), expected = System.getenv("LADS_QA_TILT_EXPECT");
        LOGGER.info("Lads 1.7.2 damage tilt as loaded: enabled,directional,intensity = {}", loaded);
        if (expected != null) check(expected.equals(loaded), "the damage tilt loaded from this config as " + loaded + " (expected " + expected + ")");
        OldAnimationsModule animations = NativeOldAnimations.module();
        for (Module module : new Module[]{animations, NativeQualityOfLife.module("LegacySwing"), NativeQualityOfLife.module(DamageTilt.MODULE)}) {
            ENABLED.put(module, module.isEnabled());
            for (Option option : module.getOptions()) OPTIONS.put(option, option.save().deepCopy());
            module.getOptions().forEach(Option::reset);
        }
        animations.setEnabled(true);
        NativeQualityOfLife.module("LegacySwing").setEnabled(false);
        NativeQualityOfLife.module(DamageTilt.MODULE).setEnabled(false);
        slotBefore = player.getInventory().getSelectedSlot();
        xRotBefore = player.getXRot();
        yRotBefore = player.getYRot();
        posBefore = new double[]{player.getX(), player.getY(), player.getZ()};
        modeBefore = mc.gameMode.getPlayerMode();
        cameraBefore = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        onServer(server -> {
            INVENTORY.clear();
            for (int i = 0; i < server.getInventory().getContainerSize(); i++) INVENTORY.add(server.getInventory().getItem(i).copy());
            offhandBefore = server.getItemBySlot(EquipmentSlot.OFFHAND).copy();
            foodBefore = server.getFoodData().getFoodLevel();
            difficultyBefore = server.level().getServer().getWorldData().getDifficulty();
            server.getInventory().clearContent();
            server.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
            server.getInventory().setItem(1, new ItemStack(Items.BOW));
            server.getInventory().setItem(2, new ItemStack(Items.COOKED_BEEF, 16));
            server.getInventory().setItem(9, new ItemStack(Items.ARROW, 32));
            server.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            server.getFoodData().setFoodLevel(10);
            server.setGameMode(GameType.SURVIVAL);
            var level = server.level();
            BlockPos base = null; // 40 blocks up, or higher where a hill or tree is in the way
            for (int up = 40; base == null && up <= 160; up += 20) {
                BlockPos at = BlockPos.containing(posBefore[0], posBefore[1], posBefore[2]).above(up);
                boolean room = at.getY() + 2 < level.getMaxY();
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++)
                        for (int dy = 0; dy <= 2; dy++) room &= level.getBlockState(at.offset(dx, dy, dz)).isAir();
                if (room) base = at;
            }
            if (base == null) throw new IllegalStateException("no room for the QA platform above " + posBefore[1]);
            PLATFORM.clear();
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    var at = base.offset(dx, 0, dz);
                    PLATFORM.put(at, level.getBlockState(at));
                    level.setBlockAndUpdate(at, Blocks.STONE.defaultBlockState());
                }
            platform = new double[]{base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5};
            server.teleportTo(platform[0], platform[1], platform[2]);
            try { // noon, so the frames show the hand
                var commands = server.level().getServer().getCommands().getDispatcher();
                var source = server.level().getServer().createCommandSourceStack().withSuppressedOutput();
                timeBefore = commands.execute("time query time", source);
                commands.execute("time set noon", source);
            } catch (Exception failure) { LOGGER.warn("Lads anim 1.7.2 capture: the clock stays as it is", failure); }
        });
        Connection connection = mc.getConnection().getConnection();
        var field = Connection.class.getDeclaredField("channel");
        field.setAccessible(true);
        channel = (Channel) field.get(connection);
        channel.pipeline().addLast("lads-qa-sent", new ChannelOutboundHandlerAdapter() {
            @Override public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
                if (counting) SENT.add(message.getClass().getName().substring(message.getClass().getName().lastIndexOf('.') + 1));
                super.write(context, message, promise);
            }
        });
        return 20;
    }

    /** One use clicked once, with 1.7 Animations on or off. */
    private static void swing(String use, boolean on) {
        int slot = use.equals("eat") ? 2 : use.equals("bow") ? 1 : 0;
        String tag = on ? "" : "-off", state = "1.7 Animations " + (on ? "on" : "off") + ", " + use + ": ";
        STEPS.add(mc -> {
            NativeOldAnimations.module().setEnabled(on);
            mc.player.getInventory().setSelectedSlot(slot);
            look(mc.player, 0, 0); // level, at the sky: the click hits nothing
            onServer(server -> server.getFoodData().setFoodLevel(10));
            return 15;
        });
        STEPS.add(mc -> {
            mc.options.keyUse.setDown(true); // the use key held: handleKeybinds starts the use, as with a real key
            useStart = mc.player.tickCount;
            foodWas = mc.player.getInventory().getItem(2).getCount();
            arrowsWas = mc.player.getInventory().getItem(9).getCount();
            return 6;
        });
        STEPS.add(mc -> {
            check(mc.player.isUsingItem() && swingOf(mc.player) == 0, state + "the held use key " + use + "s, no swing yet");
            SENT.clear();
            counting = true;
            maxSwing = 0;
            drawn = false;
            KeyMapping.click(InputConstants.getKey(mc.options.keyAttack.saveString())); // the attack key's click, as the mouse handler counts it
            sample = 0;
            return 0;
        });
        STEPS.add(mc -> {
            LocalPlayer player = mc.player;
            float swing = swingOf(player);
            maxSwing = Math.max(maxSwing, swing);
            // The hand the renderer would draw: the main hand (the eaten food, the drawn bow, the blocking sword), with this swing.
            NativeOldAnimations.APPLIED.clear();
            PoseStack pose = new PoseStack();
            pose.pushPose();
            NativeOldAnimations.firstPerson(player, InteractionHand.MAIN_HAND, player.getMainHandItem(), 0, swing, 0, pose,
                ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);
            boolean shown = NativeOldAnimations.APPLIED.contains(Feature.SWING_WHILE_USING) || NativeOldAnimations.APPLIED.contains(Feature.BLOCKHIT);
            drawn |= shown;
            SWING.append(use).append(on ? ",on," : ",off,").append(sample).append(',').append(player.tickCount - useStart).append(',')
                .append(player.isUsingItem()).append(',').append(player.getUseItemRemainingTicks()).append(',')
                .append(String.format(Locale.ROOT, "%.3f", swing)).append(',').append(shown).append('\n');
            if (sample >= 1 && sample <= 4) pendingFrame = use + "-click-" + sample + tag;
            if (++sample <= 10) return -1;
            counting = false;
            List<String> sent = new ArrayList<>(SENT);
            PACKETS.put(use + tag, kinds(sent));
            LOGGER.info("Lads 1.7.2 swing while using: {} packets around the click: {}", use + tag, sent);
            check(sent.stream().noneMatch(kind -> kind.contains("Swing") || kind.contains("Interact") || kind.contains("Attack")
                || kind.contains("PlayerAction") || kind.contains("UseItem")), state + "no swing, attack or use packet around the click " + kinds(sent));
            check(player.isUsingItem(), state + "still in use 10 ticks after the click (" + player.getUseItemRemainingTicks() + " ticks left)");
            if (on) check(maxSwing > 0.3f && drawn, state + "the click swings the arm (up to " + maxSwing + ") and the hand draws it over the use");
            else check(maxSwing == 0 && !drawn, state + "vanilla drops the click: no swing");
            return 0;
        });
        STEPS.add(mc -> {
            int held = mc.player.tickCount - useStart;
            if (use.equals("eat")) {
                if (mc.player.getInventory().getItem(2).getCount() == foodWas && held < 60) return -1;
                mc.options.keyUse.setDown(false);
                check(mc.player.getInventory().getItem(2).getCount() == foodWas - 1 && held >= 32 && held <= 38,
                    state + "one beef eaten after " + held + " ticks (32 to eat, then the server's word)");
                EATEN.put(tag, held);
                return 10;
            }
            if (use.equals("bow") && held < 25) return -1;
            arrowEntities = mc.level.getEntitiesOfClass(Arrow.class, mc.player.getBoundingBox().inflate(40, 20, 40)).size();
            mc.options.keyUse.setDown(false);
            return 8;
        });
        STEPS.add(mc -> {
            check(!mc.player.isUsingItem(), state + "the use ended when the key came up");
            if (use.equals("bow")) {
                List<Arrow> arrows = mc.level.getEntitiesOfClass(Arrow.class, mc.player.getBoundingBox().inflate(40, 20, 40));
                check(arrows.size() == arrowEntities + 1 && mc.player.getInventory().getItem(9).getCount() == arrowsWas - 1, state + "the released bow shot one arrow (speed "
                    + arrows.stream().mapToDouble(arrow -> arrow.getDeltaMovement().length()).max().orElse(0) + ", arrows " + arrowsWas + " -> "
                    + mc.player.getInventory().getItem(9).getCount() + ")");
                onServer(server -> server.level().getEntitiesOfClass(Arrow.class, server.getBoundingBox().inflate(80, 40, 80)).forEach(Arrow::discard));
            }
            mc.player.getInventory().setSelectedSlot(4);
            return 10;
        });
    }

    private static void tilt(String hit, String source, boolean module, boolean directional, int intensity, String expected) {
        String name = source + "-" + hit + (module ? "" : "-module-off") + (directional ? "" : "-fixed") + (intensity == 100 ? "" : "-" + intensity);
        STEPS.add(mc -> {
            Module tilt = NativeQualityOfLife.module(DamageTilt.MODULE);
            tilt.setEnabled(module);
            ((BoolOption) tilt.getOption(DamageTilt.DIRECTIONAL)).set(directional);
            ((SliderOption) tilt.getOption(DamageTilt.INTENSITY)).setValue(intensity);
            look(mc.player, 0, 0);
            onServer(server -> {
                server.setHealth(server.getMaxHealth());
                server.setDeltaMovement(0, 0, 0);
                server.teleportTo(platform[0], platform[1], platform[2]);
            });
            return 10;
        });
        STEPS.add(mc -> {
            check(Math.abs(mc.player.getYRot()) < 1e-3 && mc.player.hurtTime == 0, name + ": facing south, not hurt");
            waited = 0;
            hurtTicks = 0;
            // facing south (yaw 0): +X is the player's left, +Z ahead
            double dx = hit.equals("left") ? 1 : hit.equals("right") ? -1 : 0, dz = hit.equals("front") ? 1 : hit.equals("back") ? -1 : 0;
            onServer(server -> {
                var level = server.level();
                if (source.equals("fall")) server.hurtServer(level, server.damageSources().fall(), 2);
                else if (source.equals("zombie")) {
                    var zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
                    zombie.setPos(server.getX() + dx * 1.5, server.getY(), server.getZ() + dz * 1.5);
                    level.addFreshEntity(zombie);
                    zombie.doHurtTarget(level, server);
                    zombie.discard();
                } else {
                    Arrow arrow = new Arrow(level, server.getX() + dx * 3, server.getY() + 1.2, server.getZ() + dz * 3, new ItemStack(Items.ARROW), null);
                    arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                    arrow.shoot(-dx, 0, -dz, 1.5f, 0);
                    level.addFreshEntity(arrow);
                }
            });
            return 0;
        });
        STEPS.add(mc -> {
            LocalPlayer player = mc.player;
            player.setDeltaMovement(0, player.getDeltaMovement().y, 0); // the knockback would carry the player off the platform
            if (player.hurtTime == 0 && hurtTicks == 0) {
                if (++waited > 60) throw new IllegalStateException(name + ": the hit never reached the client");
                return -1;
            }
            if (++hurtTicks < 3) return -1; // two ticks in: past the wait for a direction that never comes (fall damage)
            float yaw = DamageTilt.CLIENT.yaw(System.currentTimeMillis()), hurtDir = player.getHurtDir();
            tiltAtPeak(mc, hurtDir);
            pendingFrame = "tilt-" + name; // the next frames draw the hurt near its peak
            TILT.append(hit).append(',').append(source).append(',').append(module).append(',').append(directional).append(',').append(intensity)
                .append(',').append(String.format(Locale.ROOT, "%.1f", hurtDir)).append(',')
                .append(Float.isNaN(yaw) ? "unknown" : String.format(Locale.ROOT, "%.1f", yaw)).append(',')
                .append(String.format(Locale.ROOT, "%.2f,%.2f", roll, nod)).append(',').append(expected).append('\n');
            String measured = String.format(Locale.ROOT, "roll %.2f, nod %.2f, hit yaw %s, vanilla hurtDir %.1f", roll, nod,
                Float.isNaN(yaw) ? "unknown" : String.format(Locale.ROOT, "%.1f", yaw), hurtDir);
            float full = (float) (14 * mc.options.damageTiltStrength().get() * intensity / 100), main = expected.startsWith("roll") ? roll : nod,
                other = expected.startsWith("roll") ? nod : roll;
            if (expected.equals("none")) check(Math.abs(roll) < 0.01f && Math.abs(nod) < 0.01f, name + ": Intensity 0 does not tilt (" + measured + ")");
            else check(Math.signum(main) == (expected.endsWith("+") ? 1 : -1) && Math.abs(main) > full * 0.95f && Math.abs(main) < full * 1.02f
                && Math.abs(other) < full * 0.25f, name + ": the camera's " + expected + " of " + full + " degrees at the peak (" + measured + ")");
            return 0;
        });
        STEPS.add(mc -> {
            mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y, 0);
            if (mc.player.hurtTime > 0) return -1;
            onServer(server -> { server.setHealth(server.getMaxHealth()); server.teleportTo(platform[0], platform[1], platform[2]); });
            return 25; // past the hurt cooldown
        });
    }

    /**
     * GameRenderer.bobHurt, the vanilla tilt the Lads redirects change, run on its own pose stack for the camera state extraction gives
     * the player at the tilt's peak (hurt curve 1). The model view starts as identity, so the result is the hurt rotation alone.
     */
    private static void tiltAtPeak(Minecraft mc, float hurtDir) throws ReflectiveOperationException {
        CameraRenderState camera = new CameraRenderState();
        CameraEntityRenderState entity = new CameraEntityRenderState();
        entity.isLiving = true;
        entity.hurtDuration = 10;
        entity.hurtTime = (float) (10 * Math.pow(0.5, 0.25)); // sin((t/10)^4 pi) = 1
        entity.hurtDir = hurtDir;
        camera.entityRenderState = entity;
        mc.gameRenderer.gameRenderState().optionsRenderState.damageTiltStrength = mc.options.damageTiltStrength().get();
        var bobHurt = GameRenderer.class.getDeclaredMethod("bobHurt", CameraRenderState.class, PoseStack.class);
        bobHurt.setAccessible(true);
        PoseStack pose = new PoseStack();
        bobHurt.invoke(mc.gameRenderer, camera, pose);
        Matrix4f matrix = pose.last().pose();
        roll = degrees(matrix.m01());  // the image of X's y: the horizon's turn
        nod = degrees(-matrix.m21()); // the image of -Z's y: the view tipping down
    }

    private static float degrees(float sine) {
        return (float) Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, sine))));
    }

    private static int restore(Minecraft mc) throws Exception {
        counting = false;
        if (channel != null && channel.pipeline().get("lads-qa-sent") != null) channel.pipeline().remove("lads-qa-sent");
        mc.options.keyUse.setDown(false);
        OPTIONS.forEach(Option::load);
        ENABLED.forEach(Module::setEnabled);
        mc.options.setCameraType(cameraBefore);
        if (modeBefore != null) {
            GameType mode = modeBefore;
            double[] at = posBefore;
            Difficulty difficulty = difficultyBefore;
            int food = foodBefore;
            ItemStack offhand = offhandBefore;
            int time = timeBefore;
            onServer(server -> {
                PLATFORM.forEach(server.level()::setBlockAndUpdate); // the platform goes, as found
                PLATFORM.clear();
                server.setGameMode(mode);
                if (difficulty != null) server.level().getServer().setDifficulty(difficulty, true);
                if (!INVENTORY.isEmpty()) {
                    server.getInventory().clearContent();
                    for (int i = 0; i < INVENTORY.size(); i++) server.getInventory().setItem(i, INVENTORY.get(i));
                    server.setItemSlot(EquipmentSlot.OFFHAND, offhand);
                    server.getFoodData().setFoodLevel(food);
                }
                server.setHealth(server.getMaxHealth());
                server.teleportTo(at[0], at[1], at[2]);
                if (time != Integer.MIN_VALUE) try {
                    server.level().getServer().getCommands().getDispatcher().execute("time set " + time,
                        server.level().getServer().createCommandSourceStack().withSuppressedOutput());
                } catch (Exception failure) { LOGGER.warn("Lads anim 1.7.2 capture: the clock could not be put back to {}", time, failure); }
            });
            mc.player.getInventory().setSelectedSlot(slotBefore);
            look(mc.player, yRotBefore, xRotBefore);
        }
        Files.createDirectories(mc.gameDirectory.toPath().resolve("lads-qa"));
        Files.writeString(mc.gameDirectory.toPath().resolve("lads-qa").resolve("172-tilt.csv"), TILT.toString());
        LOGGER.info("Lads 1.7.2 damage tilt per hit:\n{}", TILT);
        return 20;
    }

    private static void look(LocalPlayer player, float yaw, float pitch) {
        player.setYRot(yaw);
        player.yRotO = yaw;
        player.setXRot(pitch);
        player.xRotO = pitch;
    }

    /** The arm's swing now (vanilla's attack animation, 0 to 1). */
    private static float swingOf(LocalPlayer player) {
        return player.getAttackAnim(1);
    }

    /** The packet kinds sent, each once, without those every tick may or may not send (movement, tick end, input, keep-alive). */
    private static String kinds(List<String> sent) {
        TreeSet<String> kinds = new TreeSet<>(sent);
        kinds.removeIf(kind -> kind.contains("MovePlayer") || kind.contains("ClientTickEnd") || kind.contains("PlayerInput")
            || kind.contains("KeepAlive") || kind.contains("PlayerLoaded"));
        return kinds.toString();
    }
}
