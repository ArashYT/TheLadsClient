package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.thelads.core.client.sound.SoundsQa;
import com.thelads.core.v26_2.gui.LadsSettingsScreen26;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-capture-sounds" from the harness's LADS_VERIFY_CAPTURE_SOUNDS, files from LADS_VERIFY_SOUNDS): the
 * common uploaded-sounds routine (SoundsQa) with 26.3's pig, attack and screenshots (screenshots/sounds/&lt;name&gt;.png).
 */
final class SoundsCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static SoundsQa qa;
    private static boolean running, done;
    private static volatile int target = -1;
    private static String pendingShot;
    private static Path gameDir;

    private SoundsCapture() {}

    static boolean busy() { return running; }

    static void tick(Path game, boolean ready) {
        if (done) return;
        if (!running) {
            Path request = game.resolve(".lads-qa-capture-sounds");
            if (!ready || !Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
            gameDir = game;
            try { Files.delete(request); } catch (Exception ignored) {}
            qa = SoundsQa.fromEnv();
            if (qa == null) { LOGGER.error("Lads sounds capture FAILED: LADS_VERIFY_SOUNDS is not set"); done = true; return; }
            running = true;
        }
        if (pendingShot != null) return; // the next frame takes it
        try {
            if (qa.tick(GAME)) { running = false; done = true; }
        } catch (Exception | Error failure) {
            LOGGER.error("Lads sounds capture FAILED: {}", failure.toString(), failure);
            running = false;
            done = true;
        }
    }

    /** Each completed frame (NativeWorldVerification.renderedFrame): a requested screenshot. */
    static void frame(RenderTarget frame, Path game) {
        String name = pendingShot;
        if (name == null) return;
        try {
            Path output = game.resolve("screenshots").resolve("sounds").resolve(name + ".png");
            Files.createDirectories(output.getParent());
            net.minecraft.client.Screenshot.takeScreenshot(frame, image -> {
                try { image.writeToFile(output); LOGGER.info("Lads sounds QA frame {}", output); }
                catch (Exception failure) { LOGGER.warn("Lads sounds QA frame {} not saved", name, failure); }
                finally { image.close(); }
            });
        } catch (Exception failure) {
            LOGGER.warn("Lads sounds QA frame {} not saved", name, failure);
        }
        pendingShot = null;
    }

    private static final SoundsQa.Game GAME = new SoundsQa.Game() {
        @Override public void spawnTarget(boolean tough) {
            Minecraft mc = Minecraft.getInstance();
            var server = mc.getSingleplayerServer();
            var id = mc.player.getUUID();
            int previous = target;
            target = -1;
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayer(id);
                if (sp == null) return;
                if (previous >= 0 && sp.level().getEntity(previous) != null) sp.level().getEntity(previous).discard();
                var pig = EntityTypes.PIG.create(sp.level(), EntitySpawnReason.COMMAND);
                float yaw = (float) Math.toRadians(sp.getYRot());
                pig.setPos(sp.getX() - Math.sin(yaw) * 2.5, sp.getY(), sp.getZ() + Math.cos(yaw) * 2.5);
                pig.setNoAi(true);
                if (tough) pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
                sp.level().addFreshEntity(pig);
                pig.setHealth(tough ? 1000 : .5f);
                target = pig.getId();
            });
        }

        @Override public boolean attackTarget() {
            Minecraft mc = Minecraft.getInstance();
            if (target < 0 || !(mc.level.getEntity(target) instanceof LivingEntity mob) || !mob.isAlive()) return false;
            mc.gameMode.attack(mc.player, mob); // the player's own blow (KillBannerAttackMixin)
            return true;
        }

        @Override public void openSettings(String module) {
            LadsSettingsScreen26 menu = new LadsSettingsScreen26(null);
            Minecraft.getInstance().setScreenAndShow(menu);
            menu.openModule(module);
        }

        @Override public void closeScreen() { Minecraft.getInstance().setScreenAndShow(null); }

        @Override public void screenshot(String name) { pendingShot = name; }
    };
}
