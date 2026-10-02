package com.thelads.core.v1_21_11.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.thelads.core.client.OldAnimations;
import com.thelads.core.client.OldAnimations.Held;
import com.thelads.core.client.OldAnimations.Use;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.OldAnimationsModule;
import com.thelads.core.modules.OldAnimationsModule.Feature;
import com.thelads.core.modules.OldAnimationsModule.Platform;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwingAnimationType;
import org.joml.Matrix4f;

/**
 * "1.7 Animations" on 1.21.11. The OldAnimations* mixins ask this class and replay the shared recipes ({@link OldAnimations})
 * through PoseStack; with the module or an option off every hook passes vanilla's result on. 1.9+ has no sword blocking:
 * holding a sword while blocking with an off-hand shield stands in for it (the shield is hidden, the sword blocks). Third
 * person and dropped items decide at render-state extraction: a flat item is resolved with no display transform there, and
 * submit replays the recipe for states resolved that way (vanilla never resolves hand or dropped items with NONE).
 */
public final class NativeOldAnimations {
    public static final Platform PLATFORM = Platform.MODERN;
    private static final OldAnimationsModule MODULE = (OldAnimationsModule) ModuleManager.getInstance().getModule(OldAnimationsModule.NAME);
    /** Each place a hook changed vanilla's result; the probe checks they count with the module on and stay still with it off. */
    public enum Hook { FP_HAND, FP_SHIELD_HIDDEN, LOW_SHIELD, NO_DIP, SWING_USING, SNEAK, HEARTS, TP_POSE, TP_ARM, TP_ITEM, TP_SHIELD_HIDDEN, ARMOUR, DROP, LOW_FIRE }
    private static final int[] HITS = new int[Hook.values().length];
    private static final ItemStackRenderState SCRATCH = new ItemStackRenderState();
    /** The overlay of the armour piece HumanoidArmorLayer is submitting (NO_OVERLAY outside it). */
    private static int armour = OverlayTexture.NO_OVERLAY;
    /** QA only (NativeOldAnimationsProbe): renderItem and the hand layer record each item draw's context and matrix and skip it. */
    static boolean recording;
    static final List<ItemDisplayContext> DRAWN = new ArrayList<>();
    static final Matrix4f LAST = new Matrix4f();
    /** QA: the pitch blockingArm last gave an arm, before other mods (NotEnoughAnimations smooths arms afterwards). */
    static float armPitch;

    private NativeOldAnimations() {}

    static OldAnimationsModule module() { return MODULE; }

    static int hits(Hook hook) { return HITS[hook.ordinal()]; }

    private static void hit(Hook hook) { HITS[hook.ordinal()]++; }

    private static boolean active(Feature feature) { return MODULE.active(feature, PLATFORM); }

    /** Recipe ops on a PoseStack; each post-multiplies the current matrix, as the recipes expect. */
    private static OldAnimations.Sink sink(PoseStack pose) {
        return new OldAnimations.Sink() {
            @Override public void translate(float x, float y, float z) { pose.translate(x, y, z); }
            @Override public void rotate(float degrees, float x, float y, float z) { pose.mulPose((x != 0 ? Axis.XP : y != 0 ? Axis.YP : Axis.ZP).rotationDegrees(degrees)); }
            @Override public void scale(float x, float y, float z) { pose.scale(x, y, z); }
        };
    }

    /** A flat item model: vanilla's own test (ItemEntityRenderer's 1/16 depth at the ground scale of 0.5) with no display transform. */
    static boolean flat(ItemStackRenderState state) {
        double depth = state.isEmpty() ? 0 : state.getModelBoundingBox().getZsize();
        return depth > 0 && depth <= 0.125;
    }

