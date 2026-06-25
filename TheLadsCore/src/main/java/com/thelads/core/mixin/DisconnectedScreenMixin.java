package com.thelads.core.mixin;

import com.thelads.core.client.AutoReconnect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisconnectedScreen.class)
public abstract class DisconnectedScreenMixin extends Screen {
    @Shadow @Final private Screen parent;

    protected DisconnectedScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void ladsAddReconnect(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.multiplayer.ServerData server = mc.getCurrentServer();
        if (server == null) {
            server = AutoReconnect.get().getLastServer();
        }
        AutoReconnect.get().onScreenShown(this.parent, server);

        int y = this.height - 30;
        if (AutoReconnect.get().isModuleEnabled()) {
            // Reconnect + Cancel as a centered pair (span -154..+154 around the middle).
            this.addRenderableWidget(Button.builder(Component.literal("Reconnect"),
                    b -> AutoReconnect.get().reconnect())
                .bounds(this.width / 2 - 154, y, 150, 20).build());
            Button cancel = Button.builder(Component.literal("Cancel Auto-Reconnect"),
                    b -> AutoReconnect.get().cancel())
                .bounds(this.width / 2 + 4, y, 150, 20).build();
            this.addRenderableWidget(cancel);
            AutoReconnect.get().setCancelButton(cancel); // tick updates its label with the countdown
        } else {
            // Only the Reconnect button — center it instead of leaving it off to the left.
            this.addRenderableWidget(Button.builder(Component.literal("Reconnect"),
                    b -> AutoReconnect.get().reconnect())
                .bounds(this.width / 2 - 75, y, 150, 20).build());
            AutoReconnect.get().setCancelButton(null);
        }
    }
}
