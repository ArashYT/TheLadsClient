package com.thelads.core.v26_2.feature.cartcam;

import com.mojang.datafixers.util.Pair;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;

/**
 * Consolidates CartCam: smoothly rotates the player's camera yaw along turning minecart rails.
 */
public final class MinecartCameraRotation {
    private static final boolean EXTERNAL_LOADED = FabricLoader.getInstance().isModLoaded("cartcam");
    private static final MinecartCameraRotation INSTANCE = new MinecartCameraRotation();

    private final TurnInterpolation turn = new TurnInterpolation();
    private LocalPlayer trackedPlayer;
    private ClientLevel trackedLevel;
    private AbstractMinecart trackedMinecart;
    private Vec3 movementSamplePosition;
    private double previousTravelYaw;
    private boolean hasTravelYaw;

    private MinecartCameraRotation() {}

    public static void onEndClientTick(Minecraft mc) {
        if (!EXTERNAL_LOADED) {
            INSTANCE.sampleTick(mc);
        }
    }

    public static void onRenderFrame(DeltaTracker deltaTracker) {
        if (!EXTERNAL_LOADED) {
            INSTANCE.applyInterpolatedTurn(deltaTracker);
        }
    }

    private void sampleTick(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.isPaused()
                || !(mc.player.getVehicle() instanceof AbstractMinecart minecart)) {
            resetTracking();
            return;
        }

        Vec3 pos = minecart.position();
        if (mc.player != trackedPlayer || mc.level != trackedLevel || minecart != trackedMinecart) {
            turn.reset();
            trackedPlayer = mc.player;
            trackedLevel = mc.level;
            trackedMinecart = minecart;
            movementSamplePosition = pos;
            hasTravelYaw = false;
            return;
        }

        turn.beginTick();
        double dx = pos.x - movementSamplePosition.x;
        double dz = pos.z - movementSamplePosition.z;
        if (dx * dx + dz * dz <= 1.0E-4D) {
            return;
        }

        float moveYaw = (float) (Mth.atan2(-dx, dz) * 57.29577951308232D);
        Double railYaw = railHeadingAt(minecart, pos, moveYaw);
        double travelYaw = railYaw != null ? railYaw : moveYaw;
        movementSamplePosition = pos;

        if (!hasTravelYaw) {
            previousTravelYaw = travelYaw;
            turn.initialize(travelYaw);
            hasTravelYaw = true;
            return;
        }

        double delta = wrapDegrees(travelYaw - previousTravelYaw);
        turn.addTurn(delta);
        previousTravelYaw = travelYaw;
    }

    private void applyInterpolatedTurn(DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || trackedMinecart == null
                || mc.player != trackedPlayer || mc.level != trackedLevel
                || mc.player.getVehicle() != trackedMinecart || mc.isPaused()) {
            return;
        }

        float partialTick = Mth.clamp(deltaTracker.getGameTimeDeltaPartialTick(false), 0.0f, 1.0f);
        Vec3 lerpPos;
        MinecartBehavior behavior = trackedMinecart.getBehavior();
        if (behavior instanceof NewMinecartBehavior nmb && nmb.cartHasPosRotLerp()) {
            lerpPos = nmb.getCartLerpPosition(partialTick);
        } else {
            lerpPos = new Vec3(
                    Mth.lerp(partialTick, trackedMinecart.xOld, trackedMinecart.getX()),
                    Mth.lerp(partialTick, trackedMinecart.yOld, trackedMinecart.getY()),
                    Mth.lerp(partialTick, trackedMinecart.zOld, trackedMinecart.getZ())
            );
        }

        Double railHeading = hasTravelYaw ? railHeadingAt(trackedMinecart, lerpPos, turn.heading(partialTick)) : null;
        float delta = turn.advance(partialTick, railHeading);
        if (delta == 0.0f) {
            return;
        }

        float oldYRot = mc.player.getYRot();
        mc.player.setYRot(oldYRot + delta);
        turn.recordApplied((double) mc.player.getYRot() - (double) oldYRot);
        mc.player.yRotO += delta;
    }

    private void resetTracking() {
        trackedPlayer = null;
        trackedLevel = null;
        trackedMinecart = null;
        movementSamplePosition = null;
        hasTravelYaw = false;
        turn.reset();
    }

    static Double railHeadingAt(AbstractMinecart minecart, Vec3 pos, double refYaw) {
        BlockPos blockPos = BlockPos.containing(pos);
        if (BaseRailBlock.isRail(minecart.level().getBlockState(blockPos.below()))) {
            blockPos = blockPos.below();
        }
        BlockState state = minecart.level().getBlockState(blockPos);
        if (!(state.getBlock() instanceof BaseRailBlock railBlock)) {
            return null;
        }

        RailShape shape = state.getValue(railBlock.getShapeProperty());
        Pair<Vec3i, Vec3i> exits = AbstractMinecart.exits(shape);
        Vec3i exit0 = exits.getFirst();
        Vec3i exit1 = exits.getSecond();

        double straightDx = (exit1.getX() - exit0.getX()) * 0.5D;
        double straightDz = (exit1.getZ() - exit0.getZ()) * 0.5D;
        double yaw;
        if (straightDx == 0.0D || straightDz == 0.0D) {
            yaw = Math.toDegrees(Math.atan2(-straightDx, straightDz));
        } else {
            double lx = pos.x - blockPos.getX() - 0.5D;
            double lz = pos.z - blockPos.getZ() - 0.5D;
            double d0 = 0.5D - lx * exit0.getX() - lz * exit0.getZ();
            double d1 = 0.5D - lx * exit1.getX() - lz * exit1.getZ();
            double sum = d0 + d1;
            double t = sum > 1.0E-9D ? Math.max(0.0D, Math.min(1.0D, d0 / sum)) : 0.5D;
            double yaw0 = Math.toDegrees(Math.atan2(exit0.getX(), -exit0.getZ()));
            double yaw1 = Math.toDegrees(Math.atan2(-exit1.getX(), exit1.getZ()));
            yaw = yaw0 + wrapDegrees(yaw1 - yaw0) * t * t * (3.0D - 2.0D * t);
        }

        double diff = wrapDegrees(yaw - refYaw);
        if (diff > 90.0D) {
            yaw -= 180.0D;
        } else if (diff < -90.0D) {
            yaw += 180.0D;
        }
        return refYaw + wrapDegrees(yaw - refYaw);
    }

    static double wrapDegrees(double angle) {
        return angle - Math.floor((angle + 180.0D) / 360.0D) * 360.0D;
    }

    private static final class TurnInterpolation {
        private double baseline;
        private double previous;
        private double current;
        private double applied;
        private boolean initialized;
        private boolean firstTick;

        void initialize(double heading) {
            baseline = heading;
            initialized = true;
            firstTick = true;
        }

        void beginTick() {
            previous = current;
            firstTick = false;
        }

        void addTurn(double delta) {
            current += delta;
        }

        double heading(float partialTick) {
            return baseline + previous + (current - previous) * partialTick;
        }

        float advance(float partialTick, Double railHeading) {
            if (!initialized) {
                return 0.0f;
            }
            double target = previous + (current - previous) * partialTick;
            if (!firstTick && railHeading != null && Math.abs(current - previous) <= 90.000001D) {
                target += wrapDegrees(railHeading - (baseline + target));
            }
            return (float) (target - applied);
        }

        void recordApplied(double delta) {
            applied += delta;
        }

        void reset() {
            baseline = 0.0D;
            previous = 0.0D;
            current = 0.0D;
            applied = 0.0D;
            initialized = false;
            firstTick = false;
        }
    }
}
