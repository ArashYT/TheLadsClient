package com.thelads.smp.features.arise.nb2.mixin;

import com.thelads.smp.features.arise.nb2.QuitConfirmManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Mixin to PauseScreen to wrap the Disconnect / Quit button action with a confirmation dialog (Quit).
 */
@Environment(EnvType.CLIENT)
@Mixin(PauseScreen.class)
public abstract class MixinPauseScreen {

    @ModifyArg(
            method = "createPauseMenu()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/Button;builder(Lnet/minecraft/network/chat/Component;Lnet/minecraft/client/gui/components/Button$OnPress;)Lnet/minecraft/client/gui/components/Button$Builder;",
                    ordinal = 1
            ),
            index = 1
    )
    private Button.OnPress onWrapDisconnectButton(Button.OnPress original) {
        PauseScreen currentScreen = (PauseScreen) (Object) this;
        return button -> {
            if (QuitConfirmManager.isQuitConfirmEnabled()) {
                QuitConfirmManager.promptDisconnectConfirmation(currentScreen, () -> original.onPress(button));
            } else {
                original.onPress(button);
            }
        };
    }
}
