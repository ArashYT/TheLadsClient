package com.thelads.core.v1_8_9.feature;

import com.thelads.core.config.Module;
import com.thelads.core.v1_8_9.mixin.GuiMultiplayerInvoker;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.input.Keyboard;

/** Ctrl+R refreshes the server list (1.8.9's has no text field to type in), and IgnorePacketErrors' switch, as 26.x ConnectionTweaks. */
public final class ConnectionTweaks189 {
    public static boolean ignorePacketErrors() {
        Module module = Options189.module("IgnorePacketErrors");
        return module != null && module.isEnabled();
    }

    @SubscribeEvent
    public void key(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (!(event.gui instanceof GuiMultiplayer) || !Keyboard.getEventKeyState() || Keyboard.getEventKey() != Keyboard.KEY_R || !GuiScreen.isCtrlKeyDown()) return;
        LogManager.getLogger("TheLadsCore").info("Lads server list: Ctrl+R, pinging every server again");
        ((GuiMultiplayerInvoker) event.gui).ladsRefresh();
        event.setCanceled(true);
    }
}
