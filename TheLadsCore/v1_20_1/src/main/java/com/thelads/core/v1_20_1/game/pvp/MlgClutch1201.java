package com.thelads.core.v1_20_1.game.pvp;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.core.client.mlg.MlgClutchHelper;
import com.thelads.core.modules.MlgCobwebModule;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class MlgClutch1201 {
    private static final Vector3f VEC = new Vector3f();
    private static BlockPos currentTarget = null;
    private static double secondsToImpact = 0;
    private static boolean inWindow = false;
    private static boolean fatal = false;

    private MlgClutch1201() {}

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
            if (check.getY() < mc.level.getMinBuildHeight()) break;
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

    public static void renderWorld(WorldRenderContext context) {
        update();
        if (currentTarget == null) return;
        MlgCobwebModule mod = MlgCobwebModule.get();
        if (mod == null || !mod.ghostBox.get()) return;

        MultiBufferSource buffers = context.consumers();
        if (buffers == null) return;

        Vec3 camera = context.camera().getPosition();
        PoseStack poseStack = context.matrixStack();
        poseStack.pushPose();
        poseStack.translate(currentTarget.getX() - camera.x, currentTarget.getY() - camera.y, currentTarget.getZ() - camera.z);

        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        PoseStack.Pose last = poseStack.last();

        int color = inWindow ? 0xFF00FF33 : (fatal ? 0xFFFF3333 : mod.boxColor.getColor());
        int a = (color >> 24) & 0xFF;
        if (a <= 5) a = 200;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        drawBoxLines(lines, last, 0, 0, 0, 1, 1, 1, r, g, b, a);

        poseStack.popPose();
        if (buffers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch(RenderType.lines());
        }
    }

    private static void drawBoxLines(VertexConsumer consumer, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1, int r, int g, int b, int a) {
        line(consumer, pose, x0, y0, z0, x1, y0, z0, r, g, b, a);
        line(consumer, pose, x1, y0, z0, x1, y0, z1, r, g, b, a);
        line(consumer, pose, x1, y0, z1, x0, y0, z1, r, g, b, a);
        line(consumer, pose, x0, y0, z1, x0, y0, z0, r, g, b, a);

        line(consumer, pose, x0, y1, z0, x1, y1, z0, r, g, b, a);
        line(consumer, pose, x1, y1, z0, x1, y1, z1, r, g, b, a);
        line(consumer, pose, x1, y1, z1, x0, y1, z1, r, g, b, a);
        line(consumer, pose, x0, y1, z1, x0, y1, z0, r, g, b, a);

        line(consumer, pose, x0, y0, z0, x0, y1, z0, r, g, b, a);
        line(consumer, pose, x1, y0, z0, x1, y1, z0, r, g, b, a);
        line(consumer, pose, x1, y0, z1, x1, y1, z1, r, g, b, a);
        line(consumer, pose, x0, y0, z1, x0, y1, z1, r, g, b, a);
    }

    private static void line(VertexConsumer consumer, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1, int r, int g, int b, int a) {
        pose.pose().transformPosition(x0, y0, z0, VEC);
        consumer.vertex(VEC.x, VEC.y, VEC.z).color(r, g, b, a).normal(0, 1, 0).endVertex();
        pose.pose().transformPosition(x1, y1, z1, VEC);
        consumer.vertex(VEC.x, VEC.y, VEC.z).color(r, g, b, a).normal(0, 1, 0).endVertex();
    }

    public static void renderReticle(GuiGraphics graphics, int cx, int cy) {
        if (currentTarget == null) return;
        MlgCobwebModule mod = MlgCobwebModule.get();
        if (mod == null || !mod.timingReticle.get()) return;

        int reticleColor = inWindow ? 0xFF00FF00 : (fatal ? 0xFFFF3333 : 0xFFFFAA00);
        int radius = inWindow ? 10 : 14;

        // Draw brackets around crosshair
        graphics.fill(cx - radius - 2, cy - 1, cx - radius, cy + 1, reticleColor);
        graphics.fill(cx + radius, cy - 1, cx + radius + 2, cy + 1, reticleColor);
        graphics.fill(cx - 1, cy - radius - 2, cx + 1, cy - radius, reticleColor);
        graphics.fill(cx - 1, cy + radius, cx + 1, cy + radius + 2, reticleColor);

        Minecraft mc = Minecraft.getInstance();
        String text = inWindow ? "CLUTCH!" : String.format("%.1fs", secondsToImpact);
        int tw = mc.font.width(text);
        graphics.drawString(mc.font, text, cx - tw / 2, cy + radius + 4, reticleColor, true);
    }
}
