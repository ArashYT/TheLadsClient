package com.thelads.core.v26_2.feature;

/** Packet silence is observable; it does not prove the connection has disconnected. */
public final class ServerSilence {
    public static final long WARNING_AFTER_NANOS = 5_000_000_000L;
    private ServerSilence() {}

    public static int warningSeconds(long now, long lastPacket, boolean eligible) {
        if (!eligible || lastPacket == 0) return 0;
        long silence = now - lastPacket;
        if (silence < WARNING_AFTER_NANOS) return 0;
        return (int) Math.min(Integer.MAX_VALUE, silence / 1_000_000_000L);
    }
}
