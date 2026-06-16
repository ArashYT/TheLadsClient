package com.thelads.core.features.alwayson.advancementsreloaded.screens;

import com.thelads.core.features.alwayson.advancementsreloaded.ReloadedCriterionProgress;
import com.thelads.core.features.alwayson.advancementsreloaded.ReloadedWidgetType;
import com.thelads.core.features.alwayson.advancementsreloaded.config.Configuration;
import com.thelads.core.features.alwayson.advancementsreloaded.utils.TextUtils;
import com.thelads.core.features.alwayson.advancementsreloaded.utils.Utils;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

public class AdvancementReloadedWidget {
    private static final Identifier TITLE_BOX_TEXTURE = Identifier.withDefaultNamespace("advancements/title_box");
    private static final int[] SPLIT_OFFSET_CANDIDATES = new int[]{0, 10, -10, 25, -25};
    private final AdvancementReloadedTab tab;
    private final AdvancementNode advancement;
    private final DisplayInfo display;
    private final FormattedCharSequence title;
    private final int width;
    private final List<FormattedCharSequence> description;
    private final Minecraft client;
    @Nullable
    private AdvancementReloadedWidget parent;
    private final List<AdvancementReloadedWidget> children = Lists.newArrayList();
    @Nullable
    private AdvancementProgress progress;
    private List<ReloadedCriterionProgress> steps;
    private final int x;
    private final int y;
    private final RenderPipeline renderTypeGui = RenderPipelines.GUI_TEXTURED;

    public AdvancementReloadedWidget(AdvancementReloadedTab tab, Minecraft client, AdvancementNode advancement, DisplayInfo display) {
        this.tab = tab;
        this.advancement = advancement;
        this.display = display;
        this.client = client;
        this.title = Language.getInstance().getVisualOrder(client.font.substrByWidth(display.getTitle(), 163));
        this.x = Mth.floor(display.getX() * 28.0f);
        this.y = Mth.floor(display.getY() * 27.0f);
        int i = this.getProgressWidth();
        int j = 29 + client.font.width(this.title) + i;
        this.description = Language.getInstance().getVisualOrder(this.wrapDescription(ComponentUtils.mergeStyles(display.getDescription().copy(), Style.EMPTY.withColor(display.getType().getChatColor())), j));
        for (FormattedCharSequence orderedText : this.description) {
            j = Math.max(j, client.font.width(orderedText));
        }
        if (this.progress != null) {
            this.setSteps(this.progress);
        }
        this.width = j + 3 + 5;
    }

    private int getProgressWidth() {
        int i = this.advancement.advancement().requirements().size();
        if (i <= 1) {
            return 0;
        }
        MutableComponent mutableText = Component.translatable("advancements.progress", i, i);
        return this.client.font.width(mutableText) + 8;
    }

    private static float getMaxWidth(StringSplitter strSplitter, List<FormattedText> lines) {
        Objects.requireNonNull(strSplitter);
        return (float)lines.stream().mapToDouble(strSplitter::stringWidth).max().orElse(0.0);
    }

    private List<FormattedText> wrapDescription(Component text, int width) {
        StringSplitter strSplitter = this.client.font.getSplitter();
        List<FormattedText> list = null;
        float f = Float.MAX_VALUE;
        for (int i : SPLIT_OFFSET_CANDIDATES) {
            List<FormattedText> list2 = strSplitter.splitLines(text, width - i, Style.EMPTY);
            float g = Math.abs(AdvancementReloadedWidget.getMaxWidth(strSplitter, list2) - (float)width);
            if (g <= 10.0f) {
                return list2;
            }
            if (!(g < f)) continue;
            f = g;
            list = list2;
        }
        return list;
    }

    @Nullable
    private AdvancementReloadedWidget getParent(AdvancementNode advancement) {
        while ((advancement = advancement.parent()) != null && advancement.advancement().display().isEmpty()) {
        }
        if (advancement != null && advancement.advancement().display().isPresent()) {
            return this.tab.getWidget(advancement.holder());
        }
        Utils.LOGGER.warn("advancement parent cannot be retrieved from the advancement: " + advancement);
        return null;
    }

    public Advancement getAdvancement() {
        return this.advancement.advancement();
    }

    public AdvancementProgress getProgress() {
        return this.progress;
    }

    public List<ReloadedCriterionProgress> getSteps() {
        if (this.steps == null) {
            return Collections.emptyList();
        }
        return this.steps;
    }

