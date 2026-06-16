package com.thelads.core.features.alwayson.advancementsreloaded.screens;

import com.thelads.core.features.alwayson.advancementsreloaded.ReloadedDisplayInfo;
import com.thelads.core.features.alwayson.advancementsreloaded.ReloadedWidgetType;
import com.thelads.core.features.alwayson.advancementsreloaded.TabPlacement;
import com.thelads.core.features.alwayson.advancementsreloaded.config.Configuration;
import com.google.common.collect.Maps;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.Map;
import java.util.Optional;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStackTemplate;
import org.jetbrains.annotations.Nullable;

public class AdvancementReloadedTab {
    private final Minecraft client;
    private final AdvancementReloadedScreen screen;
    private final AdvancementNode root;
    private final ReloadedDisplayInfo display;
    private final ItemStackTemplate icon;
    private final Component title;
    private final AdvancementReloadedWidget rootWidget;
    private final Map<AdvancementHolder, AdvancementReloadedWidget> widgets = Maps.newLinkedHashMap();
    private boolean doesAnyWidgetMatchSearch = true;
    private TabPlacement tabPlacement;
    private int index;
    private double originX;
    private double originY;
    private int minPanX = Integer.MAX_VALUE;
    private int minPanY = Integer.MAX_VALUE;
    private int maxPanX = Integer.MIN_VALUE;
    private int maxPanY = Integer.MIN_VALUE;
    private float alpha;
    private boolean initialized;
    private int tab_x;
    private int tab_y;
    private final RenderPipeline renderTypeGui = RenderPipelines.GUI_TEXTURED;

    public AdvancementReloadedTab(Minecraft client, AdvancementReloadedScreen screen, TabPlacement type, int index, AdvancementNode root, ReloadedDisplayInfo display) {
        this.client = client;
        this.screen = screen;
        this.tabPlacement = type;
        this.index = index;
        this.root = root;
        this.display = display;
        this.icon = display.getIcon();
        this.title = display.getTitle();
        this.rootWidget = new AdvancementReloadedWidget(this, client, root, display);
        this.addWidget(this.rootWidget, root.holder());
    }

    public TabPlacement getTabPlacement() {
        return this.tabPlacement;
    }

    public int getIndex() {
        return this.index;
    }

    public AdvancementNode getRoot() {
        return this.root;
    }

    public Component getTitle() {
        return this.title;
    }

    public String getDisplayName() {
        return this.getRoot().advancement().name().orElse(Component.literal(this.getRoot().toString())).getString();
    }

    public ReloadedDisplayInfo getDisplay() {
        return this.display;
    }

