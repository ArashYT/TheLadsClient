package com.thelads.core.v1_20_1.feature;

import com.mojang.authlib.GameProfile;
import com.thelads.core.client.sound.SoundsQa;
import com.thelads.core.v1_20_1.gui.LadsSettingsScreen1201;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;

/**
 * QA only: the script verb {@code sounds} runs the common uploaded-sounds routine (SoundsQa, files from LADS_VERIFY_SOUNDS) with
 * 1.20.1's pig, attack and screenshots (screenshots/sounds-*.png); a failed check fails the step. Plain Fabric and Zero SMP.
 */
final class SoundsQa1201 {
    private static SoundsQa qa;
    private static volatile int target = -1, archer = -1;
    /** The stand-in player's id (client only). */
    private static final int STAND_IN = Integer.MAX_VALUE - 7;

    private SoundsQa1201() {}

    static void register() {
        if (Boolean.getBoolean("thelads.verifyAutoWorld") || System.getProperty("thelads.qaScript") != null)
            QaProbe1201.registerStep("sounds", args -> {
                if (qa == null && (qa = SoundsQa.fromEnv()) == null) throw new IllegalStateException(SoundsQa.ENV + " is not set");
                try {
                    if (!qa.tick(GAME)) return false;
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
                if (!qa.failures().isEmpty()) throw new IllegalStateException("sounds QA: " + qa.failures());
                return true;
            });
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
                var level = sp.serverLevel();
                if (previous >= 0 && level.getEntity(previous) != null) level.getEntity(previous).discard();
                var pig = EntityType.PIG.create(level);
                float yaw = (float) Math.toRadians(sp.getYRot());
                pig.setPos(sp.getX() - Math.sin(yaw) * 2.5, sp.getY(), sp.getZ() + Math.cos(yaw) * 2.5);
                pig.setNoAi(true);
                if (tough) pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
                level.addFreshEntity(pig);
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
            LadsSettingsScreen1201 menu = new LadsSettingsScreen1201(null);
            Minecraft.getInstance().setScreen(menu);
            menu.openModule(module);
            return menu.ui();
        }

        @Override public void closeScreen() { Minecraft.getInstance().setScreen(null); }

        @Override public void screenshot(String name) throws Exception { QaProbe1201.shot(Minecraft.getInstance(), name); }

        @Override public void spawnArcheryTarget(double distance, boolean player) {
            removeArcheryTarget();
            Minecraft mc = Minecraft.getInstance();
            double yaw = Math.toRadians(mc.player.getYRot());
            double x = mc.player.getX() - Math.sin(yaw) * distance, y = mc.player.getY(), z = mc.player.getZ() + Math.cos(yaw) * distance;
            float facing = mc.player.getYRot() + 180;
            if (player) {
                RemotePlayer standIn = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "LadsArcheryTarget"));
                standIn.setId(STAND_IN);
                standIn.moveTo(x, y, z, facing, 0);
                standIn.yHeadRot = standIn.yBodyRot = facing;
                mc.level.addPlayer(STAND_IN, standIn);
                archer = STAND_IN;
                return;
            }
            var server = mc.getSingleplayerServer();
            var id = mc.player.getUUID();
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayer(id);
                if (sp == null) return;
                // A clear range: other mobs of the QA world (earlier probes') would take arrows meant for the target.
                sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, sp.getBoundingBox().inflate(48)).forEach(Entity::discard);
                var villager = EntityType.VILLAGER.create(sp.serverLevel());
                villager.moveTo(x, y, z, facing, 0);
                villager.setYHeadRot(facing);
                villager.setNoAi(true);
                villager.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
                sp.serverLevel().addFreshEntity(villager);
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
            // From 12 blocks to the target's side (seen in flight a few ticks, as a real shot is, and never near the player),
            // aimed 0.3 above the mark (an arrow falls about that much on the way), a head shot 0.45: the middle of the head.
            Vec3 at = new Vec3(target.getX(), head ? target.getEyeY() : target.getY() + target.getBbHeight() * .45, target.getZ());
            Vec3 away = new Vec3(at.x - mc.player.getX(), 0, at.z - mc.player.getZ()).normalize();
            Vec3 start = at.add(-away.z * 12, 0, away.x * 12), aim = at.add(0, head ? .45 : .3, 0);
            var server = mc.getSingleplayerServer();
            var id = mc.player.getUUID();
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayer(id);
                if (sp == null) return;
                Arrow arrow = new Arrow(sp.serverLevel(), sp);
                arrow.setPos(start);
                arrow.shoot(aim.x - start.x, aim.y - start.y, aim.z - start.z, 3f, 0f);
                sp.serverLevel().addFreshEntity(arrow);
            });
        }

        @Override public void arrowHitPlayerPackets() {
            Minecraft mc = Minecraft.getInstance();
            Entity standIn = mc.level.getEntity(archer);
            if (standIn != null) mc.getConnection().handleDamageEvent(new ClientboundDamageEventPacket(standIn, mc.level.damageSources().generic()));
            mc.getConnection().handleGameEvent(new ClientboundGameEventPacket(ClientboundGameEventPacket.ARROW_HIT_PLAYER, 0));
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
                    if (sp != null && sp.serverLevel().getEntity(old) != null) sp.serverLevel().getEntity(old).discard();
                });
            }
        }
    };
}
