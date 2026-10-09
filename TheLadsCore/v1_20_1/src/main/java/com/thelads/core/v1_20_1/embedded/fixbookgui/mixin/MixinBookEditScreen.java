// Adapted from FixBookGUI 2.2.0 by KosmoMoustache (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core, ported to 1.20.1.
package com.thelads.core.v1_20_1.embedded.fixbookgui.mixin;

import com.thelads.core.v1_20_1.embedded.fixbookgui.FixBookGui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.20.1 has one screen for writing and signing (26.3 split the signing half into BookSignScreen). Every height in it is a
 * literal; the page text and the cursor/selection go through convertLocalToScreen / convertScreenToLocal, so moving the 32 in
 * those two moves the text, the caret, the selection and mouse picking together.
 */
@Mixin(BookEditScreen.class)
public abstract class MixinBookEditScreen extends Screen {

    protected MixinBookEditScreen() {
        super(null);
    }

    /** Sign / Done / Finalize / Cancel (196) and the page arrows (159), all created in init. */
    @Inject(method = "init", at = @At("RETURN"))
    private void fbg$initWidgets(CallbackInfo ci) {
        FixBookGui.shiftWidgets(this);
    }

    /** The book texture, drawn at y = 2. */
    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"), index = 2)
    private int fbg$renderBackground(int y) {
        return FixBookGui.getFixedY(this) + y;
    }

    /** "Page 1 of 2", at y = 18. */
    @ModifyConstant(method = "render", constant = @Constant(intValue = 18))
    private int fbg$renderPageIndicator(int y) {
        return FixBookGui.getFixedY(this) + y;
    }

    /** Signing: "Enter Book Title" label at y = 34. */
    @ModifyConstant(method = "render", constant = @Constant(intValue = 34))
    private int fbg$renderEditTitleLabel(int y) {
        return FixBookGui.getFixedY(this) + y;
    }

    /** Signing: the title being typed, at y = 50. */
    @ModifyConstant(method = "render", constant = @Constant(intValue = 50))
    private int fbg$renderTitle(int y) {
        return FixBookGui.getFixedY(this) + y;
    }

    /** Signing: "by <player>", at y = 60. */
    @ModifyConstant(method = "render", constant = @Constant(intValue = 60))
    private int fbg$renderOwnerText(int y) {
        return FixBookGui.getFixedY(this) + y;
    }

    /** Signing: the "can't edit after signing" warning, at y = 82. */
    @ModifyConstant(method = "render", constant = @Constant(intValue = 82))
    private int fbg$renderFinalizeWarning(int y) {
        return FixBookGui.getFixedY(this) + y;
    }

    /** Page text: local (0, 0) is screen y = 32. */
    @ModifyConstant(method = "convertLocalToScreen", constant = @Constant(intValue = 32))
    private int fbg$localToScreen(int y) {
        return FixBookGui.getFixedY(this) + y;
    }

    @ModifyConstant(method = "convertScreenToLocal", constant = @Constant(intValue = 32))
    private int fbg$screenToLocal(int y) {
        return FixBookGui.getFixedY(this) + y;
    }
}
