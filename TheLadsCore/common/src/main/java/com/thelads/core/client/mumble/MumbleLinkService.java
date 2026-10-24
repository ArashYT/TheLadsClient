package com.thelads.core.client.mumble;

/**
 * Mumble Link protocol helper: computes 3D positional audio coordinates and look vectors for Mumble VoIP (parity with Lunar / Badlion Mumble Link).
 */
public final class MumbleLinkService {
    public record PositionalData(
            float posX, float posY, float posZ,
            float frontX, float frontY, float frontZ,
            float topX, float topY, float topZ,
            String identity, String context) {}

    private static PositionalData lastData;

    private MumbleLinkService() {}

    public static PositionalData compute(double x, double y, double z, float yawDegrees, float pitchDegrees, String identity, String context) {
        float yawRad = (float) Math.toRadians(yawDegrees);
        float pitchRad = (float) Math.toRadians(pitchDegrees);

        float frontX = -(float) (Math.sin(yawRad) * Math.cos(pitchRad));
        float frontY = -(float) Math.sin(pitchRad);
        float frontZ = (float) (Math.cos(yawRad) * Math.cos(pitchRad));

        float topX = 0.0f;
        float topY = 1.0f;
        float topZ = 0.0f;

        lastData = new PositionalData(
                (float) x, (float) y, (float) z,
                frontX, frontY, frontZ,
                topX, topY, topZ,
                identity == null ? "" : identity,
                context == null ? "" : context
        );
        return lastData;
    }

    public static PositionalData getLastData() {
        return lastData;
    }

    public static void clear() {
        lastData = null;
    }
}
