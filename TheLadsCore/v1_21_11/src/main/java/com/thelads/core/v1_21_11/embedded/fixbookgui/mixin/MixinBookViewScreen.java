// Adapted from FixBookGUI 2.1.0 by KosmoMoustache (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.fixbookgui.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.thelads.core.v1_21_11.embedded.fixbookgui.FixBookGui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * @author mworzala (Implementation: KosmoMoustache)
 * @reason <a href="https://bugs.mojang.com/projects/MC/issues/MC-61489">Minecraft Bug Tracker</a>
 * @see <a href="https://gist.github.com/mworzala/9a8d86803784c9c81aac77d9a7f9fb2b">Gist</a>
 */
@Mixin(BookViewScreen.class)
public abstract class MixinBookViewScreen extends Screen {

    protected MixinBookViewScreen() {
        super(null);
    }

    @ModifyReturnValue(method = "backgroundTop", at = @At("RETURN"))
    public int fbg$backgroundTop(int original) {
        return FixBookGui.getFixedY(this) + original;
    }
}
