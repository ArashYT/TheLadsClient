package com.thelads.core.modules;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import java.util.HashMap;
import java.util.Map;

/**
 * Auto Text / text hotkeys / macros: sends predefined chat messages or commands on keybind.
 * GATED: automates input; disallowed on unknown servers.
 */
public final class AutoTextModule extends Module {
    public static final String NAME = "AutoText";
    private static final Map<Integer, String> MACROS = new HashMap<>();

    public AutoTextModule() {
        super(NAME, "Configurable keybind macros that send predefined chat messages or commands. "
                + "Gated: off on servers where macro automation is risky or rules are unknown.");
        setEnabled(false); // Gated modules default OFF
    }

    public static boolean isAllowed() {
        Module mod = ModuleManager.getInstance().getModule(NAME);
        return mod != null && mod.isEnabled() && !HypixelSafetyManager.isDisallowed(NAME);
    }

    public static synchronized void bindMacro(int keyCode, String messageOrCommand) {
        if (messageOrCommand != null && !messageOrCommand.trim().isEmpty()) {
            MACROS.put(keyCode, messageOrCommand.trim());
        } else {
            MACROS.remove(keyCode);
        }
    }

    public static synchronized String getMacro(int keyCode) {
        return MACROS.get(keyCode);
    }

    public static synchronized boolean triggerKey(int keyCode) {
        if (!isAllowed()) return false;
        String message = MACROS.get(keyCode);
        if (message != null && !message.isEmpty()) {
            LadsGameBridge.get().sendChatLine(message);
            return true;
        }
        return false;
    }
}
