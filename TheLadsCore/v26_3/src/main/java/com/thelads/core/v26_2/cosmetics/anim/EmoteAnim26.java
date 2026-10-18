package com.thelads.core.v26_2.cosmetics.anim;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.thelads.core.client.cosmetics.Emotes;
import com.thelads.core.client.cosmetics.anim.Emote;
import com.thelads.core.client.cosmetics.anim.EmoteAnimator;
import com.thelads.core.client.cosmetics.anim.EmoteLibrary;
import com.thelads.core.client.cosmetics.anim.EmotePose;
import com.thelads.core.client.cosmetics.anim.EmoteKeys;
import com.thelads.core.client.cosmetics.anim.EmoteRules;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.v26_2.feature.NativeKeyBindings;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;

import org.slf4j.LoggerFactory;

import static com.thelads.core.client.cosmetics.anim.Emote.*;

/**
 * Emotes on 26.3: the Emotes key and wheel, the local stop rules each tick, the pose on every rendered player (third person, the
 * inventory preview, armour) and the dancing arms in first person. The network side fills Emotes for other players.
 */
public final class EmoteAnim26 {
    public static final KeyMapping KEY = new KeyMapping("key.theladscore.emotes", InputConstants.Type.KEYBOARD, InputConstants.KEY_B, NativeKeyBindings.CATEGORY);
    private static final float MODEL_SCALE = 0.9375f;
    private static final String VERSION = "26.3";
    private static PlayerModel wideArms, slimArms;
    private static boolean keysChecked;

    private EmoteAnim26() {}

    public static void register() {
        KeyMappingHelper.registerKeyMapping(KEY);
        ClientTickEvents.END_CLIENT_TICK.register(EmoteAnim26::tick);
    }

    /** Plays an emote for the local player (the wheel). */
    public static void play(String id) {
        LocalPlayer self = Minecraft.getInstance().player;
        if (self != null) Emotes.playLocal(self.getUUID(), id);
    }

