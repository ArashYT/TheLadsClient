package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.hud.PaperDoll;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * The Lads paper doll on 1.8.9: feeds {@link PaperDoll} every client tick and draws the actual player in the Paperdoll HUD element's
 * box (GuiLadsAdapter.drawPlayerModel), with the doll's body and head angles put on the player only for that draw. 1.8.9 has no
 * crawling, gliding or spin attack, so those triggers never fire; "Swimming" is moving in water. Paper Doll has no 1.8.9 release.
 */
public final class PaperDoll189 {
    /** GL 1.4 blend factors (LWJGL 2 lists them under ARB_imaging only). */
    private static final int CONSTANT_ALPHA = 0x8003, ONE_MINUS_CONSTANT_ALPHA = 0x8004;
    private static float lastYaw = Float.NaN, partialTicks = 1;

    /** Every client tick (TheLadsCore189): the triggers happening now and how far the view turned. Paused: the doll stays as it is. */
    public static void tick(Minecraft mc) {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) { lastYaw = Float.NaN; return; }
        if (mc.isGamePaused()) return;
        float turned = Float.isNaN(lastYaw) ? 0 : MathHelper.wrapAngleTo180_float(player.rotationYaw - lastYaw);
        lastYaw = player.rotationYaw;
        PaperDoll.INSTANCE.tick(name -> happening(player, name), turned);
    }

    /** Whether the named trigger (a Paperdoll PlayerActionOption) is happening for this player now. */
    static boolean happening(EntityPlayerSP player, String name) {
        double horizontal = player.motionX * player.motionX + player.motionZ * player.motionZ;
        switch (name) {
            case "Sprinting": return player.isSprinting();
            case "Swimming": return player.isInWater() && horizontal > 0.0001;
            case "Crouching": return player.isSneaking();
            case "Creative Flying": return player.capabilities.isFlying;
            case "Riding": return player.isRiding();
            case "Using Items": return player.isUsingItem();
            case "Walking": return horizontal > 0.0001;
            case "Standing": return player.onGround && horizontal <= 0.0001;
            case "Jumping": return !player.onGround && player.motionY > 0;
            case "Falling": return !player.onGround && player.motionY < -0.08;
            case "Sleeping": return player.isPlayerSleeping();
            case "Climbing": return player.isOnLadder();
            case "In Water": return player.isInWater();
            case "On Fire": return player.isBurning();
            case "Attacking": return player.isSwingInProgress;
            case "Blocking": return player.isBlocking();
            case "Hurt": return player.hurtTime > 0;
            case "Dead": return player.getHealth() <= 0;
            case "Spectating": return player.isSpectator();
            default: return false; // Crawling, Elytra Gliding, Spin Attacking: not in 1.8.9
        }
    }

    /** The frame's partial tick, for smooth limbs (Forge hands it to the overlay that draws the Lads HUD). */
    @SubscribeEvent
    public void frame(RenderGameOverlayEvent.Pre event) {
        if (event.type == RenderGameOverlayEvent.ElementType.ALL) partialTicks = event.partialTicks;
    }

    /** The doll in a box of the GUI; the HUD editor shows it whatever the triggers say, at full opacity. */
    public static void render(int x, int y, int width, int height, boolean editor, int screenWidth) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || !editor && !PaperDoll.INSTANCE.visible(mc.gameSettings.thirdPersonView == 0)) return;
        float partial = editor ? 1 : partialTicks, opacity = editor ? 1 : PaperDoll.INSTANCE.opacity();
        float body = PaperDoll.INSTANCE.bodyYaw(x + width / 2 < screenWidth / 2) - 180; // 1.8.9's 0 faces the viewer here
        float pitch = PaperDoll.INSTANCE.headPitch(player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partial);
        float[] saved = {player.renderYawOffset, player.prevRenderYawOffset, player.rotationYawHead, player.prevRotationYawHead,
            player.rotationPitch, player.prevRotationPitch};
        float scale = height * 0.43f; // GUI pixels per block, as on 26.x
        GlStateManager.pushMatrix();
        try {
            GlStateManager.enableColorMaterial();
            GlStateManager.enableDepth();
            GlStateManager.translate(x + width / 2f, y + height / 2f + player.height * scale / 2, 50);
            GlStateManager.scale(-scale, scale, scale);
            GlStateManager.rotate(180, 0, 0, 1);
            // GUI entity lighting, lit from the front-left as in the inventory.
            GlStateManager.rotate(135, 0, 1, 0);
            RenderHelper.enableStandardItemLighting();
            GlStateManager.rotate(-135, 0, 1, 0);
            if (opacity < 1) {
                GlStateManager.enableBlend();
                GL14.glBlendColor(0, 0, 0, opacity);
                GlStateManager.tryBlendFuncSeparate(CONSTANT_ALPHA, ONE_MINUS_CONSTANT_ALPHA, 1, 0);
            }
            player.renderYawOffset = player.prevRenderYawOffset = body;
            player.rotationYawHead = player.prevRotationYawHead = body + PaperDoll.INSTANCE.headYaw(partial);
            player.rotationPitch = player.prevRotationPitch = pitch;
            RenderManager manager = mc.getRenderManager();
            manager.setPlayerViewY(180);
            manager.setRenderShadow(false);
            manager.renderEntityWithPosYaw(player, 0, 0, 0, 0, partial);
            manager.setRenderShadow(true);
        } finally {
            player.renderYawOffset = saved[0];
            player.prevRenderYawOffset = saved[1];
            player.rotationYawHead = saved[2];
            player.prevRotationYawHead = saved[3];
            player.rotationPitch = saved[4];
            player.prevRotationPitch = saved[5];
            GlStateManager.popMatrix();
            if (opacity < 1) GL14.glBlendColor(0, 0, 0, 0);
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            // Back to the 2D state the rest of the HUD draws in.
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableRescaleNormal();
            GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GlStateManager.disableTexture2D();
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.disableDepth();
            GlStateManager.enableAlpha();
            GlStateManager.color(1, 1, 1, 1);
        }
    }
}
