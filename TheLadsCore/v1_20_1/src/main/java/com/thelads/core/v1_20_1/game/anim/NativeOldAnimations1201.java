package com.thelads.core.v1_20_1.game.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.client.OldAnimations;
import com.thelads.core.client.OldAnimations.Held;
import com.thelads.core.client.OldAnimations.Use;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.ItemPhysicsModule;
import com.thelads.core.modules.OldAnimationsModule;
import com.thelads.core.modules.OldAnimationsModule.Feature;
import com.thelads.core.modules.OldAnimationsModule.Platform;
import java.util.EnumSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.FoodOnAStickItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.UseAnim;
import org.joml.Quaternionf;

/** 1.7 Animations on 1.20.1: the mixins ask here, and the PoseStack replays the recipes in {@link OldAnimations}. */
public final class NativeOldAnimations1201 {
    private static final Platform MODERN = Platform.MODERN;
    /** Features whose hook changed what the game drew; the QA probe clears and reads it. */
    public static final EnumSet<Feature> APPLIED = EnumSet.noneOf(Feature.class);
    /** The hurt overlay's result on white (red over the colour at 0.7): what a hurt body shows, as a colour multiplier. */
    public static final float HURT_GREEN_BLUE = 0xB3 / 255f;
    /** Both arms' rotations as the 1.7 sword block left them, re-applied after other model mods (NotEnoughAnimations). */
    private static final float[] BLOCKING_ARMS = new float[6];
    private static Object blockingModel;
    /** Swing while using items: the attack key was pressed during the current use (cleared once no item is in use). */
    private static boolean attacked;
    /** renderArmWithItem's arguments while it runs: the hand, the partial tick, the swing and the equip progress. */
    private static InteractionHand handNow = InteractionHand.MAIN_HAND;
    private static float partialNow, swingNow, equipNow;
    /** The entity whose armour is being drawn, for Red armour on hurt. */
    private static LivingEntity armourOf;
    /** Dropped item draw: copies drawn so far in this ItemEntityRenderer.render call (a 2D icon draws the first only). */
    private static int droppedCopy;
    private static boolean lowFire;

    private NativeOldAnimations1201() {}

    public static OldAnimationsModule module() {
        return ModuleManager.getInstance().getModule(OldAnimationsModule.NAME) instanceof OldAnimationsModule module ? module : null;
    }

    public static boolean active(Feature feature) {
        OldAnimationsModule module = module();
        return module != null && module.active(feature, MODERN);
    }

    private static boolean legacySwing() {
        return ModuleManager.getInstance().getModule("LegacySwing") != null && ModuleManager.getInstance().getModule("LegacySwing").isEnabled();
    }

    // ---- first person -----------------------------------------------------------------------------------------------

    /** renderArmWithItem, at its head: the arguments the item draw below needs. */
    public static void enter(InteractionHand hand, float partial, float swing, float equip) {
        handNow = hand;
        partialNow = partial;
        swingNow = swing;
        equipNow = equip;
    }

    /**
     * First person, at the item draw (the pose is inside renderArmWithItem's push): the display context to draw with,
     * {@code NONE} once the pose holds the 1.7 placement, or null to draw nothing (the sword blocks, the shield hides).
     */
    public static ItemDisplayContext firstPerson(LivingEntity player, ItemStack item, PoseStack pose, ItemDisplayContext vanilla) {
        OldAnimationsModule module = module();
        if (module == null || !module.isEnabled()) return vanilla;
        InteractionHand hand = handNow;
        boolean block = swordBlocking(player);
        if (block && hand == InteractionHand.OFF_HAND) return null;
        Use use = block ? Use.BLOCK : use(player, hand, item);
        Held held = held(item);
        if (use == null || !module.iconPlacement(MODERN, use, held) || kept(item)
            || OldAnimations.legacySwingPlaces(legacySwing(), use, item.getItem() instanceof BlockItem)
            || !ModelShape1201.flat(model(player, item))) {
            if (item.getItem() instanceof ShieldItem && module.active(Feature.LOW_SHIELD, MODERN)) {
                pose.translate(0, -0.25f, 0);
                APPLIED.add(Feature.LOW_SHIELD);
            }
            return vanilla;
        }
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        OldAnimations.Sink out = sink(pose);
        // Idle: vanilla's arm and swing stay, LegacySwing's when it is on (a block item stays vanilla's then: legacySwingPlaces).
        if (use == Use.NONE) OldAnimations.fromModernHand(out, side);
        else {
            pose.popPose(); // back to the arm origin (renderArmWithItem's own push), dropping vanilla's use transforms
            pose.pushPose();
            float shown = module.swingShown(MODERN, use, swingNow, attacked);
            OldAnimations.hand(out, side, equipNow, shown, use, player.getUseItemRemainingTicks(), partialNow, item.getUseDuration());
            if (shown > 0) APPLIED.add(use == Use.BLOCK ? Feature.BLOCKHIT : Feature.SWING_WHILE_USING);
        }
        OldAnimations.item(out, side, held == Held.ROD);
        APPLIED.add(use == Use.BLOCK ? Feature.BLOCK_POSE : use == Use.EAT_DRINK ? Feature.EAT_DRINK
            : use == Use.BOW || held == Held.BOW ? Feature.BOW : held == Held.ROD ? Feature.ROD : Feature.HELD_ITEMS);
        return ItemDisplayContext.NONE;
    }

