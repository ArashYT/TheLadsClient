package com.thelads.core.v1_20_1.hud.overlay;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.PlayerActionOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;

/** Paperdoll triggers (26.3 PlayerActions): the fixed list plus every pose and boolean is* state of the player. */
final class PlayerActions1201 {
    private static final Map<PlayerActionOption, Predicate<LocalPlayer>> STATES = new LinkedHashMap<>();
    private static boolean initialized;

    private PlayerActions1201() {}

    private static void register(String name, Predicate<LocalPlayer> detector) {
        var module = ModuleManager.getInstance().getModule("Paperdoll");
        PlayerActionOption action = module.getOption(name) instanceof PlayerActionOption existing ? existing : module.addOption(new PlayerActionOption(name, false));
        STATES.put(action, detector);
    }

    private static void state(String words, Predicate<LocalPlayer> detector) { register("State: " + words, detector); }

    /**
     * Adds the pose/state triggers to the Paperdoll module; true when it added any. 26.3 finds the player's public boolean is*
     * methods by reflection; 1.20.1 runs with obfuscated names (intermediary on Fabric, SRG on Forge), so the same list, under
     * the same option names, is written out here.
     */
    static boolean initialize() {
        if (initialized || ModuleManager.getInstance().getModule("Paperdoll") == null) return false;
        initialized = true;
        for (Pose pose : Pose.values()) register("Pose: " + words(pose.name().toLowerCase(Locale.ROOT).replace('_', ' ')), p -> p.getPose() == pose);
        state("Affected By Fluids", LocalPlayer::isAffectedByFluids); state("Affected By Potions", LocalPlayer::isAffectedByPotions);
        state("Alive", LocalPlayer::isAlive); state("Always Ticking", LocalPlayer::isAlwaysTicking);
        state("Attackable", LocalPlayer::isAttackable); state("Auto Jump Enabled", LocalPlayer::isAutoJumpEnabled);
        state("Auto Spin Attack", LocalPlayer::isAutoSpinAttack); state("Baby", LocalPlayer::isBaby);
        state("Blocking", LocalPlayer::isBlocking); state("Cape Loaded", LocalPlayer::isCapeLoaded);
        state("Controlled By Local Instance", LocalPlayer::isControlledByLocalInstance); state("Creative", LocalPlayer::isCreative);
        state("Crouching", LocalPlayer::isCrouching); state("Currently Glowing", LocalPlayer::isCurrentlyGlowing);
        state("Custom Name Visible", LocalPlayer::isCustomNameVisible); state("Dead Or Dying", LocalPlayer::isDeadOrDying);
        state("Descending", LocalPlayer::isDescending); state("Discrete", LocalPlayer::isDiscrete);
        state("Effective Ai", LocalPlayer::isEffectiveAi); state("Elytra Loaded", LocalPlayer::isElytraLoaded);
        state("Fall Flying", LocalPlayer::isFallFlying); state("Freezing", LocalPlayer::isFreezing);
        state("Fully Frozen", LocalPlayer::isFullyFrozen); state("Hands Busy", LocalPlayer::isHandsBusy);
        state("Hurt", LocalPlayer::isHurt); state("Ignoring Block Triggers", LocalPlayer::isIgnoringBlockTriggers);
        state("In Lava", LocalPlayer::isInLava); state("In Wall", LocalPlayer::isInWall); state("In Water", LocalPlayer::isInWater);
        state("In Water Or Bubble", LocalPlayer::isInWaterOrBubble); state("In Water Or Rain", LocalPlayer::isInWaterOrRain);
        state("In Water Rain Or Bubble", LocalPlayer::isInWaterRainOrBubble);
        state("Inverted Heal And Harm", LocalPlayer::isInvertedHealAndHarm); state("Invisible", LocalPlayer::isInvisible);
        state("Invulnerable", LocalPlayer::isInvulnerable); state("Local Player", LocalPlayer::isLocalPlayer);
        state("Moving Slowly", LocalPlayer::isMovingSlowly); state("No Gravity", LocalPlayer::isNoGravity);
        state("On Fire", LocalPlayer::isOnFire); state("On Portal Cooldown", LocalPlayer::isOnPortalCooldown);
        state("On Rails", LocalPlayer::isOnRails); state("Passenger", LocalPlayer::isPassenger); state("Pickable", LocalPlayer::isPickable);
        state("Pushable", LocalPlayer::isPushable); state("Pushed By Fluid", LocalPlayer::isPushedByFluid);
        state("Reduced Debug Info", LocalPlayer::isReducedDebugInfo); state("Removed", LocalPlayer::isRemoved);
        state("Scoping", LocalPlayer::isScoping); state("Secondary Use Active", LocalPlayer::isSecondaryUseActive);
        state("Sensitive To Water", LocalPlayer::isSensitiveToWater); state("Shift Key Down", LocalPlayer::isShiftKeyDown);
        state("Silent", LocalPlayer::isSilent); state("Skin Loaded", LocalPlayer::isSkinLoaded); state("Sleeping", LocalPlayer::isSleeping);
        state("Sleeping Long Enough", LocalPlayer::isSleepingLongEnough); state("Spectator", LocalPlayer::isSpectator);
        state("Sprinting", LocalPlayer::isSprinting); state("Stepping Carefully", LocalPlayer::isSteppingCarefully);
        state("Suppressing Bounce", LocalPlayer::isSuppressingBounce);
        state("Suppressing Sliding Down Ladder", LocalPlayer::isSuppressingSlidingDownLadder); state("Swimming", LocalPlayer::isSwimming);
        state("Text Filtering Enabled", LocalPlayer::isTextFilteringEnabled); state("Under Water", LocalPlayer::isUnderWater);
        state("Using Item", LocalPlayer::isUsingItem); state("Vehicle", LocalPlayer::isVehicle);
        state("Visually Crawling", LocalPlayer::isVisuallyCrawling); state("Visually Swimming", LocalPlayer::isVisuallySwimming);
        return true;
    }

