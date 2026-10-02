// Adapted from FixBookGUI 2.1.0 by KosmoMoustache (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.fixbookgui.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.thelads.core.v26_2.embedded.fixbookgui.FixBookGui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * @author KosmoMoustache
 * @reason <a href="https://bugs.mojang.com/projects/MC/issues/MC-61489">Minecraft Bug Tracker</a>
 */
@Mixin(BookEditScreen.class)
public abstract class MixinBookEditScreen extends Screen {

    protected MixinBookEditScreen() {
        super(null);
    }

    @ModifyArg(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/MultiLineEditBox$Builder;setY(I)Lnet/minecraft/client/gui/components/MultiLineEditBox$Builder;"))
    public int fbg$initSetY(int y) {
        return FixBookGui.getFixedY(this) + y;
    }

    @ModifyReturnValue(method = "backgroundTop", at = @At("RETURN"))
    public int fbg$backgroundTop(int original) {
        return FixBookGui.getFixedY(this) + original;
    }
}
