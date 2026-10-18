package com.thelads.core.v26_2.feature;

import net.minecraft.network.chat.Component;

import java.util.UUID;

public final class NametagBadgeContext {
    private NametagBadgeContext() {}

    public static UUID CURRENT_PLAYER = null;
    public static Component CURRENT_NAMETAG = null;

    public static boolean isBadgeActive(Component name) {
        return CURRENT_PLAYER != null && (name == CURRENT_NAMETAG || (CURRENT_NAMETAG != null && name.equals(CURRENT_NAMETAG)));
    }
}
