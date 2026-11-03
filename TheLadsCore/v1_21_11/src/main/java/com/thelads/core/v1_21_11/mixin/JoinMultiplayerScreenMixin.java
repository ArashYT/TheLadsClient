package com.thelads.core.v1_21_11.mixin;

import com.thelads.core.client.LadsNetwork;
import com.thelads.core.v1_21_11.gui.PauseMultiplayer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The server list's Network button, top left as on the other versions. */
@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin extends Screen {
    protected JoinMultiplayerScreenMixin() {
        super(Component.empty());
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void ladsAddNetworkButton(CallbackInfo ci) {
        if (!LadsNetwork.titleButton() || LadsNetwork.connected()) return;
        addRenderableWidget(Button.builder(Component.literal(LadsNetwork.TITLE_LABEL), b -> PauseMultiplayer.joinNetwork(this))
            .tooltip(Tooltip.create(Component.literal(LadsNetwork.TOOLTIP))).bounds(8, 8, 64, 20).build());
    }
}
