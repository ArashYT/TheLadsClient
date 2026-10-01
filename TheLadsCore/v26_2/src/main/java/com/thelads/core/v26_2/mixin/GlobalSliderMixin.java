package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSliderButton.class)
public abstract class GlobalSliderMixin {
    @Shadow protected double value;

    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsSlider(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        AbstractSliderButton slider = (AbstractSliderButton)(Object)this;
        var font = Minecraft.getInstance().font;
        var adapter = new GuiGraphicsExtractorLadsAdapter(g, font);
        int x = slider.getX();
        int y = slider.getY();
        int w = slider.getWidth();
        int h = slider.getHeight();
        boolean hovered = slider.isHoveredOrFocused();
        boolean focused = slider.isFocused();
        boolean active = slider.active;
        float alpha = slider.getAlpha();

        // 1. Render base track surface matching client button layout
        TitleScreenTheme.renderButtonSurface(adapter, x, y, w, h, hovered, focused, active, alpha);

        // 2. Render slider handle knob
        int handleWidth = 8;
        int handleX = x + (int)(this.value * (double)(w - handleWidth));
        int handleColor = active ? (hovered ? 0xFFFFFFFF : 0xFFE0E0E0) : 0xFFA0A0A0;
        adapter.fill(handleX, y + 2, handleX + handleWidth, y + h - 2, handleColor);
        adapter.fill(handleX, y + 1, handleX + handleWidth, y + 2, 0x80000000);
        adapter.fill(handleX, y + h - 2, handleX + handleWidth, y + h - 1, 0x80000000);

        // 3. Draw centered text
        var msg = slider.getMessage();
        if (msg != null) {
            String text = msg.getString();
            int textW = adapter.textWidth(text);
            int textX = x + (w - textW) / 2;
            int textY = y + (h - adapter.fontHeight()) / 2 + 1;
            int textColor = active ? 0xFFFFFFFF : 0xFFA0A0A0;
            adapter.drawText(text, textX, textY, textColor);
        }

        ci.cancel();
    }
}
