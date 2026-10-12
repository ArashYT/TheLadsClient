package com.thelads.core.v1_8_9.feature;

import java.util.List;
import net.minecraft.client.network.NetworkPlayerInfo;

public final class TabListCache189 {
    private static int version = 0;
    public static List<NetworkPlayerInfo> cachedSortedPlayers = null;
    public static int cachedSortedVersion = -1;
    public static int cachedMapSize = -1;
    public static Iterable<NetworkPlayerInfo> cachedElementsRef = null;

    private TabListCache189() {}

    public static void invalidate() {
        version++;
    }

    public static void reset() {
        version++;
        cachedSortedPlayers = null;
        cachedSortedVersion = -1;
        cachedMapSize = -1;
        cachedElementsRef = null;
    }

    public static int getVersion() {
        return version;
    }
}
