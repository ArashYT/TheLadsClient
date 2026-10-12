package com.thelads.core.v26_2.feature;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.thelads.core.client.sound.SoundsQa;
import com.thelads.core.v26_2.gui.LadsSettingsScreen26;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
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
    private static volatile int target = -1, archer = -1;
    /** The stand-in player's id (client only). */
    private static final int STAND_IN = Integer.MAX_VALUE - 7;
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

        @Override public com.thelads.core.client.gui.LadsSettingsScreen openSettings(String module) {
            LadsSettingsScreen26 menu = new LadsSettingsScreen26(null);
            Minecraft.getInstance().setScreenAndShow(menu);
            menu.openModule(module);
            return menu.ui();
        }

        @Override public void closeScreen() { Minecraft.getInstance().setScreenAndShow(null); }

        @Override public void screenshot(String name) { pendingShot = name; }

        @Override public void spawnArcheryTarget(double distance, boolean player) {
            removeArcheryTarget();
            Minecraft mc = Minecraft.getInstance();
            double yaw = Math.toRadians(mc.player.getYRot());
            double x = mc.player.getX() - Math.sin(yaw) * distance, y = mc.player.getY(), z = mc.player.getZ() + Math.cos(yaw) * distance;
            float facing = mc.player.getYRot() + 180;
            if (player) {
                RemotePlayer standIn = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "LadsArcheryTarget"));
                standIn.setId(STAND_IN);
                standIn.snapTo(x, y, z, facing, 0);
                standIn.yHeadRot = standIn.yBodyRot = facing;
                mc.level.addEntity(standIn);
                archer = STAND_IN;
                return;
            }
            var server = mc.getSingleplayerServer();
            var id = mc.player.getUUID();
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayer(id);
                if (sp == null) return;
                var villager = EntityTypes.VILLAGER.create(sp.level(), EntitySpawnReason.COMMAND);
                villager.snapTo(x, y, z, facing, 0);
                villager.setYHeadRot(facing);
                villager.setNoAi(true);
                villager.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
                sp.level().addFreshEntity(villager);
                villager.setHealth(1000);
                archer = villager.getId();
            });
        }

        @Override public boolean archeryTargetReady() {
            Minecraft mc = Minecraft.getInstance();
            return archer >= 0 && mc.level.getEntity(archer) != null;
        }

        @Override public void shootTarget(boolean head) {
            Minecraft mc = Minecraft.getInstance();
            Entity target = mc.level.getEntity(archer);
            Vec3 at = new Vec3(target.getX(), head ? target.getEyeY() : target.getY() + target.getBbHeight() * .45, target.getZ());
            Vec3 away = new Vec3(at.x - mc.player.getX(), 0, at.z - mc.player.getZ()).normalize();
            Vec3 start = at.subtract(away.scale(4));
            var server = mc.getSingleplayerServer();
            var id = mc.player.getUUID();
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayer(id);
                if (sp == null) return;
                Arrow arrow = new Arrow(sp.level(), sp, new ItemStack(Items.ARROW), null);
                arrow.setPos(start);
                arrow.shoot(at.x - start.x, at.y - start.y, at.z - start.z, 3f, 0f);
                sp.level().addFreshEntity(arrow);
            });
        }

        @Override public void arrowHitPlayerPackets() {
            Minecraft mc = Minecraft.getInstance();
            Entity standIn = mc.level.getEntity(archer);
            if (standIn != null) mc.getConnection().handleDamageEvent(new ClientboundDamageEventPacket(standIn, mc.level.damageSources().generic()));
            mc.getConnection().handleGameEvent(new ClientboundGameEventPacket(ClientboundGameEventPacket.PLAY_ARROW_HIT_SOUND, 0));
        }

        @Override public void removeArcheryTarget() {
            Minecraft mc = Minecraft.getInstance();
            int old = archer;
            archer = -1;
            if (old == STAND_IN) mc.level.removeEntity(STAND_IN, Entity.RemovalReason.DISCARDED);
            else if (old >= 0) {
                var server = mc.getSingleplayerServer();
                var id = mc.player.getUUID();
                server.execute(() -> {
                    var sp = server.getPlayerList().getPlayer(id);
                    if (sp != null && sp.level().getEntity(old) != null) sp.level().getEntity(old).discard();
                });
            }
        }
    };
}
