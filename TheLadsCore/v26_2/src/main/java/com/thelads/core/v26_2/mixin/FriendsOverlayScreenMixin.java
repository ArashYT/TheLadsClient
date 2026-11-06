package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.gui.ScreenTextGuard26;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.friends.FriendsOverlayScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla 26.2's FriendsOverlayScreen.keyPressed checks {@code getFocused() instanceof AddFriendWidget},
 * even though the focused Screen widget is the inner {@link net.minecraft.client.gui.components.EditBox} directly,
 * causing O to close the menu while typing.
 */
@Mixin(FriendsOverlayScreen.class)
public abstract class FriendsOverlayScreenMixin extends Screen {
    protected FriendsOverlayScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsAllowTypingFriendsKey(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (this.minecraft != null && this.minecraft.options.keyFriends.matches(event)
            && ScreenTextGuard26.isEditingText(this)) {
            cir.setReturnValue(super.keyPressed(event));
        }
    }
}
