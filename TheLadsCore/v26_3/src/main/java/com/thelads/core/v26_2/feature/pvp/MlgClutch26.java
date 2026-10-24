package com.thelads.core.v26_2.feature.pvp;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.core.client.mlg.MlgClutchHelper;
import com.thelads.core.modules.MlgCobwebModule;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class MlgClutch26 {
    private static final Vector3f VEC = new Vector3f();
    private static BlockPos currentTarget = null;
    private static double secondsToImpact = 0;
    private static boolean inWindow = false;
    private static boolean fatal = false;
    private static double camX, camY, camZ;

    private MlgClutch26() {}

    public static void update() {
        currentTarget = null;
        if (!MlgCobwebModule.isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;
        if (player.getAbilities().flying || player.onGround() || player.isInWater() || player.getDeltaMovement().y >= -0.1) return;

        MlgCobwebModule mod = MlgCobwebModule.get();
        if (player.fallDistance < mod.minFallDistance.getIntValue()) return;

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return;
        String id = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        if (!MlgClutchHelper.isClutchItem(id)) return;

        BlockPos playerPos = player.blockPosition();
        for (int dy = 1; dy <= 30; dy++) {
            BlockPos check = playerPos.below(dy);
            if (check.getY() < mc.level.getMinY()) break;
            BlockState bs = mc.level.getBlockState(check);
            if (!bs.isAir() && bs.isSolid()) {
                currentTarget = check.above();
                break;
            }
        }

        if (currentTarget != null) {
            double dist = player.getY() - currentTarget.getY();
            double speed = Math.abs(player.getDeltaMovement().y) * 20.0;
            secondsToImpact = dist / Math.max(0.5, speed);
            inWindow = secondsToImpact <= 0.35 && secondsToImpact >= 0.0;
            fatal = player.fallDistance >= 12.0f;
        }
    }

    public static void renderWorld(LevelRenderContext context) {
        update();
        if (currentTarget == null) return;
        MlgCobwebModule mod = MlgCobwebModule.get();
        if (mod == null || !mod.ghostBox.get()) return;

        Vec3 camera = context.levelState().cameraRenderState.pos;
        camX = camera.x; camY = camera.y; camZ = camera.z;

        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lines(), MlgClutch26::drawBoxLines);
    }

    private static void drawBoxLines(PoseStack.Pose pose, VertexConsumer lines) {
        if (currentTarget == null) return;
        MlgCobwebModule mod = MlgCobwebModule.get();
        if (mod == null) return;

        float x0 = (float) (currentTarget.getX() - camX);
        float y0 = (float) (currentTarget.getY() - camY);
        float z0 = (float) (currentTarget.getZ() - camZ);
        float x1 = x0 + 1.0f, y1 = y0 + 1.0f, z1 = z0 + 1.0f;

        int color = inWindow ? 0xFF00FF33 : (fatal ? 0xFFFF3333 : mod.boxColor.getColor());

        line(lines, pose, x0, y0, z0, x1, y0, z0, color);
        line(lines, pose, x1, y0, z0, x1, y0, z1, color);
        line(lines, pose, x1, y0, z1, x0, y0, z1, color);
        line(lines, pose, x0, y0, z1, x0, y0, z0, color);

        line(lines, pose, x0, y1, z0, x1, y1, z0, color);
        line(lines, pose, x1, y1, z0, x1, y1, z1, color);
        line(lines, pose, x1, y1, z1, x0, y1, z1, color);
        line(lines, pose, x0, y1, z1, x0, y1, z0, color);

        line(lines, pose, x0, y0, z0, x0, y1, z0, color);
        line(lines, pose, x1, y0, z0, x1, y1, z0, color);
        line(lines, pose, x1, y0, z1, x1, y1, z1, color);
        line(lines, pose, x0, y0, z1, x0, y1, z1, color);
    }

    private static void line(VertexConsumer lines, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        pose.transformNormal(dx, dy, dz, VEC);
        float nx = VEC.x, ny = VEC.y, nz = VEC.z;
        pose.pose().transformPosition(x0, y0, z0, VEC);
        lines.addVertex(VEC.x, VEC.y, VEC.z).setColor(color).setNormal(nx, ny, nz).setLineWidth(2);
        pose.pose().transformPosition(x1, y1, z1, VEC);
        lines.addVertex(VEC.x, VEC.y, VEC.z).setColor(color).setNormal(nx, ny, nz).setLineWidth(2);
    }

    public static void renderReticle(GuiGraphicsExtractor graphics, int cx, int cy) {
        if (currentTarget == null) return;
        MlgCobwebModule mod = MlgCobwebModule.get();
        if (mod == null || !mod.timingReticle.get()) return;

        int reticleColor = inWindow ? 0xFF00FF00 : (fatal ? 0xFFFF3333 : 0xFFFFAA00);
        int radius = inWindow ? 10 : 14;

        // Draw brackets around crosshair
        graphics.fill(RenderPipelines.GUI, cx - radius - 2, cy - 1, cx - radius, cy + 1, reticleColor);
        graphics.fill(RenderPipelines.GUI, cx + radius, cy - 1, cx + radius + 2, cy + 1, reticleColor);
        graphics.fill(RenderPipelines.GUI, cx - 1, cy - radius - 2, cx + 1, cy - radius, reticleColor);
        graphics.fill(RenderPipelines.GUI, cx - 1, cy + radius, cx + 1, cy + radius + 2, reticleColor);

        Minecraft mc = Minecraft.getInstance();
        String text = inWindow ? "CLUTCH!" : String.format("%.1fs", secondsToImpact);
        int tw = mc.font.width(text);
        graphics.text(mc.font, text, cx - tw / 2, cy + radius + 4, reticleColor, true);
    }
}
