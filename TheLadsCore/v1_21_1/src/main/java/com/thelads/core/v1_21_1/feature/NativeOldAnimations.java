package com.thelads.core.v1_21_1.feature;

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
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import org.joml.Matrix4f;

/**
 * "1.7 Animations" on 1.21.1. The OldAnimations* mixins ask this class and replay the shared recipes ({@link OldAnimations})
 * through PoseStack; with the module or an option off every hook passes vanilla's result on. 1.9+ has no sword blocking:
 * holding a sword while blocking with an off-hand shield stands in for it (the shield is hidden, the sword blocks).
 */
public final class NativeOldAnimations {
    public static final Platform PLATFORM = Platform.MODERN;
    private static final OldAnimationsModule MODULE = (OldAnimationsModule) ModuleManager.getInstance().getModule(OldAnimationsModule.NAME);
    /** Each place a hook changed vanilla's result; the probe checks they count with the module on and stay still with it off. */
    public enum Hook { FP_HAND, FP_SHIELD_HIDDEN, LOW_SHIELD, NO_DIP, SWING_USING, SNEAK, HEARTS, TP_POSE, TP_ARM, TP_ITEM, TP_SHIELD_HIDDEN, ARMOUR, DROP, LOW_FIRE }
    private static final int[] HITS = new int[Hook.values().length];
    /** QA only (NativeOldAnimationsProbe): ItemInHandRenderer.renderItem records each draw's context and matrix and skips it. */
    static boolean recording;
    static final List<ItemDisplayContext> DRAWN = new ArrayList<>();
    static final Matrix4f LAST = new Matrix4f();

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

