package com.thelads.ladsapi.fabric;

import java.lang.reflect.Method;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/**
 * Soft integration with fabric-permissions-api (me.lucko.fabric.api.permissions.v0.Permissions).
 * Falls back to op level 2 (Permissions.COMMANDS_ADMIN) when absent.
 */
public final class FabricPermissions {
    private static final Method CHECK_PLAYER;
    private static final Method CHECK_SOURCE;
    private static final boolean HAS_FABRIC_PERMS;

    static {
        Method checkPlayer = null;
        Method checkSource = null;
        boolean hasPerms = false;
        try {
            Class<?> permsClass = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            for (Method m : permsClass.getMethods()) {
                if (m.getName().equals("check") && m.getParameterCount() == 3) {
                    Class<?>[] p = m.getParameterTypes();
                    if (p[1] == String.class) {
                        if (CommandSourceStack.class.isAssignableFrom(p[0])) {
                            checkSource = m;
                        } else if (p[0].isAssignableFrom(ServerPlayer.class)) {
                            checkPlayer = m;
                        }
                    }
                }
            }
            hasPerms = true;
        } catch (Throwable ignored) {
            hasPerms = false;
        }
        CHECK_PLAYER = checkPlayer;
        CHECK_SOURCE = checkSource;
        HAS_FABRIC_PERMS = hasPerms;
    }

    private FabricPermissions() {}

    public static boolean hasFabricPermissionsApi() {
        return HAS_FABRIC_PERMS;
    }

    public static boolean check(ServerPlayer player, String node, int defaultOpLevel) {
        if (HAS_FABRIC_PERMS && CHECK_PLAYER != null) {
            try {
                Class<?> thirdParam = CHECK_PLAYER.getParameterTypes()[2];
                if (thirdParam == int.class || thirdParam == Integer.class) {
                    return (Boolean) CHECK_PLAYER.invoke(null, player, node, defaultOpLevel);
                } else if (thirdParam == boolean.class || thirdParam == Boolean.class) {
                    boolean defaultVal = player.permissions().hasPermission(Permissions.COMMANDS_ADMIN);
                    return (Boolean) CHECK_PLAYER.invoke(null, player, node, defaultVal);
                }
            } catch (Throwable ignored) {}
        }
        // Fallback: op level 2 for ladsapi.admin, groups only for ops
        return player.permissions().hasPermission(Permissions.COMMANDS_ADMIN);
    }

    public static boolean check(CommandSourceStack source, String node, int defaultOpLevel) {
        if (HAS_FABRIC_PERMS && CHECK_SOURCE != null) {
            try {
                Class<?> thirdParam = CHECK_SOURCE.getParameterTypes()[2];
                if (thirdParam == int.class || thirdParam == Integer.class) {
                    return (Boolean) CHECK_SOURCE.invoke(null, source, node, defaultOpLevel);
                } else if (thirdParam == boolean.class || thirdParam == Boolean.class) {
                    boolean defaultVal = source.permissions().hasPermission(Permissions.COMMANDS_ADMIN);
                    return (Boolean) CHECK_SOURCE.invoke(null, source, node, defaultVal);
                }
            } catch (Throwable ignored) {}
        }
        return source.permissions().hasPermission(Permissions.COMMANDS_ADMIN);
    }
}
