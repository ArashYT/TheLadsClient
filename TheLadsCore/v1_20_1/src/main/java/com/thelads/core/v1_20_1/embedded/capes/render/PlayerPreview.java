// Ported from Capes 1.5.10+26.3 by Cael (LGPL-2.1-only) from Kotlin to Java; modified by The Lads: ported to Minecraft 1.20.1 from the Lads 26.3 embedded copy.
package com.thelads.core.v1_20_1.embedded.capes.render;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.thelads.core.v1_20_1.embedded.capes.Capes;
import com.thelads.core.v1_20_1.embedded.capes.handler.PlayerHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.ElytraModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * The player in the cape selector. 26.3 renders a placeholder avatar through the entity renderer with a hand-made render state;
 * 1.20.1 has no render states and an entity needs a level (the screen is reachable from the title screen), so the vanilla models
 * are drawn directly with the same transforms {@code LivingEntityRenderer}, {@code CapeLayer} and {@code ElytraLayer} apply. State
 * is static like the 26.3 placeholder object.
 */
public final class PlayerPreview {
    private static final float SCALE = 69.0F;
    private static final ResourceLocation VANILLA_ELYTRA = new ResourceLocation("textures/entity/elytra.png");

    public static boolean showBody = true;
    public static boolean showElytra = false;
    public static boolean capeLoaded = false;
    public static float limbDistance = 0f;
    public static float lastLimbDistance = 0f;
    public static float limbAngle = 0f;
    public static float yaw = 0f;
    public static float prevYaw = 0f;
    public static double x = 0.0;
    public static double prevX = 0.0;

    private static GameProfile profile;
    private static PlayerInfo info;
    private static PlayerModel<AbstractClientPlayer> wideModel;
    private static PlayerModel<AbstractClientPlayer> slimModel;
    private static ElytraModel<AbstractClientPlayer> elytraModel;
    private static EntityModelSet builtFrom;
    private static boolean broken;

    private PlayerPreview() {
    }

    /** True once drawing the preview failed (it then stays off for the session). */
    public static boolean isBroken() {
        return broken;
    }

    public static void updateLimbs() {
        lastLimbDistance = limbDistance;
        double d = x - prevX;
        float g = (float) Math.sqrt(d * d) * 4.0f;
        if (g > 1.0f) {
            g = 1.0f;
        }
        limbDistance += (g - limbDistance) * 0.4f;
        limbAngle += limbDistance;
    }

    /** The profile the preview is for (the local user): the cape handler of this profile is the one the game uses too. */
    public static GameProfile profile() {
        if (profile == null) profile = Minecraft.getInstance().getUser().getGameProfile();
        return profile;
    }

    /** The cape the preview shows: the loaded custom cape, else the account's own. */
    public static ResourceLocation capeTexture() {
        GameProfile gameProfile = profile();
        if (!capeLoaded) {
            capeLoaded = true;
            PlayerHandler.onLoadTexture(gameProfile);
        }
        PlayerHandler handler = PlayerHandler.fromProfile(gameProfile);
        return handler.getHasCape() ? handler.getCape() : info().getCapeLocation();
    }

    public static ResourceLocation elytraTexture() {
        PlayerHandler handler = PlayerHandler.fromProfile(profile());
        ResourceLocation cape = capeTexture();
        return handler.getHasElytraTexture() && Capes.getConfig().getEnableElytraTexture() && cape != null ? cape : VANILLA_ELYTRA;
    }

    private static PlayerInfo info() {
        if (info == null) info = new PlayerInfo(profile(), false);
        return info;
    }

    /** Draws the player inside the box; the origin (feet) is the centre of the box, 26.3 does the same. */
    public static void render(GuiGraphics graphics, int x1, int y1, int x2, int y2, float partialTick) {
        if (broken) return;
        PoseStack pose = graphics.pose();
        graphics.enableScissor(x1, y1, x2, y2);
        pose.pushPose();
        try {
            draw(graphics, pose, (x1 + x2) / 2.0, (y1 + y2) / 2.0, partialTick);
        } catch (RuntimeException | LinkageError failure) {
            broken = true;
            Capes.LOGGER.warn("The cape selector cannot draw its preview player", failure);
        } finally {
            pose.popPose();
            Lighting.setupFor3DItems();
            graphics.disableScissor();
        }
    }

