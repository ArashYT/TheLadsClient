/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.Font
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.FormattedText
 *  net.minecraft.util.Tuple
 *  net.minecraft.world.item.ItemStack
 *  org.jetbrains.annotations.NotNull
 */
package dev.ultimatchamp.enhancedtooltips.component;

import dev.ultimatchamp.enhancedtooltips.component.EnhancedTooltipsTooltipComponent;
import dev.ultimatchamp.enhancedtooltips.config.EnhancedTooltipsConfig;
import dev.ultimatchamp.enhancedtooltips.tooltip.TooltipHelper;
import dev.ultimatchamp.enhancedtooltips.util.BadgesUtils;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class HeaderTooltipComponent
implements EnhancedTooltipsTooltipComponent {
    private static final int TEXTURE_SIZE = 16;
    private static final int SPACING = 4;
    private final ItemStack stack;
    private final Component nameText;
    private final Component rarityName;
    private final EnhancedTooltipsConfig config;

    public HeaderTooltipComponent(ItemStack stack) {
        this.stack = stack;
        this.nameText = TooltipHelper.getDisplayName(stack);
        this.rarityName = TooltipHelper.getRarityName(stack);
        this.config = EnhancedTooltipsConfig.load();
    }

    @Override
    public int height() {
        return this.getTitleOffset();
    }

    public int getWidth(@NotNull Font textRenderer) {
        int rarityWidth = 0;
        if (this.config.general.rarityTooltip) {
            rarityWidth = textRenderer.width((FormattedText)this.rarityName);
        }
        int badgeWidth = 0;
        Component badgeText = (Component)BadgesUtils.getBadgeText(this.stack).getA();
        if (this.config.general.itemBadges && !badgeText.toFlatList().isEmpty()) {
            badgeWidth = textRenderer.width((FormattedText)badgeText) + 8;
        }
        int titleWidth = this.config.general.rarityTooltip ? textRenderer.width((FormattedText)this.nameText) + badgeWidth : Math.max(textRenderer.width((FormattedText)this.nameText), badgeWidth);
        return Math.max(titleWidth, rarityWidth) + this.getTitleOffset() + (this.getTitleOffset() - 16) / 2 + 2;
    }

    public int getTitleOffset() {
        return 26;
    }

    @Override
    public void drawText(@NotNull GuiGraphicsExtractor context, @NotNull Font textRenderer, int x, int y) {
        int startDrawX = x + this.getTitleOffset();
        int startDrawY = y;
        if (this.config.general.rarityTooltip) {
            startDrawY += 2;
        } else if (!this.config.general.itemBadges || ((Component)BadgesUtils.getBadgeText(this.stack).getA()).toFlatList().isEmpty()) {
            int n = this.getTitleOffset();
            Objects.requireNonNull(textRenderer);
            startDrawY += (int)((float)(n - 9) / 2.0f);
        }
        TooltipHelper.renderText(context, textRenderer, this.nameText, startDrawX, startDrawY, -1, true);
        if (this.config.general.rarityTooltip) {
            Objects.requireNonNull(textRenderer);
            TooltipHelper.renderText(context, textRenderer, this.rarityName, startDrawX, startDrawY += 9 + 4, -1, true);
        }
    }

    @Override
    public void drawImage(@NotNull Font textRenderer, int x, int y, int width, int height, @NotNull GuiGraphicsExtractor context) {
        Tuple<Component, Integer> badgeText;
        int startDrawX = x + (this.getTitleOffset() - 16) / 2 - 1;
        int startDrawY = y + (this.getTitleOffset() - 16) / 2 - 1;
        float bounce = 0.0f;
        if (this.config.itemPreviewAnimation.enabled) {
            int sec = (int)(this.config.itemPreviewAnimation.time * 1000.0f);
            float time = (float)(System.currentTimeMillis() % (long)sec) / (float)sec;
            bounce = (float)Math.sin((double)time * Math.PI * 2.0) * (this.config.itemPreviewAnimation.magnitude * 1.0f);
        }
        context.item(this.stack, startDrawX, (int)((float)startDrawY - bounce));
        if (!this.config.general.itemBadges) {
            return;
        }
        if (!this.config.general.rarityTooltip) {
            Objects.requireNonNull(textRenderer);
            y += 9 + 4;
        }
        if (!((Component)(badgeText = BadgesUtils.getBadgeText(this.stack)).getA()).toFlatList().isEmpty()) {
            this.drawBadge(textRenderer, (Component)badgeText.getA(), x, y, context, (Integer)badgeText.getB());
        }
    }

    private void drawBadge(Font textRenderer, Component text, int x, int y, GuiGraphicsExtractor context, int fillColor) {
        int textWidth = textRenderer.width((FormattedText)text);
        Objects.requireNonNull(textRenderer);
        int textHeight = 9;
        int textX = x + this.getTitleOffset() + (!this.config.general.rarityTooltip ? 4 : textRenderer.width((FormattedText)this.nameText) + 4 + 2);
        Objects.requireNonNull(textRenderer);
        int textY = y - 9 + 8 + 2 + 1;
        context.fill(textX - 4, textY - 2, textX + textWidth + 4, textY + textHeight, BadgesUtils.darkenColor(fillColor, 0.9f));
        TooltipHelper.renderText(context, textRenderer, text, textX, textY, -1, true);
        BadgesUtils.drawFrame(context, textX - 4, textY - 2, textWidth + 8, textHeight + 4, 400, BadgesUtils.darkenColor(fillColor, 0.8f));
    }
}

