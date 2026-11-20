package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;

import com.thelads.core.client.ladsapi.LadsApiClient;
import com.thelads.core.client.ladsapi.minigame.Caps;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * QA only (LADS_VERIFY_189_ONLY=mgworld): the minigame API's world half (camera, fx, waypoint, telegraph, ghost) fed with the same
 * JSON a server sends, through LadsApiClient.receive, in the QA world, each feature photographed (lads-qa/screenshots/mg-*.png).
 * Client-only blocks and a cow are
 * put in the world and removed again.
 */
final class ProbeMgWorld189 {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    static final List<CoreProbe.Step> STEPS = new ArrayList<CoreProbe.Step>();
    private static double px, py, pz;
    private static EntityCow cow;
    private static net.minecraft.entity.EntityLivingBase attackMob;
    private static double attackHealth;
    private static double[] attackPos;
    private static final List<BlockPos> WALL = new ArrayList<BlockPos>();

    private static void send(String json) {
        LadsApiClient.receive(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String n(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    /** A step that does something, then waits this many ticks. */
    private static void act(final int wait, final Runnable what) {
        STEPS.add(new CoreProbe.Step() {
            public boolean run(Minecraft mc) {
                what.run();
                return after(wait);
            }
        });
    }

    private static void shot(final String name, final int wait) {
        STEPS.add(new CoreProbe.Step() {
            public boolean run(Minecraft mc) {
                CoreProbe.screenshot(mc, "mg-" + name);
                LOG.info("MGQA shot {} view={} cam={} hud={}", name, mc.getRenderViewEntity() == mc.thePlayer ? "player" : "OTHER",
                    Minigame.camera.active(Minigame.now()), Minigame.camera.hidesHud(Minigame.now()));
                return after(wait);
            }
        });
    }

    static {
        STEPS.add(new CoreProbe.Step() {
            public boolean run(Minecraft mc) {
                px = mc.thePlayer.posX;
                py = Math.floor(mc.thePlayer.posY + 0.001);
                pz = mc.thePlayer.posZ;
                mc.thePlayer.rotationYaw = 0;
                mc.thePlayer.prevRotationYaw = 0;
                mc.thePlayer.rotationPitch = 0;
                mc.thePlayer.prevRotationPitch = 0;
                mc.gameSettings.thirdPersonView = 0;
                mc.gameSettings.hideGUI = false;
                cow = new EntityCow(mc.theWorld);
                cow.setPosition(px + 3, py, pz + 6);
                mc.theWorld.spawnEntityInWorld(cow);
                send("{\"t\":\"hello\",\"v\":1,\"server\":\"MG QA\",\"caps\":[\"camera\",\"fx\",\"waypoint\",\"telegraph\",\"ghost\"]}");
                LOG.info("MGQA setup pos={},{},{} cow={} caps camera={} fx={} waypoint={} telegraph={} ghost={} run={}", n(px), n(py), n(pz),
                    cow.getUniqueID(), Caps.declared(Caps.CAMERA), Caps.declared(Caps.FX), Caps.declared(Caps.WAYPOINT),
                    Caps.declared(Caps.TELEGRAPH), Caps.declared(Caps.GHOST), Caps.declared(Caps.RUN));
                return after(40);
            }
        });
        shot("00-baseline", 5);

        // ---- camera
        act(30, new Runnable() { public void run() {
            send("{\"t\":\"camera\",\"mode\":\"fixed\",\"x\":" + px + ",\"y\":" + (py + 2.5) + ",\"z\":" + (pz + 6) + ",\"yaw\":180,\"pitch\":12,\"ms\":30000}");
        } });
        shot("10-cam-fixed", 5);
        STEPS.add(new CoreProbe.Step() { // a pick from the real player while the shot looks elsewhere: straight down at the block under the feet
            public boolean run(Minecraft mc) {
                mc.thePlayer.rotationPitch = 90;
                mc.thePlayer.prevRotationPitch = 90;
                return after(8);
            }
        });
        STEPS.add(new CoreProbe.Step() {
            public boolean run(Minecraft mc) {
                MovingObjectPosition mop = mc.objectMouseOver;
                boolean ok = mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && mop.getBlockPos().getY() == (int) py - 1
                    && mop.getBlockPos().getX() == (int) Math.floor(px) && mop.getBlockPos().getZ() == (int) Math.floor(pz);
                LOG.info("MGQA pick {} mouseOver={} viewIsPlayerBetweenFrames={}", ok ? "OK (real player's block)" : "FAIL", mop,
                    mc.getRenderViewEntity() == mc.thePlayer);
                mc.thePlayer.rotationPitch = 0;
                mc.thePlayer.prevRotationPitch = 0;
                return after(5);
            }
        });
        // pick safety: a real attack at a mob in the crosshair while the fixed shot looks from elsewhere (the integrated server owns the mob)
        act(25, new Runnable() { public void run() {
            final Minecraft mc = Minecraft.getMinecraft();
            mc.thePlayer.rotationYaw = mc.thePlayer.prevRotationYaw = 0;
            mc.thePlayer.rotationPitch = mc.thePlayer.prevRotationPitch = 20;
            final net.minecraft.server.MinecraftServer server = mc.getIntegratedServer();
            server.addScheduledTask(new Runnable() { public void run() {
                EntityCow target = new EntityCow(server.worldServerForDimension(0));
                target.setPosition(px, py, pz + 2.5);
                target.setNoAI(true);
                server.worldServerForDimension(0).spawnEntityInWorld(target);
            } });
        } });
        STEPS.add(new CoreProbe.Step() {
            public boolean run(Minecraft mc) {
                MovingObjectPosition mop = mc.objectMouseOver;
                if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY || !(mop.entityHit instanceof net.minecraft.entity.EntityLivingBase)) {
                    LOG.info("MGQA attack FAIL: no mob under the crosshair, mouseOver={}", mop);
                    return after(1);
                }
                net.minecraft.entity.EntityLivingBase hit = (net.minecraft.entity.EntityLivingBase) mop.entityHit;
                LOG.info("MGQA attack before: mob health={} player pos={},{},{} yaw={} pitch={} view={} cam={}", hit.getHealth(), n(mc.thePlayer.posX),
                    n(mc.thePlayer.posY), n(mc.thePlayer.posZ), n(mc.thePlayer.rotationYaw), n(mc.thePlayer.rotationPitch),
                    mc.getRenderViewEntity() == mc.thePlayer ? "player" : "OTHER", Minigame.camera.active(Minigame.now()));
                attackMob = hit;
                attackHealth = hit.getHealth();
                attackPos = new double[] {mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch};
                mc.playerController.attackEntity(mc.thePlayer, hit);
                mc.thePlayer.swingItem();
                return after(10);
            }
        });
        STEPS.add(new CoreProbe.Step() {
            public boolean run(Minecraft mc) {
                if (attackMob == null) return after(1);
                double[] now = {mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch};
                boolean same = true;
                for (int i = 0; i < 5; i++) same &= Math.abs(now[i] - attackPos[i]) < 1e-6;
                boolean hurt = attackMob.getHealth() < attackHealth;
                LOG.info("MGQA attack after: mob health={} (was {}) player pos={},{},{} yaw={} pitch={} -> {}", attackMob.getHealth(), attackHealth,
                    n(now[0]), n(now[1]), n(now[2]), n(now[3]), n(now[4]), hurt && same ? "OK (mob hurt, player pos/rotation unchanged)" : "FAIL hurt=" + hurt + " same=" + same);
                mc.thePlayer.rotationPitch = mc.thePlayer.prevRotationPitch = 0;
                final net.minecraft.server.MinecraftServer server = mc.getIntegratedServer();
                final int id = attackMob.getEntityId();
                server.addScheduledTask(new Runnable() { public void run() {
                    net.minecraft.entity.Entity e = server.worldServerForDimension(0).getEntityByID(id);
                    if (e != null) e.setDead();
                } });
                return after(5);
            }
        });
        // Freelook wins over a shot: detached, the view is Freelook's (behind the player, looking +Z), not the shot's (in front, looking -Z)
        STEPS.add(new CoreProbe.Step() {
            public boolean run(Minecraft mc) {
                com.thelads.core.modules.FreelookModule look = com.thelads.core.modules.FreelookModule.get();
                look.setEnabled(true);
                look.key(true);
                return after(20);
            }
        });
        STEPS.add(new CoreProbe.Step() {
            public boolean run(Minecraft mc) {
                com.thelads.core.modules.FreelookModule look = com.thelads.core.modules.FreelookModule.get();
                LOG.info("MGQA freelook detached={} thirdPerson={} cam={}", look.detached(), mc.gameSettings.thirdPersonView, Minigame.camera.active(Minigame.now()));
                return after(1);
            }
        });
        shot("17-freelook-over-shot", 5);
        STEPS.add(new CoreProbe.Step() {
            public boolean run(Minecraft mc) {
                com.thelads.core.modules.FreelookModule look = com.thelads.core.modules.FreelookModule.get();
                look.key(false);
                return after(20);
            }
        });
        STEPS.add(new CoreProbe.Step() {
            public boolean run(Minecraft mc) {
                com.thelads.core.modules.FreelookModule look = com.thelads.core.modules.FreelookModule.get();
                LOG.info("MGQA freelook released detached={} thirdPerson={} cam={}", look.detached(), mc.gameSettings.thirdPersonView, Minigame.camera.active(Minigame.now()));
                return after(1);
            }
        });
        shot("18-shot-back-after-freelook", 5);
        act(30, new Runnable() { public void run() {
            send("{\"t\":\"camera\",\"mode\":\"spline\",\"ms\":8000,\"ease\":\"smooth\",\"fov\":80,\"points\":[{\"x\":" + (px - 8) + ",\"y\":" + (py + 3)
                + ",\"z\":" + (pz + 8) + ",\"yaw\":210,\"pitch\":15},{\"x\":" + (px + 8) + ",\"y\":" + (py + 4) + ",\"z\":" + (pz + 8) + ",\"yaw\":150,\"pitch\":15}]}");
        } });
        shot("11-cam-spline", 5);
        act(60, new Runnable() { public void run() {
            send("{\"t\":\"camera\",\"mode\":\"orbit\",\"x\":" + px + ",\"y\":" + py + ",\"z\":" + pz + ",\"radius\":5,\"height\":2,\"period_ms\":8000,\"ms\":30000}");
        } });
        shot("12-cam-orbit", 5);
        act(60, new Runnable() { public void run() {
            send("{\"t\":\"camera\",\"mode\":\"orbit\",\"entity\":\"" + cow.getUniqueID() + "\",\"radius\":4,\"height\":1.5,\"period_ms\":8000,\"ms\":30000}");
        } });
        shot("13-cam-orbit-entity", 5);
        act(30, new Runnable() { public void run() {
            send("{\"t\":\"camera\",\"mode\":\"fixed\",\"x\":" + px + ",\"y\":" + (py + 2.5) + ",\"z\":" + (pz + 6) + ",\"look_at\":{\"x\":" + px + ",\"y\":" + (py + 1)
                + ",\"z\":" + pz + "},\"bars\":true,\"hide_hud\":true,\"ms\":30000}");
        } });
        shot("14-cam-bars-hidehud", 5);
        act(30, new Runnable() { public void run() {
            send("{\"t\":\"camera\",\"mode\":\"fixed\",\"x\":" + px + ",\"y\":" + (py + 2.5) + ",\"z\":" + (pz + 6) + ",\"yaw\":180,\"pitch\":12,\"fov\":30,\"ms\":30000}");
        } });
        shot("15-cam-fov30", 5);
        act(30, new Runnable() { public void run() {
            send("{\"t\":\"camera\",\"mode\":\"off\"}");
        } });
        shot("16-cam-off", 5);

        // ---- fx (the shader kinds and the overlays)
        String[] shaders = {"blur", "grayscale", "sepia", "invert"};
        for (final String s : shaders) {
            act(25, new Runnable() { public void run() { send("{\"t\":\"fx\",\"ms\":20000,\"shader\":\"" + s + "\"}"); } });
            shot("20-fx-" + s, 5);
        }
        act(15, new Runnable() { public void run() { send("{\"t\":\"fx\",\"ms\":20000,\"vignette\":{\"color\":\"#FF0000\",\"intensity\":0.8}}"); } });
        shot("21-fx-vignette", 5);
        act(15, new Runnable() { public void run() { send("{\"t\":\"fx\",\"ms\":20000,\"tint\":{\"color\":\"#2244FF\",\"intensity\":0.5}}"); } });
        shot("22-fx-tint", 5);
        act(2, new Runnable() { public void run() { send("{\"t\":\"fx\",\"flash\":{\"color\":\"#FFFFFF\",\"ms\":2000}}"); } });
        shot("23-fx-flash", 5);
        act(60, new Runnable() { public void run() { send("{\"t\":\"fx\"}"); } });
        shot("24-fx-cleared", 5);

        // ---- waypoints
        act(30, new Runnable() { public void run() {
            String[] glyphs = {"flag", "bed", "skull", "star", "arrow", "shield", "sword", "question", "exclamation", "circle", "diamond"};
            for (int i = 0; i < glyphs.length; i++) {
                send("{\"t\":\"waypoint\",\"op\":\"set\",\"id\":\"g" + i + "\",\"x\":" + (px + (5 - i) * 2.2) + ",\"y\":" + py + ",\"z\":" + (pz + 14)
                    + ",\"label\":\"" + glyphs[i] + "\",\"color\":\"#" + (i % 2 == 0 ? "FFD700" : "55FFFF") + "\",\"icon\":\"" + glyphs[i] + "\",\"distance\":true,\"beam\":" + (i == 3) + "}");
            }
            send("{\"t\":\"waypoint\",\"op\":\"set\",\"id\":\"item\",\"x\":" + (px + 12) + ",\"y\":" + py + ",\"z\":" + (pz + 14)
                + ",\"label\":\"Diamond item\",\"color\":\"#40FF80\",\"icon\":\"minecraft:diamond\",\"distance\":true}");
        } });
        shot("30-waypoints-glyphs", 5);
        act(30, new Runnable() { public void run() {
            send("{\"t\":\"waypoint\",\"op\":\"clear\"}");
            send("{\"t\":\"waypoint\",\"op\":\"set\",\"id\":\"mob\",\"entity\":\"" + cow.getUniqueID() + "\",\"label\":\"Cow\",\"icon\":\"star\",\"color\":\"#FF55FF\"}");
        } });
        shot("31-waypoint-entity", 5);
        act(30, new Runnable() { public void run() { cow.setDead(); } });
        shot("32-waypoint-entity-dead", 5);
        act(30, new Runnable() { // a stone wall between the player and the markers (client-side blocks)
            public void run() {
                Minecraft mc = Minecraft.getMinecraft();
                send("{\"t\":\"waypoint\",\"op\":\"clear\"}");
                for (int dx = -9; dx <= 9; dx++) {
                    for (int dy = 0; dy <= 8; dy++) {
                        BlockPos p = new BlockPos(px + dx, py + dy, pz + 5);
                        mc.theWorld.setBlockState(p, Blocks.stone.getDefaultState());
                        WALL.add(p);
                    }
                }
                send("{\"t\":\"waypoint\",\"op\":\"set\",\"id\":\"hidden\",\"x\":" + (px - 3) + ",\"y\":" + py + ",\"z\":" + (pz + 12) + ",\"label\":\"Behind wall (hidden)\",\"icon\":\"flag\",\"color\":\"#FF5555\"}");
                send("{\"t\":\"waypoint\",\"op\":\"set\",\"id\":\"through\",\"x\":" + (px + 3) + ",\"y\":" + py + ",\"z\":" + (pz + 12) + ",\"label\":\"Through walls\",\"icon\":\"flag\",\"color\":\"#55FF55\",\"through_walls\":true}");
            }
        });
        shot("33-waypoint-wall", 5);
        act(30, new Runnable() { public void run() {
            Minecraft mc = Minecraft.getMinecraft();
            for (BlockPos p : WALL) mc.theWorld.setBlockToAir(p);
            WALL.clear();
            send("{\"t\":\"waypoint\",\"op\":\"clear\"}");
        } });

        // ---- telegraphs
        act(20, new Runnable() { public void run() {
            send("{\"t\":\"telegraph\",\"op\":\"set\",\"id\":\"c\",\"shape\":\"circle\",\"x\":" + (px + 8) + ",\"y\":" + py + ",\"z\":" + (pz + 10) + ",\"radius\":3,\"color\":\"#FF2020\",\"ms\":25000,\"fill_ms\":3000}");
            send("{\"t\":\"telegraph\",\"op\":\"set\",\"id\":\"r\",\"shape\":\"ring\",\"x\":" + (px + 3) + ",\"y\":" + py + ",\"z\":" + (pz + 10) + ",\"radius\":3,\"inner\":1.5,\"color\":\"#FF8800\",\"ms\":25000,\"fill_ms\":3000}");
            send("{\"t\":\"telegraph\",\"op\":\"set\",\"id\":\"k\",\"shape\":\"cone\",\"x\":" + (px - 1) + ",\"y\":" + py + ",\"z\":" + (pz + 5) + ",\"radius\":7,\"angle\":90,\"yaw\":0,\"color\":\"#FFFF00\",\"ms\":25000,\"fill_ms\":3000}");
            send("{\"t\":\"telegraph\",\"op\":\"set\",\"id\":\"l\",\"shape\":\"line\",\"x\":" + (px - 5) + ",\"y\":" + py + ",\"z\":" + (pz + 5) + ",\"yaw\":0,\"length\":8,\"width\":1.5,\"color\":\"#20FF20\",\"ms\":25000,\"fill_ms\":3000}");
            send("{\"t\":\"telegraph\",\"op\":\"set\",\"id\":\"x\",\"shape\":\"rect\",\"x\":" + (px - 9) + ",\"y\":" + py + ",\"z\":" + (pz + 12) + ",\"yaw\":0,\"length\":6,\"width\":3,\"color\":\"#2080FF\",\"ms\":25000,\"fill_ms\":3000}");
        } });
        shot("40-telegraph-partial", 5);
        act(70, new Runnable() { public void run() { } });
        shot("41-telegraph-full", 5);
        act(20, new Runnable() { public void run() { send("{\"t\":\"telegraph\",\"op\":\"clear\"}"); } });
        shot("42-telegraph-cleared", 5);

        // ---- ghost (needs the run clock)
        act(20, new Runnable() { public void run() {
            send("{\"t\":\"run\",\"op\":\"start\",\"course\":\"c1\",\"name\":\"QA\",\"time_ms\":0}");
            send("{\"t\":\"ghost\",\"op\":\"play\",\"id\":\"pb\",\"course\":\"c1\",\"name\":\"Record: Steve\",\"frames\":[[0," + (px - 4) + "," + py + "," + (pz + 5)
                + ",270,0],[2500," + px + "," + py + "," + (pz + 5) + ",270,0],[5000," + (px + 4) + "," + py + "," + (pz + 5) + ",270,0]]}");
        } });
        STEPS.add(new CoreProbe.Step() {
            public boolean run(Minecraft mc) {
                LOG.info("MGQA ghost armed={} visible={}", Minigame.ghosts.armedCount(), Minigame.ghosts.visible(Minigame.run, Minigame.now()).size());
                return after(30);
            }
        });
        shot("50-ghost-a", 25);
        shot("51-ghost-b", 5);

        // ---- reset clears everything (state still set: a waypoint, a telegraph, a camera, an fx)
        act(20, new Runnable() { public void run() {
            send("{\"t\":\"waypoint\",\"op\":\"set\",\"id\":\"z\",\"x\":" + px + ",\"y\":" + py + ",\"z\":" + (pz + 8) + ",\"label\":\"Z\"}");
            send("{\"t\":\"telegraph\",\"op\":\"set\",\"id\":\"z\",\"shape\":\"circle\",\"x\":" + px + ",\"y\":" + py + ",\"z\":" + (pz + 8) + ",\"radius\":2,\"ms\":20000}");
            send("{\"t\":\"camera\",\"mode\":\"fixed\",\"x\":" + px + ",\"y\":" + (py + 5) + ",\"z\":" + (pz + 10) + ",\"yaw\":180,\"pitch\":30,\"ms\":20000}");
            send("{\"t\":\"fx\",\"ms\":20000,\"tint\":{\"color\":\"#FF0000\",\"intensity\":0.5}}");
        } });
        shot("60-before-reset", 5);
        act(20, new Runnable() { public void run() { send("{\"t\":\"reset\"}"); } });
        STEPS.add(new CoreProbe.Step() {
            public boolean run(Minecraft mc) {
                long now = Minigame.now();
                boolean empty = Minigame.waypoints.all().isEmpty() && Minigame.telegraphs.active(now).isEmpty() && !Minigame.camera.active(now)
                    && Minigame.fx.tint(now) == null && Minigame.ghosts.armedCount() == 0 && !Minigame.run.running();
                LOG.info("MGQA reset {} view={}", empty ? "OK (all state empty)" : "FAIL", mc.getRenderViewEntity() == mc.thePlayer ? "player" : "OTHER");
                return after(5);
            }
        });
        shot("61-after-reset", 5);
        // disconnect: state set again, then the world is left; the transport clears on the disconnect
        act(10, new Runnable() { public void run() {
            send("{\"t\":\"hello\",\"v\":1,\"server\":\"MG QA\"}");
            send("{\"t\":\"waypoint\",\"op\":\"set\",\"id\":\"z\",\"x\":" + px + ",\"y\":" + py + ",\"z\":" + (pz + 8) + ",\"label\":\"Z\"}");
            send("{\"t\":\"telegraph\",\"op\":\"set\",\"id\":\"z\",\"shape\":\"circle\",\"x\":" + px + ",\"y\":" + py + ",\"z\":" + (pz + 8) + ",\"radius\":2,\"ms\":20000}");
            send("{\"t\":\"camera\",\"mode\":\"fixed\",\"x\":" + px + ",\"y\":" + (py + 5) + ",\"z\":" + (pz + 10) + ",\"yaw\":180,\"pitch\":30,\"ms\":20000}");
            LOG.info("MGQA before disconnect: waypoints={} telegraphs={} camera={}", Minigame.waypoints.all().size(),
                Minigame.telegraphs.active(Minigame.now()).size(), Minigame.camera.active(Minigame.now()));
        } });
        STEPS.add(new CoreProbe.Step() {
            public boolean run(Minecraft mc) {
                mc.theWorld.sendQuittingDisconnectingPacket();
                mc.loadWorld(null);
                mc.displayGuiScreen(new net.minecraft.client.gui.GuiMainMenu());
                return after(10);
            }
        });
        STEPS.add(new CoreProbe.Step() { public boolean run(Minecraft mc) { return afterLeave(mc); } });
    }

    /** After the disconnect: the state is gone with the connection. */
    static boolean afterLeave(Minecraft mc) {
        long now = Minigame.now();
        boolean empty = Minigame.waypoints.all().isEmpty() && Minigame.telegraphs.active(now).isEmpty() && !Minigame.camera.active(now);
        LOG.info("MGQA disconnect {} (waypoints={} telegraphs={} camera={})", empty ? "OK (all state empty)" : "FAIL", Minigame.waypoints.all().size(),
            Minigame.telegraphs.active(now).size(), Minigame.camera.active(now));
        return after(0);
    }

    private ProbeMgWorld189() {}
}
