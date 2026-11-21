package com.thelads.core.perf;

import org.slf4j.LoggerFactory;

/**
 * Lads remake of the cheap parts of Fast ImageFrame: map packets that carry only markers no longer re-upload the map texture, and the
 * frame model of a map frame that cannot be seen (interior of a map wall) is not drawn. Always on, no setting. Off for the session with
 * {@code -Dlads.mapframes=false} or when the real Fast ImageFrame ("fastimageframe") is installed.
 */
public final class MapFrames {
    private static String reason;
    private static boolean decided;
    private static boolean loggedUpload, loggedFrames;

    private MapFrames() {}

    public static synchronized boolean enabled() {
        if (!decided) {
            reason = BuiltInRender.decide("mapframes", System.getProperty("lads.mapframes"), "fastimageframe");
            decided = true;
            LoggerFactory.getLogger("TheLadsCore").info("[Lads] Map frames fast path {}", reason == null ? "on" : "off (" + reason + ")");
        }
        return reason == null;
    }

    /** First map packet whose texture upload was skipped because it carried no colours. */
    public static void skippedUpload() {
        if (loggedUpload) return;
        loggedUpload = true;
        LoggerFactory.getLogger("TheLadsCore").info("[Lads] Map frames: marker-only map packet, texture upload skipped");
    }

    /** First map frame model skipped inside a map wall. */
    public static void hidFrame() {
        if (loggedFrames) return;
        loggedFrames = true;
        LoggerFactory.getLogger("TheLadsCore").info("[Lads] Map frames: hidden frame model skipped inside a map wall");
    }
}
