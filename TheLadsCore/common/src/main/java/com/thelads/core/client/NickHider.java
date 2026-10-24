package com.thelads.core.client;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.NickHiderModule;
import java.util.UUID;

/**
 * Manages player identity and skin disguising for streamers and content creators.
 */
public final class NickHider {
    private static NickHiderModule cachedModule;

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
