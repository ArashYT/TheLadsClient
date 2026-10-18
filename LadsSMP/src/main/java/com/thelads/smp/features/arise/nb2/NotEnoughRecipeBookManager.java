package com.thelads.smp.features.arise.nb2;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Handles the Not Enough Recipe Book (NERB) functionality:
 * repurposes the vanilla recipe book button to toggle the JEI overlay visibility.
 */
public final class NotEnoughRecipeBookManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/NERB");

    private NotEnoughRecipeBookManager() {}

    public static boolean isNerbEnabled() {
        return NB2Config.get().notEnoughRecipeBook;
    }

    public static boolean isToggleMode() {
        return isNerbEnabled() && "TOGGLE".equalsIgnoreCase(NB2Config.get().recipeBookMode);
    }

    public static boolean isDisabledMode() {
        return isNerbEnabled() && "DISABLED".equalsIgnoreCase(NB2Config.get().recipeBookMode);
    }

    @Environment(EnvType.CLIENT)
    public static void toggleJeiOverlay() {
        if (!FabricLoader.getInstance().isModLoaded("jei")) {
            return;
        }
        try {
            Class<?> internalClass = Class.forName("mezz.jei.common.Internal");
            Method getToggleState = internalClass.getMethod("getClientToggleState");
            Object toggleState = getToggleState.invoke(null);
            if (toggleState != null) {
                Method toggleOverlayEnabled = toggleState.getClass().getMethod("toggleOverlayEnabled");
                toggleOverlayEnabled.invoke(toggleState);
            }
        } catch (Exception e) {
            LOGGER.warn("[NERB] Failed to toggle JEI overlay: {}", e.getMessage());
        }
    }
}
