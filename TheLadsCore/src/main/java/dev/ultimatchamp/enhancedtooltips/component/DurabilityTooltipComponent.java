/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.Font
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.FormattedText
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.network.chat.Style
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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class DurabilityTooltipComponent
implements EnhancedTooltipsTooltipComponent {
    private static final int SPACING = 4;
    private static final int WIDTH = 80;
    private final ItemStack stack;
    private final EnhancedTooltipsConfig config;

    public DurabilityTooltipComponent(ItemStack stack) {
        this.stack = stack;
        this.config = EnhancedTooltipsConfig.load();
    }

    public boolean isDurabilityDisabled() {
        return !this.stack.isDamageableItem() || this.config.durability.durabilityTooltip.equals((Object)EnhancedTooltipsConfig.DurabilityTooltipMode.OFF) && !this.config.durability.durabilityBar;
    }

    private Component getDurabilityText() {
        int remaining = this.stack.getMaxDamage() - this.stack.getDamageValue();
        if (remaining <= 0) {
            return Component.empty();
        }
        var module = com.thelads.core.config.ModuleManager.getInstance().getModule("EnhancedToolbars");
        boolean showMax = true;
        boolean colorize = true;
        if (module != null && module.isEnabled()) {
            var showMaxOpt = module.getOption("Show Max Durability");
            if (showMaxOpt instanceof com.thelads.core.config.BoolOption bo) showMax = bo.get();
            var colorizeOpt = module.getOption("Colorize Durability");
            if (colorizeOpt instanceof com.thelads.core.config.BoolOption bo) colorize = bo.get();
        }

        switch (this.config.durability.durabilityTooltip) {
            case VALUE: {
                Style remainingStyle = Style.EMPTY;
                if (colorize) remainingStyle = remainingStyle.withColor(this.stack.getBarColor());

                MutableComponent comp = Component.literal(" ").append(Component.literal(String.valueOf(remaining)).setStyle(remainingStyle));
                if (showMax) {
                    Style maxStyle = Style.EMPTY;
                    if (colorize) maxStyle = maxStyle.withColor(-16711936);
                    comp = comp.append(Component.literal(" / ").setStyle(Style.EMPTY.withColor(-4539718)))
                               .append(Component.literal(String.valueOf(this.stack.getMaxDamage())).setStyle(maxStyle));
                }
                return comp;
            }
            case PERCENTAGE: {
                int pct = remaining * 100 / this.stack.getMaxDamage();
                Style remainingStyle = Style.EMPTY;
                if (colorize) remainingStyle = remainingStyle.withColor(this.stack.getBarColor());
                return Component.literal(" " + pct + "%").setStyle(remainingStyle);
            }
            default:
                return Component.empty();
        }
    }

    @Override
    public int height() {
        if (this.isDurabilityDisabled()) {
            return 0;
        }
        return (this.config.durability.durabilityBar ? 18 : 17) - (this.config.general.removeAllSpacing ? (this.config.durability.durabilityBar ? 4 : 6) : 0);
    }

    public int getWidth(@NotNull Font textRenderer) {
        if (this.isDurabilityDisabled()) {
            return 0;
        }
        int durabilityTextWidth = textRenderer.width((FormattedText)Component.translatable((String)"enhancedtooltips.tooltip.durability"));
        if (this.config.durability.durabilityBar) {
            return durabilityTextWidth + 4 + 80 + 1;
        }
        Component durability = this.getDurabilityText();
        return durabilityTextWidth + textRenderer.width((FormattedText)durability);
    }

    @Override
    public void drawImage(@NotNull Font textRenderer, int x, int y, int width, int height, @NotNull GuiGraphicsExtractor context) {
        Component durabilityText;
        if (this.isDurabilityDisabled()) {
            return;
        }
        if (!this.config.general.removeAllSpacing) {
            y += this.config.durability.durabilityBar ? 4 : 8;
        }
        if (this.config.durability.durabilityBar) {
            y += 2;
        }
        Objects.requireNonNull(textRenderer);
        int textHeight = 9;
        int textY = this.config.durability.durabilityBar ? y - textHeight + 8 + 2 : y;
        TooltipHelper.renderText(context, textRenderer, (Component)Component.translatable((String)"enhancedtooltips.tooltip.durability"), x, textY, -4539718, true);
        x += textRenderer.width((FormattedText)Component.translatable((String)"enhancedtooltips.tooltip.durability")) + 4;
        int remaining = this.stack.getMaxDamage() - this.stack.getDamageValue();
        if (this.config.durability.durabilityBar && remaining > 0) {
            context.fill(x, textY - 2, x + remaining * 80 / this.stack.getMaxDamage(), textY + textHeight, BadgesUtils.darkenColor(0xFF000000 | this.stack.getBarColor(), 0.9f));
        }
        if (!(durabilityText = this.getDurabilityText()).equals((Object)Component.empty())) {
            int textX = this.config.durability.durabilityBar ? x + (80 - textRenderer.width((FormattedText)durabilityText)) / 2 : x - 4;
            TooltipHelper.renderText(context, textRenderer, durabilityText, textX, textY, -1, true);
        }
        if (this.config.durability.durabilityBar) {
            BadgesUtils.drawFrame(context, x, textY - 2, 80, textHeight + 4, 400, BadgesUtils.darkenColor(0xFF000000 | this.stack.getBarColor(), 0.8f));
        }
    }
}

