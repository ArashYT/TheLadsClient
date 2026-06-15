package com.thelads.core.mixin;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.client.gui.GalleryScreen;

@Mixin(PauseScreen.class)
public abstract class GameMenuMixin extends Screen {

    protected GameMenuMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"), require = 0)
    private void addLadsSettingsButton(CallbackInfo ci) {
        // Anchored to the Disconnect / Save-and-Quit button so the row follows
        // the pause-menu column instead of floating at the bottom of the screen.
        AbstractWidget anchor = ladsFindDisconnectButton();

        int rowW = anchor != null ? anchor.getWidth() : 204;
        int x    = anchor != null ? anchor.getX() : this.width / 2 - rowW / 2;
        int y    = anchor != null ? anchor.getY() + anchor.getHeight() + 4 : this.height - 48;
        int half = (rowW - 4) / 2;

        this.addRenderableWidget(Button.builder(Component.literal("Lads Client"), (button) -> {
            this.minecraft.setScreen(new LadsSettingsScreen(this));
        }).bounds(x, y, half, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Gallery"), (button) -> {
            this.minecraft.setScreen(new GalleryScreen(this));
        }).bounds(x + half + 4, y, rowW - half - 4, 20).build());
    }

    @Unique
    private AbstractWidget ladsFindDisconnectButton() {
        AbstractWidget best = null;
        for (GuiEventListener child : this.children()) {
            if (!(child instanceof AbstractWidget w)) continue;
            String key = w.getMessage().getContents() instanceof TranslatableContents tc ? tc.getKey() : "";
            String text = w.getMessage().getString();
            if (key.equals("menu.disconnect") || key.equals("menu.returnToTitle")
                    || text.equalsIgnoreCase("Disconnect") || text.contains("Save and Quit")) {
                // Take the lowest match in case anything else echoes the label.
                if (best == null || w.getY() > best.getY()) best = w;
            }
        }
        return best;
    }
}