    private static BakedModel model(LivingEntity entity, ItemStack item) {
        return Minecraft.getInstance().getItemRenderer().getModel(item, entity.level(), entity, entity.getId());
    }

    /** Items with modern-only animations 1.7 never had (the loaded crossbow): always vanilla. */
    private static boolean kept(ItemStack item) {
        return item.getItem() instanceof CrossbowItem;
    }

    /**
     * Minecraft.startUseItem while the attack key mines a block: true lets a held item's 1.7 use (a shield's block, a bow, food)
     * start at once, as it does when looking at air.
     */
    public static boolean useWhileMining(LivingEntity player) {
        OldAnimationsModule module = module();
        if (module == null || player == null) return false;
        for (InteractionHand hand : InteractionHand.values()) if (module.useWhileMining(action(player.getItemInHand(hand)))) return true;
        return false;
    }

    /**
     * Swing while using items and Blockhitting (Minecraft.handleKeybinds): the attack key was pressed while an item is in use. Vanilla
     * drops that click; with the option on, the main arm swings over the use with the held item's swing, on this client only (no swing
     * packet is sent): the use goes on and the server sees what vanilla sends, nothing.
     */
    public static void attackClicked(LocalPlayer player) {
        OldAnimationsModule module = module();
        if (module == null || !player.isUsingItem() || !module.swingOnAttack(MODERN, action(player.getUseItem()))) return;
        attacked = true;
        player.swing(InteractionHand.MAIN_HAND, false);
    }

    /** Every client tick: a new use starts without an attack. */
    public static void tick(LocalPlayer player) {
        if (player == null || !player.isUsingItem()) attacked = false;
    }

    /** The 1.7 pose an item's use draws, or null for a use 1.7 never had. */
    private static Use action(ItemStack item) {
        UseAnim animation = item.getUseAnimation();
        return animation == UseAnim.EAT || animation == UseAnim.DRINK ? Use.EAT_DRINK
            : animation == UseAnim.BOW ? Use.BOW : animation == UseAnim.BLOCK ? Use.BLOCK : null;
    }

    /** The trigger for the 1.7 sword block: a sword in the main hand while the off hand blocks with a shield. */
    public static boolean swordBlocking(LivingEntity entity) {
        return (active(Feature.BLOCK_POSE) || active(Feature.BLOCKHIT)) && entity.isUsingItem()
            && entity.getUsedItemHand() == InteractionHand.OFF_HAND && entity.getOffhandItem().getUseAnimation() == UseAnim.BLOCK
            && entity.getMainHandItem().is(ItemTags.SWORDS);
    }

    /** 1.7's EnumAction for this hand: NONE when idle, null for a use 1.7 never had (vanilla draws it). */
    private static Use use(LivingEntity entity, InteractionHand hand, ItemStack item) {
        if (!entity.isUsingItem() || entity.getUseItemRemainingTicks() <= 0 || entity.getUsedItemHand() != hand) return Use.NONE;
        UseAnim animation = item.getUseAnimation();
        return animation == UseAnim.EAT || animation == UseAnim.DRINK ? Use.EAT_DRINK : animation == UseAnim.BOW ? Use.BOW : null;
    }

    static Held held(ItemStack item) {
        if (item.getItem() instanceof BowItem) return Held.BOW;
        if (item.getItem() instanceof FishingRodItem || item.getItem() instanceof FoodOnAStickItem<?>) return Held.ROD;
        // 1.7.10 isFull3D: swords, tools, sticks, bones and blaze rods.
        if (item.is(ItemTags.SWORDS) || item.is(ItemTags.AXES) || item.is(ItemTags.PICKAXES) || item.is(ItemTags.SHOVELS)
            || item.is(ItemTags.HOES) || item.is(Items.STICK) || item.is(Items.BONE) || item.is(Items.BLAZE_ROD)) return Held.TOOL;
        return Held.ITEM;
    }

