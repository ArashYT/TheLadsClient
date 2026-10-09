package com.thelads.core.v1_20_1.perf.connection;

import com.thelads.core.config.ModuleSupport;
import com.thelads.core.v1_20_1.compat.ModPresence1201;
import com.thelads.core.v1_20_1.perf.PerfOptions1201;
import com.thelads.core.v1_20_1.perf.mixin.JoinMultiplayerInvoker;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Small multiplayer features with no settings of their own: Ctrl+R refreshes the server list, and IgnorePacketErrors' switch.
 * Each stands down while the mod it replaces is installed.
 */
public final class ConnectionTweaks1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-Connection");
    private static final boolean PACKET_MOD = ModPresence1201.loaded("netprodis");

    private ConnectionTweaks1201() {}

    public static void register() {
        if (PACKET_MOD) {
            ModuleSupport.registerExternal("IgnorePacketErrors", "Network Protocol Disconnect", "netprodis", true);
            LOGGER.info("IgnorePacketErrors: Network Protocol Disconnect is installed, Lads stands down");
        } else {
            ModuleSupport.registerBuiltIn("IgnorePacketErrors");
        }
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof JoinMultiplayerScreen) {
                ScreenKeyboardEvents.allowKeyPress(screen).register((shown, key, scancode, modifiers) -> {
                    if (!refreshShortcut(shown, key)) return true;
                    LOGGER.info("Lads server list: Ctrl+R, pinging every server again");
                    ((JoinMultiplayerInvoker) shown).lads$refresh();
                    return false;
                });
            }
        });
    }

    /** Ctrl+R while no text box has the focus. */
    private static boolean refreshShortcut(Screen screen, int key) {
        return key == GLFW.GLFW_KEY_R && Screen.hasControlDown() && !(screen.getFocused() instanceof EditBox);
    }

    public static boolean ignorePacketErrors() {
        return !PACKET_MOD && PerfOptions1201.enabled("IgnorePacketErrors");
    }
}
