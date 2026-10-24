package com.thelads.core.client.font;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.CustomFontModule;

public final class CustomFontManager {
    private static CustomFontModule cachedModule;

    private CustomFontManager() {}

    public static CustomFontModule module() {
        CustomFontModule m = cachedModule;
        if (m == null) {
            if (ModuleManager.getInstance().getModule(CustomFontModule.NAME) instanceof CustomFontModule found) {
                cachedModule = m = found;
            }
        }
        return m;
    }

    public static boolean isCustomFontActive() {
        CustomFontModule m = module();
        return m != null && m.isEnabled();
    }

    public static float getFontScaleMultiplier() {
        CustomFontModule m = module();
        if (m == null || !m.isEnabled()) return 1.0f;
        int pct = m.fontScale.getIntValue();
        return pct <= 0 ? 1.0f : pct / 100.0f;
    }

    public static boolean isTextShadowEnabled() {
        CustomFontModule m = module();
        if (m == null || !m.isEnabled()) return true;
        return m.textShadow.get();
    }

    public static boolean isSmoothFont() {
        CustomFontModule m = module();
        if (m == null || !m.isEnabled()) return false;
        return m.smoothFont.get();
    }

    public static String getFontFamily() {
        CustomFontModule m = module();
        if (m == null || !m.isEnabled()) return "Default";
        return m.fontFamily.getValue();
    }
}
