package com.thelads.core.v1_20_1.cosmetics.anim;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.thelads.core.client.cosmetics.Emotes;
import com.thelads.core.client.cosmetics.anim.Emote;
import com.thelads.core.client.cosmetics.anim.EmoteAnimator;
import com.thelads.core.client.cosmetics.anim.EmoteLibrary;
import com.thelads.core.client.cosmetics.anim.EmotePose;
import com.thelads.core.client.cosmetics.anim.EmoteRules;
import com.thelads.core.v1_20_1.feature.NativeKeyBindings1201;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.lwjgl.glfw.GLFW;

import static com.thelads.core.client.cosmetics.anim.Emote.*;

/**
 * Emotes on 1.20.1: the Emotes key and wheel, the local stop rules each tick, the pose on every rendered player (third person, the
 * inventory preview, armour copies it) and the dancing arms in first person. The network side fills Emotes for other players.
 */
public final class EmoteAnim1201 {
    public static final KeyMapping KEY = new KeyMapping("key.theladscore.emotes", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, NativeKeyBindings1201.CATEGORY);
    private static final float MODEL_SCALE = 0.9375f;
    /** PlayerRenderer.renderHand (the vanilla first-person arm) runs setupAnim too: no emote there. */
    public static boolean inHand;
    private static PlayerModel<AbstractClientPlayer> wideArms, slimArms;
    private static Entity lastEntity;
    private static EmotePose lastPose;

    private EmoteAnim1201() {}

    public static void register() {
        KeyBindingHelper.registerKeyBinding(KEY);
        ClientTickEvents.END_CLIENT_TICK.register(EmoteAnim1201::tick);
        EmoteCapture1201.register();
    }

    public static void play(String id) {
        LocalPlayer self = Minecraft.getInstance().player;
        if (self != null) Emotes.playLocal(self.getUUID(), id);
    }

    private static void tick(Minecraft mc) {
        LocalPlayer self = mc.player;
        if (self == null) return;
        UUID id = self.getUUID();
        while (KEY.consumeClick()) {
            Emotes.Playing playing = Emotes.get(id);
            if (playing != null && !playing.stopping()) Emotes.stopLocal(id); // tap again: stop
            else if (mc.screen == null) mc.setScreen(new EmoteWheelScreen1201());
        }
        if (Emotes.get(id) == null) return;
        Options o = mc.options;
        EmoteRules.Input in = EmoteRules.in;
        in.moving = o.keyUp.isDown() || o.keyDown.isDown() || o.keyLeft.isDown() || o.keyRight.isDown();
        in.jumping = o.keyJump.isDown();
        in.sneaking = o.keyShift.isDown() || self.isShiftKeyDown();
        in.attacking = o.keyAttack.isDown();
        in.usingItem = o.keyUse.isDown() || self.isUsingItem();
        in.hurtTime = self.hurtTime;
        in.dead = self.isDeadOrDying() || mc.screen instanceof DeathScreen;
        in.busy = self.isPassenger() || self.isFallFlying() || self.isSwimming() || self.isSleeping() || self.getAbilities().flying;
        in.level = self.level();
        in.x = self.getX(); in.y = self.getY(); in.z = self.getZ();
        EmoteRules.tick(id);
        pose(self); // the blend-out finishes even while the player is not drawn
    }

    /** A rendered player's emote pose this frame, or null (PlayerRenderer.scale, once per drawn player: one lookup when nobody emotes). */
    public static EmotePose pose(Entity entity) {
        lastPose = EmoteAnimator.pose(entity.getUUID(), entity != Minecraft.getInstance().player, entity.getX(), entity.getY(), entity.getZ());
        lastEntity = entity;
        return lastPose;
    }

    /** The same player's pose again within its draw (the model's setupAnim), without a second lookup. */
    public static EmotePose posed(Entity entity) { return entity == lastEntity ? lastPose : pose(entity); }

