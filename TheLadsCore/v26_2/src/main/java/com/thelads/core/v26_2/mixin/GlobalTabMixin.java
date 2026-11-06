package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter;
import com.thelads.core.v26_2.gui.MessageIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.TabButton;
import net.minecraft.client.gui.components.tabs.MenuTabBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A tab (Create World's Game, World, More) with its label's icon before the text, the two centred together, when the tab is wide enough. */
@Mixin(MenuTabBar.MenuTabButton.class)
public abstract class GlobalTabMixin {
    /** The icon this tab's label gets, or null (no icon for it, or the tab is too narrow for both). */
    @Unique
    private String ladsTabIcon() {
        TabButton tab = (TabButton) (Object) this;
        String icon = MessageIcons.of(tab.getMessage());
        return icon != null && TitleScreenTheme.iconFits(tab.getWidth(), Minecraft.getInstance().font.width(tab.getMessage())) ? icon : null;
    }

    @Inject(method = "extractWidgetRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("TAIL"), require = 1)
    private void ladsTabIcon(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        String icon = ladsTabIcon();
        if (icon == null) return;
        TabButton tab = (TabButton) (Object) this;
        int top = tab.getY() + (tab.isSelected() ? 0 : 3); // where vanilla centres the label
        TitleScreenTheme.renderLabelIcon(new GuiGraphicsExtractorLadsAdapter(g), icon, tab.getX(), top, tab.getWidth(), tab.getHeight() - (tab.isSelected() ? 0 : 3),
            Minecraft.getInstance().font.width(tab.getMessage()), tab.active ? 0xFFFFFFFF : 0xFFA0A0A0);
    }

    @Inject(method = "renderLabel(Lnet/minecraft/client/gui/ActiveTextCollector;)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsTabLabel(ActiveTextCollector text, CallbackInfo ci) {
        if (ladsTabIcon() == null) return;
        TabButton tab = (TabButton) (Object) this;
        int width = Minecraft.getInstance().font.width(tab.getMessage()), left = TitleScreenTheme.iconLabelX(tab.getX(), tab.getWidth(), width);
        int top = tab.getY() + (tab.isSelected() ? 0 : 3);
        text.acceptScrollingWithDefaultCenter(tab.getMessage(), left, Math.min(left + width, tab.getX() + tab.getWidth() - 4), top, tab.getY() + tab.getHeight());
        ci.cancel();
    }
}
