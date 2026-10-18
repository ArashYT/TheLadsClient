package com.thelads.core.v1_8_9.cosmetics.anim;

import com.thelads.core.client.cosmetics.Emotes;
import com.thelads.core.client.cosmetics.anim.Emote;
import com.thelads.core.client.cosmetics.anim.EmoteAnimator;
import com.thelads.core.client.cosmetics.anim.EmoteLibrary;
import com.thelads.core.client.cosmetics.anim.EmotePose;
import com.thelads.core.client.cosmetics.anim.EmoteRules;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import static com.thelads.core.client.cosmetics.anim.Emote.*;

/**
 * Emotes on 1.8.9: the Emotes key and wheel, the local stop rules each tick, the pose on every rendered player (third person, the
 * inventory preview, armour) and the dancing arms in first person. The network side fills Emotes for other players.
 */
public final class EmoteAnim189 {
    public static final KeyBinding KEY = new KeyBinding("key.theladscore.emotes", Keyboard.KEY_B, "key.category.theladscore.controls");
    private static final float MODEL_SCALE = 0.9375F;
    /** RenderPlayer.renderRightArm/renderLeftArm (the vanilla first-person arm) run setRotationAngles too: no emote there. */
    public static boolean inHand;
    private static ModelPlayer wideArms, slimArms;
    private static Entity lastEntity;
    private static EmotePose lastPose;

    public static void register() {
        ClientRegistry.registerKeyBinding(KEY);
        MinecraftForge.EVENT_BUS.register(new EmoteAnim189());
    }

    public static void play(String id) {
        EntityPlayerSP self = Minecraft.getMinecraft().thePlayer;
        if (self != null) Emotes.playLocal(self.getUniqueID(), id);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP self = mc.thePlayer;
        if (self == null) return;
        UUID id = self.getUniqueID();
        while (KEY.isPressed()) {
            Emotes.Playing playing = Emotes.get(id);
            if (playing != null && !playing.stopping()) Emotes.stopLocal(id); // tap again: stop
            else if (mc.currentScreen == null) mc.displayGuiScreen(new EmoteWheelScreen189());
        }
        if (Emotes.get(id) == null) return;
        GameSettings o = mc.gameSettings;
        EmoteRules.Input in = EmoteRules.in;
        in.moving = o.keyBindForward.isKeyDown() || o.keyBindBack.isKeyDown() || o.keyBindLeft.isKeyDown() || o.keyBindRight.isKeyDown();
        in.jumping = o.keyBindJump.isKeyDown();
        in.sneaking = o.keyBindSneak.isKeyDown() || self.isSneaking();
        in.attacking = o.keyBindAttack.isKeyDown();
        in.usingItem = o.keyBindUseItem.isKeyDown() || self.isUsingItem();
        in.hurtTime = self.hurtTime;
        in.dead = self.getHealth() <= 0 || mc.currentScreen instanceof GuiGameOver;
        in.busy = self.isRiding() || self.isPlayerSleeping() || self.capabilities.isFlying;
        in.level = mc.theWorld;
        in.x = self.posX; in.y = self.posY; in.z = self.posZ;
        EmoteRules.tick(id);
        pose(self); // the blend-out finishes even while the player is not drawn
    }

    /** A rendered player's emote pose this frame, or null (preRenderCallback, once per drawn player: one lookup when nobody emotes). */
    public static EmotePose pose(Entity entity) {
        lastPose = EmoteAnimator.pose(entity.getUniqueID(), entity != Minecraft.getMinecraft().thePlayer, entity.posX, entity.posY, entity.posZ);
        lastEntity = entity;
        return lastPose;
    }

    /** The same player's pose again within its draw (its model and armour models), without another lookup. */
    public static EmotePose posed(Entity entity) { return entity == lastEntity ? lastPose : pose(entity); }

    /** Third person: the pose over the model vanilla (and 1.7 Animations) posed; ModelPlayer copies it to the outer layer afterwards. */
    public static void apply(ModelBiped model, EmotePose pose) {
        part(model.bipedHead, HEAD, pose);
        part(model.bipedBody, BODY, pose);
        part(model.bipedRightArm, RIGHT_ARM, pose);
        part(model.bipedLeftArm, LEFT_ARM, pose);
        part(model.bipedRightLeg, RIGHT_LEG, pose);
        part(model.bipedLeftLeg, LEFT_LEG, pose);
        ModelBase.copyModelAngles(model.bipedHead, model.bipedHeadwear);
    }

    /** The fields vanilla's setRotationAngles never resets, saved (13 floats) before an emote touches them... */
    public static void save(ModelBiped m, float[] r) {
        r[0] = m.bipedHead.rotateAngleZ; r[1] = m.bipedHead.rotationPointX; r[2] = m.bipedHead.rotationPointZ;
        r[3] = m.bipedBody.rotateAngleZ; r[4] = m.bipedBody.rotationPointX; r[5] = m.bipedBody.rotationPointY; r[6] = m.bipedBody.rotationPointZ;
        r[7] = m.bipedRightArm.rotationPointY; r[8] = m.bipedLeftArm.rotationPointY;
        r[9] = m.bipedRightLeg.rotateAngleZ; r[10] = m.bipedRightLeg.rotationPointX; r[11] = m.bipedLeftLeg.rotateAngleZ; r[12] = m.bipedLeftLeg.rotationPointX;
    }

