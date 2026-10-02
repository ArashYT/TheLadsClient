package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.client.OldAnimations;
import com.thelads.core.client.OldAnimations.Held;
import com.thelads.core.client.OldAnimations.Use;
import com.thelads.core.modules.OldAnimationsModule;
import com.thelads.core.modules.OldAnimationsModule.Feature;
import com.thelads.core.modules.OldAnimationsModule.Platform;
import java.util.EnumSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.FoodOnAStickItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import org.joml.Quaternionf;

/** 1.7 Animations on 26.x: the mixins ask here, and the PoseStack replays the recipes in {@link OldAnimations}. */
public final class NativeOldAnimations {
    private static final Platform MODERN = Platform.MODERN;
    /** Entity render state flags set at extraction: what the 1.7 placement prepared for the submit hooks. */
    static final int DROPPED = 1, RIGHT_ICON = 2, LEFT_ICON = 4, SWORD_BLOCK = 8;
    /** Features whose hook changed what the game drew; the QA probe clears and reads it. */
    static final EnumSet<Feature> APPLIED = EnumSet.noneOf(Feature.class);
    /** The blink flag the last health bar was drawn with (QA). */
    static boolean lastHeartsBlink;
    private NativeOldAnimations() {}

    /** Extraction flags carried on every entity render state (OldAnimationsStateMixin). */
    public interface State {
        int lads$oldAnimations();
        void lads$oldAnimations(int flags);
    }

    static OldAnimationsModule module() {
        return NativeQualityOfLife.module(OldAnimationsModule.NAME) instanceof OldAnimationsModule module ? module : null;
    }

    static boolean active(Feature feature) {
        OldAnimationsModule module = module();
        return module != null && module.active(feature, MODERN);
    }

    /**
     * First person, at submitArmWithItem's item draw (the pose is inside its push): the display context to draw with,
     * {@code NONE} once the pose holds the 1.7 placement, or null to draw nothing (the sword blocks, the shield hides).
     */
    public static ItemDisplayContext firstPerson(LivingEntity player, InteractionHand hand, ItemStack item, float partial, float swing,
                                                 float equip, PoseStack pose, ItemDisplayContext vanilla) {
        OldAnimationsModule module = module();
        if (module == null || !module.isEnabled()) return vanilla;
        boolean block = swordBlocking(player);
        if (block && hand == InteractionHand.OFF_HAND) return null;
        Use use = block ? Use.BLOCK : use(player, hand, item);
        Held held = held(item);
        if (use == null || !module.iconPlacement(MODERN, use, held)) {
            if (item.getItem() instanceof ShieldItem && module.active(Feature.LOW_SHIELD, MODERN)) {
                pose.translate(0, -0.25f, 0);
                APPLIED.add(Feature.LOW_SHIELD);
            }
            return vanilla;
        }
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        OldAnimations.Sink out = sink(pose);
        if (use == Use.NONE) OldAnimations.fromModernHand(out, side); // idle bow or rod: vanilla's arm and swing (or LegacySwing's) stay
        else {
            pose.popPose(); // back to the arm origin (submitArmWithItem's own push), dropping vanilla's use transforms
            pose.pushPose();
            float shown = module.swingShown(MODERN, use, swing);
            OldAnimations.hand(out, side, equip, shown, use, player.getUseItemRemainingTicks(), partial, item.getUseDuration(player));
            if (shown > 0) APPLIED.add(use == Use.BLOCK ? Feature.BLOCKHIT : Feature.SWING_WHILE_USING);
        }
        OldAnimations.item(out, side, held == Held.ROD);
        APPLIED.add(use == Use.BLOCK ? Feature.BLOCK_POSE : use == Use.EAT_DRINK ? Feature.EAT_DRINK
            : use == Use.BOW || held == Held.BOW ? Feature.BOW : held == Held.ROD ? Feature.ROD : Feature.HELD_ITEMS);
        return ItemDisplayContext.NONE;
    }

    /** The 26.x trigger for the 1.7 sword block: a sword in the main hand while the off hand blocks with a shield. */
    static boolean swordBlocking(LivingEntity entity) {
        return (active(Feature.BLOCK_POSE) || active(Feature.BLOCKHIT)) && entity.isUsingItem()
            && entity.getUsedItemHand() == InteractionHand.OFF_HAND && entity.getOffhandItem().getUseAnimation() == ItemUseAnimation.BLOCK
            && entity.getMainHandItem().is(ItemTags.SWORDS);
    }

