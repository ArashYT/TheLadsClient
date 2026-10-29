package com.thelads.smp.features.arise.nb5.client;

import com.thelads.smp.features.arise.nb5.AriseNB5Config;
import com.thelads.smp.features.arise.nb5.ParkourActionType;
import com.thelads.smp.features.arise.nb5.network.ParkourActionPayload;
import com.thelads.smp.features.arise.nb5.network.ParkourSyncPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side parkour movement controller: input handling, obstacle detection,
 * client prediction, and packet transmission to server.
 */
public final class ParkourClientController {
    private static int stamina = 1000;
    private static boolean exhausted = false;
    private static ParkourActionType activeAction = ParkourActionType.NONE;
    private static int actionTicks = 0;
    private static int dodgeCooldown = 0;
    private static int wallJumpCooldown = 0;

    // Double-tap timers
    private static int doubleTapWTimer = 0;
    private static int doubleTapATimer = 0;
    private static int doubleTapSTimer = 0;
    private static int doubleTapDTimer = 0;
    private static boolean prevW = false;
    private static boolean prevA = false;
    private static boolean prevS = false;
    private static boolean prevD = false;

    // Fall tracking for breakfall
    private static double maxFallDistanceThisFall = 0.0;
    private static boolean wasInAir = false;
    private static boolean serverConfirmed = false;

    private ParkourClientController() {}

    public static boolean isServerConfirmed() {
        return serverConfirmed;
    }

    public static void setServerConfirmedForTesting(boolean confirmed) {
        serverConfirmed = confirmed;
    }

    public static void resetSession() {
        serverConfirmed = false;
        stamina = 1000;
        exhausted = false;
        activeAction = ParkourActionType.NONE;
        actionTicks = 0;
        dodgeCooldown = 0;
        wallJumpCooldown = 0;
        doubleTapWTimer = 0;
        doubleTapATimer = 0;
        doubleTapSTimer = 0;
        doubleTapDTimer = 0;
        prevW = false;
        prevA = false;
        prevS = false;
        prevD = false;
        maxFallDistanceThisFall = 0.0;
        wasInAir = false;
    }