    public boolean isSearchQueryMatched() {
        String search = this.tab.getScreen().getSearchText();
        if (search == null || search.isEmpty() || search.trim().isEmpty()) {
            return true;
        }
        String searchLower = search.toLowerCase();
        if (TextUtils.toString(this.display.getTitle()).toLowerCase().contains(searchLower)) {
            return true;
        }
        for (FormattedCharSequence line : this.description) {
            if (!TextUtils.toString(line).toLowerCase().contains(searchLower)) continue;
            return true;
        }
        for (ReloadedCriterionProgress step : this.getSteps()) {
            if (!TextUtils.toString(step.getHumanCriterionName()).toLowerCase().contains(searchLower)) continue;
            return true;
        }
        return false;
    }

    public void renderLines(GuiGraphicsExtractor context, int x, int y, boolean border) {
        if (this.parent != null) {
            int n;
            int i = x + this.parent.x + 13;
            int j = x + this.parent.x + 26 + 4;
            int k = y + this.parent.y + 13;
            int l = x + this.x + 13;
            int m = y + this.y + 13;
            n = border ? -16777216 : -1;
            if (border) {
                context.horizontalLine(j, i, k - 1, n);
                context.horizontalLine(j + 1, i, k, n);
                context.horizontalLine(j, i, k + 1, n);
                context.horizontalLine(l, j - 1, m - 1, n);
                context.horizontalLine(l, j - 1, m, n);
                context.horizontalLine(l, j - 1, m + 1, n);
                context.verticalLine(j - 1, m, k, n);
                context.verticalLine(j + 1, m, k, n);
            } else {
                context.horizontalLine(j, i, k, n);
                context.horizontalLine(l, j, m, n);
                context.verticalLine(j, m, k, n);
            }
        }
        for (AdvancementReloadedWidget advancementWidget : this.children) {
            advancementWidget.renderLines(context, x, y, border);
        }
    }

    public void renderWidgets(GuiGraphicsExtractor context, int x, int y) {
        if (!this.display.isHidden() || (this.progress != null && this.progress.isDone())) {
            float currentProgress = this.progress == null ? 0.0f : this.progress.getPercent();
            ReloadedWidgetType widgetType = currentProgress >= 1.0f ? ReloadedWidgetType.OBTAINED : ReloadedWidgetType.UNOBTAINED;
            boolean isDimmed = !this.isSearchQueryMatched();
            Identifier backgroundResource = widgetType.frameSprite(this.display.getType(), isDimmed);
            context.blitSprite(this.renderTypeGui, backgroundResource, x + this.x + 3, y + this.y, 26, 26);
            context.fakeItem(this.display.getIcon().create(), x + this.x + 8, y + this.y + 5);
            if (isDimmed) {
                Identifier dimmedResource = widgetType.frameSprite(this.display.getType(), true);
                context.blitSprite(this.renderTypeGui, dimmedResource, x + this.x + 3, y + this.y, 26, 26, 0.6f);
            }
        }
        for (AdvancementReloadedWidget advancementWidget : this.children) {
            advancementWidget.renderWidgets(context, x, y);
        }
    }

    public int getWidth() {
        return this.width;
    }

    public void setProgress(AdvancementProgress progress) {
        this.progress = progress;
        this.setSteps(progress);
    }

    public void setSteps(AdvancementProgress progress) {
        ArrayList<ReloadedCriterionProgress> steps = new ArrayList<>();
        Iterable<String> remainingCriteriaIterable = progress.getRemainingCriteria();
        Iterable<String> completedCriteriaIterable = progress.getCompletedCriteria();
        if (Configuration.criteriasAlphabeticOrder) {
            ArrayList<String> unobtainedList = new ArrayList<>();
            ArrayList<String> obtainedList = new ArrayList<>();
            remainingCriteriaIterable.forEach(unobtainedList::add);
            completedCriteriaIterable.forEach(obtainedList::add);
            unobtainedList.sort(String::compareToIgnoreCase);
            obtainedList.sort(String::compareToIgnoreCase);
            remainingCriteriaIterable = unobtainedList;
            completedCriteriaIterable = obtainedList;
        }
        remainingCriteriaIterable.forEach(criterion -> steps.add(new ReloadedCriterionProgress(this.advancement, progress, criterion)));
        completedCriteriaIterable.forEach(criterion -> steps.add(new ReloadedCriterionProgress(this.advancement, progress, criterion)));
        this.steps = steps;
    }

    public void addChild(AdvancementReloadedWidget widget) {
        this.children.add(widget);
    }

