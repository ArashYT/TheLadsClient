package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.retry;

import com.thelads.core.client.sound.SoundsQa;
import com.thelads.core.v1_8_9.gui.LadsSettingsScreen189;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * QA only (LADS_VERIFY_189_ONLY=sounds, files from LADS_VERIFY_SOUNDS): the common uploaded-sounds routine (SoundsQa) with 1.8.9's
 * pig (on the integrated server), the player's own blow (PlayerControllerMP.attackEntity, Forge's attack event) and screenshots
 * (lads-qa/screenshots/sounds-*.png).
 */
final class Probe190Sounds {
    private static SoundsQa qa;
    private static volatile int target = -1;

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

        @Override public void openSettings(String module) {
            LadsSettingsScreen189 menu = new LadsSettingsScreen189(null);
            Minecraft.getMinecraft().displayGuiScreen(menu);
            menu.openModule(module);
        }

        @Override public void closeScreen() { Minecraft.getMinecraft().displayGuiScreen(null); }

        @Override public void screenshot(String name) { CoreProbe.screenshot(Minecraft.getMinecraft(), name); }
    };
}
