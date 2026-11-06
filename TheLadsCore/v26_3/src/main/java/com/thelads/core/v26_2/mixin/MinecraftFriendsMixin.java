package com.thelads.core.v26_2.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.v26_2.social.FriendsScreen26;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.friends.FriendsOverlayScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftFriendsMixin {
    @Inject(method = "handleGlobalKeyPress(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsGuardGlobalKeysWhileEditing(InputConstants.Key key, boolean inGame, CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = (Minecraft) (Object) this;
        Screen screen = mc.gui != null ? mc.gui.screen() : null;
        if (screen != null && screen.isInputCaptured()
            && key.getType() == InputConstants.Type.KEYBOARD && !isFunctionKey(key.getValue())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "toggleFriendsScreen()Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsToggleFriendsScreen(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.gui == null) {
            cir.setReturnValue(false);
            return;
        }
        Screen screen = mc.gui.screen();
        if (screen != null && screen.isInputCaptured()) {
            cir.setReturnValue(false);
            return;
        }
        if (screen instanceof FriendsScreen26 || screen instanceof FriendsOverlayScreen) {
            screen.onClose();
            cir.setReturnValue(true);
            return;
        }
        if (screen == null || screen instanceof TitleScreen || screen instanceof PauseScreen) {
            mc.setScreenAndShow(new FriendsScreen26(screen));
            cir.setReturnValue(true);
            return;
        }
        cir.setReturnValue(false);
    }

    private static boolean isFunctionKey(int key) {
        return (key >= InputConstants.KEY_F1 && key <= InputConstants.KEY_F12)
            || (key >= InputConstants.KEY_F13 && key <= InputConstants.KEY_F24);
    }
}
