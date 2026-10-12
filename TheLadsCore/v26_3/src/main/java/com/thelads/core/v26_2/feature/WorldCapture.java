package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.client.WavyCape;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.modules.TntTimerModule;
import com.thelads.core.modules.WavyCapesModule;
import com.thelads.core.v26_2.world.World26;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-capture-world191" from the harness's LADS_VERIFY_CAPTURE_WORLD191): the 1.9.1 world lane. The QA
 * player gets a test cape (common WavyCape.qaPixel, through LocalSkins as an offline account's own skin does) and is photographed in
 * third person standing, sprinting, strafing, from the front, Blocky, sneaking, with a chestplate, glowing, with an elytra and with Wavy
 * Capes off (screenshots/world191-*.png); then three primed TNT (fuse 80, 50, 26) and a TNT minecart lit by a powered activator rail are
 * photographed with their timers (the server's ticks frozen for each shot), behind a stone wall with Through blocks off and on, and
 * removed before they explode. Modules,
 * skin, camera, keys, game mode, items and blocks are put back.
 */
final class WorldCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final Identifier CAPE = Identifier.fromNamespaceAndPath("theladscore", "qa_cape");
    private interface Step { int run(Minecraft mc) throws Exception; }
    private static final List<Step> STEPS = new ArrayList<>();
    private static final Map<Option, com.google.gson.JsonElement> OPTIONS = new LinkedHashMap<>();
    private static int step = -1, wait, checks, failures, frames;
    private static String pendingFrame;
    private static boolean frameBusy, capesOn, timerOn;
    private static Object skinBefore;
    private static CameraType cameraBefore;
    private static BlockPos base;
    private static int capesAt, timersAt, waited;

    private WorldCapture() {}

    static boolean busy() { return step >= 0 && step < STEPS.size(); }

    static void tick(Path game, boolean ready) {
        if (busy()) { run(Minecraft.getInstance()); return; }
        if (step >= 0 || !ready) return;
        Path request = game.resolve(".lads-qa-capture-world191");
        if (!Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
        try { Files.delete(request); } catch (Exception failure) { LOGGER.error("Lads world191 capture FAILED: request", failure); return; }
        STEPS.clear();
        STEPS.addAll(steps());
        LOGGER.info("Lads world191 capture BEGIN: {} steps", STEPS.size());
        step = 0;
    }

    private static void run(Minecraft mc) {
        if (pendingFrame != null || frameBusy) return;
        if (wait > 0) { wait--; return; }
        try {
            int next = STEPS.get(step).run(mc);
            if (next < 0) return;
            wait = next;
            if (++step == STEPS.size()) LOGGER.info("Lads world191 capture END: {} passed, {} failed; {} frames", checks, failures, frames);
        } catch (Throwable failure) {
            LOGGER.error("Lads world191 capture FAILED: step {}", step, failure);
            failures++;
            step = STEPS.size() - 1; // the last step puts everything back
        }
    }

    /** Each completed frame (NativeWorldVerification.renderedFrame): saves the frame a step asked for. */
    static void frame(RenderTarget target, Path game) {
        if (pendingFrame == null || frameBusy) return;
        String name = "world191-" + pendingFrame;
        pendingFrame = null;
        frameBusy = true;
        try {
            Path output = game.resolve("screenshots").resolve(name + ".png");
            Files.createDirectories(output.getParent());
            net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
                try { image.writeToFile(output); frames++; LOGGER.info("Lads world191 frame {}", output); }
                catch (Exception failure) { failures++; LOGGER.error("Lads world191 capture FAILED: {}", name, failure); }
                finally { image.close(); frameBusy = false; }
            });
        } catch (Exception failure) { frameBusy = false; failures++; LOGGER.error("Lads world191 capture FAILED: {}", name, failure); }
    }

    private static void check(boolean ok, String what) {
        if (ok) { checks++; LOGGER.info("Lads world191 check PASS: {}", what); }
        else { failures++; LOGGER.error("Lads world191 check FAILED: {}", what); }
    }

    private static void command(String command) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    /** The chest item on this client's player and the integrated server's (1.20.1's QA saw a command's change not reach the client). */
    private static void chest(Minecraft mc, net.minecraft.world.item.Item item) {
        mc.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(item));
        var server = mc.getSingleplayerServer();
        var id = mc.player.getUUID();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(id);
            if (player != null) player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(item));
        });
    }

    /** A block position (setblock and fill take whole numbers) this many blocks from base. */
    private static String block(int x, int y, int z) { return (base.getX() + x) + " " + (base.getY() + y) + " " + (base.getZ() + z); }

    private static String at(double x, double y, double z) {
        return String.format(Locale.ROOT, "%.2f %.2f %.2f", base.getX() + x, base.getY() + y, base.getZ() + z);
    }

    private static WavyCapesModule capes() { return (WavyCapesModule) ModuleManager.getInstance().getModule(WavyCapesModule.NAME); }

    private static TntTimerModule timer() { return (TntTimerModule) ModuleManager.getInstance().getModule(TntTimerModule.NAME); }

    private static void keys(Minecraft mc, boolean forward, boolean left, boolean sprint, boolean sneak) {
        mc.options.keyUp.setDown(forward);
        mc.options.keyLeft.setDown(left);
        mc.options.keySprint.setDown(sprint);
        mc.options.keyShift.setDown(sneak);
        if (sprint && forward) mc.player.setSprinting(true);
    }

    /** The player's cape links in degrees, as a log line. */
    private static String links(Minecraft mc) {
        WavyCape cape = World26.capeOf(mc.player);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < cape.segments(); i++) text.append(i == 0 ? "" : " ").append(Math.round(cape.linkDegrees(i)));
        return "links(deg)=[" + text + "] sway=" + String.format(Locale.ROOT, "%.1f", cape.sway(1));
    }

    private static float maxLink(Minecraft mc) {
        WavyCape cape = World26.capeOf(mc.player);
        float max = -999;
        for (int i = 0; i < cape.segments(); i++) max = Math.max(max, cape.linkDegrees(i));
        return max;
    }

    private static Step shot(String name) { return mc -> { mc.gui.hud.getChat().clearMessages(false); pendingFrame = name; return 2; }; }

    /** Logs every primed TNT and lit TNT minecart with its fuse and the timer text; returns the labels. */
    private static List<String> timers(Minecraft mc) {
        List<String> labels = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            float left = entity instanceof PrimedTnt tnt ? tnt.getFuse() : entity instanceof MinecartTNT cart && cart.getFuse() > -1 ? cart.getFuse() + 1 : Float.NaN;
            if (Float.isNaN(left)) continue;
            labels.add(TntTimerModule.label(left));
            LOGGER.info("Lads world191 tnt: {} fuse ticks left={} timer='{}' colour={}", entity.getType().toShortString(), left, TntTimerModule.label(left),
                String.format("%08X", TntTimerModule.color(left)));
        }
        return labels;
    }

    private static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(mc -> { // save and set up: modules on at their defaults, the test cape, creative, third person facing south
            OPTIONS.clear();
            for (Module module : new Module[] {capes(), timer()}) for (Option option : module.getOptions()) OPTIONS.put(option, option.save().deepCopy());
            capesOn = capes().isEnabled();
            timerOn = timer().isEnabled();
            capes().getOptions().forEach(Option::reset);
            timer().getOptions().forEach(Option::reset);
            capes().setEnabled(true);
            timer().setEnabled(true);
            NativeImage image = new NativeImage(64, 32, true);
            for (int x = 0; x < 64; x++) for (int y = 0; y < 32; y++) image.setPixel(x, y, WavyCape.qaPixel(x, y));
            mc.getTextureManager().register(CAPE, new DynamicTexture(() -> "Lads QA cape", image));
            Field selected = LocalSkins.class.getDeclaredField("selected");
            selected.setAccessible(true);
            skinBefore = selected.get(null);
            PlayerSkin skin = mc.player.getSkin();
            selected.set(null, new PlayerSkin(skin.body(), new ClientAsset.ResourceTexture(CAPE, CAPE), null, skin.model(), false));
            cameraBefore = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            base = mc.player.blockPosition();
            command("gamemode creative " + mc.player.getGameProfile().name());
            command("time set noon");
            mc.player.setYRot(0);
            mc.player.setXRot(10);
            mc.player.getAbilities().flying = false;
            capesAt = World26.capesDrawn;
            return 2;
        });
        steps.add(mc -> mc.player.onGround() || ++waited > 200 ? 40 : -1); // on the ground (an earlier probe may have left it in the air)
        steps.add(mc -> {
            check(mc.player.getSkin().cape() != null, "the QA player wears the test cape: " + mc.player.getSkin().cape());
            check(World26.capesDrawn > capesAt, "the cape is drawn as a wavy cape (" + (World26.capesDrawn - capesAt) + " frames)");
            LOGGER.info("Lads world191 cape standing: {}", links(mc));
            check(maxLink(mc) < 30, "standing, the cape hangs (highest link " + maxLink(mc) + " degrees)");
            return 0;
        });
        steps.add(shot("cape-standing"));
        steps.add(mc -> { keys(mc, true, false, true, false); return 30; });
        steps.add(mc -> {
            LOGGER.info("Lads world191 cape sprinting: {} speed={}", links(mc), String.format(Locale.ROOT, "%.3f", mc.player.getDeltaMovement().horizontalDistance()));
            check(maxLink(mc) > 25 && maxLink(mc) <= 110, "sprinting lifts the cape (highest link " + maxLink(mc) + " degrees)");
            return 0;
        });
        steps.add(shot("cape-sprinting"));
        steps.add(mc -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); return 3; });
        steps.add(shot("cape-sprinting-front"));
        steps.add(mc -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); keys(mc, false, true, false, false); return 25; });
        steps.add(mc -> { LOGGER.info("Lads world191 cape strafing left: {}", links(mc)); return 0; });
        steps.add(shot("cape-strafing"));
        steps.add(mc -> { capes().style.setIndex(1); keys(mc, true, false, true, false); return 25; });
        steps.add(shot("cape-blocky-sprinting"));
        steps.add(mc -> { capes().style.setIndex(0); keys(mc, false, false, false, true); return 20; });
        steps.add(mc -> { LOGGER.info("Lads world191 cape sneaking: {}", links(mc)); return 0; });
        steps.add(shot("cape-sneaking"));
        steps.add(mc -> { keys(mc, false, false, false, false); chest(mc, net.minecraft.world.item.Items.IRON_CHESTPLATE); return 20; });
        steps.add(shot("cape-chestplate"));
        steps.add(mc -> { command("effect give " + mc.player.getGameProfile().name() + " minecraft:glowing 10 0 true"); return 10; });
        steps.add(shot("cape-glowing-outline"));
        steps.add(mc -> { command("effect clear " + mc.player.getGameProfile().name() + " minecraft:glowing"); return 2; });
        steps.add(mc -> { chest(mc, net.minecraft.world.item.Items.ELYTRA); return 20; });
        steps.add(mc -> { capesAt = World26.capesDrawn; return 5; });
        steps.add(mc -> { check(World26.capesDrawn == capesAt, "with an elytra the cape is not drawn (vanilla's rule)"); return 0; });
        steps.add(shot("cape-elytra"));
        steps.add(mc -> { chest(mc, net.minecraft.world.item.Items.AIR); capes().setEnabled(false); keys(mc, true, false, true, false); return 25; });
        steps.add(shot("cape-module-off-sprinting"));
        steps.add(mc -> { // TNT: first person, three primed TNT and a lit TNT minecart in front
            keys(mc, false, false, false, false);
            capes().setEnabled(true);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            base = mc.player.blockPosition();
            mc.player.setYRot(0);
            mc.player.setXRot(5);
            command("setblock " + block(4, -1, 6) + " redstone_block");
            command("setblock " + block(4, 0, 6) + " activator_rail");
            return 5;
        });
        steps.add(mc -> {
            timersAt = World26.timersDrawn;
            command("summon tnt " + at(-2, 0.5, 6) + " {fuse:80s,NoGravity:1b}");
            command("summon tnt " + at(0.5, 0.5, 6) + " {fuse:50s,NoGravity:1b}");
            command("summon tnt " + at(2.5, 0.5, 6) + " {fuse:26s,NoGravity:1b}");
            command("summon tnt_minecart " + at(4.5, 0, 6.5));
            waited = 0;
            return 1;
        });
        steps.add(mc -> { // as soon as this client has all four (a busy server sends them a few ticks late)
            List<String> labels = timers(mc);
            if (labels.size() < 4 && ++waited < 40) return -1;
            LOGGER.info("Lads world191 timers a: {}", labels);
            check(labels.size() == 4, "three primed TNT and one lit minecart carry timers: " + labels);
            command("tick freeze"); // each shot of a frozen world, however long the frame takes to write
            return 2;
        });
        steps.add(shot("tnt-a"));
        steps.add(mc -> { command("tick unfreeze"); return 1; });
        steps.add(mc -> { // the 26-tick one about a quarter second from 0
            float least = Float.MAX_VALUE;
            for (Entity entity : mc.level.entitiesForRendering()) if (entity instanceof PrimedTnt tnt) least = Math.min(least, tnt.getFuse());
            if (least > 6) return -1;
            command("tick freeze");
            return 2;
        });
        steps.add(mc -> { LOGGER.info("Lads world191 timers b: {}", timers(mc)); return 0; });
        steps.add(shot("tnt-b"));
        steps.add(mc -> { // the primed TNT go before the 26-tick one explodes; two fresh ones behind a stone wall
            check(World26.timersDrawn > timersAt, "the timers are drawn (" + (World26.timersDrawn - timersAt) + ")");
            command("kill @e[type=tnt]");
            command("summon tnt " + at(-2, 0.5, 6) + " {fuse:80s,NoGravity:1b}");
            command("summon tnt " + at(1, 0.5, 6) + " {fuse:80s,NoGravity:1b}");
            command("fill " + block(-4, 0, 3) + " " + block(6, 3, 3) + " stone");
            return 3;
        });
        steps.add(shot("tnt-wall-hidden"));
        steps.add(mc -> { timer().throughBlocks.set(true); return 2; });
        steps.add(shot("tnt-wall-through-blocks"));
        steps.add(mc -> { // long before the 80-tick ones explode
            command("kill @e[type=tnt]");
            command("kill @e[type=tnt_minecart]");
            command("tick unfreeze");
            return 3;
        });
        steps.add(mc -> { // put everything back
            keys(mc, false, false, false, false);
            if (base != null) {
                command("fill " + block(-4, 0, 3) + " " + block(6, 3, 3) + " air");
                command("setblock " + block(4, 0, 6) + " air");
                command("setblock " + block(4, -1, 6) + " grass_block");
                command("kill @e[type=item]");
            }
            command("tick unfreeze");
            command("gamemode survival " + mc.player.getGameProfile().name());
            if (cameraBefore != null) mc.options.setCameraType(cameraBefore);
            try {
                Field selected = LocalSkins.class.getDeclaredField("selected");
                selected.setAccessible(true);
                selected.set(null, skinBefore);
            } catch (Exception failure) { failures++; LOGGER.error("Lads world191 capture FAILED: skin restore", failure); }
            OPTIONS.forEach((option, value) -> option.load(value));
            capes().setEnabled(capesOn);
            timer().setEnabled(timerOn);
            return 0;
        });
        return steps;
    }
}
