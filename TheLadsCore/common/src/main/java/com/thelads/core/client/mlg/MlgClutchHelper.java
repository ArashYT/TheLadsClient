package com.thelads.core.client.mlg;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.MlgCobwebModule;

public final class MlgClutchHelper {
    public record LandingPrediction(int x, int y, int z, boolean fatal) {}

    private static MlgCobwebModule cachedModule;

    private MlgClutchHelper() {}

    public static MlgCobwebModule module() {
        MlgCobwebModule m = cachedModule;
        if (m == null) {
            if (ModuleManager.getInstance().getModule(MlgCobwebModule.NAME) instanceof MlgCobwebModule found) {
                cachedModule = m = found;
            }
        }
        return m;
    }

    public static boolean isClutchItem(String id) {
        if (id == null) return false;
        String s = id.toLowerCase();
        MlgCobwebModule mod = module();
        boolean water = mod == null || mod.waterBucket.get();
        boolean cob = mod == null || mod.cobweb.get();
        boolean slime = mod == null || mod.slimeBlock.get();

        if (water && (s.contains("water_bucket") || s.contains("bucket_water"))) return true;
        if (cob && (s.contains("web") || s.contains("cobweb"))) return true;
        if (slime && (s.contains("slime_block") || s.contains("slime"))) return true;
        return false;
    }

    public static LandingPrediction predictLanding(double x, double y, double z, double motionY, float fallDistance, int minFall) {
        if (motionY >= -0.2 || fallDistance < minFall) return null;
        int targetY = (int) Math.floor(y + motionY * 2.0);
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        boolean fatal = fallDistance >= 12.0f;
        return new LandingPrediction(blockX, targetY, blockZ, fatal);
    }
}