    /** 1.7's EnumAction for this hand: NONE when idle, null for a use 1.7 never had (vanilla draws it). */
    private static Use use(LivingEntity entity, InteractionHand hand, ItemStack item) {
        if (!entity.isUsingItem() || entity.getUseItemRemainingTicks() <= 0 || entity.getUsedItemHand() != hand) return Use.NONE;
        ItemUseAnimation animation = item.getUseAnimation();
        return animation == ItemUseAnimation.EAT || animation == ItemUseAnimation.DRINK ? Use.EAT_DRINK
            : animation == ItemUseAnimation.BOW ? Use.BOW : null;
    }

    static Held held(ItemStack item) {
        if (item.getItem() instanceof BowItem) return Held.BOW;
        if (item.getItem() instanceof FishingRodItem || item.getItem() instanceof FoodOnAStickItem) return Held.ROD;
        // 1.7.10 isFull3D: swords, tools, sticks, bones and blaze rods.
        if (item.is(ItemTags.SWORDS) || item.is(ItemTags.AXES) || item.is(ItemTags.PICKAXES) || item.is(ItemTags.SHOVELS)
            || item.is(ItemTags.HOES) || item.is(Items.STICK) || item.is(Items.BONE) || item.is(Items.BLAZE_ROD)) return Held.TOOL;
        return Held.ITEM;
    }

    /** No attack-cooldown dip: the swap scale the equip animation reads. */
    public static float equipScale(float vanilla) {
        OldAnimationsModule module = module();
        float scale = module == null ? vanilla : module.equipScale(MODERN, vanilla);
        if (scale != vanilla) APPLIED.add(Feature.NO_COOLDOWN_DIP);
        return scale;
    }

    /** No heart flashing: vanilla's health-bar blink flag. */
    public static boolean heartsBlink(boolean vanilla) {
        OldAnimationsModule module = module();
        lastHeartsBlink = module == null ? vanilla : module.heartsBlink(MODERN, vanilla);
        if (lastHeartsBlink != vanilla) APPLIED.add(Feature.NO_HEART_FLASH);
        return lastHeartsBlink;
    }

    /** Instant sneak camera, each Camera.tick: 1.7's step towards the entity's eye height instead of vanilla's half-way ease. */
    public static float eyeHeight(float previous, float target, float vanilla) {
        if (!active(Feature.INSTANT_SNEAK)) return vanilla;
        float eye = OldAnimations.sneakEyeHeight(previous, target);
        if (eye != vanilla) APPLIED.add(Feature.INSTANT_SNEAK);
        return eye;
    }

    /** Low Fire: lower the first-person fire overlay. */
    public static boolean lowFire() {
        if (!active(Feature.LOW_FIRE)) return false;
        APPLIED.add(Feature.LOW_FIRE);
        return true;
    }

    /** Red armour on hurt: humanoid armour layers take the body's hurt overlay instead of none. */
    public static int armourOverlay(EquipmentClientInfo.LayerType type, Object state, int vanilla) {
        if (type != EquipmentClientInfo.LayerType.HUMANOID && type != EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS
            && type != EquipmentClientInfo.LayerType.HUMANOID_BABY || !(state instanceof LivingEntityRenderState living)) return vanilla;
        OldAnimationsModule module = module();
        if (module == null || !module.tintArmour(MODERN, living.hasRedOverlay ? 1 : 0, 0)) return vanilla;
        APPLIED.add(Feature.RED_ARMOUR);
        return LivingEntityRenderer.getOverlayCoords(living, 0);
    }

    /** ItemEntityRenderer extraction: a flat item is re-resolved without a display transform for the 1.7 icon. */
    public static void droppedItem(ItemEntity entity, ItemEntityRenderState state) {
        int flags = 0;
        ItemStackRenderState item = state.item;
        if (active(Feature.DROPPED_2D) && !item.isEmpty() && !item.usesBlockLight()) { // blocks keep their 3D model
            var resolver = Minecraft.getInstance().getItemModelResolver();
            resolver.updateForNonLiving(item, entity.getItem(), ItemDisplayContext.NONE, entity);
            // 3D outside the ground context (a trident, a spyglass): vanilla again.
            if (item.usesBlockLight()) resolver.updateForNonLiving(item, entity.getItem(), ItemDisplayContext.GROUND, entity);
            else flags = DROPPED;
        }
        ((State) state).lads$oldAnimations(flags);
    }

    /** ItemEntityRenderer submit, at vanilla's draw: the 1.7 Fast-graphics icon facing the camera; false lets vanilla draw. */
    public static boolean droppedItem(ItemEntityRenderState state, PoseStack pose, SubmitNodeCollector collector, int light, float cameraYaw) {
        if ((((State) state).lads$oldAnimations() & DROPPED) == 0) return false;
        pose.popPose(); // back to the entity origin (submit's own push), dropping vanilla's bob and spin
        pose.pushPose();
        OldAnimations.droppedItem(sink(pose), state.ageInTicks, state.bobOffset, cameraYaw);
        state.item.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
        APPLIED.add(Feature.DROPPED_2D);
        return true;
    }

