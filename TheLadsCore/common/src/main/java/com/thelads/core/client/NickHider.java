package com.thelads.core.client;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.NickHiderModule;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player identity and skin disguising for streamers and content creators.
 */
public final class NickHider {
    private static NickHiderModule cachedModule;
    private static final Set<String> OTHER_PLAYERS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private NickHider() {}

    public static NickHiderModule module() {
        NickHiderModule m = cachedModule;
        if (m == null) {
            if (ModuleManager.getInstance().getModule(NickHiderModule.NAME) instanceof NickHiderModule found) {
                cachedModule = m = found;
            }
        }
        return m;
    }

    public static boolean isEnabled() {
        NickHiderModule m = module();
        return m != null && m.isEnabled();
    }

    public static boolean isHideOwnName() {
        NickHiderModule m = module();
        return m != null && m.isEnabled() && m.hideOwnName.get();
    }

    public static String getCustomNick() {
        NickHiderModule m = module();
        if (m == null) return "You";
        String val = m.customNick.getValue().trim();
        return val.isEmpty() ? "You" : val;
    }

    public static boolean isHideOthersNames() {
        NickHiderModule m = module();
        return m != null && m.isEnabled() && m.hideOthersNames.get();
    }

    public static String getOthersNick() {
        NickHiderModule m = module();
        if (m == null) return "Player";
        String val = m.othersNick.getValue().trim();
        return val.isEmpty() ? "Player" : val;
    }

    public static void registerOtherPlayer(String name) {
        if (name != null && !name.trim().isEmpty()) {
            OTHER_PLAYERS.add(name.trim());
        }
    }

    public static Set<String> getOtherPlayers() {
        return Collections.unmodifiableSet(OTHER_PLAYERS);
    }

    public static void clearOthers() {
        OTHER_PLAYERS.clear();
    }

    public static boolean shouldHideSkin(UUID playerUuid, boolean isLocal) {
        NickHiderModule m = module();
        if (m == null || !m.isEnabled()) return false;
        if (isLocal) {
            return m.hideOwnSkin.get();
        } else {
            return m.hideOthersSkins.get();
        }
    }
}
