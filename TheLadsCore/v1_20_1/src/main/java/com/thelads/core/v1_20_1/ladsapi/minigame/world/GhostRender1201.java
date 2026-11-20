package com.thelads.core.v1_20_1.ladsapi.minigame.world;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.client.ladsapi.minigame.GhostState;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Ghosts: a dummy player that is never added to the level (no collision, no packets, no tick, seen by nobody else) is posed from the
 * recorded frame and drawn through the normal player renderer, translucent through the flag below that MgGhostOpacityMixin1201 reads.
 * The skin is whatever the client already has for the uuid (a connected player's), else the default one; nothing is fetched.
 */
public final class GhostRender1201 {
    /** True only while a ghost is being drawn; the opacity mixin makes the model translucent and drops its name tag then. */
    public static boolean rendering;

    private static final Map<String, Dummy> DUMMIES = new HashMap<>();
    private static int nextId;

    private static final class Dummy {
        RemotePlayer player;
        double lastX = Double.NaN, lastZ = Double.NaN;
        long lastTick = -1;
    }

    private GhostRender1201() {}

    static void draw(WorldRenderContext context, long now) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || Minigame.ghosts.armedCount() == 0) {
            if (!DUMMIES.isEmpty()) DUMMIES.clear();
            return;
        }
        List<GhostState.Pose> poses = Minigame.ghosts.visible(Minigame.run, now);
        if (poses.isEmpty() || context.consumers() == null) return;
        Camera camera = context.camera();
        Vec3 cp = camera.getPosition();
        float partial = context.tickDelta();
        PoseStack stack = context.matrixStack();
        for (GhostState.Pose p : poses) {
            Dummy d = DUMMIES.get(p.id());
            if (d == null || d.player.level() != level) {
                d = new Dummy();
                UUID id = p.skin() != null ? p.skin() : new UUID(0x9057L, p.id().hashCode());
                d.player = new RemotePlayer(level, new GameProfile(id, "Ghost"));
                d.player.setId(-1_000_000 - nextId++); // negative ids never match a real entity
                DUMMIES.put(p.id(), d);
            }
            pose(d, level, p);
            rendering = true;
            try {
                mc.getEntityRenderDispatcher().render(d.player, p.x() - cp.x, p.y() - cp.y, p.z() - cp.z, (float) p.yaw(), partial, stack,
                    context.consumers(), 0xF000F0);
                if (p.name() != null && !p.name().isEmpty()) label(mc, camera, stack, context, p, cp);
            } finally {
                rendering = false;
            }
        }
    }

    /** The ghost's name over its head, facing the camera, like a name tag. */
    private static void label(Minecraft mc, Camera camera, PoseStack stack, WorldRenderContext context, GhostState.Pose p, Vec3 cp) {
        stack.pushPose();
        stack.translate(p.x() - cp.x, p.y() - cp.y + 2.1, p.z() - cp.z);
        stack.mulPose(camera.rotation());
        stack.scale(-0.025f, -0.025f, 0.025f);
        Matrix4f m = stack.last().pose();
        Font font = mc.font;
        font.drawInBatch(p.name(), -font.width(p.name()) / 2f, 0, 0x80FFFFFF, false, m, context.consumers(), Font.DisplayMode.SEE_THROUGH, 0x40000000, 0xF000F0);
        stack.popPose();
    }

    /** Puts the dummy exactly on the pose (old values too, so nothing lags) and steps its walk animation once per game tick. */
    private static void pose(Dummy d, ClientLevel level, GhostState.Pose p) {
        RemotePlayer e = d.player;
        e.moveTo(p.x(), p.y(), p.z(), (float) p.yaw(), (float) p.pitch());
        e.yHeadRot = e.yHeadRotO = (float) p.yaw();
        e.yBodyRot = e.yBodyRotO = (float) p.yaw();
        long tick = level.getGameTime();
        if (tick != d.lastTick) {
            double moved = Double.isNaN(d.lastX) ? 0 : Math.hypot(p.x() - d.lastX, p.z() - d.lastZ);
            e.walkAnimation.update((float) Math.min(1.0, moved * 4), 0.4f);
            d.lastTick = tick;
            d.lastX = p.x();
            d.lastZ = p.z();
        }
    }
}