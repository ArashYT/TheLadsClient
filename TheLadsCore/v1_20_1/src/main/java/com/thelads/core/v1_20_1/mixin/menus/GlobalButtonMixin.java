package com.thelads.core.v1_20_1.mixin.menus;

import com.thelads.core.client.title.ButtonLift;
import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import com.thelads.core.v1_20_1.gui.MessageIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.TextAndImageButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every vanilla button (and every mod's plain Button) on the Lads button surface; the label stays vanilla's. */
@Mixin(AbstractButton.class)
public abstract class GlobalButtonMixin extends AbstractWidget {
    protected GlobalButtonMixin(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    /**
     * The icon drawn before the button's label: the one its screen gave it (the pause menu's), else the one its text maps to
     * (ButtonIcons: vanilla's keys, the Lads' own labels); null for none, or for a button too narrow for both.
     */
    @Unique
    private String ladsIcon(Font font) {
        if ((Object) this instanceof TextAndImageButton) return null; // already draws its own sprite beside its label
        String icon = ButtonLift.icon(this);
        if (icon == null) icon = MessageIcons.of(getMessage());
        return icon != null && TitleScreenTheme.iconFits(getWidth(), font.width(getMessage())) ? icon : null;
    }

    @Inject(method = "renderWidget(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsButton(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        var adapter = new GuiGraphicsLadsAdapter1201(g);
        float lift = ButtonLift.eased(this);
        adapter.managed(() -> TitleScreenTheme.renderButtonSurface(adapter, getX(), getY(), getWidth(), getHeight(), isHoveredOrFocused(),
            isFocused(), active, alpha, lift));
        Font font = Minecraft.getInstance().font;
        int color = (active ? 0xFFFFFF : 0xA0A0A0) | Mth.ceil(alpha * 255.0f) << 24;
        // An icon before the label, the two centred together.
        String icon = ladsIcon(font);
        if (icon != null) {
            int textWidth = font.width(getMessage());
            TitleScreenTheme.renderLabelIcon(adapter, icon, getX(), getY(), getWidth(), getHeight(), textWidth, active ? 0xFFFFFFFF : 0xFFA0A0A0);
            int left = TitleScreenTheme.iconLabelX(getX(), getWidth(), textWidth);
            // Exactly its width: drawn where the icon expects it; a label too long for the button scrolls in the space left.
            renderScrollingString(g, font, getMessage(), left, getY(), Math.min(left + textWidth, getX() + getWidth() - 4), getY() + getHeight(), color);
        } else {
            ((AbstractButton) (Object) this).renderString(g, font, color);
        }
        ci.cancel();
    }
}
