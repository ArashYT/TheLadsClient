package com.thelads.core.v26_2.feature;

/** Read-only, per-connection timestamp supplied by the actual inbound network path. */
public interface PacketActivitySource {
    long lads$lastPacketNanos();
}