    private static void tick(Minecraft mc) {
        if (!keysChecked) giveWay(mc);
        LocalPlayer self = mc.player;
        if (self == null) return;
        UUID id = self.getUUID();
        while (KEY.consumeClick()) {
            Emotes.Playing playing = Emotes.get(id);
            if (playing != null && !playing.stopping()) Emotes.stopLocal(id); // tap again: stop
            else if (mc.gui.screen() == null) mc.setScreenAndShow(new EmoteWheelScreen26());
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
        in.dead = self.isDeadOrDying() || mc.gui.screen() instanceof DeathScreen;
        in.busy = self.isPassenger() || self.isFallFlying() || self.isSwimming() || self.isSleeping() || self.getAbilities().flying;
        in.level = self.level();
        in.x = self.getX(); in.y = self.getY(); in.z = self.getZ();
        EmoteRules.tick(id);
        pose(self); // the blend-out finishes even while the player is not drawn
    }

    /** Once per install (first tick, all mods' keys loaded): Xaero's New Waypoint gives B to Emotes when both are on it (EmoteKeys). */
    private static void giveWay(Minecraft mc) {
        keysChecked = true;
        if (ConfigManager.emoteKeyClashResolved(VERSION)) return;
        StringBuilder freed = new StringBuilder();
        if (KEY.isDefault())
            for (KeyMapping mapping : mc.options.keyMappings)
                if (mapping != KEY && EmoteKeys.GIVE_WAY.contains(mapping.getName()) && mapping.same(KEY)) {
                    mapping.setKey(InputConstants.UNKNOWN);
                    freed.append(freed.length() == 0 ? "" : ", ").append(mapping.getName());
                }
        if (freed.length() > 0) {
            KeyMapping.resetMapping();
            mc.options.save();
            LoggerFactory.getLogger("TheLadsCore").info("Lads Emotes: B is the Emotes key; unbound {} (it was on B too), once", freed);
        }
        ConfigManager.markEmoteKeyClashResolved(VERSION);
    }

    /** A rendered player's emote pose this frame, or null. */
    public static EmotePose pose(Avatar entity) {
        return EmoteAnimator.pose(entity.getUUID(), entity != Minecraft.getInstance().player, entity.getX(), entity.getY(), entity.getZ());
    }

    /** Third person: the pose over the model vanilla (and the other mods) posed. */
    public static void apply(PlayerModel model, EmotePose pose) {
        if (pose == null) return;
        part(model.head, HEAD, pose);
        part(model.body, BODY, pose);
        part(model.rightArm, RIGHT_ARM, pose);
        part(model.leftArm, LEFT_ARM, pose);
        part(model.rightLeg, RIGHT_LEG, pose);
        part(model.leftLeg, LEFT_LEG, pose);
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
        Emotes.Playing playing = Emotes.get(self.getUUID());
        if (playing == null || playing.stopping()) return false;
        Emote emote = EmoteLibrary.get(playing.id());
        return emote != null && (emote.rightArm || emote.leftArm);
    }

    /**
     * First person: the emote's arms where the body has them. The hand pose stack is the camera's; undoing the camera's pitch gives the
     * body's frame (the body does not pitch), then the model's axes and scale with the eyes at the origin, so ModelPart's own pivot and
     * rotation put the arm where third person has it. Drawn with an arm model of our own: the shared one belongs to the vanilla hand.
     */
    public static void firstPersonArms(PoseStack pose, SubmitNodeCollector collector, PlayerRenderState player, FirstPersonHandsAndItemsRenderState state) {
        LocalPlayer self = Minecraft.getInstance().player;
        AvatarRenderState avatar = player.avatarRenderState;
        if (self == null || avatar == null || avatar.isInvisible) return;
        EmotePose emote = pose(self);
        if (emote == null || !emote.firstPersonArms()) return;
        boolean slim = avatar.skin.model() == PlayerModelType.SLIM;
        PlayerModel model = slim ? (slimArms != null ? slimArms : (slimArms = arms(true))) : (wideArms != null ? wideArms : (wideArms = arms(false)));
        pose.pushPose();
        pose.rotateDegrees(Axis.XP, state.viewXRot);
        pose.scale(-MODEL_SCALE, -MODEL_SCALE, MODEL_SCALE);
        pose.translate(0, self.getEyeHeight() / MODEL_SCALE - 1.501f, 0);
        RenderType type = RenderTypes.entityTranslucent(avatar.skin.body().texturePath());
        if (emote.emote.rightArm) {
            arm(model.rightArm, model.rightSleeve, avatar.showRightSleeve, RIGHT_ARM, emote, pose, collector, type, avatar.lightCoords);
            heldItem(model, avatar, HumanoidArm.RIGHT, avatar.rightHandItemState, pose, collector);
        }
        if (emote.emote.leftArm) {
            arm(model.leftArm, model.leftSleeve, avatar.showLeftSleeve, LEFT_ARM, emote, pose, collector, type, avatar.lightCoords);
            heldItem(model, avatar, HumanoidArm.LEFT, avatar.leftHandItemState, pose, collector);
        }
        pose.popPose();
    }

    private static void arm(ModelPart arm, ModelPart sleeve, boolean sleeveShown, int part, EmotePose emote, PoseStack pose,
                            SubmitNodeCollector collector, RenderType type, int light) {
        arm.resetPose();
        arm.setPos(emote.armPos(part, 0), emote.armPos(part, 1), emote.armPos(part, 2));
        arm.setRotation(emote.armRot(part, RX), emote.armRot(part, RY), emote.armRot(part, RZ));
        arm.visible = true;
        sleeve.visible = sleeveShown;
        collector.submitModelPart(arm, pose, type, light, OverlayTexture.NO_OVERLAY, null);
    }

    /** The item in that hand, held as third person holds it (ItemInHandLayer's placement on the hand). */
    private static void heldItem(PlayerModel model, AvatarRenderState avatar, HumanoidArm arm, ItemStackRenderState item, PoseStack pose,
                                 SubmitNodeCollector collector) {
        if (item.isEmpty()) return;
        pose.pushPose();
        model.translateToHand(avatar, arm, pose);
        pose.rotateDegrees(Axis.XP, -90);
        pose.rotateDegrees(Axis.YP, 180);
        pose.translate((arm == HumanoidArm.LEFT ? -1 : 1) / 16f, 2 / 16f, -10 / 16f);
        item.submit(pose, collector, avatar.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }

    private static PlayerModel arms(boolean slim) {
        return new PlayerModel(Minecraft.getInstance().getEntityModels().bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim);
    }
}
