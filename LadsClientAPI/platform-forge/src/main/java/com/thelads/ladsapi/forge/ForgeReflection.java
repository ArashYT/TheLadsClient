package com.thelads.ladsapi.forge;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Safely resolves Minecraft player methods via Forge/Mojang/SRG mapping names or AuthLib.
 * Ensures platform-forge touches only Forge API + non-obfuscated members, running seamlessly
 * in both development and production SRG environments.
 */
public final class ForgeReflection {
    private static Method getGameProfileMethod;
    private static Method hasPermissionsMethod;
    private static Method sourceHasPermissionMethod;
    private static Method levelMethod;
    private static Method dimensionMethod;
    private static Method locationMethod;
    private static Field playerConnectionField;
    private static Method disconnectMethod;

    private ForgeReflection() {}

    public static GameProfile getGameProfile(Player player) {
        if (player == null) return null;
        try {
            if (getGameProfileMethod == null) {
                getGameProfileMethod = findMethod(player.getClass(), new String[]{"getGameProfile", "m_36316_"}, GameProfile.class);
            }
            if (getGameProfileMethod != null) {
                return (GameProfile) getGameProfileMethod.invoke(player);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static UUID getPlayerUUID(Player player) {
        GameProfile profile = getGameProfile(player);
        if (profile != null && profile.getId() != null) {
            return profile.getId();
        }
        try {
            for (Method m : player.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType() == UUID.class) {
                    return (UUID) m.invoke(player);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static String getPlayerName(Player player) {
        GameProfile profile = getGameProfile(player);
        if (profile != null && profile.getName() != null) {
            return profile.getName();
        }
        return "Unknown";
    }

    public static String getWorldName(Player player) {
        try {
            if (levelMethod == null) {
                levelMethod = findMethod(player.getClass(), new String[]{"level", "m_9236_", "getLevel"}, null);
            }
            if (levelMethod != null) {
                Object level = levelMethod.invoke(player);
                if (level != null) {
                    if (dimensionMethod == null) {
                        dimensionMethod = findMethod(level.getClass(), new String[]{"dimension", "m_46472_"}, null);
                    }
                    if (dimensionMethod != null) {
                        Object dim = dimensionMethod.invoke(level);
                        if (dim != null) {
                            if (locationMethod == null) {
                                locationMethod = findMethod(dim.getClass(), new String[]{"location", "m_135782_"}, null);
                            }
                            if (locationMethod != null) {
                                Object loc = locationMethod.invoke(dim);
                                if (loc != null) {
                                    return loc.toString();
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return "minecraft:overworld";
    }

    public static boolean hasPermissions(ServerPlayer player, int level) {
        try {
            if (hasPermissionsMethod == null) {
                hasPermissionsMethod = findMethod(player.getClass(), new String[]{"hasPermissions", "m_20310_"}, boolean.class, int.class);
            }
            if (hasPermissionsMethod != null) {
                return (Boolean) hasPermissionsMethod.invoke(player, level);
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static boolean hasPermission(CommandSourceStack source, int level) {
        try {
            if (sourceHasPermissionMethod == null) {
                sourceHasPermissionMethod = findMethod(source.getClass(), new String[]{"hasPermission", "m_6761_"}, boolean.class, int.class);
            }
            if (sourceHasPermissionMethod != null) {
                return (Boolean) sourceHasPermissionMethod.invoke(source, level);
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static void disconnect(ServerPlayer player, String message) {
        try {
            if (playerConnectionField == null) {
                for (Field f : player.getClass().getFields()) {
                    if (f.getName().equals("connection") || f.getName().equals("f_8906_")) {
                        playerConnectionField = f;
                        break;
                    }
                }
            }
            if (playerConnectionField != null) {
                Object conn = playerConnectionField.get(player);
                if (conn != null) {
                    if (disconnectMethod == null) {
                        for (Method m : conn.getClass().getMethods()) {
                            if ((m.getName().equals("disconnect") || m.getName().equals("m_9942_")) && m.getParameterCount() == 1) {
                                disconnectMethod = m;
                                break;
                            }
                        }
                    }
                    if (disconnectMethod != null) {
                        disconnectMethod.invoke(conn, Component.literal(message));
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static Method findMethod(Class<?> clazz, String[] names, Class<?> returnType, Class<?>... params) {
        for (String name : names) {
            try {
                Method m = clazz.getMethod(name, params);
                if (returnType == null || returnType.isAssignableFrom(m.getReturnType())) {
                    return m;
                }
            } catch (NoSuchMethodException ignored) {}
        }
        for (Method m : clazz.getMethods()) {
            if (m.getParameterCount() == params.length && (returnType == null || returnType.isAssignableFrom(m.getReturnType()))) {
                boolean match = true;
                for (int i = 0; i < params.length; i++) {
                    if (!m.getParameterTypes()[i].isAssignableFrom(params[i])) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    for (String name : names) {
                        if (m.getName().equals(name)) return m;
                    }
                }
            }
        }
        return null;
    }
}
