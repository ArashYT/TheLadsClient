// Adapted from Controlling 12.0.2 (Forge 1.20.1) by Jaredlll08 (MIT); modified by The Lads: Forge reached by reflection, Core builds without it.
package com.thelads.core.v1_20_1.embedded.controlling.platform;

import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Method;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;

/**
 * Forge (Zero SMP, through Sinytra Connector) binds Shift, Ctrl or Alt + a key (options.txt {@code key_x:key.keyboard.k:SHIFT}). The Fabric
 * helper set only the key, so a held Shift became the binding itself and Shift + key could not be bound; this does what Forge's own key
 * binds screen does (KeyModifier.getActiveModifier, setKeyModifierAndCode, setToDefault with the default modifier).
 */
final class ForgePlatformHelper extends FabricPlatformHelper {
    private final Method activeModifier, isModifier, setModifierAndCode, setToDefault, modifierConflict;

    private ForgePlatformHelper() throws ReflectiveOperationException {
        Class<?> modifier = Class.forName("net.minecraftforge.client.settings.KeyModifier");
        activeModifier = modifier.getMethod("getActiveModifier");
        isModifier = modifier.getMethod("isKeyCodeModifier", InputConstants.Key.class);
        setModifierAndCode = KeyMapping.class.getMethod("setKeyModifierAndCode", modifier, InputConstants.Key.class);
        setToDefault = KeyMapping.class.getMethod("setToDefault");
        modifierConflict = KeyMapping.class.getMethod("hasKeyModifierConflict", KeyMapping.class);
    }

    /** Forge's helper when the game runs on Forge, else the Fabric one. */
    static IPlatformHelper create() {
        try {
            return new ForgePlatformHelper();
        } catch (ReflectiveOperationException | LinkageError notForge) {
            return new FabricPlatformHelper();
        }
    }

    @Override
    public boolean hasConflictingModifier(KeyMapping keybinding, KeyMapping other) {
        return (boolean) call(modifierConflict, keybinding, other) || (boolean) call(modifierConflict, other, keybinding);
    }

    @Override
    public void setKey(Options options, KeyMapping keybinding, InputConstants.Key key) {
        call(setModifierAndCode, keybinding, call(activeModifier, null), key);
        options.setKey(keybinding, key);
    }

    @Override
    public void setToDefault(Options options, KeyMapping keybinding) {
        call(setToDefault, keybinding);
        options.setKey(keybinding, keybinding.getDefaultKey());
    }

    @Override
    public boolean isKeyCodeModifier(InputConstants.Key key) {
        return (boolean) call(isModifier, null, key);
    }

    private static Object call(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Forge key binding call " + method.getName() + " failed", failure);
        }
    }
}
