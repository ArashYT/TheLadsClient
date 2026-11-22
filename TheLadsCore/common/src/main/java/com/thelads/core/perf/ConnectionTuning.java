package com.thelads.core.perf;

/**
 * Connection Tuning: socket options on the client's game connection (TCP_NODELAY, SO_KEEPALIVE, low-delay IP_TOS).
 * Local to the TCP socket: no packet is added or changed, nothing the server can see. Read at channel init.
 * Kill switch: -Dlads.connTuning=false. Mirrored from the "Connection Tuning" option in Performance.
 */
public final class ConnectionTuning {
    private static final boolean PROPERTY_ON = !"false".equalsIgnoreCase(System.getProperty("lads.connTuning"));
    public static volatile boolean enabled = true;
    /** IPTOS_LOWDELAY. */
    public static final int TOS_LOW_DELAY = 0x10;

    private ConnectionTuning() {}

    public static boolean shouldApply() { return PROPERTY_ON && enabled; }
}
