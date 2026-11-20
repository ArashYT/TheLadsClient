package com.thelads.core.v26_2.ladsapi.minigame.world;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.client.ladsapi.minigame.GhostState;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ghosts: a dummy player that is never added to the level (no collision, no packets, no tick, seen by nobody else) is posed from the
 * recorded frame and drawn through the normal player renderer, translucent through the flag below that GhostOpacityMixin reads.
 * The skin is whatever the client already has for the uuid (a connected player's), else the default one; nothing is fetched.
 */
public final class GhostRender26 {
    /** True only while a ghost is being submitted; GhostOpacityMixin makes the model translucent then. */
    public static boolean rendering;

    private static final Map<String, Dummy> DUMMIES = new HashMap<>();
    private static int nextId;
    private static final Vec3 LABEL = new Vec3(0, 2.1, 0);

    private static final class Dummy {
        RemotePlayer player;
        double lastX = Double.NaN, lastZ = Double.NaN;
        long lastTick = -1;
    }

    private GhostRender26() {}

    static void submit(LevelRenderContext context, long now) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || Minigame.ghosts.armedCount() == 0) {
            if (!DUMMIES.isEmpty()) DUMMIES.clear();
            return;
        }
        List<GhostState.Pose> poses = Minigame.ghosts.visible(Minigame.run, now);
        if (poses.isEmpty()) return;
        CameraRenderState camera = context.levelState().cameraRenderState;
        Vec3 cp = camera.pos;
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        PoseStack stack = context.poseStack();
        for (GhostState.Pose p : poses) {
            Dummy d = DUMMIES.get(p.id());
            if (d == null || d.player.level() != level) {
                d = new Dummy();
                UUID id = p.skin() != null ? p.skin() : new UUID(0x9057L, p.id().hashCode());
                d.player = new RemotePlayer(level, new GameProfile(id, "Ghost"));
                d.player.setId(-1_000_000 - nextId++); // render-state extraction asks for an id; negative ones never match a real entity
                DUMMIES.put(p.id(), d);
            }
            pose(d, level, p);
            EntityRenderState state = mc.getEntityRenderDispatcher().extractEntity(d.player, partial);
            state.nameTag = null;
            state.displayFireAnimation = false;
            rendering = true;
            try {
                mc.getEntityRenderDispatcher().submit(state, camera, p.x() - cp.x, p.y() - cp.y, p.z() - cp.z, stack, context.submitNodeCollector());
                if (p.name() != null && !p.name().isEmpty()) {
                    stack.pushPose();
                    stack.translate(p.x() - cp.x, p.y() - cp.y, p.z() - cp.z);
                    context.submitNodeCollector().submitNameTag(stack, LABEL, 0, Component.literal(p.name()), true, state.lightCoords, camera);
                    stack.popPose();
                }
            } finally {
                rendering = false;
            }
        }
    }

    /** Puts the dummy exactly on the pose (old values too, so nothing lags) and steps its walk animation once per game tick. */
    private static void pose(Dummy d, ClientLevel level, GhostState.Pose p) {
        RemotePlayer e = d.player;
        e.snapTo(p.x(), p.y(), p.z(), (float) p.yaw(), (float) p.pitch());
        e.yHeadRot = e.yHeadRotO = (float) p.yaw();
        e.yBodyRot = e.yBodyRotO = (float) p.yaw();
        long tick = level.getGameTime();
        if (tick != d.lastTick) {
            double moved = Double.isNaN(d.lastX) ? 0 : Math.hypot(p.x() - d.lastX, p.z() - d.lastZ);
            e.walkAnimation.update((float) Math.min(1.0, moved * 4), 0.4f, 1f);
            d.lastTick = tick;
            d.lastX = p.x();
            d.lastZ = p.z();
        }
    }
}
