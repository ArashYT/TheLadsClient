package com.thelads.core.v1_20_1.mixin.menus;

import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import com.thelads.core.v1_20_1.gui.MessageIcons;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.TabButton;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A tab (Create World's Game, World, More) with its label's icon before the text, the two centred together, when the tab is wide enough. */
@Mixin(TabButton.class)
public abstract class GlobalTabMixin extends AbstractWidget {
    protected GlobalTabMixin(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Shadow public abstract boolean isSelected();

    @Inject(method = "renderString(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;I)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsTabLabel(GuiGraphics g, Font font, int color, CallbackInfo ci) {
        String icon = MessageIcons.of(getMessage());
        if (icon == null) return;
        int textWidth = font.width(getMessage());
        if (!TitleScreenTheme.iconFits(getWidth(), textWidth)) return;
        int top = getY() + (isSelected() ? 0 : 3), height = getHeight() - (isSelected() ? 0 : 3); // where vanilla centres the label
        TitleScreenTheme.renderLabelIcon(new GuiGraphicsLadsAdapter1201(g), icon, getX(), top, getWidth(), height, textWidth, color);
        int left = TitleScreenTheme.iconLabelX(getX(), getWidth(), textWidth);
        renderScrollingString(g, font, getMessage(), left, top, Math.min(left + textWidth, getX() + getWidth() - 4), top + height, color);
        ci.cancel();
    }
}