    public static boolean isParkourAllowed() {
        if (!AriseNB5Config.get().enabled) return false;
        if (!serverConfirmed) return false;
        try {
            return ClientPlayNetworking.canSend(ParkourActionPayload.TYPE);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static int getStamina() {
        return stamina;
    }

    public static boolean isExhausted() {
        return exhausted;
    }

    public static ParkourActionType getActiveAction() {
        return activeAction;
    }

    public static int getActionTicks() {
        return actionTicks;
    }

    public static void onSync(ParkourSyncPayload payload) {
        serverConfirmed = true;
        stamina = payload.stamina();
        exhausted = payload.exhausted();
        activeAction = payload.getActionType();
        actionTicks = payload.actionTicks();
    }

    public static void onLocalPlayerAiStep(LocalPlayer player) {
        if (!isParkourAllowed() || player.isSpectator() || player.isSleeping()) return;
        AriseNB5Config config = AriseNB5Config.get();

        // Decrement cooldowns
        if (dodgeCooldown > 0) dodgeCooldown--;
        if (wallJumpCooldown > 0) wallJumpCooldown--;
        if (doubleTapWTimer > 0) doubleTapWTimer--;
        if (doubleTapATimer > 0) doubleTapATimer--;
        if (doubleTapSTimer > 0) doubleTapSTimer--;
        if (doubleTapDTimer > 0) doubleTapDTimer--;

        if (actionTicks > 0) {
            actionTicks--;
            if (actionTicks == 0 && activeAction != ParkourActionType.NONE && activeAction != ParkourActionType.CRAWL) {
                activeAction = ParkourActionType.NONE;
            }
        }

        Level level = player.level();
        Vec3 pos = player.position();
        Vec3 move = player.getDeltaMovement();

        // 1. Breakfall detection
        boolean inAir = !player.onGround();
        if (inAir) {
            maxFallDistanceThisFall = Math.max(maxFallDistanceThisFall, player.fallDistance);
            if (config.breakfallEnabled && maxFallDistanceThisFall >= config.lowestFallDistanceForBreakfall && move.y < -0.3) {
                // Check if ground is within 1.8 blocks beneath
                BlockPos belowPos = BlockPos.containing(pos.x, pos.y - 1.2, pos.z);
                if (level.getBlockState(belowPos).isSolid() && player.input.keyPresses.shift()) {
                    triggerBreakfall(player, maxFallDistanceThisFall);
                }
            }
        } else {
            maxFallDistanceThisFall = 0.0;
        }
        wasInAir = inAir;

        // 2. Ledge Hang & Climb
        if (activeAction == ParkourActionType.LEDGE_HANG) {
            handleLedgeHang(player);
            return;
        }

        // 3. Slide & Crawl
        if (activeAction == ParkourActionType.SLIDE || activeAction == ParkourActionType.CRAWL) {
            handleSlideAndCrawl(player, level, config);
        } else if (config.slideEnabled && player.isSprinting() && player.onGround()) {
            boolean slideRequested = (ParkourKeyMappings.KEY_SLIDE != null && ParkourKeyMappings.KEY_SLIDE.consumeClick())
                    || (player.input.keyPresses.shift() && player.input.keyPresses.forward());
            if (slideRequested && !exhausted) {
                triggerSlide(player, config);
                return;
            }
        }

        // 4. Ledge Grab Check
        if (config.ledgeGrabEnabled && inAir && move.y < 0.0) {
            boolean grabRequested = (ParkourKeyMappings.KEY_LEDGE_GRAB != null && ParkourKeyMappings.KEY_LEDGE_GRAB.isDown())
                    || player.input.keyPresses.forward();
            if (grabRequested) {
                Vec3 look = Vec3.directionFromRotation(0, player.getYRot()).normalize();
                BlockPos chestPos = BlockPos.containing(pos.x + look.x * 0.7, pos.y + 1.2, pos.z + look.z * 0.7);
                BlockPos ledgeTop = chestPos.above();

                if (level.getBlockState(chestPos).isSolid() && !level.getBlockState(ledgeTop).isSolid()) {
                    triggerLedgeHang(player, look);
                    return;
                }
            }
        }

        // 5. Wall Jump Check
        if (config.wallJumpEnabled && inAir && wallJumpCooldown == 0 && player.input.keyPresses.jump() && !exhausted) {
            Vec3 wallNormal = findAdjacentWallNormal(player, level);
            if (wallNormal != null) {
                triggerWallJump(player, wallNormal, config);
                return;
            }
        }

        // 6. Vault Check
        if (config.vaultEnabled && player.isSprinting() && !exhausted && activeAction == ParkourActionType.NONE) {
            Vec3 look = Vec3.directionFromRotation(0, player.getYRot()).normalize();
            BlockPos footLevel = BlockPos.containing(pos.x + look.x * 1.0, pos.y + 0.5, pos.z + look.z * 1.0);
            BlockPos headLevel = footLevel.above();
            BlockPos destLevel = BlockPos.containing(pos.x + look.x * 1.8, pos.y + 1.2, pos.z + look.z * 1.8);

            if (level.getBlockState(footLevel).isSolid() && !level.getBlockState(headLevel).isSolid() && !level.getBlockState(destLevel).isSolid()) {
                if (player.input.keyPresses.jump() || player.onGround()) {
                    triggerVault(player, look);
                    return;
                }
            }
        }

        // 7. Dodge Roll Check (Key or Double-tap)
        if (config.dodgeRollEnabled && player.onGround() && dodgeCooldown == 0 && !exhausted) {
            Vec3 dodgeDir = checkDodgeInput(player);
            if (dodgeDir != null) {
                triggerDodgeRoll(player, dodgeDir, config);
            }
        }
    }

    private static void handleLedgeHang(LocalPlayer player) {
        Vec3 look = Vec3.directionFromRotation(0, player.getYRot()).normalize();
        if (player.input.keyPresses.jump() || player.input.keyPresses.forward()) {
            // Climb up
            activeAction = ParkourActionType.LEDGE_CLIMB;
            actionTicks = 12;
            player.setDeltaMovement(new Vec3(look.x * 0.35, 0.44, look.z * 0.35));
            sendAction(new ParkourActionPayload(ParkourActionType.LEDGE_CLIMB.getId(), (float) look.x, 0, (float) look.z, 0));
        } else if (player.input.keyPresses.shift()) {
            // Release hang
            activeAction = ParkourActionType.NONE;
            actionTicks = 0;
        } else {
            // Suspend in air
            player.setDeltaMovement(Vec3.ZERO);
            if (player.tickCount % 20 == 0) {
                sendAction(new ParkourActionPayload(ParkourActionType.LEDGE_HANG.getId(), (float) look.x, 0, (float) look.z, 0));
            }
        }
    }

    private static void handleSlideAndCrawl(LocalPlayer player, Level level, AriseNB5Config config) {
        player.setPose(Pose.SWIMMING);
        if (actionTicks == 0 && activeAction == ParkourActionType.SLIDE) {
            // Check 1-block ceiling
            BlockPos headPos = BlockPos.containing(player.getX(), player.getY() + 1.6, player.getZ());
            if (level.getBlockState(headPos).isSolid()) {
                activeAction = ParkourActionType.CRAWL;
                actionTicks = 20;
            } else {
                activeAction = ParkourActionType.NONE;
            }
        } else if (activeAction == ParkourActionType.CRAWL) {
            BlockPos headPos = BlockPos.containing(player.getX(), player.getY() + 1.6, player.getZ());
            if (!level.getBlockState(headPos).isSolid() && !player.input.keyPresses.shift()) {
                activeAction = ParkourActionType.NONE;
                actionTicks = 0;
            }
        }
    }

    private static void triggerBreakfall(LocalPlayer player, double fallDist) {
        activeAction = ParkourActionType.BREAKFALL;
        actionTicks = 10;
        player.fallDistance = (float) Math.max(0.0, player.fallDistance - 5.0);
        sendAction(new ParkourActionPayload(ParkourActionType.BREAKFALL.getId(), 0, 0, 0, (float) fallDist));
    }

    private static void triggerLedgeHang(LocalPlayer player, Vec3 look) {
        activeAction = ParkourActionType.LEDGE_HANG;
        actionTicks = 200;
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        sendAction(new ParkourActionPayload(ParkourActionType.LEDGE_HANG.getId(), (float) look.x, 0, (float) look.z, 0));
    }

    private static void triggerWallJump(LocalPlayer player, Vec3 wallNormal, AriseNB5Config config) {
        Vec3 look = Vec3.directionFromRotation(0, player.getYRot()).normalize();
        double dot = look.dot(new Vec3(-wallNormal.x, 0, -wallNormal.z));
        double angleDeg = Math.toDegrees(Math.acos(Math.clamp(dot, -1.0, 1.0)));
        if (angleDeg > config.acceptableAngleWallJump) return;

        wallJumpCooldown = 14;
        activeAction = ParkourActionType.WALL_JUMP;
        actionTicks = 10;

        Vec3 jumpVel = new Vec3(wallNormal.x * 0.46 + look.x * 0.20, 0.48, wallNormal.z * 0.46 + look.z * 0.20);
        player.setDeltaMovement(jumpVel);
        player.resetFallDistance();

        sendAction(new ParkourActionPayload(ParkourActionType.WALL_JUMP.getId(), (float) wallNormal.x, 0, (float) wallNormal.z, 0));
    }

    private static void triggerVault(LocalPlayer player, Vec3 look) {
        activeAction = ParkourActionType.VAULT;
        actionTicks = 12;

        Vec3 vaultVel = new Vec3(look.x * 0.45, 0.40, look.z * 0.45);
        player.setDeltaMovement(vaultVel);

        sendAction(new ParkourActionPayload(ParkourActionType.VAULT.getId(), (float) look.x, 0, (float) look.z, 0));
    }

    private static void triggerSlide(LocalPlayer player, AriseNB5Config config) {
        Vec3 forward = Vec3.directionFromRotation(0, player.getYRot()).normalize();
        activeAction = ParkourActionType.SLIDE;
        actionTicks = config.slidingContinuableTick;
        player.setPose(Pose.SWIMMING);

        Vec3 slideVel = new Vec3(forward.x * 0.50, player.getDeltaMovement().y, forward.z * 0.50);
        player.setDeltaMovement(slideVel);

        sendAction(new ParkourActionPayload(ParkourActionType.SLIDE.getId(), (float) forward.x, 0, (float) forward.z, 0));
    }

    private static void triggerDodgeRoll(LocalPlayer player, Vec3 dodgeDir, AriseNB5Config config) {
        boolean forwardRoll = dodgeDir.dot(Vec3.directionFromRotation(0, player.getYRot()).normalize()) > 0.7;
        ParkourActionType action = forwardRoll ? ParkourActionType.ROLL : ParkourActionType.DODGE;

        dodgeCooldown = config.dodgeCoolTime;
        activeAction = action;
        actionTicks = (action == ParkourActionType.ROLL ? 14 : 10);

        float speed = (action == ParkourActionType.ROLL ? 0.72f : 0.65f) * config.dodgeSpeedModifier;
        Vec3 dodgeVel = new Vec3(dodgeDir.x * speed, 0.15, dodgeDir.z * speed);
        player.setDeltaMovement(dodgeVel);

        sendAction(new ParkourActionPayload(action.getId(), (float) dodgeDir.x, 0, (float) dodgeDir.z, 0));
    }

    private static Vec3 checkDodgeInput(LocalPlayer player) {
        // 1. Check dedicated dodge key
        if (ParkourKeyMappings.KEY_DODGE_ROLL != null && ParkourKeyMappings.KEY_DODGE_ROLL.consumeClick()) {
            float forward = (player.input.keyPresses.forward() ? 1f : 0f) - (player.input.keyPresses.backward() ? 1f : 0f);
            float strafe = (player.input.keyPresses.left() ? 1f : 0f) - (player.input.keyPresses.right() ? 1f : 0f);

            if (forward == 0 && strafe == 0) forward = 1.0f; // Default forward if stationary

            float yaw = player.getYRot();
            float rad = (float) Math.toRadians(yaw);
            double sin = Math.sin(rad);
            double cos = Math.cos(rad);
            return new Vec3(strafe * cos - forward * sin, 0, forward * cos + strafe * sin).normalize();
        }

        // 2. Double-tap detection
        boolean curW = player.input.keyPresses.forward();
        boolean curA = player.input.keyPresses.left();
        boolean curS = player.input.keyPresses.backward();
        boolean curD = player.input.keyPresses.right();

        Vec3 result = null;
        if (curW && !prevW) {
            if (doubleTapWTimer > 0) result = Vec3.directionFromRotation(0, player.getYRot());
            doubleTapWTimer = 7;
        } else if (curS && !prevS) {
            if (doubleTapSTimer > 0) result = Vec3.directionFromRotation(0, player.getYRot() + 180f);
            doubleTapSTimer = 7;
        } else if (curA && !prevA) {
            if (doubleTapATimer > 0) result = Vec3.directionFromRotation(0, player.getYRot() - 90f);
            doubleTapATimer = 7;
        } else if (curD && !prevD) {
            if (doubleTapDTimer > 0) result = Vec3.directionFromRotation(0, player.getYRot() + 90f);
            doubleTapDTimer = 7;
        }

        prevW = curW;
        prevA = curA;
        prevS = curS;
        prevD = curD;

        return result != null ? result.normalize() : null;
    }

    private static void sendAction(ParkourActionPayload payload) {
        if (isParkourAllowed()) {
            ClientPlayNetworking.send(payload);
        }
    }

    private static Vec3 findAdjacentWallNormal(LocalPlayer player, Level level) {
        Vec3 pos = player.position();
        Direction[] dirs = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

        for (Direction d : dirs) {
            BlockPos wallPos = BlockPos.containing(pos.x + d.getStepX() * 0.7, pos.y + 0.5, pos.z + d.getStepZ() * 0.7);
            if (level.getBlockState(wallPos).isSolid()) {
                // Wall normal points outward from wall towards player
                return new Vec3(-d.getStepX(), 0, -d.getStepZ()).normalize();
            }
        }
        return null;
    }
}
