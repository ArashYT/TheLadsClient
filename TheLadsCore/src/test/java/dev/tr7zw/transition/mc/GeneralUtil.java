package dev.tr7zw.transition.mc;

import net.minecraft.resources.Identifier;
import net.minecraft.client.multiplayer.ClientLevel;

public class GeneralUtil {
    public static Identifier getResourceLocation(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }
    public static Identifier getResourceLocation(String path) {
        return Identifier.fromNamespaceAndPath("minecraft", path);
    }
    public static ClientLevel getWorld() {
        return null;
    }
}
