package com.thelads.core.v1_20_1.mixin.menus;

import com.thelads.core.client.title.ButtonLift;
import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every vanilla button (and every mod's plain Button) on the Lads button surface; the label stays vanilla's. */
@Mixin(AbstractButton.class)
public abstract class GlobalButtonMixin extends AbstractWidget {
    protected GlobalButtonMixin(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Inject(method = "renderWidget(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsButton(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        var adapter = new GuiGraphicsLadsAdapter1201(g);
        float lift = ButtonLift.eased(this);
        adapter.managed(() -> TitleScreenTheme.renderButtonSurface(adapter, getX(), getY(), getWidth(), getHeight(), isHoveredOrFocused(),
            isFocused(), active, alpha, lift));
        Font font = Minecraft.getInstance().font;
        int color = (active ? 0xFFFFFF : 0xA0A0A0) | Mth.ceil(alpha * 255.0f) << 24;
        // The pause menu's buttons: an icon before the label, the two centred together.
        String icon = ButtonLift.icon(this);
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
