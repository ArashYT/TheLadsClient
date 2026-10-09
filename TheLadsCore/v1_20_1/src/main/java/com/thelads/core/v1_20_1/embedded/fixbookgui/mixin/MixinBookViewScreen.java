// Adapted from FixBookGUI 2.2.0 by KosmoMoustache (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core, ported to 1.20.1.
package com.thelads.core.v1_20_1.embedded.fixbookgui.mixin;

import com.thelads.core.v1_20_1.embedded.fixbookgui.FixBookGui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.20.1's book view has no overridable top (26.3's {@code backgroundTop()}): its heights are literals, so each is moved here.
 * The lectern screen extends this one and builds its buttons in the same init.
 */
@Mixin(BookViewScreen.class)
public abstract class MixinBookViewScreen extends Screen {

    protected MixinBookViewScreen() {
        super(null);
    }

    /** Done, page arrows (and the lectern's Take Book): created in init at 196 / 159. */
    @Inject(method = "init", at = @At("RETURN"))
    private void fbg$initWidgets(CallbackInfo ci) {
        FixBookGui.shiftWidgets(this);
    }

    /** The book texture, drawn at y = 2. */
    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"), index = 2)
    private int fbg$renderBackground(int y) {
        return FixBookGui.getFixedY(this) + y;
    }

    /** "Page 1 of 2", drawn at y = 18. */
    @ModifyConstant(method = "render", constant = @Constant(intValue = 18))
    private int fbg$renderPageIndicator(int y) {
        return FixBookGui.getFixedY(this) + y;
    }

    /** First line of text, at y = 32 (then 9 further down for each line). */
    @ModifyConstant(method = "render", constant = @Constant(intValue = 32))
    private int fbg$renderFirstLine(int y) {
        return FixBookGui.getFixedY(this) + y;
    }

    /** Click detection for links in the text starts at the book's top (2) plus 30. */
    @ModifyConstant(method = "getClickedComponentStyleAt", constant = @Constant(doubleValue = 2.0))
    private double fbg$clickTop(double y) {
        return FixBookGui.getFixedY(this) + y;
    }
}
