package com.thelads.core.client.mlg;

import com.thelads.core.modules.MlgCobwebModule;

public final class MlgClutchHelper {
    public record LandingPrediction(
        int x, int y, int z,
        double distanceToLanding,
        double secondsToImpact,
        boolean fatal,
        boolean inTimingWindow
    ) {}

    private MlgClutchHelper() {}

    public static boolean isClutchItem(String id) {
        if (id == null) return false;
        String s = id.toLowerCase();
        MlgCobwebModule mod = MlgCobwebModule.get();
        boolean water = mod == null || mod.waterBucket.get();
        boolean cob = mod == null || mod.cobweb.get();
        boolean slime = mod == null || mod.slimeBlock.get();
        boolean hay = mod == null || mod.hayBale.get();

        if (water && (s.contains("water_bucket") || s.contains("bucket_water"))) return true;
        if (cob && (s.contains("web") || s.contains("cobweb"))) return true;
        if (slime && (s.contains("slime_block") || s.contains("slime"))) return true;
        if (hay && (s.contains("hay_block") || s.contains("hay"))) return true;
        return false;
    }

    public static LandingPrediction predictLanding(double x, double y, double z, double motionY, float fallDistance, int minFall) {
        if (motionY >= -0.1 || fallDistance < minFall) return null;
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        // Estimate ground landing Y based on downward trajectory
        double fallSpeed = Math.abs(motionY);
        double distance = Math.max(1.0, fallSpeed * 3.0);
        int targetY = (int) Math.floor(y - distance);
        double distToGround = y - targetY;
        double secondsToImpact = distToGround / Math.max(0.5, fallSpeed * 20.0);
        boolean fatal = fallDistance >= 12.0f;
        boolean inTimingWindow = secondsToImpact <= 0.35 && secondsToImpact >= 0.0;
        return new LandingPrediction(blockX, targetY, blockZ, distToGround, secondsToImpact, fatal, inTimingWindow);
    }
}