    private static void draw(GuiGraphics graphics, PoseStack pose, double centerX, double centerY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        EntityModelSet modelSet = minecraft.getEntityModels();
        if (builtFrom != modelSet) {
            wideModel = new PlayerModel<>(modelSet.bakeLayer(ModelLayers.PLAYER), false);
            slimModel = new PlayerModel<>(modelSet.bakeLayer(ModelLayers.PLAYER_SLIM), true);
            elytraModel = new ElytraModel<>(modelSet.bakeLayer(ModelLayers.ELYTRA));
            wideModel.young = false;
            slimModel.young = false;
            elytraModel.young = false;
            builtFrom = modelSet;
        }
        PlayerInfo playerInfo = info();
        PlayerModel<AbstractClientPlayer> model = "slim".equals(playerInfo.getModelName()) ? slimModel : wideModel;
        ResourceLocation skin = playerInfo.getSkinLocation();
        ResourceLocation cape = capeTexture();
        ResourceLocation elytra = elytraTexture();
        setPose(model, minecraft.options);

        // The inventory screen transform: origin at the feet, +y up, scaled to pixels.
        pose.translate(centerX, centerY, 50.0);
        pose.mulPoseMatrix(new Matrix4f().scaling(SCALE, SCALE, -SCALE));
        pose.mulPose(new Quaternionf().rotateZ((float) Math.PI));
        Lighting.setupForEntityInInventory();

        // LivingEntityRenderer.render: body rotation (0 = the back faces the screen, which is what a cape preview wants), mirror, scale, lift.
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(partialTick, prevYaw, yaw)));
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.scale(0.9375F, 0.9375F, 0.9375F);
        pose.translate(0.0F, -1.501F, 0.0F);

        MultiBufferSource.BufferSource buffers = graphics.bufferSource();
        int light = 15728880;
        if (showBody) {
            model.renderToBuffer(pose, buffers.getBuffer(model.renderType(skin)), light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        }
        if (showElytra) {
            // ElytraLayer
            pose.pushPose();
            pose.translate(0.0F, 0.0F, 0.125F);
            elytraModel.renderToBuffer(pose, buffers.getBuffer(RenderType.armorCutoutNoCull(elytra)), light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
            pose.popPose();
        } else if (cape != null) {
            // CapeLayer with the entity at rest (no movement, no sneaking), and the cutout render type the cape mixin gives every cape
            pose.pushPose();
            pose.translate(0.0F, 0.0F, 0.125F);
            pose.mulPose(Axis.XP.rotationDegrees(6.0F));
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            model.renderCloak(pose, buffers.getBuffer(RenderType.armorCutoutNoCull(cape)), light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        graphics.flush();
    }

    /** Visibility of the outer layers from the skin options, and the slow walk of the 26.3 placeholder (HumanoidModel limb swing). */
    private static void setPose(PlayerModel<AbstractClientPlayer> model, Options options) {
        model.setAllVisible(true);
        model.hat.visible = options.isModelPartEnabled(PlayerModelPart.HAT);
        model.jacket.visible = options.isModelPartEnabled(PlayerModelPart.JACKET);
        model.leftPants.visible = options.isModelPartEnabled(PlayerModelPart.LEFT_PANTS_LEG);
        model.rightPants.visible = options.isModelPartEnabled(PlayerModelPart.RIGHT_PANTS_LEG);
        model.leftSleeve.visible = options.isModelPartEnabled(PlayerModelPart.LEFT_SLEEVE);
        model.rightSleeve.visible = options.isModelPartEnabled(PlayerModelPart.RIGHT_SLEEVE);

        float pos = limbAngle;
        float speed = limbDistance;
        model.rightArm.xRot = Mth.cos(pos * 0.6662F + (float) Math.PI) * 2.0F * speed * 0.5F;
        model.leftArm.xRot = Mth.cos(pos * 0.6662F) * 2.0F * speed * 0.5F;
        model.rightLeg.xRot = Mth.cos(pos * 0.6662F) * 1.4F * speed;
        model.leftLeg.xRot = Mth.cos(pos * 0.6662F + (float) Math.PI) * 1.4F * speed;
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);
    }
}
