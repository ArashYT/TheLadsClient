package com.thelads.core.v1_20_1.feature;

import com.thelads.core.client.sound.SoundsQa;
import com.thelads.core.v1_20_1.gui.LadsSettingsScreen1201;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * QA only: the script verb {@code sounds} runs the common uploaded-sounds routine (SoundsQa, files from LADS_VERIFY_SOUNDS) with
 * 1.20.1's pig, attack and screenshots (screenshots/sounds-*.png); a failed check fails the step. Plain Fabric and Zero SMP.
 */
final class SoundsQa1201 {
    private static SoundsQa qa;
    private static volatile int target = -1;

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
    };
}