    /** The flat item kind 1.7 drew differently. */
    static Held kind(ItemStack stack) {
        if (stack.is(Items.BOW)) return Held.BOW;
        if (stack.is(Items.FISHING_ROD) || stack.is(Items.CARROT_ON_A_STICK) || stack.is(Items.WARPED_FUNGUS_ON_A_STICK)) return Held.ROD;
        // 1.7's isFull3D items; on 1.21.11 the handheld models.
        return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS)
            || stack.is(ItemTags.HOES) || stack.is(Items.STICK) || stack.is(Items.BONE) || stack.is(Items.BLAZE_ROD)
            || stack.is(Items.BREEZE_ROD) || stack.is(Items.MACE) ? Held.TOOL : Held.ITEM;
    }

    /** The flat item kind, or null for block and special models, which keep vanilla's rendering. */
    static Held held(ItemStack stack, LivingEntity entity) {
        if (stack.isEmpty()) return null;
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(SCRATCH, stack, ItemDisplayContext.NONE, entity.level(), entity, entity.getId());
        return flat(SCRATCH) ? kind(stack) : null;
    }

    /** The shield-triggered sword block: a sword in the main hand while an off-hand shield blocks. */
    static boolean swordBlock(LivingEntity entity) {
        return entity.isUsingItem() && entity.getUsedItemHand() == InteractionHand.OFF_HAND && entity.getUseItemRemainingTicks() > 0
            && entity.getUseItem().getUseAnimation() == ItemUseAnimation.BLOCK && entity.getMainHandItem().is(ItemTags.SWORDS);
    }

    /** A hand's use as 1.7 saw it: NONE while idle, null for uses 1.7 did not have (vanilla keeps them). */
    private static Use use(LivingEntity player, InteractionHand hand, ItemStack stack) {
        if (!player.isUsingItem() || player.getUseItemRemainingTicks() <= 0 || player.getUsedItemHand() != hand) return Use.NONE;
        return switch (stack.getUseAnimation()) {
            case EAT, DRINK -> Use.EAT_DRINK;
            case BOW -> stack.is(Items.BOW) ? Use.BOW : null;
            case BLOCK -> stack.is(ItemTags.SWORDS) ? Use.BLOCK : null;
            default -> null;
        };
    }

    /**
     * ItemInHandRenderer.renderArmWithItem right after its push (the arm origin): the 1.7 hand and icon placement, drawn with
     * no display transform. True when this drew the hand (or hid the blocking shield); the caller pops and cancels.
     */
    public static boolean firstPerson(ItemInHandRenderer renderer, AbstractClientPlayer player, float partial, InteractionHand hand,
                                      float swing, ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector collector, int light) {
        if (!MODULE.isEnabled() || stack.isEmpty()) return false;
        boolean main = hand == InteractionHand.MAIN_HAND;
        boolean block = swordBlock(player) && (active(Feature.BLOCKHIT) || active(Feature.BLOCK_POSE));
        if (block && !main) {
            hit(Hook.FP_SHIELD_HIDDEN);
            return true;
        }
        // Blockhitting without the block pose: the sword stays in its idle (1.7 held item) place, swinging.
        Use use = block ? active(Feature.BLOCK_POSE) ? Use.BLOCK : Use.NONE : use(player, hand, stack);
        Held held = use == null ? null : held(stack, player);
        if (held == null || !MODULE.iconPlacement(PLATFORM, use, held)) return false;
        int side = (main ? player.getMainArm() : player.getMainArm().getOpposite()) == HumanoidArm.RIGHT ? 1 : -1;
        OldAnimations.Sink sink = sink(pose);
        OldAnimations.hand(sink, side, equip, MODULE.swingShown(PLATFORM, use, swing), use, player.getUseItemRemainingTicks(), partial,
            stack.getUseDuration(player));
        OldAnimations.item(sink, side, held == Held.ROD);
        renderer.renderItem(player, stack, ItemDisplayContext.NONE, pose, collector, light);
        hit(Hook.FP_HAND);
        return true;
    }

    /** ItemInHandRenderer.renderItem HEAD: Low Shield lowers a first-person shield. */
    public static void lowShield(ItemDisplayContext context, ItemStack stack, PoseStack pose) {
        if (!context.firstPerson() || !stack.is(Items.SHIELD) || !active(Feature.LOW_SHIELD)) return;
        pose.translate(0.0f, -0.25f, 0.0f);
        hit(Hook.LOW_SHIELD);
    }

    /** QA only: while the probe records, an item draw is noted and skipped (true). */
    public static boolean recorded(ItemDisplayContext context, PoseStack pose) {
        if (!recording) return false;
        DRAWN.add(context);
        LAST.set(pose.last().pose());
        return true;
    }

    /** ItemInHandRenderer.tick: the swap scale the equip height follows (No attack-cooldown dip keeps it at 1). */
    public static float equipScale(float vanilla) {
        float scale = MODULE.equipScale(PLATFORM, vanilla);
        if (scale != vanilla) hit(Hook.NO_DIP);
        return scale;
    }

    /** Minecraft.handleKeybinds consumed an attack click while an item is in use: 1.7 still swung (shown locally, nothing is sent). */
    public static void attackWhileUsing(LocalPlayer player) {
        if (!(swordBlock(player) ? active(Feature.BLOCKHIT) : active(Feature.SWING_WHILE_USING))) return;
        player.swing(InteractionHand.MAIN_HAND, false);
        hit(Hook.SWING_USING);
    }

    /** Camera.tick: 1.7's sneak camera, at a lower eye height in one tick and easing back up. */
    public static float eyeHeight(float previous, float vanilla, Entity entity) {
        if (entity == null || !active(Feature.INSTANT_SNEAK)) return vanilla;
        float height = OldAnimations.sneakEyeHeight(previous, entity.getEyeHeight());
        if (height != vanilla) hit(Hook.SNEAK);
        return height;
    }

    /** Gui.renderPlayerHealth: the damage blink passed to renderHearts. */
    public static boolean heartsBlink(boolean vanilla) {
        boolean blink = MODULE.heartsBlink(PLATFORM, vanilla);
        if (blink != vanilla) hit(Hook.HEARTS);
        return blink;
    }

    /** ScreenEffectRenderer.renderFire: Low Fire lowers both fire quads. */
    public static float fireY(float y) {
        if (!active(Feature.LOW_FIRE)) return y;
        hit(Hook.LOW_FIRE);
        return y - 0.3f;
    }

    /**
     * AvatarRenderer.extractRenderState: during the sword block the sword arm blocks, the shield arm is empty and the shield
     * hidden; flat held items are resolved with no display transform for 1.7's placement.
     */
    public static void extract(LivingEntity avatar, AvatarRenderState state) {
        if (!active(Feature.THIRD_PERSON)) return;
        if (swordBlock(avatar)) {
            boolean right = state.mainArm == HumanoidArm.RIGHT;
            state.rightArmPose = right ? ArmPose.BLOCK : ArmPose.EMPTY;
            state.leftArmPose = right ? ArmPose.EMPTY : ArmPose.BLOCK;
            (right ? state.leftHandItemState : state.rightHandItemState).clear();
            if (right) state.leftHandItemStack = ItemStack.EMPTY; else state.rightHandItemStack = ItemStack.EMPTY;
            hit(Hook.TP_POSE);
            hit(Hook.TP_SHIELD_HIDDEN);
        }
        flatInHand(state.rightHandItemState, state.rightHandItemStack, avatar, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
        flatInHand(state.leftHandItemState, state.leftHandItemStack, avatar, ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
    }

    /** Re-resolves a flat held item with no display transform; anything else (and spears, which animate in hand) keeps vanilla's state. */
    private static void flatInHand(ItemStackRenderState state, ItemStack stack, LivingEntity entity, ItemDisplayContext context) {
        if (stack.isEmpty() || stack.getSwingAnimation().type() != SwingAnimationType.WHACK || stack.is(ItemTags.SPEARS)) return;
        var resolver = Minecraft.getInstance().getItemModelResolver();
        resolver.updateForLiving(state, stack, ItemDisplayContext.NONE, entity);
        if (!flat(state)) resolver.updateForLiving(state, stack, context, entity);
    }

    /** HumanoidModel.setupAnim: the arm a player's render state poses as 1.7's sword block, or null. */
    public static HumanoidArm swordArm(HumanoidRenderState state) {
        if (!(state instanceof AvatarRenderState) || !active(Feature.THIRD_PERSON)) return null;
        if (state.rightArmPose == ArmPose.BLOCK && state.rightHandItemStack.is(ItemTags.SWORDS)) return HumanoidArm.RIGHT;
        return state.leftArmPose == ArmPose.BLOCK && state.leftHandItemStack.is(ItemTags.SWORDS) ? HumanoidArm.LEFT : null;
    }

    /** 1.7.10 ModelBiped blocking arm: only pitched (x * 0.5 - 3π/10); 1.8+ also turns it 30° inward and follows the head. */
    public static void blockingArm(ModelPart arm, boolean right) {
        arm.xRot = arm.xRot * 0.5f - 0.9424779f;
        arm.yRot = right ? OldAnimations.BLOCKING_ARM_YAW : -OldAnimations.BLOCKING_ARM_YAW;
        armPitch = arm.xRot;
        hit(Hook.TP_ARM);
    }

    /**
     * ItemInHandLayer.submitArmWithItem's submit, after vanilla's hand offsets: an item extract resolved with no display transform
     * goes where 1.7 held it.
     */
    public static void thirdPersonItem(ItemStackRenderState item, ItemStack stack, HumanoidArm arm, ArmedEntityRenderState state, PoseStack pose) {
        if (item.displayContext != ItemDisplayContext.NONE) return;
        Held held = kind(stack);
        int side = arm == HumanoidArm.LEFT ? -1 : 1;
        // Undo the layer's -90° X, 180° Y and (±1, 2, -10)/16: the recipe starts where translateToHand left the matrix.
        pose.translate(-side / 16.0f, -0.125f, 0.625f);
        pose.mulPose(Axis.YP.rotationDegrees(-180.0f));
        pose.mulPose(Axis.XP.rotationDegrees(90.0f));
        OldAnimations.Sink sink = sink(pose);
        OldAnimations.thirdPersonHand(sink, side);
        OldAnimations.thirdPersonItem(sink, side, held, held == Held.TOOL && (arm == HumanoidArm.LEFT ? state.leftArmPose : state.rightArmPose) == ArmPose.BLOCK);
        hit(Hook.TP_ITEM);
    }

    /** HumanoidArmorLayer.submit: the overlay its armour pieces take (the body's hurt overlay with Red armour on hurt); null ends it. */
    public static void armourPieces(LivingEntityRenderState state) {
        armour = state != null && MODULE.tintArmour(PLATFORM, state.hasRedOverlay ? 1 : 0, 0)
            ? LivingEntityRenderer.getOverlayCoords(state, 0.0f) : OverlayTexture.NO_OVERLAY;
    }

    /** EquipmentLayerRenderer.renderLayers: an armour layer's overlay. */
    public static int armourOverlay(int vanilla) {
        if (armour == OverlayTexture.NO_OVERLAY) return vanilla;
        hit(Hook.ARMOUR);
        return armour;
    }

    /** ItemEntityRenderer.extractRenderState: a flat dropped item (vanilla's own test) is re-resolved with no display transform. */
    public static void droppedItem(ItemEntity entity, ItemEntityRenderState state) {
        if (!active(Feature.DROPPED_2D) || state.item.isEmpty() || state.item.getModelBoundingBox().getZsize() > 0.0625) return;
        var resolver = Minecraft.getInstance().getItemModelResolver();
        resolver.updateForNonLiving(state.item, entity.getItem(), ItemDisplayContext.NONE, entity);
        // Some items are flat only on the ground (the trident's in-hand model is 3D): those keep vanilla.
        if (!flat(state.item)) resolver.updateForNonLiving(state.item, entity.getItem(), ItemDisplayContext.GROUND, entity);
    }

    /** ItemEntityRenderer.submit: 1.7's camera-facing icon for a state extract resolved flat; true when it submitted. */
    public static boolean droppedItem(ItemEntityRenderState state, PoseStack pose, SubmitNodeCollector collector) {
        if (state.item.displayContext != ItemDisplayContext.NONE) return false;
        pose.pushPose();
        OldAnimations.droppedItem(sink(pose), state.ageInTicks, state.bobOffset, Minecraft.getInstance().gameRenderer.getMainCamera().yRot());
        state.item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        pose.popPose();
        hit(Hook.DROP);
        return true;
    }
}