    /** ...and put back before the next entity is posed on this (shared) model. */
    public static void restore(ModelBiped m, float[] r) {
        m.bipedHead.rotateAngleZ = r[0]; m.bipedHead.rotationPointX = r[1]; m.bipedHead.rotationPointZ = r[2];
        m.bipedBody.rotateAngleZ = r[3]; m.bipedBody.rotationPointX = r[4]; m.bipedBody.rotationPointY = r[5]; m.bipedBody.rotationPointZ = r[6];
        m.bipedRightArm.rotationPointY = r[7]; m.bipedLeftArm.rotationPointY = r[8];
        m.bipedRightLeg.rotateAngleZ = r[9]; m.bipedRightLeg.rotationPointX = r[10]; m.bipedLeftLeg.rotateAngleZ = r[11]; m.bipedLeftLeg.rotationPointX = r[12];
    }

    private static void part(ModelRenderer p, int part, EmotePose pose) {
        p.rotateAngleX = pose.rot(part, RX, p.rotateAngleX);
        p.rotateAngleY = pose.rot(part, RY, p.rotateAngleY);
        p.rotateAngleZ = pose.rot(part, RZ, p.rotateAngleZ);
        p.rotationPointX = pose.pos(part, 0, p.rotationPointX);
        p.rotationPointY = pose.pos(part, 1, p.rotationPointY);
        p.rotationPointZ = pose.pos(part, 2, p.rotationPointZ);
    }

    /** The normal first-person hand/item stays lowered while the local player's emote moves its arms. */
    public static boolean handsDown() {
        EntityPlayerSP self = Minecraft.getMinecraft().thePlayer;
        Emotes.Playing playing = self == null ? null : Emotes.get(self.getUniqueID());
        if (playing == null || playing.stopping()) return false;
        Emote emote = EmoteLibrary.get(playing.id());
        return emote != null && (emote.rightArm || emote.leftArm);
    }

    /**
     * First person (end of ItemRenderer.renderItemInFirstPerson): the emote's arms where the body has them. The hand matrix is the
     * camera's; undoing the camera's pitch gives the body's frame, then the model's axes and scale with the eyes at the origin, so
     * ModelRenderer's own pivot and rotation put the arm where third person has it. Lit as vanilla lights the hand.
     */
    public static void firstPersonArms(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP self = mc.thePlayer;
        if (self == null || self.isInvisible()) return;
        EmotePose emote = pose(self);
        if (emote == null || !emote.firstPersonArms()) return;
        boolean slim = "slim".equals(self.getSkinType());
        ModelPlayer model = slim ? (slimArms != null ? slimArms : (slimArms = new ModelPlayer(0, true)))
            : (wideArms != null ? wideArms : (wideArms = new ModelPlayer(0, false)));
        float pitch = self.prevRotationPitch + (self.rotationPitch - self.prevRotationPitch) * partialTicks;
        float yaw = self.prevRotationYaw + (self.rotationYaw - self.prevRotationYaw) * partialTicks;
        GlStateManager.pushMatrix();
        GlStateManager.rotate(pitch, 1, 0, 0);
        GlStateManager.rotate(yaw, 0, 1, 0);
        RenderHelper.enableStandardItemLighting();
        GlStateManager.popMatrix();
        GlStateManager.enableRescaleNormal();
        GlStateManager.pushMatrix();
        GlStateManager.rotate(pitch, 1, 0, 0);
        GlStateManager.scale(-MODEL_SCALE, -MODEL_SCALE, MODEL_SCALE);
        GlStateManager.translate(0, self.getEyeHeight() / MODEL_SCALE - 1.5078125F, 0);
        mc.getTextureManager().bindTexture(self.getLocationSkin());
        GlStateManager.color(1, 1, 1, 1);
        if (emote.emote.rightArm) arm(model.bipedRightArm, model.bipedRightArmwear, self.isWearing(EnumPlayerModelParts.RIGHT_SLEEVE), RIGHT_ARM, emote);
        if (emote.emote.leftArm) arm(model.bipedLeftArm, model.bipedLeftArmwear, self.isWearing(EnumPlayerModelParts.LEFT_SLEEVE), LEFT_ARM, emote);
        GlStateManager.popMatrix();
        GlStateManager.disableRescaleNormal();
        RenderHelper.disableStandardItemLighting();
    }

    private static void arm(ModelRenderer arm, ModelRenderer sleeve, boolean sleeveShown, int part, EmotePose emote) {
        arm.setRotationPoint(emote.armPos(part, 0), emote.armPos(part, 1), emote.armPos(part, 2));
        arm.rotateAngleX = emote.armRot(part, RX);
        arm.rotateAngleY = emote.armRot(part, RY);
        arm.rotateAngleZ = emote.armRot(part, RZ);
        arm.showModel = true;
        arm.render(0.0625F);
        if (!sleeveShown) return;
        ModelBase.copyModelAngles(arm, sleeve);
        sleeve.showModel = true;
        sleeve.render(0.0625F);
    }
}
