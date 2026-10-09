package com.thelads.core.v1_20_1.hud.overlay;

/** Read-only, per-connection time of the last received packet, supplied by the inbound network path (ConnectionActivityMixin). */
public interface PacketActivity1201 {
    long lads$lastPacketNanos();
}
