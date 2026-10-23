package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.gui.ServerDiscoveryScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin extends Screen {
    @Unique private Button ladsDiscoverButton;

    protected JoinMultiplayerScreenMixin() {
        super(Component.empty());
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void ladsAddDiscoverButton(CallbackInfo ci) {
        ladsPurgeEssential();
        ladsDiscoverButton = addRenderableWidget(Button.builder(Component.literal("Discover Servers"), b -> {
            if (minecraft != null) {
                minecraft.setScreenAndShow(new ServerDiscoveryScreen(this));
            }
        }).bounds(width - 124, 8, 116, 20).build());
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void ladsSuppressEssential(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ladsPurgeEssential();
        ladsPlaceDiscover();
    }

    /** Top right, unless another button (ViaFabricPlus' default spot) is there: then top left. */
    @Unique
    private void ladsPlaceDiscover() {
        if (ladsDiscoverButton == null) return;
        ladsDiscoverButton.setPosition(width - 124, 8);
        if (ladsDiscoverCovered()) ladsDiscoverButton.setPosition(8, 8);
    }

    @Unique
    private boolean ladsDiscoverCovered() {
        Button d = ladsDiscoverButton;
        for (var child : children()) {
            if (child instanceof Button b && b != d && b.visible
                && b.getX() < d.getX() + d.getWidth() && d.getX() < b.getX() + b.getWidth()
                && b.getY() < d.getY() + d.getHeight() && d.getY() < b.getY() + b.getHeight()) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private void ladsPurgeEssential() {
        for (var child : List.copyOf(children())) {
            if (child instanceof AbstractWidget widget && widget != ladsDiscoverButton) {
                if (widget.getClass().getName().startsWith("gg.essential.")) {
                    widget.visible = false;
                    widget.active = false;
                    removeWidget(widget);
                }
            }
        }
    }
}
