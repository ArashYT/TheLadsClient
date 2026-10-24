package com.thelads.core.perf;

import com.thelads.core.client.ParticleFrustum;
import com.thelads.core.modules.PerformanceManagerModule;

/**
 * Particle section culling and batched particle vertex pipeline (Polydium parity; 1.20.1 and 26.3).
 * Skips particle simulation and rendering in chunk sections outside the camera frustum or culled by Sodium.
 * Batches compatible particle layers directly into vertex buffers.
 */
public final class ParticleCullPipeline {
    private static volatile boolean sectionCullingDisabled = "false".equalsIgnoreCase(System.getProperty("thelads.particleSectionCulling"));
    private static volatile boolean batchedPipelineDisabled = "false".equalsIgnoreCase(System.getProperty("thelads.batchedParticlePipeline"));

    private ParticleCullPipeline() {}

    public static boolean isSectionCullingEnabled() {
        return !sectionCullingDisabled && PerformanceManagerModule.bool("Particle Section Culling", true);
    }

    public static boolean isBatchedPipelineEnabled() {
        return !batchedPipelineDisabled && PerformanceManagerModule.bool("Batched Particle Pipeline", true);
    }

    public static void setSectionCullingDisabled(boolean off) {
        sectionCullingDisabled = off;
    }

    public static void setBatchedPipelineDisabled(boolean off) {
        batchedPipelineDisabled = off;
    }

    /**
     * Determines whether a particle in chunk section (secX, secY, secZ) should be culled.
     * Tests the 16x16x16 section bounding sphere against the view frustum.
     */
    public static boolean isSectionVisible(ParticleFrustum frustum, double secX, double secY, double secZ) {
        if (!isSectionCullingEnabled() || frustum == null || !frustum.isValid()) return true;
        // Section center in world coordinates, radius ~13.86 (sqrt(3 * 8^2))
        double cx = secX * 16.0 + 8.0;
        double cy = secY * 16.0 + 8.0;
        double cz = secZ * 16.0 + 8.0;
        return frustum.visible(cx, cy, cz, 14.0);
    }
}
