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

    public static LandingPrediction predictLanding(double playerY, double groundY, double motionY, double fallDistance, int minFall) {
        if (motionY >= -0.1 || fallDistance < minFall) return null;
        double dist = playerY - groundY;
        if (dist <= 0) return null;
        double speed = Math.abs(motionY) * 20.0;
        double secondsToImpact = dist / Math.max(0.5, speed);
        boolean fatal = fallDistance >= 12.0;
        boolean inTimingWindow = secondsToImpact <= 0.35 && secondsToImpact >= 0.0;
        return new LandingPrediction(0, (int) Math.floor(groundY), 0, dist, secondsToImpact, fatal, inTimingWindow);
    }

    public static LandingPrediction predictLanding(double x, double y, double z, double motionY, double fallDistance, int minFall) {
        if (motionY >= -0.1 || fallDistance < minFall) return null;
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        double fallSpeed = Math.abs(motionY);
        double distance = Math.max(1.0, fallSpeed * 3.0);
        int targetY = (int) Math.floor(y - distance);
        LandingPrediction p = predictLanding(y, targetY, motionY, fallDistance, minFall);
        if (p == null) return null;
        return new LandingPrediction(blockX, targetY, blockZ, p.distanceToLanding(), p.secondsToImpact(), p.fatal(), p.inTimingWindow());
    }
}