    public void setPos(int x, int y) {
        this.tab_x = x + this.tabPlacement.getTabX(this.index);
        this.tab_y = y + this.tabPlacement.getTabY(this.index);
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public void setTabPlacement(TabPlacement type) {
        this.tabPlacement = type;
    }

    public void drawBackground(GuiGraphicsExtractor context, boolean selected) {
        this.drawBackground(context, selected, 1.0f);
    }

    public void drawBackground(GuiGraphicsExtractor context, boolean selected, float alpha) {
        ReloadedWidgetType type = selected ? ReloadedWidgetType.OBTAINED : ReloadedWidgetType.UNOBTAINED;
        context.blitSprite(this.renderTypeGui, type.frameSprite(AdvancementType.TASK, !this.isAnyWidgetMatchSearch()), this.tab_x, this.tab_y, this.tabPlacement.getWidth(), this.tabPlacement.getHeight(), alpha);
    }

    public void drawIcon(GuiGraphicsExtractor context) {
        context.fakeItem(this.icon.create(), this.tab_x + this.tabPlacement.getTopMargin(), this.tab_y + this.tabPlacement.getLeftMargin());
    }

    public int getWidth() {
        if (this.screen.hasVisibleSidebar()) {
            return this.screen.width - Configuration.criteriasWidth;
        }
        return this.screen.width;
    }

    public int getHeight() {
        return this.screen.height - Configuration.headerHeight - Configuration.footerHeight - 2;
    }

    public boolean isAnyWidgetMatchSearch() {
        return this.doesAnyWidgetMatchSearch;
    }

    public void updateDoesWidgetMatchSearch() {
        for (AdvancementReloadedWidget widget : this.widgets.values()) {
            if (!widget.isSearchQueryMatched()) continue;
            this.doesAnyWidgetMatchSearch = true;
            return;
        }
        this.doesAnyWidgetMatchSearch = false;
    }

    public void render(GuiGraphicsExtractor context, int x, int y) {
        if (!this.initialized) {
            this.originX = this.getWidth() / 2.0 - (this.maxPanX + this.minPanX) / 2.0;
            this.originY = this.screen.height / 2.0 - Configuration.headerHeight - 1 - (this.maxPanY + this.minPanY) / 2.0;
            this.initialized = true;
        }
        context.enableScissor(x, y, x + this.getWidth(), y + this.getHeight());
        context.pose().pushMatrix();
        context.pose().translate(x, y);
        int i = Mth.floor(this.originX);
        int j = Mth.floor(this.originY);
        this.rootWidget.renderLines(context, i, j, true);
        this.rootWidget.renderLines(context, i, j, false);
        this.rootWidget.renderWidgets(context, i, j);
        context.pose().popMatrix();
        context.disableScissor();
    }

    public void drawWidgetTooltip(GuiGraphicsExtractor context, int mouseX, int mouseY, int x, int y) {
        context.fill(0, 0, this.getWidth(), this.getHeight(), Mth.floor(this.alpha * 255.0f) << 24);
        boolean rendered = false;
        int i = Mth.floor(this.originX);
        int j = Mth.floor(this.originY);
        if (mouseX > 0 && mouseX < this.getWidth() && mouseY > 0 && mouseY < this.getHeight()) {
            for (AdvancementReloadedWidget advancementWidget : this.widgets.values()) {
                if (!advancementWidget.shouldRender(i, j, mouseX, mouseY)) continue;
                rendered = true;
                advancementWidget.drawTooltip(context, i, j, this.alpha, x, y);
                break;
            }
        }
        this.alpha = rendered ? Mth.clamp(this.alpha + 0.02f, 0.0f, 0.3f) : Mth.clamp(this.alpha - 0.04f, 0.0f, 1.0f);
    }

    public boolean isClickOnTab(int screenX, int screenY, double mouseX, double mouseY) {
        return mouseX > (double)this.tab_x && mouseX < (double)(this.tab_x + this.tabPlacement.getWidth()) && mouseY > (double)this.tab_y && mouseY < (double)(this.tab_y + this.tabPlacement.getHeight());
    }

    @Nullable
    public AdvancementReloadedWidget clickOnWidget(int screenX, int screenY, double mouseX, double mouseY) {
        int flooredOriginX = Mth.floor(this.originX);
        int flooredOriginY = Mth.floor(this.originY + Configuration.headerHeight - 1.0);
        if (mouseX < (double)screenX || mouseX > (double)this.getWidth() || mouseY < (double)screenY || mouseY > (double)(this.screen.height - Configuration.footerHeight - 1)) {
            return null;
        }
        for (AdvancementReloadedWidget advancementWidget : this.widgets.values()) {
            if (!advancementWidget.isMouseOn(flooredOriginX, flooredOriginY, mouseX, mouseY) || !advancementWidget.shouldRender(flooredOriginX, flooredOriginY, (int)mouseX, (int)mouseY)) continue;
            return advancementWidget;
        }
        return null;
    }

    @Nullable
    public static AdvancementReloadedTab create(Minecraft client, AdvancementReloadedScreen screen, int index, AdvancementNode root) {
        Optional<DisplayInfo> optional = root.advancement().display();
        if (optional.isEmpty()) {
            return null;
        }
        for (TabPlacement advancementTabType : TabPlacement.values()) {
            if (index < advancementTabType.getTabLimit()) {
                return new AdvancementReloadedTab(client, screen, advancementTabType, index, root, ReloadedDisplayInfo.cast(optional.get()));
            }
            index -= advancementTabType.getTabLimit();
        }
        return null;
    }

    public void move(double offsetX, double offsetY) {
        int maxHeight;
        int maxWidth = this.getWidth();
        if (this.maxPanX - this.minPanX > maxWidth - 8) {
            this.originX = Mth.clamp(this.originX + offsetX, -(this.maxPanX - maxWidth + 8), 8.0);
        }
        if (this.maxPanY - this.minPanY > (maxHeight = this.getHeight()) - 16) {
            this.originY = Mth.clamp(this.originY + offsetY, -(this.maxPanY - maxHeight + 16), 16.0);
        }
    }

    public void addAdvancement(AdvancementNode advancement) {
        Optional<DisplayInfo> optional = advancement.advancement().display();
        if (optional.isPresent()) {
            AdvancementReloadedWidget advancementWidget = new AdvancementReloadedWidget(this, this.client, advancement, ReloadedDisplayInfo.cast(optional.get()));
            this.addWidget(advancementWidget, advancement.holder());
        }
    }

    private void addWidget(AdvancementReloadedWidget widget, AdvancementHolder advancement) {
        this.widgets.put(advancement, widget);
        int widgetXPosition = widget.getX();
        int widgetXPositionOffset = widgetXPosition + 28;
        int widgetYPosition = widget.getY();
        int widgetYPositionOffset = widgetYPosition + 27;
        this.minPanX = Math.min(this.minPanX, widgetXPosition);
        this.maxPanX = Math.max(this.maxPanX, widgetXPositionOffset);
        this.minPanY = Math.min(this.minPanY, widgetYPosition);
        this.maxPanY = Math.max(this.maxPanY, widgetYPositionOffset);
        for (AdvancementReloadedWidget advancementWidget : this.widgets.values()) {
            advancementWidget.addToTree();
        }
    }

    @Nullable
    public AdvancementReloadedWidget getWidget(AdvancementHolder advancement) {
        return this.widgets.get(advancement);
    }

    public AdvancementReloadedScreen getScreen() {
        return this.screen;
    }
}
