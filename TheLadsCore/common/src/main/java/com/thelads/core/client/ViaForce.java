package com.thelads.core.client;

import java.lang.reflect.Method;

/**
 * ViaFabricPlus' per-server forced version, the field its own server screen sets (a mixin on ServerData), so it stays with that entry in
 * servers.dat and the global target version is left alone. Reflection, because the mod is optional and its API differs per game version:
 * verified against the jars the launcher ships: 2.8.7 on 1.20.1 ({@code IServerInfo.viafabricplus_forceVersion(VersionEnum)}), 4.4.15 on
 * 1.21.11 ({@code access.base.IServerData}), 5.0.2 and 5.1.1 on 26.2 and 26.3 ({@code access.core.IServerData}, both
 * {@code viaFabricPlus$forceVersion(ProtocolVersion)}). 1.8.9 has no ViaFabricPlus: every method here then answers "no".
 */
public final class ViaForce {
    /**
     * ViaFabricPlus' own tag is an int on 1.20.1 and a version name from 1.21.11 on, and neither reads the other's, while servers.dat is shared
     * by every Lads version. Each version also keeps the protocol under this neutral tag and restores from it when it reads the entry.
     */
    public static final String TAG = "lads_forced_protocol", VIA_TAG = "viafabricplus_forcedversion";
    private static final Method SET, GET, LOOKUP, KNOWN, NUMBER;

    static {
        Method set = null, get = null, lookup = null, known = null, number = null;
        try {
            Class<?> version = Class.forName("com.viaversion.viaversion.api.protocol.version.ProtocolVersion");
            Class<?> duck = duck("com.viaversion.viafabricplus.injection.access.core.IServerData");
            if (duck == null) duck = duck("com.viaversion.viafabricplus.injection.access.base.IServerData");
            if (duck != null) {
                set = duck.getMethod("viaFabricPlus$forceVersion", version);
                get = duck.getMethod("viaFabricPlus$forcedVersion");
                lookup = version.getMethod("getProtocol", int.class);
                known = version.getMethod("isRegistered", int.class);
                number = version.getMethod("getVersion");
            } else if ((duck = duck("de.florianmichael.viafabricplus.injection.access.IServerInfo")) != null) {
                version = Class.forName("net.raphimc.vialoader.util.VersionEnum");
                set = duck.getMethod("viafabricplus_forceVersion", version);
                get = duck.getMethod("viafabricplus_forcedVersion");
                lookup = version.getMethod("fromProtocolId", int.class);
                number = version.getMethod("getVersion");
            }
        } catch (ReflectiveOperationException | LinkageError absent) {
            set = null;
        }
        SET = set;
        GET = get;
        LOOKUP = lookup;
        KNOWN = known;
        NUMBER = number;
    }

    private ViaForce() {}

    private static Class<?> duck(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException | LinkageError absent) {
            return null;
        }
    }

    public static boolean available() {
        return SET != null;
    }

    /** This protocol's version object, null when ViaFabricPlus has none (it is not loaded, or does not support this protocol). */
    private static Object version(int protocol) throws ReflectiveOperationException {
        if (SET == null) return null;
        if (KNOWN != null && !(boolean) KNOWN.invoke(null, protocol)) return null;
        Object version = LOOKUP.invoke(null, protocol);
        return version == null || KNOWN == null && "UNKNOWN".equals(version.toString()) ? null : version;
    }

    public static boolean supports(int protocol) {
        try {
            return version(protocol) != null;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return false;
        }
    }

    /** Forces this server (only) to the protocol. False when ViaFabricPlus is not there or does not support it; the caller saves the list. */
    public static boolean force(Object serverData, int protocol) {
        try {
            Object version = version(protocol);
            if (version == null) return false;
            SET.invoke(serverData, version);
            return true;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return false;
        }
    }

    /** The protocol this server is forced to, null when it is not (or there is no ViaFabricPlus). */
    public static Integer forced(Object serverData) {
        if (GET == null) return null;
        try {
            Object version = GET.invoke(serverData);
            return version == null ? null : (Integer) NUMBER.invoke(version);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return null;
        }
    }
}