    /** The flat item kind 1.7 drew differently, or null for block and special models, which keep vanilla's rendering. */
    static Held held(ItemStack stack, LivingEntity entity) {
        if (stack.isEmpty()) return null;
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(stack, entity.level(), entity, entity.getId());
        if (model.isGui3d() || model.isCustomRenderer()) return null;
        if (stack.is(Items.BOW)) return Held.BOW;
        if (stack.is(Items.FISHING_ROD) || stack.is(Items.CARROT_ON_A_STICK) || stack.is(Items.WARPED_FUNGUS_ON_A_STICK)) return Held.ROD;
        // 1.7's isFull3D items; on 1.21.1 the handheld models.
        return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS)
            || stack.is(ItemTags.HOES) || stack.is(Items.STICK) || stack.is(Items.BONE) || stack.is(Items.BLAZE_ROD)
            || stack.is(Items.BREEZE_ROD) || stack.is(Items.MACE) ? Held.TOOL : Held.ITEM;
    }

    /** The shield-triggered sword block: a sword in the main hand while an off-hand shield blocks. */
    static boolean swordBlock(LivingEntity entity) {
        return entity.isUsingItem() && entity.getUsedItemHand() == InteractionHand.OFF_HAND && entity.getUseItemRemainingTicks() > 0
            && entity.getUseItem().getUseAnimation() == UseAnim.BLOCK && entity.getMainHandItem().is(ItemTags.SWORDS);
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
                                      float swing, ItemStack stack, float equip, PoseStack pose, MultiBufferSource buffers, int light) {
        if (!MODULE.isEnabled() || stack.isEmpty()) return false;
        boolean main = hand == InteractionHand.MAIN_HAND;
        boolean block = swordBlock(player) && (active(Feature.BLOCKHIT) || active(Feature.BLOCK_POSE));
        if (block && !main) {
            hit(Hook.FP_SHIELD_HIDDEN);
            return true;
        }
        Use use = block ? Use.BLOCK : use(player, hand, stack);
        Held held = use == null ? null : held(stack, player);
        if (held == null || !MODULE.iconPlacement(PLATFORM, use, held)) return false;
        int side = (main ? player.getMainArm() : player.getMainArm().getOpposite()) == HumanoidArm.RIGHT ? 1 : -1;
        OldAnimations.Sink sink = sink(pose);
        OldAnimations.hand(sink, side, equip, MODULE.swingShown(PLATFORM, use, swing), use, player.getUseItemRemainingTicks(), partial,
            stack.getUseDuration(player));
        OldAnimations.item(sink, side, held == Held.ROD);
        renderer.renderItem(player, stack, ItemDisplayContext.NONE, side < 0, pose, buffers, light);
        hit(Hook.FP_HAND);
        return true;
    }

    /** ItemInHandRenderer.renderItem HEAD: Low Shield lowers a first-person shield. */
    public static void lowShield(ItemDisplayContext context, ItemStack stack, PoseStack pose) {
        if (!context.firstPerson() || !stack.is(Items.SHIELD) || !active(Feature.LOW_SHIELD)) return;
        pose.translate(0.0f, -0.25f, 0.0f);
        hit(Hook.LOW_SHIELD);
    }

    /** QA only: while the probe records, renderItem notes the draw and skips it (true). */
    public static boolean recorded(ItemDisplayContext context, PoseStack pose) {
        if (!recording) return false;
        DRAWN.add(context);
        LAST.set(pose.last().pose());
        return true;
    }

    /** ItemInHandRenderer.tick: the attack scale the equip height follows (No attack-cooldown dip keeps it at 1). */
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

    /** PlayerRenderer.getArmPose: during the sword block the sword arm blocks and the shield arm is empty. */
    public static HumanoidModel.ArmPose armPose(AbstractClientPlayer player, InteractionHand hand, HumanoidModel.ArmPose vanilla) {
        if (!active(Feature.THIRD_PERSON) || !swordBlock(player)) return vanilla;
        hit(Hook.TP_POSE);
        return hand == InteractionHand.MAIN_HAND ? HumanoidModel.ArmPose.BLOCK : HumanoidModel.ArmPose.EMPTY;
    }

    /** HumanoidModel.setupAnim: the arm posed as 1.7's sword block, or null. */
    public static HumanoidArm swordArm(LivingEntity entity) {
        return entity instanceof Player && active(Feature.THIRD_PERSON) && swordBlock(entity) ? entity.getMainArm() : null;
    }

    /** 1.7.10 ModelBiped blocking arm: only pitched (x * 0.5 - 3π/10); 1.8+ also turns it 30° inward and follows the head. */
    public static void blockingArm(ModelPart arm, boolean right) {
        arm.xRot = arm.xRot * 0.5f - 0.9424779f;
        arm.yRot = right ? OldAnimations.BLOCKING_ARM_YAW : -OldAnimations.BLOCKING_ARM_YAW;
        hit(Hook.TP_ARM);
    }

    /** ItemInHandLayer.render: the stack drawn in an arm; the blocking shield is hidden during the sword block. */
    public static ItemStack layerStack(LivingEntity entity, ItemStack stack, HumanoidArm arm) {
        if (stack.isEmpty() || arm == entity.getMainArm() || swordArm(entity) == null) return stack;
        hit(Hook.TP_SHIELD_HIDDEN);
        return ItemStack.EMPTY;
    }

    /**
     * ItemInHandLayer.renderArmWithItem's draw, after vanilla's hand offsets: a player's flat item in 1.7's place with no display
     * transform. True when this drew it.
     */
    public static boolean thirdPersonItem(ItemInHandRenderer renderer, LivingEntity entity, ItemStack stack, boolean left, boolean blockingArm,
                                          PoseStack pose, MultiBufferSource buffers, int light) {
        Held held = entity instanceof Player && active(Feature.THIRD_PERSON) ? held(stack, entity) : null;
        if (held == null) return false;
        int side = left ? -1 : 1;
        // Undo the layer's -90° X, 180° Y and (±1, 2, -10)/16: the recipe starts where translateToHand left the matrix.
        pose.translate(-side / 16.0f, -0.125f, 0.625f);
        pose.mulPose(Axis.YP.rotationDegrees(-180.0f));
        pose.mulPose(Axis.XP.rotationDegrees(90.0f));
        OldAnimations.Sink sink = sink(pose);
        OldAnimations.thirdPersonHand(sink, side);
        OldAnimations.thirdPersonItem(sink, side, held, held == Held.TOOL && blockingArm);
        renderer.renderItem(entity, stack, ItemDisplayContext.NONE, left, pose, buffers, light);
        hit(Hook.TP_ITEM);
        return true;
    }

    /** HumanoidArmorLayer: armour takes the body's hurt overlay while Red armour on hurt is on (1.7 drew it in the tinted pass). */
    public static int armourOverlay(LivingEntity entity) {
        return MODULE.tintArmour(PLATFORM, entity.hurtTime, entity.deathTime) ? LivingEntityRenderer.getOverlayCoords(entity, 0.0f) : OverlayTexture.NO_OVERLAY;
    }

    /** HumanoidArmorLayer's armour and trim draws: the overlay armourOverlay chose for this entity. */
    public static int armourLayer(int overlay) {
        if (overlay != OverlayTexture.NO_OVERLAY) hit(Hook.ARMOUR);
        return overlay;
    }

    /** ItemEntityRenderer.render: a flat dropped item as 1.7's camera-facing icon; true when it drew (blocks stay 3D). */
    public static boolean droppedItem(ItemEntity entity, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        if (!active(Feature.DROPPED_2D)) return false;
        Minecraft mc = Minecraft.getInstance();
        ItemStack stack = entity.getItem();
        BakedModel model = mc.getItemRenderer().getModel(stack, entity.level(), null, entity.getId());
        if (model.isGui3d() || model.isCustomRenderer()) return false;
        pose.pushPose();
        OldAnimations.droppedItem(sink(pose), entity.getAge() + partial, entity.bobOffs, mc.getEntityRenderDispatcher().camera.getYRot());
        mc.getItemRenderer().render(stack, ItemDisplayContext.NONE, false, pose, buffers, light, OverlayTexture.NO_OVERLAY, model);
        pose.popPose();
        hit(Hook.DROP);
        return true;
    }
}
