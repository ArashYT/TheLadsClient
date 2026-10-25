package com.thelads.core.v1_8_9.social;

import com.thelads.core.client.social.SocialBridge;
import com.thelads.core.client.social.ui.SocialToasts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

/** Friends screen key (default O) and accept/deny-the-newest-toast keys (unbound) on 1.8.9. */
public final class SocialKeybind189 {
    public static final KeyBinding FRIENDS_KEY = new KeyBinding("key.theladscore.friends", Keyboard.KEY_O, "key.category.theladscore.controls");
    public static final KeyBinding TOAST_ACCEPT = new KeyBinding("key.theladscore.toast_accept", Keyboard.KEY_NONE, "key.category.theladscore.controls");
    public static final KeyBinding TOAST_DENY = new KeyBinding("key.theladscore.toast_deny", Keyboard.KEY_NONE, "key.category.theladscore.controls");

    public static void register() {
        ClientRegistry.registerKeyBinding(FRIENDS_KEY);
        ClientRegistry.registerKeyBinding(TOAST_ACCEPT);
        ClientRegistry.registerKeyBinding(TOAST_DENY);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;
        if (FRIENDS_KEY.isPressed() && mc.currentScreen == null) {
            SocialBridge.get().openFriendsScreen();
        }
        while (TOAST_ACCEPT.isPressed()) SocialToasts.acceptNewest();
        while (TOAST_DENY.isPressed()) SocialToasts.denyNewest();
    }
}