    public void drawTooltip(GuiGraphicsExtractor context, int originX, int originY, float alpha, int x, int y) {
        ReloadedWidgetType advancementObtainedStatus3;
        ReloadedWidgetType advancementObtainedStatus2;
        ReloadedWidgetType advancementObtainedStatus;
        boolean bl = x + originX + this.x + this.width + 26 >= this.tab.getScreen().width;
        Component text = this.progress == null ? null : this.progress.getProgressText();
        int i = text == null ? 0 : this.client.font.width(text);
        Objects.requireNonNull(this.client.font);
        boolean bl2 = 113 - originY - this.y - 26 <= 6 + this.description.size() * 9;
        float f = this.progress == null ? 0.0f : this.progress.getPercent();
        int j = Mth.floor(f * this.width);
        if (f >= 1.0f) {
            j = this.width / 2;
            advancementObtainedStatus = ReloadedWidgetType.OBTAINED;
            advancementObtainedStatus2 = ReloadedWidgetType.OBTAINED;
            advancementObtainedStatus3 = ReloadedWidgetType.OBTAINED;
        } else if (j < 2) {
            j = this.width / 2;
            advancementObtainedStatus = ReloadedWidgetType.UNOBTAINED;
            advancementObtainedStatus2 = ReloadedWidgetType.UNOBTAINED;
            advancementObtainedStatus3 = ReloadedWidgetType.UNOBTAINED;
        } else if (j > this.width - 2) {
            j = this.width / 2;
            advancementObtainedStatus = ReloadedWidgetType.OBTAINED;
            advancementObtainedStatus2 = ReloadedWidgetType.OBTAINED;
            advancementObtainedStatus3 = ReloadedWidgetType.UNOBTAINED;
        } else {
            advancementObtainedStatus = ReloadedWidgetType.OBTAINED;
            advancementObtainedStatus2 = ReloadedWidgetType.UNOBTAINED;
            advancementObtainedStatus3 = ReloadedWidgetType.UNOBTAINED;
        }
        int k = this.width - j;
        int l = originY + this.y;
        int m = bl ? originX + this.x - this.width + 26 + 6 : originX + this.x;
        Objects.requireNonNull(this.client.font);
        int n = 32 + this.description.size() * 9;
        if (!this.description.isEmpty()) {
            if (bl2) {
                context.blitSprite(this.renderTypeGui, TITLE_BOX_TEXTURE, m, l + 26 - n, this.width, n);
            } else {
                context.blitSprite(this.renderTypeGui, TITLE_BOX_TEXTURE, m, l, this.width, n);
            }
        }
        boolean isDimmed = !this.isSearchQueryMatched();
        context.blitSprite(this.renderTypeGui, advancementObtainedStatus.boxSprite(isDimmed), 200, 26, 0, 0, m, l, j, 26);
        context.blitSprite(this.renderTypeGui, advancementObtainedStatus2.boxSprite(isDimmed), 200, 26, 200 - k, 0, m + j, l, k, 26);
        context.blitSprite(this.renderTypeGui, advancementObtainedStatus3.frameSprite(this.display.getType(), isDimmed), originX + this.x + 3, originY + this.y, 26, 26);
        if (bl) {
            context.text(this.client.font, this.title, m + 5, originY + this.y + 9, -1);
            if (text != null) {
                context.text(this.client.font, text, originX + this.x - i, originY + this.y + 9, -1);
            }
        } else {
            context.text(this.client.font, this.title, originX + this.x + 32, originY + this.y + 9, -1);
            if (text != null) {
                context.text(this.client.font, text, originX + this.x + this.width - i - 5, originY + this.y + 9, -1);
            }
        }
        if (bl2) {
            for (int o = 0; o < this.description.size(); ++o) {
                Objects.requireNonNull(this.client.font);
                context.text(this.client.font, this.description.get(o), m + 5, l + 26 - n + 7 + o * 9, -5592406, false);
            }
        } else {
            for (int o = 0; o < this.description.size(); ++o) {
                Objects.requireNonNull(this.client.font);
                context.text(this.client.font, this.description.get(o), m + 5, originY + this.y + 9 + 17 + o * 9, -5592406, false);
            }
        }
        context.fakeItem(this.display.getIcon().create(), originX + this.x + 8, originY + this.y + 5);
    }

    public boolean shouldRender(int originX, int originY, int mouseX, int mouseY) {
        if (this.display.isHidden() && (this.progress == null || !this.progress.isDone())) {
            return false;
        }
        return this.isMouseOn(originX, originY, mouseX, mouseY);
    }

    public boolean isMouseOn(int originX, int originY, double mouseX, double mouseY) {
        return (double)(originX + this.x) < mouseX && mouseX < (double)(originX + this.x + 26) && (double)(originY + this.y) < mouseY && mouseY < (double)(originY + this.y + 26);
    }

    public void addToTree() {
        if (this.parent == null && this.advancement.parent() != null) {
            this.parent = this.getParent(this.advancement);
            if (this.parent != null) {
                this.parent.addChild(this);
            }
        }
    }

    public int getY() {
        return this.y;
    }

    public int getX() {
        return this.x;
    }
}
