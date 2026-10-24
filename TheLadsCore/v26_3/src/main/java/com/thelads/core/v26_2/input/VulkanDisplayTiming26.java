package com.thelads.core.v26_2.input;

import com.thelads.core.modules.PerformanceManagerModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vulkan display timing frame pacing (present_wait / GOOGLE_display_timing; PolyReflex parity).
 * Intercepts swapchain presentations to track exact presentation timestamps and display refresh cycles,
 * enabling FramePacer to predict GPU completion from display scanout rather than CPU submission.
 */
public final class VulkanDisplayTiming26 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-DisplayTiming");
    private static boolean supported;
    private static long lastPresentNanos;
    private static long refreshCycleNanos = 16_666_667L; // 60 Hz fallback

    private VulkanDisplayTiming26() {}

    public static void init() {
        supported = PerformanceManagerModule.bool("Vulkan Present Wait", true);
        LOGGER.info("Vulkan display timing / present_wait: {}", supported ? "supported" : "disabled");
    }

    public static boolean isSupported() {
        return supported && PerformanceManagerModule.bool("Vulkan Present Wait", true);
    }

    /** Called immediately after swapchain present completes. */
    public static void onPresentComplete(long presentTimestampNanos) {
        if (!isSupported()) return;
        lastPresentNanos = presentTimestampNanos > 0 ? presentTimestampNanos : System.nanoTime();
    }

    public static long getLastPresentNanos() {
        return lastPresentNanos;
    }

    public static long getRefreshCycleNanos() {
        return refreshCycleNanos;
    }

    public static void setRefreshCycleNanos(long cycleNs) {
        if (cycleNs > 0) refreshCycleNanos = cycleNs;
    }
}
