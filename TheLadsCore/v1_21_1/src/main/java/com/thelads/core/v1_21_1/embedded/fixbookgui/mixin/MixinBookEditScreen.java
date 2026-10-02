// Adapted from FixBookGUI 2.0.0 by KosmoMoustache (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.fixbookgui.mixin;

import com.thelads.core.v1_21_1.embedded.fixbookgui.FixBookGui;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * @author KosmoMoustache
 * @reason <a href="https://bugs.mojang.com/projects/MC/issues/MC-61489">Minecraft Bug Tracker</a>
 */
@Mixin(BookEditScreen.class)
public abstract class MixinBookEditScreen extends Screen {

    protected MixinBookEditScreen() {
        super(null);
    }

    // ! SAME
    @ModifyArg(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/Button$Builder;bounds(IIII)Lnet/minecraft/client/gui/components/Button$Builder;",
            ordinal = 0), index = 1)
    private int fbg$InitSignBtn(int y) {
        return FixBookGui.getFixedY(this) + y;
    }

    // ! SAME
    @ModifyArg(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/Button$Builder;bounds(IIII)Lnet/minecraft/client/gui/components/Button$Builder;",
            ordinal = 1), index = 1)
    private int fbg$InitDoneBtn(int y) {
        return FixBookGui.getFixedY(this) + y;
    }

    // ! SAME
    @ModifyArg(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/Button$Builder;bounds(IIII)Lnet/minecraft/client/gui/components/Button$Builder;",
            ordinal = 2), index = 1)
    private int fbg$InitFinalizeBtn(int y) {
        return FixBookGui.getFixedY(this) + y;
    }

    // ! SAME
    @ModifyArg(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/Button$Builder;bounds(IIII)Lnet/minecraft/client/gui/components/Button$Builder;",
            ordinal = 3), index = 1)
    private int fbg$InitCancelBtn(int y) {
        return FixBookGui.getFixedY(this) + y;
    }

    // ! SAME
    @Redirect(method = "init", at = @At(value = "NEW", target = "net/minecraft/client/gui/screens/inventory/PageButton"))
    private PageButton fbg$InitPageButton(int x, int y, boolean isForward, Button.OnPress onPress, boolean playTurnSound) {
        return new PageButton(x, FixBookGui.getFixedY(this) + y, isForward, onPress, playTurnSound);
    }

    @ModifyArg(method = "renderBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"), index = 2)
    public int fbg$renderBackgroundBlit(int y) {
        return FixBookGui.getFixedY(this) + y;
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I",
            ordinal = 0), index = 3)
    public int fbg$renderDrawStringEditTitleLabel(int y) {
        return FixBookGui.getFixedY(this) + y;
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I",
            ordinal = 0), index = 3)
    public int fbg$renderDrawStringFormattedCharSequence(int y) {
        return FixBookGui.getFixedY(this) + y;
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I",
            ordinal = 1), index = 3)
    public int fbg$renderDrawStringOwnerText(int y) {
        return FixBookGui.getFixedY(this) + y;
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawWordWrap(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/FormattedText;IIII)V"), index = 3)
    public int fbg$renderDrawWordWrapFinalizeWarningLabel(int y) {
        return FixBookGui.getFixedY(this) + y;
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I",
            ordinal = 2), index = 3)
    public int fbg$renderPageMsg(int y) {
        return FixBookGui.getFixedY(this) + y;
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I",
            ordinal = 3), index = 3)
    public int fbg$renderDrawStringLineInfo(int y) {
        return FixBookGui.getFixedY(this) + y;
    }

    @ModifyArgs(method = "renderCursor", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))
    public void fbg$renderCursorFill(Args args) {
        args.set(1, FixBookGui.getFixedY(this) + (int) args.get(1));
        args.set(3, FixBookGui.getFixedY(this) + (int) args.get(3));
    }

    @ModifyArg(method = "renderCursor", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I"),
            index = 3)
    public int fbg$renderCursorDrawStringUnderscore(int y) {
        return FixBookGui.getFixedY(this) + y;
    }

    @ModifyArgs(method = "renderHighlight", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(Lnet/minecraft/client/renderer/RenderType;IIIII)V"))
    public void fbg$renderHighlightFill(Args args) {
        args.set(2, FixBookGui.getFixedY(this) + (int) args.get(2));
        args.set(4, FixBookGui.getFixedY(this) + (int) args.get(4));
    }

    @Redirect(method = "convertScreenToLocal", at = @At(value = "NEW", target = "net/minecraft/client/gui/screens/inventory/BookEditScreen$Pos2i"))
    public BookEditScreen.Pos2i fbg$convertScreenToLocal(int x, int y) {
        return new BookEditScreen.Pos2i(x, y - FixBookGui.getFixedY(this));
    }
}