    /** No attack-cooldown dip: the swap scale the equip animation reads (Legacy Swing keeps the item up the same way). */
    public static float equipScale(float vanilla) {
        float scale = legacySwing() ? 1 : module() == null ? vanilla : module().equipScale(MODERN, vanilla);
        if (scale != vanilla) APPLIED.add(Feature.NO_COOLDOWN_DIP);
        return scale;
    }

    /** No heart flashing: vanilla's health-bar blink flag. */
    public static boolean heartsBlink(boolean vanilla) {
        OldAnimationsModule module = module();
        boolean blink = module == null ? vanilla : module.heartsBlink(MODERN, vanilla);
        if (blink != vanilla) APPLIED.add(Feature.NO_HEART_FLASH);
        return blink;
    }

    /**
     * Instant sneak camera, each Camera.tick: 1.7's step towards the eye height instead of vanilla's half-way ease. Your own sneak
     * follows your keys, as in 1.7 and 1.8, not the pose a server sends back a tick or more later.
     */
    public static float eyeHeight(Entity entity, float previous, float vanilla) {
        if (!active(Feature.INSTANT_SNEAK)) return vanilla;
        float target = entity.getEyeHeight();
        if (entity instanceof LocalPlayer player && (player.getPose() == Pose.STANDING || player.getPose() == Pose.CROUCHING)) {
            boolean crouch = player.isShiftKeyDown() && !player.getAbilities().flying
                || !player.isSpectator() && !player.isPassenger()
                && !player.level().noCollision(player, player.getDimensions(Pose.STANDING).makeBoundingBox(player.position()).deflate(1.0E-7));
            target = player.getEyeHeight(crouch ? Pose.CROUCHING : Pose.STANDING);
        }
        float eye = OldAnimations.sneakEyeHeight(previous, target);
        if (eye != vanilla) APPLIED.add(Feature.INSTANT_SNEAK);
        return eye;
    }

    /** ScreenEffectRenderer.renderFire, at its head: true when Low Fire lowers the overlay (the caller pushes and later pops). */
    public static boolean lowFireBegin() {
        lowFire = active(Feature.LOW_FIRE);
        if (lowFire) APPLIED.add(Feature.LOW_FIRE);
        return lowFire;
    }

    public static boolean lowFireEnd() {
        boolean was = lowFire;
        lowFire = false;
        return was;
    }

    // ---- red armour ---------------------------------------------------------------------------------------------------

    /** HumanoidArmorLayer.render, at its head and return: the entity whose armour is drawn (null: none). */
    public static void armourOf(LivingEntity entity) {
        armourOf = entity;
    }

    /** The multiplier on the green and blue of armour being drawn: 1 normally, the hurt body's red when the entity is hurt. */
    public static float armourGreenBlue() {
        LivingEntity entity = armourOf;
        OldAnimationsModule module = module();
        if (entity == null || module == null || !module.tintArmour(MODERN, entity.hurtTime, entity.deathTime)) return 1;
        APPLIED.add(Feature.RED_ARMOUR);
        return HURT_GREEN_BLUE;
    }

    // ---- dropped items ---------------------------------------------------------------------------------------------

    private static boolean itemPhysicsRenders() {
        return ModuleManager.getInstance().getModule(ItemPhysicsModule.NAME) instanceof ItemPhysicsModule physics
            && physics.isEnabled() && physics.animation.get();
    }

    /** ItemEntityRenderer.render, at its head. */
    public static void droppedBegin() {
        droppedCopy = 0;
    }

    /**
     * ItemEntityRenderer.render at the draw of each copy of the stack: the 1.7 Fast-graphics icon facing the camera for the first
     * copy (true: the caller draws with NONE), nothing for the others (null), or vanilla's draw (false). The pose is inside render's
     * push and the copy's own push; an icon starts again from the entity origin.
     */
    public static Boolean droppedItem(net.minecraft.world.entity.item.ItemEntity entity, BakedModel model, PoseStack pose, float partial) {
        if (!active(Feature.DROPPED_2D) || itemPhysicsRenders() || !ModelShape1201.flat(model)) return Boolean.FALSE;
        if (droppedCopy++ > 0) return null;
        pose.popPose(); // the copy's push
        pose.popPose(); // render's push: back to the entity origin
        pose.pushPose();
        pose.pushPose();
        float yaw = Minecraft.getInstance().gameRenderer.getMainCamera().getYRot();
        OldAnimations.droppedItem(sink(pose), entity.getAge() + partial, entity.bobOffs, yaw);
        APPLIED.add(Feature.DROPPED_2D);
        return Boolean.TRUE;
    }