    /** AvatarRenderer extraction: the third-person sword block (arm poses, hidden shield) and flat held items re-resolved for 1.7. */
    public static void thirdPerson(LivingEntity entity, AvatarRenderState state) {
        int flags = 0;
        if (active(Feature.THIRD_PERSON)) {
            HumanoidArm main = state.mainArm, off = main.getOpposite();
            if (swordBlocking(entity)) {
                flags = SWORD_BLOCK;
                if (main == HumanoidArm.RIGHT) { state.rightArmPose = HumanoidModel.ArmPose.BLOCK; state.leftArmPose = HumanoidModel.ArmPose.EMPTY; }
                else { state.leftArmPose = HumanoidModel.ArmPose.BLOCK; state.rightArmPose = HumanoidModel.ArmPose.EMPTY; }
                itemState(state, off).clear();
            }
            var resolver = Minecraft.getInstance().getItemModelResolver();
            for (HumanoidArm arm : HumanoidArm.values()) {
                ItemStackRenderState item = itemState(state, arm);
                if (item.isEmpty() || item.usesBlockLight()) continue; // blocks and special models keep vanilla
                ItemStack stack = entity.getItemHeldByArm(arm);
                resolver.updateForLiving(item, stack, ItemDisplayContext.NONE, entity);
                if (item.usesBlockLight()) resolver.updateForLiving(item, stack,
                    arm == HumanoidArm.RIGHT ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND, entity);
                else flags |= arm == HumanoidArm.RIGHT ? RIGHT_ICON : LEFT_ICON;
            }
        }
        ((State) state).lads$oldAnimations(flags);
    }

    private static ItemStackRenderState itemState(ArmedEntityRenderState state, HumanoidArm arm) {
        return arm == HumanoidArm.RIGHT ? state.rightHandItemState : state.leftHandItemState;
    }

    /**
     * ItemInHandLayer, at vanilla's item draw: true once the pose holds 1.7's third-person placement (draw with NONE).
     * {@code pivot}: the pose right after the layer's translateToHand, where 1.7's frames start.
     */
    public static boolean thirdPersonItem(ArmedEntityRenderState state, ItemStack stack, HumanoidArm arm, PoseStack pose, PoseStack.Pose pivot) {
        int flags = ((State) state).lads$oldAnimations();
        if ((flags & (arm == HumanoidArm.RIGHT ? RIGHT_ICON : LEFT_ICON)) == 0) return false;
        pose.last().set(pivot); // drops vanilla's hand transforms
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        Held held = held(stack);
        OldAnimations.thirdPersonHand(sink(pose), side);
        OldAnimations.thirdPersonItem(sink(pose), side, held, (flags & SWORD_BLOCK) != 0 && arm == state.mainArm && held == Held.TOOL);
        APPLIED.add(Feature.THIRD_PERSON);
        return true;
    }

    /** HumanoidModel.setupAnim tail: 1.7's sword-blocking arm only pitches; vanilla's block also follows the head and turns 30° in. */
    public static void blockingArm(HumanoidRenderState state, HumanoidModel<?> model) {
        if ((((State) state).lads$oldAnimations() & SWORD_BLOCK) == 0) return;
        boolean right = state.mainArm == HumanoidArm.RIGHT;
        ModelPart arm = right ? model.rightArm : model.leftArm;
        // Remove exactly vanilla poseBlockingArm's extra terms; the swing and crouch added after it stay.
        arm.xRot -= Mth.clamp(model.head.xRot, -1.3962634F, 0.43633232F);
        arm.yRot += OldAnimations.BLOCKING_ARM_YAW - (right ? -30 : 30) * Mth.DEG_TO_RAD - Mth.clamp(model.head.yRot, -0.5235988F, 0.5235988F);
        APPLIED.add(Feature.THIRD_PERSON);
    }

    private static OldAnimations.Sink sink(PoseStack pose) {
        return new OldAnimations.Sink() {
            @Override public void translate(float x, float y, float z) { pose.translate(x, y, z); }
            @Override public void rotate(float degrees, float x, float y, float z) {
                pose.mulPose(new Quaternionf().rotationAxis(degrees * Mth.DEG_TO_RAD, x, y, z));
            }
            @Override public void scale(float x, float y, float z) { pose.scale(x, y, z); }
        };
    }
}
