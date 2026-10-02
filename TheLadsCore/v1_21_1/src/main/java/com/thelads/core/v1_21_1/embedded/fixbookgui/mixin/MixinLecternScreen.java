// Adapted from FixBookGUI 2.0.0 by KosmoMoustache (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.fixbookgui.mixin;

import com.thelads.core.v1_21_1.embedded.fixbookgui.FixBookGui;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.LecternScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.world.inventory.LecternMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * @author KosmoMoustache
 * @reason <a href="https://bugs.mojang.com/projects/MC/issues/MC-61489">Minecraft Bug Tracker</a>
 */
@Mixin(LecternScreen.class)
public abstract class MixinLecternScreen extends BookViewScreen implements MenuAccess<LecternMenu> {

    protected MixinLecternScreen() {
        super(null);
    }

    @Redirect(method = "createMenuControls", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/LecternScreen;addRenderableWidget(Lnet/minecraft/client/gui/components/events/GuiEventListener;)Lnet/minecraft/client/gui/components/events/GuiEventListener;"))
    public <T extends GuiEventListener & Renderable & NarratableEntry> T fbg$createMenuControlsButtonBuilder(LecternScreen screen, T element) {
        if (element instanceof LayoutElement widget) {
            widget.setY(widget.getY() + FixBookGui.getFixedY(this));
        }
        return this.addRenderableWidget(element);
    }
}
