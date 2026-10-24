package com.thelads.core.v1_8_9.social;

import com.thelads.core.client.social.SocialBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

/**
 * Keybind for opening the Friends screen (default 'O') on 1.8.9.
 */
public final class SocialKeybind189 {
    public static final KeyBinding FRIENDS_KEY = new KeyBinding("key.theladscore.friends", Keyboard.KEY_O, "key.category.theladscore.controls");

    public static void register() {
        ClientRegistry.registerKeyBinding(FRIENDS_KEY);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;
        if (FRIENDS_KEY.isPressed() && mc.currentScreen == null) {
            SocialBridge.get().openFriendsScreen();
        }
    }
}