    // ---- third person ------------------------------------------------------------------------------------------------

    /** The shield is hidden while the sword blocks: its item draw is skipped. */
    public static boolean hideShield(LivingEntity entity, ItemStack stack) {
        return entity instanceof AbstractClientPlayer && active(Feature.THIRD_PERSON) && swordBlocking(entity) && stack == entity.getOffhandItem();
    }

    /**
     * ItemInHandLayer, at vanilla's item draw: true once the pose holds 1.7's third-person placement (draw with NONE). The pose
     * starts again from the arm pivot (after translateToHand), where 1.7's frames start.
     */
    public static boolean thirdPersonItem(LivingEntity entity, ItemStack stack, HumanoidArm arm, PoseStack pose,
                                          net.minecraft.client.model.ArmedModel model) {
        if (!(entity instanceof AbstractClientPlayer) || !active(Feature.THIRD_PERSON) || kept(stack)) return false;
        if (!ModelShape1201.flat(model(entity, stack))) return false;
        pose.popPose(); // renderArmWithItem's push
        pose.pushPose();
        model.translateToHand(arm, pose);
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        Held held = held(stack);
        OldAnimations.thirdPersonHand(sink(pose), side);
        OldAnimations.thirdPersonItem(sink(pose), side, held, swordBlocking(entity) && arm == entity.getMainArm() && held == Held.TOOL);
        APPLIED.add(Feature.THIRD_PERSON);
        return true;
    }

    /**
     * PlayerModel.setupAnim, right after vanilla's pose: 1.7's sword-blocking arm only pitches; vanilla's block also turns it 30°
     * inward. Both arms are kept for {@link #reapplyBlockingArms}.
     */
    public static void blockingArm(LivingEntity entity, HumanoidModel<?> model) {
        blockingModel = null;
        if (!(entity instanceof AbstractClientPlayer) || !active(Feature.THIRD_PERSON) || !swordBlocking(entity)) return;
        boolean right = entity.getMainArm() == HumanoidArm.RIGHT;
        ModelPart arm = right ? model.rightArm : model.leftArm;
        // Remove exactly vanilla's block yaw (-30° on the right arm, +30° on the left): the swing and crouch added after it stay.
        arm.yRot += OldAnimations.BLOCKING_ARM_YAW - (right ? -1 : 1) * (float) Math.PI / 6;
        ModelPart[] arms = {model.rightArm, model.leftArm};
        for (int i = 0; i < 2; i++) {
            BLOCKING_ARMS[i * 3] = arms[i].xRot;
            BLOCKING_ARMS[i * 3 + 1] = arms[i].yRot;
            BLOCKING_ARMS[i * 3 + 2] = arms[i].zRot;
        }
        blockingModel = model;
        APPLIED.add(Feature.THIRD_PERSON);
    }

    /**
     * PlayerModel.setupAnim return, after other mods' arm animations: the 1.7 sword block's arms win. Not Enough Animations otherwise
     * eases the arm in from its last pose. Public so the animation mods of this adapter (NEA) can call it last.
     */
    public static void reapplyBlockingArms(HumanoidModel<?> model) {
        if (blockingModel != model) return;
        blockingModel = null;
        ModelPart[] arms = {model.rightArm, model.leftArm};
        for (int i = 0; i < 2; i++) {
            arms[i].xRot = BLOCKING_ARMS[i * 3];
            arms[i].yRot = BLOCKING_ARMS[i * 3 + 1];
            arms[i].zRot = BLOCKING_ARMS[i * 3 + 2];
        }
        if (model instanceof net.minecraft.client.model.PlayerModel<?> player) {
            player.rightSleeve.copyFrom(model.rightArm);
            player.leftSleeve.copyFrom(model.leftArm);
        }
    }

    public static OldAnimations.Sink sink(PoseStack pose) {
        return new OldAnimations.Sink() {
            @Override public void translate(float x, float y, float z) { pose.translate(x, y, z); }
            @Override public void rotate(float degrees, float x, float y, float z) {
                pose.mulPose(new Quaternionf().rotationAxis(degrees * Mth.DEG_TO_RAD, x, y, z));
            }
            @Override public void scale(float x, float y, float z) { pose.scale(x, y, z); }
        };
    }
}
