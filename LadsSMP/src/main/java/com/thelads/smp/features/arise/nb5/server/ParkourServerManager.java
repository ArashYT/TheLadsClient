package com.thelads.smp.features.arise.nb5.server;

import com.thelads.smp.features.arise.nb5.AriseNB5Config;
import com.thelads.smp.features.arise.nb5.ParkourActionType;
import com.thelads.smp.features.arise.nb5.PlayerStaminaData;
import com.thelads.smp.features.arise.nb5.network.ParkourActionPayload;
import com.thelads.smp.features.arise.nb5.network.ParkourSyncPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative parkour physics validation, stamina management,
 * persistence integration, and network synchronization.
 */
public final class ParkourServerManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/ParkourServer");
    private static final Map<UUID, PlayerStaminaData> STAMINA_MAP = new ConcurrentHashMap<>();

    private ParkourServerManager() {}

    public static void init() {
        // Register custom networking payloads
        PayloadTypeRegistry.serverboundPlay().register(ParkourActionPayload.TYPE, ParkourActionPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ParkourSyncPayload.TYPE, ParkourSyncPayload.STREAM_CODEC);

        // Register incoming action receiver
        ServerPlayNetworking.registerGlobalReceiver(ParkourActionPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> handleActionPayload(player, payload));
        });

        // Tick stamina pool, cooldowns, and sync changes every tick
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            AriseNB5Config config = AriseNB5Config.get();
            if (!config.enabled) return;

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                PlayerStaminaData data = getOrCreateData(player.getUUID());
                int prevStamina = data.getCurrentStamina();
                boolean prevExhausted = data.isExhausted();
                ParkourActionType prevAction = data.getActiveAction();

                data.tick(player.isCreative());

                // Sync if stamina changed or action state altered
                if (data.getCurrentStamina() != prevStamina || data.isExhausted() != prevExhausted || data.getActiveAction() != prevAction || (player.tickCount % 20 == 0)) {
                    syncToPlayer(player, data);
                }
            }
        });

        // Initial sync on join
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            if (player != null) {
                PlayerStaminaData data = getOrCreateData(player.getUUID());
                syncToPlayer(player, data);
            }
        });

        // Cleanup on disconnect
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (handler.player != null) {
                STAMINA_MAP.remove(handler.player.getUUID());
            }
        });

        LOGGER.info("[Lads SMP] Authoritative Parkour & Stamina Engine initialized (ParCool-style clean-room)");
    }

    public static PlayerStaminaData getOrCreateData(UUID uuid) {
        return STAMINA_MAP.computeIfAbsent(uuid, k -> new PlayerStaminaData());
    }

    public static void syncToPlayer(ServerPlayer player, PlayerStaminaData data) {
        if (!ServerPlayNetworking.canSend(player, ParkourSyncPayload.TYPE)) return;
        ParkourSyncPayload payload = new ParkourSyncPayload(
                data.getCurrentStamina(),
                data.isExhausted(),
                data.getActiveAction().getId(),
                data.getActionTicks()
        );
        ServerPlayNetworking.send(player, payload);
    }

    private static void handleActionPayload(ServerPlayer player, ParkourActionPayload payload) {
        AriseNB5Config config = AriseNB5Config.get();
        if (!config.enabled || player.isSpectator()) return;

        PlayerStaminaData data = getOrCreateData(player.getUUID());
        ParkourActionType action = payload.getActionType();
        boolean isCreative = player.isCreative();

        switch (action) {
            case VAULT -> validateAndExecuteVault(player, payload, data, config, isCreative);
            case WALL_JUMP -> validateAndExecuteWallJump(player, payload, data, config, isCreative);
            case LEDGE_HANG -> validateAndExecuteLedgeHang(player, payload, data, config, isCreative);
            case LEDGE_CLIMB -> validateAndExecuteLedgeClimb(player, payload, data, config, isCreative);
            case SLIDE -> validateAndExecuteSlide(player, payload, data, config, isCreative);
            case DODGE, ROLL -> validateAndExecuteDodgeRoll(player, payload, data, config, isCreative);
            case BREAKFALL -> validateAndExecuteBreakfall(player, payload, data, config, isCreative);
            default -> {}
        }
    }

    private static void validateAndExecuteVault(ServerPlayer player, ParkourActionPayload payload, PlayerStaminaData data, AriseNB5Config config, boolean isCreative) {
        if (!config.vaultEnabled || !data.canPerform(ParkourActionType.VAULT, isCreative)) return;

        Level level = player.level();
        Vec3 pos = player.position();
        Vec3 dir = new Vec3(payload.dirX(), 0, payload.dirZ()).normalize();
        if (dir.lengthSqr() < 1e-4) {
            dir = Vec3.directionFromRotation(0, player.getYRot()).normalize();
        }

        // Validate obstacle in front (1 block high obstacle, air above)
        BlockPos checkPos = BlockPos.containing(pos.x + dir.x * 1.0, pos.y + 0.5, pos.z + dir.z * 1.0);
        BlockPos abovePos = checkPos.above();
        BlockPos destAbove = BlockPos.containing(pos.x + dir.x * 1.8, pos.y + 1.2, pos.z + dir.z * 1.8);

        BlockState obstacleState = level.getBlockState(checkPos);
        boolean obstacleSolid = obstacleState.isSolid();
        boolean aboveClear = level.getBlockState(abovePos).isAir() || !level.getBlockState(abovePos).isSolid();
        boolean destClear = level.getBlockState(destAbove).isAir() || !level.getBlockState(destAbove).isSolid();

        if (obstacleSolid && aboveClear && destClear) {
            data.consumeForAction(ParkourActionType.VAULT, isCreative);
            Vec3 vaultVelocity = new Vec3(dir.x * 0.45, 0.40, dir.z * 0.45);
            player.setDeltaMovement(vaultVelocity);

            if (level instanceof ServerLevel sl) {
                sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5f, 1.4f);
            }
            syncToPlayer(player, data);
        }
    }

    private static void validateAndExecuteWallJump(ServerPlayer player, ParkourActionPayload payload, PlayerStaminaData data, AriseNB5Config config, boolean isCreative) {
        if (!config.wallJumpEnabled || !data.canPerform(ParkourActionType.WALL_JUMP, isCreative)) return;
        if (player.onGround()) return; // Must be airborne for wall jump

        Level level = player.level();
        Vec3 normal = new Vec3(payload.dirX(), 0, payload.dirZ()).normalize();
        if (normal.lengthSqr() < 1e-4) return;

        // Check adjacent solid wall block
        BlockPos wallPos = BlockPos.containing(player.getX() - normal.x * 0.7, player.getY() + 0.5, player.getZ() - normal.z * 0.7);
        BlockState wallState = level.getBlockState(wallPos);
        if (!wallState.isSolid()) return;

        // Angle validation: ensure player direction vs wall normal satisfies acceptable angle
        Vec3 look = Vec3.directionFromRotation(0, player.getYRot()).normalize();
        double dot = look.dot(new Vec3(-normal.x, 0, -normal.z));
        double angleDeg = Math.toDegrees(Math.acos(Math.clamp(dot, -1.0, 1.0)));
        if (angleDeg > config.acceptableAngleWallJump) return;

        data.consumeForAction(ParkourActionType.WALL_JUMP, isCreative);

        // Push off wall horizontally + lift vertically
        Vec3 jumpVel = new Vec3(normal.x * 0.46 + look.x * 0.20, 0.48, normal.z * 0.46 + look.z * 0.20);
        player.setDeltaMovement(jumpVel);
        player.resetFallDistance();

        if (level instanceof ServerLevel sl) {
            sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6f, 1.2f);
            sl.sendParticles(ParticleTypes.POOF, player.getX(), player.getY(), player.getZ(), 5, 0.2, 0.1, 0.2, 0.05);
        }
        syncToPlayer(player, data);
    }

    private static void validateAndExecuteLedgeHang(ServerPlayer player, ParkourActionPayload payload, PlayerStaminaData data, AriseNB5Config config, boolean isCreative) {
        if (!config.ledgeGrabEnabled || !data.canPerform(ParkourActionType.LEDGE_HANG, isCreative)) return;

        Level level = player.level();
        Vec3 dir = new Vec3(payload.dirX(), 0, payload.dirZ()).normalize();
        if (dir.lengthSqr() < 1e-4) {
            dir = Vec3.directionFromRotation(0, player.getYRot()).normalize();
        }

        // Ledge check: block at chest level is solid, block above ledge is air, space above player is air
        BlockPos chestPos = BlockPos.containing(player.getX() + dir.x * 0.7, player.getY() + 1.2, player.getZ() + dir.z * 0.7);
        BlockPos ledgeTop = chestPos.above();

        if (level.getBlockState(chestPos).isSolid() && !level.getBlockState(ledgeTop).isSolid()) {
            data.setActiveAction(ParkourActionType.LEDGE_HANG, 200);
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
            syncToPlayer(player, data);
        }
    }

    private static void validateAndExecuteLedgeClimb(ServerPlayer player, ParkourActionPayload payload, PlayerStaminaData data, AriseNB5Config config, boolean isCreative) {
        if (!config.ledgeGrabEnabled || !data.canPerform(ParkourActionType.LEDGE_CLIMB, isCreative)) return;

        Vec3 dir = new Vec3(payload.dirX(), 0, payload.dirZ()).normalize();
        if (dir.lengthSqr() < 1e-4) {
            dir = Vec3.directionFromRotation(0, player.getYRot()).normalize();
        }

        data.consumeForAction(ParkourActionType.LEDGE_CLIMB, isCreative);

        // Pull up onto ledge: upward impulse + forward placement
        Vec3 climbVel = new Vec3(dir.x * 0.35, 0.44, dir.z * 0.35);
        player.setDeltaMovement(climbVel);
        player.resetFallDistance();

        if (player.level() instanceof ServerLevel sl) {
            sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS, 0.5f, 1.2f);
        }
        syncToPlayer(player, data);
    }

    private static void validateAndExecuteSlide(ServerPlayer player, ParkourActionPayload payload, PlayerStaminaData data, AriseNB5Config config, boolean isCreative) {
        if (!config.slideEnabled || !player.isSprinting() || !player.onGround()) return;

        Vec3 forward = new Vec3(payload.dirX(), 0, payload.dirZ()).normalize();
        if (forward.lengthSqr() < 1e-4) {
            forward = Vec3.directionFromRotation(0, player.getYRot()).normalize();
        }

        data.consumeForAction(ParkourActionType.SLIDE, isCreative);

        // Slide forward with sprint boost and swimming pose for 1-block clearance
        player.setPose(Pose.SWIMMING);
        Vec3 slideVel = new Vec3(forward.x * 0.48, player.getDeltaMovement().y, forward.z * 0.48);
        player.setDeltaMovement(slideVel);

        if (player.level() instanceof ServerLevel sl) {
            sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.4f, 1.5f);
        }
        syncToPlayer(player, data);
    }

    private static void validateAndExecuteDodgeRoll(ServerPlayer player, ParkourActionPayload payload, PlayerStaminaData data, AriseNB5Config config, boolean isCreative) {
        ParkourActionType action = payload.getActionType();
        if (!config.dodgeRollEnabled || !data.canPerform(action, isCreative)) return;

        Vec3 dir = new Vec3(payload.dirX(), 0, payload.dirZ()).normalize();
        if (dir.lengthSqr() < 1e-4) {
            dir = Vec3.directionFromRotation(0, player.getYRot()).normalize();
        }

        data.consumeForAction(action, isCreative);

        // Apply impulse in dodge direction scaled by modifier
        float speed = (action == ParkourActionType.ROLL ? 0.72f : 0.65f) * config.dodgeSpeedModifier;
        Vec3 dodgeVel = new Vec3(dir.x * speed, 0.15, dir.z * speed);
        player.setDeltaMovement(dodgeVel);

        if (player.level() instanceof ServerLevel sl) {
            sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5f, 1.3f);
        }
        syncToPlayer(player, data);
    }

    private static void validateAndExecuteBreakfall(ServerPlayer player, ParkourActionPayload payload, PlayerStaminaData data, AriseNB5Config config, boolean isCreative) {
        if (!config.breakfallEnabled || !data.canPerform(ParkourActionType.BREAKFALL, isCreative)) return;

        // Player must have fallen at least the configured minimum fall distance
        float fallDist = (float) Math.max((double) player.fallDistance, (double) payload.param());
        if (fallDist < config.lowestFallDistanceForBreakfall) return;

        data.consumeForAction(ParkourActionType.BREAKFALL, isCreative);
        data.setBreakfallPrimed(true);
        data.setLastFallDistance(fallDist);

        // Authoritatively soften fall distance
        player.fallDistance = (float) Math.max(0.0, player.fallDistance - config.breakfallFallReduction);

        if (player.level() instanceof ServerLevel sl) {
            sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.WOOL_FALL, SoundSource.PLAYERS, 0.8f, 1.2f);
            sl.sendParticles(ParticleTypes.POOF, player.getX(), player.getY(), player.getZ(), 8, 0.3, 0.1, 0.3, 0.04);
        }
        syncToPlayer(player, data);
    }

    public static double applyBreakfallDamageReduction(Player player, double originalFallDistance) {
        AriseNB5Config config = AriseNB5Config.get();
        if (!config.enabled || !config.breakfallEnabled) return originalFallDistance;

        PlayerStaminaData data = getOrCreateData(player.getUUID());
        if (data.isBreakfallPrimed()) {
            data.setBreakfallPrimed(false);
            double reduced = Math.max(0.0, originalFallDistance - config.breakfallFallReduction);
            if (player.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.POOF, player.getX(), player.getY(), player.getZ(), 8, 0.3, 0.1, 0.3, 0.05);
            }
            return reduced;
        }
        return originalFallDistance;
    }
}