    private static String words(String s) { return Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    /** Whether the named trigger (a Paperdoll PlayerActionOption) is happening for this player now. */
    static boolean happening(LocalPlayer player, String name) {
        return switch (name) {
            case "Sprinting" -> player.isSprinting();
            case "Swimming" -> player.isSwimming();
            case "Crawling" -> player.isVisuallyCrawling();
            case "Crouching" -> player.isCrouching();
            case "Creative Flying" -> player.getAbilities().flying;
            case "Elytra Gliding" -> player.isFallFlying();
            case "Riding" -> player.isPassenger() || player.getPose().name().equals("SITTING");
            case "Spin Attacking" -> player.isAutoSpinAttack();
            case "Using Items" -> player.isUsingItem();
            case "Walking" -> player.getDeltaMovement().horizontalDistanceSqr() > .0001;
            case "Standing" -> player.onGround() && player.getDeltaMovement().horizontalDistanceSqr() <= .0001;
            case "Jumping" -> !player.onGround() && player.getDeltaMovement().y > 0;
            case "Falling" -> !player.onGround() && player.getDeltaMovement().y < -.08;
            case "Sleeping" -> player.isSleeping();
            case "Climbing" -> player.onClimbable();
            case "In Water" -> player.isInWater();
            case "On Fire" -> player.isOnFire();
            case "Attacking" -> player.swinging;
            case "Blocking" -> player.isBlocking();
            case "Hurt" -> player.hurtTime > 0;
            case "Dead" -> !player.isAlive();
            case "Spectating" -> player.isSpectator();
            default -> {
                var detector = STATES.get(ModuleManager.getInstance().getModule("Paperdoll").getOption(name));
                yield detector != null && detector.test(player);
            }
        };
    }
}
