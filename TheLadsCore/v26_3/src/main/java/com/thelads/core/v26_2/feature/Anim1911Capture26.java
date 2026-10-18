package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (LADS_QA_ANIM1911=1): 1.9.11's 1.7 Animations held and used items, main hand and off hand, each photographed with the
 * module on and off (screenshots/anim1911/&lt;name&gt;-on|off.png). The use key is HELD for uses: vanilla releases a use once it is up.
 * Modded items (Alex's Mobs, added with LADS_VERIFY_ADD_MODS) are skipped by the game when the mod is absent (the command fails).
 */
final class Anim1911Capture26 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final String ARROW = "minecraft:arrow";

    /** One photographed setup. use: ticks the use key is held before the shot (0: idle); pitch: camera pitch; third: F5 front view. */
    private record Shot(String name, String main, String off, int use, boolean riptide, float pitch, boolean third, boolean leftArm) {
        Shot(String name, String main, String off, int use) { this(name, main, off, use, false, 0, false, false); }
    }

    private static final List<Shot> SHOTS = new ArrayList<>();
    static {
        String trident = "minecraft:trident", riptide = "minecraft:trident[minecraft:enchantments={\"minecraft:riptide\":3}]";
        String loaded = "minecraft:crossbow[minecraft:charged_projectiles=[{id:\"" + ARROW + "\"}]]";
        String potion = "minecraft:potion[minecraft:potion_contents={potion:\"minecraft:healing\"}]";
        String bundle = "minecraft:bundle[minecraft:bundle_contents=[{id:\"minecraft:stone\",count:16}]]";
        String horn = "minecraft:goat_horn[minecraft:instrument=\"minecraft:ponder_goat_horn\"]";
        both("sword", "minecraft:diamond_sword", 0);
        both("bow", "minecraft:bow", 0);
        both("bow-drawing", "minecraft:bow", 22);
        SHOTS.add(new Shot("sword-main-bow-off", "minecraft:diamond_sword", "minecraft:bow", 0));
        SHOTS.add(new Shot("sword-main-bow-off-drawing", "minecraft:diamond_sword", "minecraft:bow", 22));
        both("crossbow-charging", "minecraft:crossbow", 14);
        both("crossbow-loaded", loaded, 0);
        both("trident", trident, 0);
        both("trident-charging", trident, 16);
        SHOTS.add(new Shot("trident-riptide-main", riptide, "minecraft:air", 14, true, 0, false, false));
        SHOTS.add(new Shot("trident-riptide-off", "minecraft:air", riptide, 14, true, 0, false, false));
        SHOTS.add(new Shot("shield-blocking-off", "minecraft:air", "minecraft:shield", 12));
        SHOTS.add(new Shot("sword-block-shield-off", "minecraft:diamond_sword", "minecraft:shield", 12));
        both("shield", "minecraft:shield", 0);
        both("food-eating", "minecraft:cooked_beef", 14);
        both("potion-drinking", potion, 14);
        SHOTS.add(new Shot("spyglass-using-main", "minecraft:spyglass", "minecraft:air", 10));
        both("spyglass", "minecraft:spyglass", 0);
        both("goat-horn-using", horn, 10);
        SHOTS.add(new Shot("brush-using-main", "minecraft:brush", "minecraft:air", 14, false, 55, false, false));
        SHOTS.add(new Shot("brush-using-off", "minecraft:air", "minecraft:brush", 14, false, 55, false, false));
        both("bundle-using", bundle, 8);
        both("fishing-rod", "minecraft:fishing_rod", 0);
        both("stick", "minecraft:stick", 0);
        both("apple", "minecraft:apple", 0);
        // Modded (Alex's Mobs): a gun with a 3D hand model picked by display context, a 3D gun, a flat bow-like item, a flat rod.
        both("am-stink-ray", "alexsmobs:stink_ray", 0);
        both("am-stink-ray-using", "alexsmobs:stink_ray", 12);
        both("am-blood-sprayer", "alexsmobs:blood_sprayer", 0);
        both("am-blood-sprayer-using", "alexsmobs:blood_sprayer", 12);
        both("am-squid-grapple", "alexsmobs:squid_grapple", 0);
        both("am-squid-grapple-using", "alexsmobs:squid_grapple", 14);
        both("am-tendon-whip", "alexsmobs:tendon_whip", 0);
        // Main Arm = Left: the main hand is drawn on the left, by the same mirror as the off hand.
        SHOTS.add(new Shot("leftarm-sword-main", "minecraft:diamond_sword", "minecraft:air", 0, false, 0, false, true));
        SHOTS.add(new Shot("leftarm-bow-drawing-main", "minecraft:bow", "minecraft:air", 22, false, 0, false, true));
        // Third person, front view: both hands' items as others see them.
        SHOTS.add(new Shot("tp-trident-main-bow-off", trident, "minecraft:bow", 0, false, 0, true, false));
        SHOTS.add(new Shot("tp-sword-main-shield-off", "minecraft:diamond_sword", "minecraft:shield", 0, false, 0, true, false));
        SHOTS.add(new Shot("tp-trident-charging", trident, "minecraft:air", 16, false, 0, true, false));
        SHOTS.add(new Shot("tp-crossbow-main-stinkray-off", loaded, "alexsmobs:stink_ray", 0, false, 0, true, false));
    }

    private static void both(String name, String item, int use) {
        SHOTS.add(new Shot(name + "-main", item, "minecraft:air", use));
        SHOTS.add(new Shot(name + "-off", "minecraft:air", item, use));
    }

    private static int tick = -1, step, at = 100, passed, failed;
    private static String pendingShot;
    private static boolean done, enabledBefore, legacyBefore;
    private static int fovBefore;

    private Anim1911Capture26() {}

    static boolean busy() { return tick >= 0 && !done; }

    static void tick(boolean ready) {
        if (done || System.getenv("LADS_QA_ANIM1911") == null || (tick < 0 && (!ready || NativeRenderScaleProbe.pending()))) return;
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        if (mc.player == null || server == null) return;
        if (++tick == 0) {
            enabledBefore = NativeOldAnimations.module().isEnabled();
            legacyBefore = NativeQualityOfLife.module("LegacySwing").isEnabled();
            NativeQualityOfLife.module("LegacySwing").setEnabled(false); // the pure 1.7 swing
            fovBefore = mc.options.fov().get();
            command(server, "gamemode creative @p"); // no ammo, hunger or durability in the way of a use
            command(server, "time set noon");
            command(server, "weather clear");
            command(server, "clear @p");
            LOGGER.info("Lads anim 1.9.11 capture BEGIN: {} setups, 1.7 Animations on and off", SHOTS.size());
            com.thelads.core.client.qa.QaProgress.begin("anim 1.9.11", SHOTS.size() * 2);
            return;
        }
        if (tick < 100) return; // the QA world's own probes settle first
        if (step >= SHOTS.size() * 2) {
            finish(mc, server);
            return;
        }
        Shot shot = SHOTS.get(step / 2);
        boolean on = step % 2 == 0;
        int t = tick - at;
        // The use starts once rain has set in (riptide) or the horn's 7 s cooldown from the last shot is over.
        int start = shot.riptide ? 40 : (shot.main + shot.off).contains("goat_horn") ? 150 : 14;
        int shotAt = shot.riptide ? start + shot.use + 4 : start + Math.max(shot.use, 6);
        if (t == 0) {
            com.thelads.core.client.qa.QaProgress.step(shot.name + (on ? "-on" : "-off"));
            mc.options.keyUse.setDown(false);
            NativeOldAnimations.module().setEnabled(on);
            mc.options.setCameraType(shot.third ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
            mc.options.mainHand().set(shot.leftArm ? HumanoidArm.LEFT : HumanoidArm.RIGHT);
            mc.options.broadcastOptions(); // the main arm the server echoes back
            mc.options.fov().set(shot.third ? 30 : fovBefore); // third person: zoomed in on the player
            mc.player.setYRot(0);
            mc.player.setXRot(shot.pitch);
            command(server, "weather " + (shot.riptide ? "rain" : "clear"));
            command(server, "item replace entity @p weapon.mainhand with " + shot.main);
            command(server, "item replace entity @p weapon.offhand with " + shot.off);
        } else if (t == 10) NativeOldAnimations.APPLIED.clear();
        else if (t == start && shot.use > 0) mc.options.keyUse.setDown(true);
        else if (shot.riptide && t == start + shot.use) mc.options.keyUse.setDown(false); // released: the riptide launch
        if (t == shotAt) {
            String name = shot.name + (on ? "-on" : "-off");
            pendingShot = name;
            var player = mc.player;
            boolean held = holds(player.getMainHandItem(), shot.main) && holds(player.getOffhandItem(), shot.off)
                && (shot.use == 0 || shot.riptide || player.isUsingItem()) && (!shot.riptide || player.isAutoSpinAttack());
            if (held) passed++; else { failed++; com.thelads.core.client.qa.QaProgress.result(com.thelads.core.client.qa.QaProgress.Result.FAIL); }
            LOGGER.info("Lads anim 1.9.11 capture shot {} {}: main={} off={} using={} hand={} spin={} applied={}", name, held ? "PASS" : "FAIL",
                player.getMainHandItem(), player.getOffhandItem(), player.isUsingItem(),
                player.isUsingItem() ? player.getUsedItemHand() : "-", player.isAutoSpinAttack(), NativeOldAnimations.APPLIED);
        }
        if (t == shotAt + 6) {
            mc.options.keyUse.setDown(false);
            step++;
            at = tick + 1;
        }
    }

    private static void finish(Minecraft mc, net.minecraft.server.MinecraftServer server) {
        done = true;
        mc.options.keyUse.setDown(false);
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.options.mainHand().set(HumanoidArm.RIGHT);
        mc.options.broadcastOptions();
        mc.options.fov().set(fovBefore);
        NativeOldAnimations.module().setEnabled(enabledBefore);
        NativeQualityOfLife.module("LegacySwing").setEnabled(legacyBefore);
        command(server, "weather clear");
        command(server, "clear @p");
        command(server, "gamemode survival @p");
        com.thelads.core.client.qa.QaProgress.done();
        LOGGER.info("Lads anim 1.9.11 capture END: {} passed, {} failed (each shot holds the asked items, in use when asked)", passed, failed);
    }

    /** The stack is the asked item (its id before any components; air: empty). */
    private static boolean holds(net.minecraft.world.item.ItemStack stack, String asked) {
        String id = asked.contains("[") ? asked.substring(0, asked.indexOf('[')) : asked;
        return id.equals("minecraft:air") ? stack.isEmpty() : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(id);
    }

    private static void command(net.minecraft.server.MinecraftServer server, String command) {
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command));
    }

    /** Each completed frame (NativeWorldVerification.renderedFrame): a requested screenshot. */
    static void frame(RenderTarget target, Path game) {
        String name = pendingShot;
        if (name == null) return;
        pendingShot = null;
        Path output = game.resolve("screenshots").resolve("anim1911").resolve(name + ".png");
        net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
            try (image) {
                Files.createDirectories(output.getParent());
                image.writeToFile(output);
            } catch (Exception failure) {
                LOGGER.error("Lads anim 1.9.11 capture {} failed", name, failure);
            }
        });
    }
}
