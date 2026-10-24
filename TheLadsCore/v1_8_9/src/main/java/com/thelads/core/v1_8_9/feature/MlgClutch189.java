package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.mlg.MlgClutchHelper;
import com.thelads.core.modules.MlgCobwebModule;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import org.lwjgl.opengl.GL11;

public final class MlgClutch189 {
    private static BlockPos currentTarget = null;
    private static double secondsToImpact = 0;
    private static boolean inWindow = false;
    private static boolean fatal = false;

    private MlgClutch189() {}

    public static void update() {
        currentTarget = null;
        if (!MlgCobwebModule.isActive()) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        if (player == null || mc.theWorld == null) return;
        if (player.capabilities.isFlying || player.onGround || player.isInWater() || player.motionY >= -0.1) return;

        MlgCobwebModule mod = MlgCobwebModule.get();
        if (player.fallDistance < mod.minFallDistance.getIntValue()) return;

        ItemStack held = player.getHeldItem();
        if (held == null || held.getItem() == null) return;
        String name = held.getItem().getRegistryName() != null ? held.getItem().getRegistryName() : held.getUnlocalizedName();
        if (!MlgClutchHelper.isClutchItem(name)) return;

        BlockPos playerPos = new BlockPos(player.posX, player.posY, player.posZ);
        for (int dy = 1; dy <= 30; dy++) {
            BlockPos check = playerPos.down(dy);
            if (check.getY() < 0) break;
            IBlockState bs = mc.theWorld.getBlockState(check);
            if (bs.getBlock().getMaterial().isSolid()) {
                currentTarget = check.up();
                break;
            }
        }

        if (currentTarget != null) {
            double dist = player.posY - currentTarget.getY();
            double speed = Math.abs(player.motionY) * 20.0;
            secondsToImpact = dist / Math.max(0.5, speed);
            inWindow = secondsToImpact <= 0.35 && secondsToImpact >= 0.0;
            fatal = player.fallDistance >= 12.0f;
        }
    }

    public static void renderWorld(float partialTicks) {
        update();
        if (currentTarget == null) return;
        MlgCobwebModule mod = MlgCobwebModule.get();
        if (mod == null || !mod.ghostBox.get()) return;

        Minecraft mc = Minecraft.getMinecraft();
        RenderManager rm = mc.getRenderManager();
        double renderX = rm.viewerPosX;
        double renderY = rm.viewerPosY;
        double renderZ = rm.viewerPosZ;

        int color = mod.boxColor.getColor();
        float a = ((color >> 24) & 0xFF) / 255.0f;
        if (a <= 0.01f) a = 0.35f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        if (inWindow) {
            r = 0.0f; g = 1.0f; b = 0.2f; a = 0.6f;
        }

        AxisAlignedBB box = new AxisAlignedBB(
            currentTarget.getX() - renderX,
            currentTarget.getY() - renderY,
            currentTarget.getZ() - renderZ,
            currentTarget.getX() + 1.0 - renderX,
            currentTarget.getY() + 1.0 - renderY,
            currentTarget.getZ() + 1.0 - renderZ
        );

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();
        GlStateManager.depthMask(false);
        GL11.glLineWidth(2.5f);

        RenderGlobal.drawOutlinedBoundingBox(box, (int) (r * 255), (int) (g * 255), (int) (b * 255), (int) (a * 255));

        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    public static void renderReticle(int cx, int cy) {
        if (currentTarget == null) return;
        MlgCobwebModule mod = MlgCobwebModule.get();
        if (mod == null || !mod.timingReticle.get()) return;

        int reticleColor = inWindow ? 0xFF00FF00 : (fatal ? 0xFFFF3333 : 0xFFFFAA00);
        int radius = inWindow ? 10 : 14;

        // Draw timing brackets around crosshair
        Gui.drawRect(cx - radius - 2, cy - 1, cx - radius, cy + 1, reticleColor);
        Gui.drawRect(cx + radius, cy - 1, cx + radius + 2, cy + 1, reticleColor);
        Gui.drawRect(cx - 1, cy - radius - 2, cx + 1, cy - radius, reticleColor);
        Gui.drawRect(cx - 1, cy + radius, cx + 1, cy + radius + 2, reticleColor);

        Minecraft mc = Minecraft.getMinecraft();
        String text = inWindow ? "CLUTCH!" : String.format("%.1fs", secondsToImpact);
        int tw = mc.fontRendererObj.getStringWidth(text);
        mc.fontRendererObj.drawString(text, cx - tw / 2, cy + radius + 4, reticleColor, true);
    }
}