    /** Third person: the pose over the model vanilla (and the other mods) posed; the outer layer copies its parts again. */
    public static void apply(PlayerModel<?> model, EmotePose pose) {
        part(model.head, HEAD, pose);
        part(model.body, BODY, pose);
        part(model.rightArm, RIGHT_ARM, pose);
        part(model.leftArm, LEFT_ARM, pose);
        part(model.rightLeg, RIGHT_LEG, pose);
        part(model.leftLeg, LEFT_LEG, pose);
        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg);
        model.leftPants.copyFrom(model.leftLeg);
    }

    /** Fields vanilla's setupAnim never resets, back to rest before the next player is posed on this (shared) model. */
    public static void restore(PlayerModel<?> model) {
        model.head.zRot = 0; model.head.x = 0; model.head.z = 0;
        model.body.zRot = 0; model.body.x = 0; model.body.z = 0;
        model.rightLeg.x = -1.9f; model.leftLeg.x = 1.9f;
    }

    private static void part(ModelPart p, int part, EmotePose pose) {
        p.xRot = pose.rot(part, RX, p.xRot);
        p.yRot = pose.rot(part, RY, p.yRot);
        p.zRot = pose.rot(part, RZ, p.zRot);
        p.x = pose.pos(part, 0, p.x);
        p.y = pose.pos(part, 1, p.y);
        p.z = pose.pos(part, 2, p.z);
    }

    /** The normal first-person hand/item stays lowered while the local player's emote moves its arms. */
    public static boolean handsDown(LocalPlayer self) {
        Emotes.Playing playing = self == null ? null : Emotes.get(self.getUUID());
        if (playing == null || playing.stopping()) return false;
        Emote emote = EmoteLibrary.get(playing.id());
        return emote != null && (emote.rightArm || emote.leftArm);
    }

    /**
     * First person: the emote's arms where the body has them. The hand pose stack is the camera's; undoing the camera's pitch gives the
     * body's frame, then the model's axes and scale with the eyes at the origin, so ModelPart's own pivot and rotation put the arm where
     * third person has it. Drawn with an arm model of our own (the renderer's belongs to the vanilla hand).
     */
    public static void firstPersonArms(PoseStack pose, MultiBufferSource buffers, LocalPlayer self, int light, float partialTicks) {
        if (self == null || self.isInvisible()) return;
        EmotePose emote = pose(self);
        if (emote == null || !emote.firstPersonArms()) return;
        boolean slim = "slim".equals(self.getModelName());
        PlayerModel<AbstractClientPlayer> model = slim ? (slimArms != null ? slimArms : (slimArms = arms(true)))
            : (wideArms != null ? wideArms : (wideArms = arms(false)));
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(self.getViewXRot(partialTicks)));
        pose.scale(-MODEL_SCALE, -MODEL_SCALE, MODEL_SCALE);
        pose.translate(0, self.getEyeHeight() / MODEL_SCALE - 1.501f, 0);
        ResourceLocation skin = self.getSkinTextureLocation();
        if (emote.emote.rightArm) arm(model.rightArm, model.rightSleeve, self.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE), RIGHT_ARM, emote, pose, buffers, skin, light);
        if (emote.emote.leftArm) arm(model.leftArm, model.leftSleeve, self.isModelPartShown(PlayerModelPart.LEFT_SLEEVE), LEFT_ARM, emote, pose, buffers, skin, light);
        pose.popPose();
    }

    private static void arm(ModelPart arm, ModelPart sleeve, boolean sleeveShown, int part, EmotePose emote, PoseStack pose,
                            MultiBufferSource buffers, ResourceLocation skin, int light) {
        arm.setPos(emote.armPos(part, 0), emote.armPos(part, 1), emote.armPos(part, 2));
        arm.setRotation(emote.armRot(part, RX), emote.armRot(part, RY), emote.armRot(part, RZ));
        arm.visible = true;
        arm.render(pose, buffers.getBuffer(RenderType.entitySolid(skin)), light, OverlayTexture.NO_OVERLAY);
        if (!sleeveShown) return;
        sleeve.copyFrom(arm);
        sleeve.visible = true;
        sleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucent(skin)), light, OverlayTexture.NO_OVERLAY);
    }

    private static PlayerModel<AbstractClientPlayer> arms(boolean slim) {
        return new PlayerModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim);
    }
}
