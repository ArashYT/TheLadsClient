package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.retry;

import com.mojang.authlib.GameProfile;
import com.thelads.core.client.sound.SoundsQa;
import com.thelads.core.v1_8_9.gui.LadsSettingsScreen189;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S2BPacketChangeGameState;

/**
 * QA only (LADS_VERIFY_189_ONLY=sounds, files from LADS_VERIFY_SOUNDS): the common uploaded-sounds routine (SoundsQa) with 1.8.9's
 * pig (on the integrated server), the player's own blow (PlayerControllerMP.attackEntity, Forge's attack event) and screenshots
 * (lads-qa/screenshots/sounds-*.png).
 */
final class Probe190Sounds {
    private static SoundsQa qa;
    private static volatile int target = -1, archer = -1;
    /** The stand-in player's id (client only). */
    private static final int STAND_IN = Integer.MAX_VALUE - 7;

    private Probe190Sounds() {}

    static boolean run(Minecraft mc) throws Exception {
        if (qa == null && (qa = SoundsQa.fromEnv()) == null) throw new IllegalStateException(SoundsQa.ENV + " is not set");
        if (!qa.tick(GAME)) return retry(1);
        check(qa.failures().isEmpty(), "uploaded sounds: " + qa.failures());
        return after(5);
    }

