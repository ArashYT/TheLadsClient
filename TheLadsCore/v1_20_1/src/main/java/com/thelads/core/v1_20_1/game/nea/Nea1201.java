package com.thelads.core.v1_20_1.game.nea;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.NotEnoughAnimationsModule;
import com.thelads.core.modules.NotEnoughAnimationsModule.Family;
import com.thelads.core.modules.OldAnimationsModule;
import com.thelads.core.v1_20_1.game.ModPresence1201;
import com.thelads.core.v1_20_1.game.anim.NativeOldAnimations1201;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Native Not Enough Animations (module "NotEnoughAnimations"): third-person player poses applied at the end of
 * PlayerModel.setupAnim, after other mods' hooks (PlayerModelMixin), plus the eased crouch drop (PlayerRendererMixin).
 * Every pose is a pure function of the player's state and its render age (tickCount + partial tick), so it is smooth at any
 * frame rate; the only memory is one {@link State} per player for the cross-fade between poses. Stands down when the real
 * mod is installed and while player-animation-lib plays an animation on that player.
 */
public final class Nea1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-1.20.1");
    /** Pose changes cross-fade over this many ticks. */
    static final float FADE_TICKS = 4f;
    private static final float ITEM_PITCH = -0.31415927f, RIDE_PITCH = -0.62831855f, PI = (float) Math.PI;
    /** Cross-faded channels: x/y/zRot then x/y/z of body, right arm, left arm, right leg, left leg; then the head tilt (x, z). */
    private static final int CHANNELS = 32;
    // Pose-key bits: any change cross-fades from the last drawn pose.
    private static final int CROUCH = 1 << 8, CLIMB = 1 << 9, CRAWL = 1 << 10, GLIDE = 1 << 11, FALL = 1 << 12, SWIM = 1 << 13,
        RIDE_SHIFT = 14, USE_SHIFT = 17, HOLD_RIGHT_SHIFT = 21, HOLD_LEFT_SHIFT = 24, BUSY_RIGHT = 1 << 27, BUSY_LEFT = 1 << 28;
    private static final int RIDE_ROW = 1, RIDE_SIT = 2, RIDE_REINS = 3, RIDE_CART = 4, RIDE_OTHER = 5;
    private static final int HOLD_ROD = 1, HOLD_MAP = 2, HOLD_MAP_BOTH = 3, HOLD_GAUGE = 4, HOLD_LIGHT = 5, HOLD_SUPPORT = 6;

    private static boolean external, qa;
    private static NotEnoughAnimationsModule module;
    /** player-animation-lib (Zero SMP: emotes, Better Combat style attacks): while it animates a player it owns the pose. */
    private static Class<?> animatedPlayer;
    private static Method animation, animationActive;
    private static final Map<AbstractClientPlayer, State> STATES = new WeakHashMap<>();
    private static Level statesLevel;
    private static final float[] NOW = new float[CHANNELS];

    private Nea1201() {}

    /** Per-player cross-fade memory. */
    static final class State {
        int key = -1;
        boolean falling, crouched;
        float changeAge = -100f, lastAge = -100f, crouchFrom, crouchAge = -100f, drop = -1f;
        final float[] from = new float[CHANNELS], last = new float[CHANNELS];
    }

    public static void register() {
        external = ModPresence1201.loaded("notenoughanimations");
        qa = com.thelads.core.v1_20_1.game.Gameplay1201.qaActive();
        if (external) ModuleSupport.registerExternal(NotEnoughAnimationsModule.NAME, "Not Enough Animations", "notenoughanimations", true);
        else ModuleSupport.registerBuiltIn(NotEnoughAnimationsModule.NAME);
        try {
            animatedPlayer = Class.forName("dev.kosmx.playerAnim.impl.IAnimatedPlayer", false, Nea1201.class.getClassLoader());
            animation = animatedPlayer.getMethod("playerAnimator_getAnimation");
            animationActive = Class.forName("dev.kosmx.playerAnim.core.impl.AnimationProcessor", false, Nea1201.class.getClassLoader())
                .getMethod("isActive");
        } catch (ReflectiveOperationException | LinkageError absent) {
            animatedPlayer = null;
        }
        LOGGER.info("Lads Not Enough Animations: {}{}", external ? "the installed mod animates players (Lads stands down)" : "built in",
            animatedPlayer != null ? "; player-animation-lib animations take precedence" : "");
    }

    static NotEnoughAnimationsModule module() {
        if (module == null && ModuleManager.getInstance().getModule(NotEnoughAnimationsModule.NAME) instanceof NotEnoughAnimationsModule m) module = m;
        return module;
    }

    private static boolean on(Family family) {
        NotEnoughAnimationsModule m = module();
        return !external && m != null && m.on(family);
    }

    /** PlayerModel.setupAnim return: the families pose the parts, then the pose cross-fades from the last one drawn. */
    public static void afterSetupAnim(PlayerModel<?> model, LivingEntity entity, float limbSwing, float limbAmount, float age) {
        // Age 0 is PlayerRenderer.renderHand's first-person arm (it zeroes every input); world renders pass tickCount + partial.
        if (external || age == 0f || !(entity instanceof AbstractClientPlayer player) || player.isSleeping() || player.isSpectator()) return;
        NotEnoughAnimationsModule m = module();
        if (m == null || !m.isEnabled() || animated(player)) return;
        // 1.7 Animations' third-person sword block keeps its arms (NativeOldAnimations1201 poses them).
        if (NativeOldAnimations1201.active(OldAnimationsModule.Feature.THIRD_PERSON) && NativeOldAnimations1201.swordBlocking(player)) return;
        State s = state(player);
        float partial = Mth.clamp(age - player.tickCount, 0f, 1f);
        boolean rightMain = player.getMainArm() == HumanoidArm.RIGHT;
        HumanoidArm attacking = model.attackTime <= 0f ? null
            : player.swingingArm == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        boolean rightBusy = false, leftBusy = false;
        float tiltX = 0f, tiltZ = 0f;
        int key = model.rightArmPose.ordinal() | model.leftArmPose.ordinal() << 4 | (model.crouching ? CROUCH : 0);
        boolean swimming = model.swimAmount > 0f;
        boolean crawling = swimming && player.isVisuallyCrawling();
        boolean gliding = player.isFallFlying() && player.getFallFlyingTicks() > 4;
        if (swimming && !crawling) key |= SWIM;

        // Item use: the used arm.
        if (!swimming && player.isUsingItem() && player.getUseItemRemainingTicks() > 0 && on(Family.ITEMS)) {
            boolean right = (player.getUsedItemHand() == InteractionHand.MAIN_HAND) == rightMain;
            ModelPart arm = right ? model.rightArm : model.leftArm, other = right ? model.leftArm : model.rightArm;
            float side = right ? 1f : -1f;
            float used = player.getTicksUsingItem() + partial;
            UseAnim anim = player.getUseItem().getUseAnimation();
            key |= (anim.ordinal() + 1) << USE_SHIFT;
            switch (anim) {
                case EAT, DRINK -> {
                    // Hand to the mouth (the goat horn's pose), following the head; food is chewed, a drink tips the head back.
                    float chew = anim == UseAnim.EAT ? chew(player, partial) : 0f;
                    arm.xRot = Mth.clamp(model.head.xRot, -1.2f, 1.2f) - 1.4835298f + (model.crouching ? 0.4f : 0f)
                        + (anim == UseAnim.DRINK ? -0.25f : chew * 1.5f);
                    arm.yRot = model.head.yRot - side * 0.5235988f;
                    arm.zRot = 0f;
                    tiltX = anim == UseAnim.DRINK ? -0.35f : chew * 0.8f;
                    if (right) rightBusy = true; else leftBusy = true;
                }
                case BOW -> {
                    // Vanilla jumps straight to a full draw: the string hand comes back from the bow as the draw builds.
                    float draw = used / 20f;
                    draw = Math.min(1f, (draw * draw + draw * 2f) / 3f);
                    other.yRot -= side * 0.4f * (1f - draw);
                    rightBusy = leftBusy = true;
                }
                case SPEAR -> {
                    // The trident arm winds back while charging; the free arm points ahead for balance.
                    float charge = Math.min(1f, used / 10f);
                    arm.xRot -= 0.5f * charge;
                    if (freeEmpty(right ? model.leftArmPose : model.rightArmPose, right ? HumanoidArm.LEFT : HumanoidArm.RIGHT, attacking)) {
                        other.xRot -= 0.7f * charge;
                        if (right) leftBusy = true; else rightBusy = true;
                    }
                    if (right) rightBusy = true; else leftBusy = true;
                }
                case BRUSH -> {
                    // Brushing sweeps side to side, one stroke per brush cycle (10 ticks).
                    arm.yRot += Mth.sin(used * (2f * PI / 10f)) * 0.35f;
                    if (right) rightBusy = true; else leftBusy = true;
                }
                case SPYGLASS -> tiltZ = side * 0.12f; // the head leans into the eyepiece
                default -> { }
            }
        }

        // Held items (not in use): rods with a line out, maps, compasses and clocks are raised to look at, lights held up.
        if (!swimming && !gliding && on(Family.ITEMS)) {
            ArmPose mainPose = rightMain ? model.rightArmPose : model.leftArmPose;
            ArmPose offPose = rightMain ? model.leftArmPose : model.rightArmPose;
            int mainHold = mainPose == ArmPose.ITEM && !(rightMain ? rightBusy : leftBusy) ? hold(player, player.getMainHandItem()) : 0;
            int offHold = offPose == ArmPose.ITEM && !(rightMain ? leftBusy : rightBusy) ? hold(player, player.getOffhandItem()) : 0;
            if (mainHold == HOLD_MAP && offPose == ArmPose.EMPTY && !(rightMain ? leftBusy : rightBusy)) {
                mainHold = HOLD_MAP_BOTH;
                offHold = HOLD_SUPPORT;
            }
            int rightHold = rightMain ? mainHold : offHold, leftHold = rightMain ? offHold : mainHold;
            if (rightHold != 0) { holdPose(model.rightArm, rightHold, 1f); rightBusy = true; }
            if (leftHold != 0) { holdPose(model.leftArm, leftHold, -1f); leftBusy = true; }
            key |= rightHold << HOLD_RIGHT_SHIFT | leftHold << HOLD_LEFT_SHIFT;
        }

        boolean rightFree = !rightBusy && free(model.rightArmPose, HumanoidArm.RIGHT, attacking);
        boolean leftFree = !leftBusy && free(model.leftArmPose, HumanoidArm.LEFT, attacking);
        boolean climbing = false;
        Entity vehicle = player.getVehicle();
        if (vehicle != null && on(Family.RIDING)) {
            int ride = ride(model, player, vehicle, partial, rightFree, leftFree);
            key |= ride << RIDE_SHIFT;
            if (ride == RIDE_ROW || ride == RIDE_REINS || ride == RIDE_CART) key |= (rightFree ? BUSY_RIGHT : 0) | (leftFree ? BUSY_LEFT : 0);
        } else if (vehicle == null && !swimming && !gliding && !player.onGround() && player.onClimbable() && !flying(player)
            && on(Family.CLIMBING)) {
            climbing = true;
            climb(model, (float) Mth.lerp(partial, player.yo, player.getY()), rightFree, leftFree);
            key |= CLIMB | (rightFree ? BUSY_RIGHT : 0) | (leftFree ? BUSY_LEFT : 0);
        }
        if (on(Family.MOVEMENT)) {
            if (crawling) {
                crawl(model, limbSwing, rightFree && !player.isUsingItem(), leftFree && !player.isUsingItem());
                key |= CRAWL;
            } else if (gliding) {
                glide(model, rightFree, leftFree);
                key |= GLIDE | (rightFree ? BUSY_RIGHT : 0) | (leftFree ? BUSY_LEFT : 0);
            } else {
                double fall = player.getY() - player.yo;
                boolean airborne = vehicle == null && !swimming && !climbing && !player.onGround() && !player.isInWater() && !flying(player);
                s.falling = airborne && (s.falling ? fall < -0.3 : fall < -0.5);
                if (s.falling) {
                    // Arms fly up and out (unless busy with an item), legs part: added on top so a swing still shows.
                    if (!rightBusy) { model.rightArm.zRot += 0.8f; model.rightArm.xRot -= 0.25f; }
                    if (!leftBusy) { model.leftArm.zRot -= 0.8f; model.leftArm.xRot -= 0.25f; }
                    model.rightLeg.zRot += 0.12f;
                    model.leftLeg.zRot -= 0.12f;
                    key |= FALL;
                }
            }
        }

        if (on(Family.TRANSITIONS)) {
            NOW[30] = tiltX;
            NOW[31] = tiltZ;
            float t = crossfade(model, s, key, age);
            if (qa && t < 1f && player instanceof LocalPlayer) {
                LOGGER.info("Lads qa nea fade: key {} t {} body x {} right arm x {} z {} left arm x {} z {} crouch drop {}", Integer.toHexString(key),
                    t, model.body.xRot, model.rightArm.xRot, model.rightArm.zRot, model.leftArm.xRot, model.leftArm.zRot, s.drop);
            }
            tiltX = NOW[30];
            tiltZ = NOW[31];
        }
        model.head.xRot += tiltX;
        model.head.zRot += tiltZ;
        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg);
        model.leftPants.copyFrom(model.leftLeg);
        NativeOldAnimations1201.reapplyBlockingArms(model); // the 1.7 sword block always wins, whichever return hook runs last
    }

    /** PlayerRenderer.getRenderOffset: the fraction of the 2-pixel crouch drop to use while it eases, or -1 for vanilla's. */
    public static float crouchDrop(AbstractClientPlayer player, float partial) {
        if (!on(Family.TRANSITIONS) || player.isSleeping() || animated(player)) return -1f;
        State s = state(player);
        float age = player.tickCount + partial;
        boolean crouching = player.isCrouching();
        if (s.crouchAge < -50f) { // first sight: already settled
            s.crouched = crouching;
            s.crouchAge = age - FADE_TICKS;
        }
        if (crouching != s.crouched) {
            s.crouchFrom = crouchValue(s, age);
            s.crouchAge = age;
            s.crouched = crouching;
        }
        return s.drop = (age - s.crouchAge) / FADE_TICKS >= 1f ? -1f : crouchValue(s, age);
    }

    private static float crouchValue(State s, float age) {
        return Mth.lerp(ease((age - s.crouchAge) / FADE_TICKS), s.crouchFrom, s.crouched ? 1f : 0f);
    }

    /** Smoothstep of t clamped to [0, 1]. */
    static float ease(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /** The shortest turn from a to b, in (-PI, PI]. */
    static float turn(float a, float b) {
        return Mth.wrapDegrees((b - a) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
    }

    /** Returns how far the cross-fade is (1 or more: settled). */
    private static float crossfade(PlayerModel<?> model, State s, int key, float age) {
        for (int p = 0; p < 5; p++) read(part(model, p), NOW, p * 6);
        if (key != s.key) {
            s.key = key;
            // A player not drawn for a while (out of view, just re-tracked) starts in the new pose.
            if (age - s.lastAge <= 3f) {
                System.arraycopy(s.last, 0, s.from, 0, CHANNELS);
                s.changeAge = age;
            } else {
                s.changeAge = -100f;
            }
        }
        float t = (age - s.changeAge) / FADE_TICKS;
        if (t < 1f) {
            float e = ease(t);
            for (int i = 0; i < CHANNELS; i++) {
                boolean angle = i >= 30 || i % 6 < 3;
                NOW[i] = s.from[i] + (angle ? turn(s.from[i], NOW[i]) : NOW[i] - s.from[i]) * e;
            }
            for (int p = 0; p < 5; p++) write(part(model, p), NOW, p * 6);
        }
        System.arraycopy(NOW, 0, s.last, 0, CHANNELS);
        s.lastAge = age;
        return t;
    }

    private static ModelPart part(PlayerModel<?> model, int p) {
        return switch (p) {
            case 0 -> model.body;
            case 1 -> model.rightArm;
            case 2 -> model.leftArm;
            case 3 -> model.rightLeg;
            default -> model.leftLeg;
        };
    }

    private static void read(ModelPart part, float[] to, int at) {
        to[at] = part.xRot;
        to[at + 1] = part.yRot;
        to[at + 2] = part.zRot;
        to[at + 3] = part.x;
        to[at + 4] = part.y;
        to[at + 5] = part.z;
    }

    private static void write(ModelPart part, float[] from, int at) {
        part.xRot = from[at];
        part.yRot = from[at + 1];
        part.zRot = from[at + 2];
        part.x = from[at + 3];
        part.y = from[at + 4];
        part.z = from[at + 5];
    }

    private static State state(AbstractClientPlayer player) {
        Level level = Minecraft.getInstance().level;
        if (level != statesLevel) {
            STATES.clear();
            statesLevel = level;
        }
        return STATES.computeIfAbsent(player, ignored -> new State());
    }

    /** Vanilla's first-person chew bob (ItemInHandRenderer.applyEatTransform), as a 0..0.1 amount. */
    private static float chew(AbstractClientPlayer player, float partial) {
        float remaining = player.getUseItemRemainingTicks() - partial + 1f;
        int duration = player.getUseItem().getUseDuration();
        return duration > 0 && remaining / duration < 0.8f ? Mth.abs(Mth.cos(remaining / 4f * PI) * 0.1f) : 0f;
    }

    private static int hold(AbstractClientPlayer player, ItemStack stack) {
        if (stack.getItem() instanceof FishingRodItem) return player.fishing != null ? HOLD_ROD : 0;
        if (stack.is(Items.FILLED_MAP)) return HOLD_MAP;
        if (stack.is(Items.COMPASS) || stack.is(Items.RECOVERY_COMPASS) || stack.is(Items.CLOCK)) return HOLD_GAUGE;
        if (stack.is(Items.TORCH) || stack.is(Items.SOUL_TORCH) || stack.is(Items.REDSTONE_TORCH) || stack.is(Items.LANTERN)
            || stack.is(Items.SOUL_LANTERN)) return HOLD_LIGHT;
        return 0;
    }

    /** Moves vanilla's ITEM pitch (-18°) to the hold's; the walk swing, attack, crouch and bob already added stay. */
    private static void holdPose(ModelPart arm, int hold, float side) {
        float pitch, yaw, roll = 0f;
        switch (hold) {
            case HOLD_ROD -> { pitch = -0.8f; yaw = 0f; }
            case HOLD_MAP -> { pitch = -0.7f; yaw = -side * 0.2f; }
            case HOLD_MAP_BOTH -> { pitch = -0.9f; yaw = -side * 0.35f; }
            case HOLD_SUPPORT -> { pitch = -0.9f - ITEM_PITCH; yaw = -side * 0.35f; } // an EMPTY arm: vanilla added no item pitch
            case HOLD_GAUGE -> { pitch = -0.95f; yaw = -side * 0.3f; }
            default -> { pitch = -0.8f; yaw = 0f; roll = side * 0.15f; } // HOLD_LIGHT
        }
        arm.xRot += pitch - ITEM_PITCH;
        arm.yRot += yaw;
        arm.zRot += roll;
    }

    private static int ride(PlayerModel<?> model, AbstractClientPlayer player, Entity vehicle, float partial, boolean rightFree, boolean leftFree) {
        if (vehicle instanceof Boat boat) {
            // Sitting in a boat: knees together rather than astride.
            model.rightLeg.yRot = 0.1f;
            model.leftLeg.yRot = -0.1f;
            if (boat.getControllingPassenger() != player) return RIDE_SIT;
            // Both hands on the oars, following the paddles (BoatModel.animatePaddle's phase): reach forward, pull back.
            if (rightFree) row(model.rightArm, boat.getRowingTime(1, partial), 1f);
            if (leftFree) row(model.leftArm, boat.getRowingTime(0, partial), -1f);
            return RIDE_ROW;
        }
        if (vehicle instanceof AbstractMinecart) {
            // Arms out on the rim of the cart.
            if (rightFree && model.rightArmPose == ArmPose.EMPTY) rest(model.rightArm, -0.15f, 1.1f);
            if (leftFree && model.leftArmPose == ArmPose.EMPTY) rest(model.leftArm, -0.15f, -1.1f);
            return RIDE_CART;
        }
        if (vehicle instanceof Saddleable && vehicle.getControllingPassenger() == player) {
            // Hands together on the reins, bobbing with the mount's gait.
            float bob = 0f;
            if (vehicle instanceof LivingEntity mount) {
                bob = Mth.cos(mount.walkAnimation.position(partial) * 0.6662f) * 0.12f * Math.min(1f, mount.walkAnimation.speed(partial));
            }
            if (rightFree && model.rightArmPose == ArmPose.EMPTY) reins(model.rightArm, bob, 1f);
            if (leftFree && model.leftArmPose == ArmPose.EMPTY) reins(model.leftArm, bob, -1f);
            return RIDE_REINS;
        }
        return RIDE_OTHER;
    }

    /** The hand on the oar's handle: forward while the blade sweeps back (paddle yaw), wider while the blade is deep (pitch). */
    private static void row(ModelPart arm, float rowing, float side) {
        float reach = (1f - Mth.sin(-rowing + 1f)) / 2f, lift = (Mth.sin(-rowing) + 1f) / 2f;
        arm.xRot = -0.35f - 0.75f * reach;
        arm.yRot = 0f;
        arm.zRot = side * (0.3f + 0.15f * lift);
    }

    private static void rest(ModelPart arm, float pitch, float roll) {
        arm.xRot = pitch;
        arm.yRot = 0f;
        arm.zRot = roll;
    }

    private static void reins(ModelPart arm, float bob, float side) {
        arm.xRot = -0.85f + bob;
        arm.yRot = -side * 0.25f;
        arm.zRot = 0f;
    }

    /** Ladders and vines: hands reach up the rungs and feet step, alternating, one cycle per ~1.6 blocks climbed. */
    private static void climb(PlayerModel<?> model, float y, boolean rightFree, boolean leftFree) {
        float step = Mth.sin(y * 4f);
        if (rightFree) rest(model.rightArm, -2.5f - 0.4f * step, -0.1f);
        if (leftFree) rest(model.leftArm, -2.5f + 0.4f * step, 0.1f);
        model.rightLeg.xRot = -0.2f - 0.5f * Math.max(0f, -step);
        model.leftLeg.xRot = -0.2f - 0.5f * Math.max(0f, step);
        model.rightLeg.yRot = model.leftLeg.yRot = 0f;
    }

    /** Crawling on land: hands reach ahead in turn instead of the swim stroke; blended by vanilla's swim amount. */
    private static void crawl(PlayerModel<?> model, float limbSwing, boolean rightFree, boolean leftFree) {
        float w = model.swimAmount, reach = Mth.sin(limbSwing * 0.6662f) * 0.4f;
        if (rightFree) blend(model.rightArm, w, -2.6f + reach, 0f, 0.3f);
        if (leftFree) blend(model.leftArm, w, -2.6f - reach, 0f, -0.3f);
        blend(model.rightLeg, w, Mth.cos(limbSwing * 0.6662f) * 0.25f, 0f, 0.1f);
        blend(model.leftLeg, w, -Mth.cos(limbSwing * 0.6662f) * 0.25f, 0f, -0.1f);
    }

    private static void blend(ModelPart part, float w, float x, float y, float z) {
        part.xRot += turn(part.xRot, x) * w;
        part.yRot += turn(part.yRot, y) * w;
        part.zRot += turn(part.zRot, z) * w;
    }

    /** Elytra: arms swept back along the body, legs together. */
    private static void glide(PlayerModel<?> model, boolean rightFree, boolean leftFree) {
        if (rightFree) rest(model.rightArm, 0.25f, 0.35f);
        if (leftFree) rest(model.leftArm, 0.25f, -0.35f);
        model.rightLeg.xRot = model.leftLeg.xRot = 0.05f;
        model.rightLeg.yRot = model.leftLeg.yRot = 0f;
        model.rightLeg.zRot = 0.03f;
        model.leftLeg.zRot = -0.03f;
    }

    /** An arm the movement families may pose outright: empty or simply holding something, and not swinging. */
    private static boolean free(ArmPose pose, HumanoidArm arm, HumanoidArm attacking) {
        return (pose == ArmPose.EMPTY || pose == ArmPose.ITEM) && arm != attacking;
    }

    private static boolean freeEmpty(ArmPose pose, HumanoidArm arm, HumanoidArm attacking) {
        return pose == ArmPose.EMPTY && arm != attacking;
    }

    /** Creative flight (only known for the local player; other players' abilities are not sent). */
    private static boolean flying(AbstractClientPlayer player) {
        return player instanceof LocalPlayer local && local.getAbilities().flying;
    }

    private static boolean animated(AbstractClientPlayer player) {
        if (animatedPlayer == null || !animatedPlayer.isInstance(player)) return false;
        try {
            Object applier = animation.invoke(player);
            return applier != null && (boolean) animationActive.invoke(applier);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failed) {
            animatedPlayer = null;
            LOGGER.warn("Lads Not Enough Animations: player-animation-lib check failed; ignoring it from now on", failed);
            return false;
        }
    }

}