    private static final SoundsQa.Game GAME = new SoundsQa.Game() {
        @Override public void spawnTarget(final boolean tough) {
            final Minecraft mc = Minecraft.getMinecraft();
            final UUID id = mc.thePlayer.getUniqueID();
            final int previous = target;
            target = -1;
            mc.getIntegratedServer().addScheduledTask(() -> {
                EntityPlayerMP player = mc.getIntegratedServer().getConfigurationManager().getPlayerByUUID(id);
                if (player == null) return;
                Entity old = previous >= 0 ? player.worldObj.getEntityByID(previous) : null;
                if (old != null) old.setDead();
                EntityPig pig = new EntityPig(player.worldObj);
                double yaw = Math.toRadians(player.rotationYaw);
                pig.setLocationAndAngles(player.posX - Math.sin(yaw) * 2.5, player.posY, player.posZ + Math.cos(yaw) * 2.5, 0, 0);
                pig.setNoAI(true);
                if (tough) pig.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(1000);
                player.worldObj.spawnEntityInWorld(pig);
                pig.setHealth(tough ? 1000 : .5F);
                target = pig.getEntityId();
            });
        }

        @Override public boolean attackTarget() {
            Minecraft mc = Minecraft.getMinecraft();
            Entity mob = target >= 0 ? mc.theWorld.getEntityByID(target) : null;
            if (!(mob instanceof EntityLivingBase) || !mob.isEntityAlive()) return false;
            mc.playerController.attackEntity(mc.thePlayer, mob); // the player's own blow (Forge's attack event, KillBanner189)
            return true;
        }

        @Override public com.thelads.core.client.gui.LadsSettingsScreen openSettings(String module) {
            LadsSettingsScreen189 menu = new LadsSettingsScreen189(null);
            Minecraft.getMinecraft().displayGuiScreen(menu);
            menu.openModule(module);
            return menu.ui();
        }

        @Override public void closeScreen() { Minecraft.getMinecraft().displayGuiScreen(null); }

        @Override public void screenshot(String name) { CoreProbe.screenshot(Minecraft.getMinecraft(), name); }

        @Override public void spawnArcheryTarget(final double distance, final boolean player) {
            removeArcheryTarget();
            final Minecraft mc = Minecraft.getMinecraft();
            double yaw = Math.toRadians(mc.thePlayer.rotationYaw);
            final double x = mc.thePlayer.posX - Math.sin(yaw) * distance, y = mc.thePlayer.posY, z = mc.thePlayer.posZ + Math.cos(yaw) * distance;
            final float facing = mc.thePlayer.rotationYaw + 180;
            if (player) {
                EntityOtherPlayerMP standIn = new EntityOtherPlayerMP(mc.theWorld, new GameProfile(UUID.randomUUID(), "LadsArcheryTarget"));
                standIn.setPositionAndRotation(x, y, z, facing, 0);
                standIn.rotationYawHead = standIn.renderYawOffset = facing;
                mc.theWorld.addEntityToWorld(STAND_IN, standIn);
                archer = STAND_IN;
                return;
            }
            final UUID id = mc.thePlayer.getUniqueID();
            mc.getIntegratedServer().addScheduledTask(() -> {
                EntityPlayerMP sp = mc.getIntegratedServer().getConfigurationManager().getPlayerByUUID(id);
                if (sp == null) return;
                // A clear range: other mobs of the QA world (earlier probes') would take arrows meant for the target.
                for (net.minecraft.entity.EntityLiving mob : sp.worldObj.getEntitiesWithinAABB(net.minecraft.entity.EntityLiving.class, sp.getEntityBoundingBox().expand(48, 48, 48))) mob.setDead();
                EntityVillager villager = new EntityVillager(sp.worldObj);
                villager.setLocationAndAngles(x, y, z, facing, 0);
                villager.rotationYawHead = villager.renderYawOffset = facing;
                villager.setNoAI(true);
                villager.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(1000);
                sp.worldObj.spawnEntityInWorld(villager);
                villager.setHealth(1000);
                archer = villager.getEntityId();
            });
        }

        @Override public boolean archeryTargetReady() {
            return archer >= 0 && Minecraft.getMinecraft().theWorld.getEntityByID(archer) != null;
        }

        @Override public void shootTarget(boolean head) {
            final Minecraft mc = Minecraft.getMinecraft();
            Entity target = mc.theWorld.getEntityByID(archer);
            // From 12 blocks to the target's side (seen in flight a few ticks, as a real shot is: the server moves an arrow once
            // before the client hears of it), aimed 0.3 above the mark: an arrow falls about that much on the way.
            final double ax = target.posX, ay = head ? target.posY + target.getEyeHeight() : target.posY + target.height * .45, az = target.posZ;
            double dx = ax - mc.thePlayer.posX, dz = az - mc.thePlayer.posZ, length = Math.sqrt(dx * dx + dz * dz);
            final double sx = ax - dz / length * 12, sz = az + dx / length * 12;
            final UUID id = mc.thePlayer.getUniqueID();
            mc.getIntegratedServer().addScheduledTask(() -> {
                EntityPlayerMP sp = mc.getIntegratedServer().getConfigurationManager().getPlayerByUUID(id);
                if (sp == null) return;
                EntityArrow arrow = new EntityArrow(sp.worldObj, sp, 1F);
                arrow.setLocationAndAngles(sx, ay, sz, 0, 0);
                arrow.setThrowableHeading(ax - sx, .3, az - sz, 3F, 0F);
                sp.worldObj.spawnEntityInWorld(arrow);
            });
        }

        @Override public void arrowHitPlayerPackets() {
            Minecraft mc = Minecraft.getMinecraft();
            Entity standIn = mc.theWorld.getEntityByID(archer);
            if (standIn != null) mc.getNetHandler().handleEntityStatus(new S19PacketEntityStatus(standIn, (byte) 2));
            mc.getNetHandler().handleChangeGameState(new S2BPacketChangeGameState(6, 0F));
        }

        @Override public void removeArcheryTarget() {
            final Minecraft mc = Minecraft.getMinecraft();
            final int old = archer;
            archer = -1;
            if (old == STAND_IN) mc.theWorld.removeEntityFromWorld(STAND_IN);
            else if (old >= 0) {
                final UUID id = mc.thePlayer.getUniqueID();
                mc.getIntegratedServer().addScheduledTask(() -> {
                    EntityPlayerMP sp = mc.getIntegratedServer().getConfigurationManager().getPlayerByUUID(id);
                    Entity entity = sp == null ? null : sp.worldObj.getEntityByID(old);
                    if (entity != null) entity.setDead();
                });
            }
        }
    };
}
