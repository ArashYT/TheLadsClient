package com.thelads.core.features.alwayson.advancementsreloaded.screens;

import com.thelads.core.features.alwayson.advancementsreloaded.ClickableRegion;
import com.thelads.core.features.alwayson.advancementsreloaded.ReloadedCriterionProgress;
import com.thelads.core.features.alwayson.advancementsreloaded.ReloadedDisplayInfo;
import com.thelads.core.features.alwayson.advancementsreloaded.TabPlacement;
import com.thelads.core.features.alwayson.advancementsreloaded.config.Configuration;
import com.thelads.core.features.alwayson.advancementsreloaded.config.gui.ConfigurationScreen;
import com.thelads.core.features.alwayson.advancementsreloaded.utils.Memory;
import com.thelads.core.features.alwayson.advancementsreloaded.utils.Utils;
import com.google.common.collect.Maps;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;

public class AdvancementReloadedScreen extends Screen implements ClientAdvancements.Listener {
    private static final Identifier criteriasSeparator = Identifier.parse("advancements_reloaded:textures/gui/inworld_right_separator.png");
    private static final Identifier GEAR_GUI_SPRITE_TEXURE = Identifier.parse("advancements_reloaded:gear");
    private static final Identifier SCROLLER_TEXTURE = Identifier.withDefaultNamespace("widget/scroller");
    private static final Identifier SCROLLER_BACKGROUND_TEXTURE = Identifier.withDefaultNamespace("widget/scroller_background");
    private static final Component SAD_LABEL_TEXT = Component.translatable("advancements.sad_label");
    private static final Component EMPTY_TEXT = Component.translatable("advancements.empty");
    private static final Component SEARCH_HINT_TEXT = Component.translatable("text.advancements_reloaded.search_hint").withStyle(ChatFormatting.DARK_GRAY);
    
    @Nullable
    private final Screen parent;
    private final ClientAdvancements advancementHandler;
    private final Map<AdvancementHolder, AdvancementReloadedTab> tabs = Maps.newLinkedHashMap();
    private Optional<AdvancementReloadedTab> selectedTab = Optional.empty();
    private AdvancementReloadedWidget selectedWidget;
    private List<ClickableRegion> clickableRegions;
    private int scrollOffset = 0;
    private int contentHeight = 0;
    private final RenderPipeline renderTypeGui = RenderPipelines.GUI_TEXTURED;
    private EditBox searchBox;
    private String searchText = "";
    private boolean isSearching = false;

    public AdvancementReloadedScreen(ClientAdvancements advancementHandler) {
        this(advancementHandler, null);
    }

    public AdvancementReloadedScreen(ClientAdvancements advancementHandler, @Nullable Screen parent) {
        super(Component.empty());
        this.advancementHandler = advancementHandler;
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.tabs.clear();
        this.selectedTab = Optional.empty();
        this.selectedWidget = Memory.getWidget();
        this.advancementHandler.setListener(this);
        if (this.selectedTab.isEmpty() && !this.tabs.isEmpty()) {
            AdvancementReloadedTab advancementTab = this.tabs.values().iterator().next();
            this.advancementHandler.setSelectedTab(advancementTab.getRoot().holder(), true);
        } else {
            this.selectedTab.ifPresent(tab -> this.advancementHandler.setSelectedTab(tab.getRoot().holder(), true));
        }
        this.initClickableRegions();
        this.initComponents();
    }

    private void initComponents() {
        this.initSettingsButton();
        this.initSearchBox();
    }

    private void initSearchBox() {
        this.searchBox = new EditBox(this.font, (this.width - 196) / 2, this.height - 16 - 5, 196, 16, SEARCH_HINT_TEXT);
        this.searchBox.setHint(SEARCH_HINT_TEXT);
        this.searchBox.setCanLoseFocus(true);
        this.searchBox.setVisible(true);
        this.searchBox.setTextColor(ARGB.opaque(ChatFormatting.WHITE.getColor()));
        this.searchBox.setBordered(true);
        this.searchBox.setMaxLength(32);
        this.searchBox.setValue(this.searchText);
        this.searchBox.setResponder(this::onSearchTextChanged);
        this.addRenderableWidget(this.searchBox);
    }

    private void initSettingsButton() {
        SpriteIconButton settingsIconButton = this.addRenderableWidget(SpriteIconButton.builder(Component.translatable("options.settings"), button -> this.minecraft.setScreen(ConfigurationScreen.screen(this)), true).width(20).sprite(GEAR_GUI_SPRITE_TEXURE, 14, 14).build());
        settingsIconButton.setPosition(this.width - 25, 5);
    }

    private void initClickableRegions() {
        this.clickableRegions = new ArrayList<>();
        this.clickableRegions.add(ClickableRegion.create("advancement_tree", 0, Configuration.headerHeight + 1, this.width - (this.hasVisibleSidebar() ? Configuration.criteriasWidth : 0), this.height - Configuration.headerHeight - Configuration.footerHeight));
        if (this.hasVisibleSidebar()) {
            this.clickableRegions.add(ClickableRegion.create("advancement_criterias", this.width - Configuration.criteriasWidth, Configuration.headerHeight + 1, Configuration.criteriasWidth - 6, this.height - Configuration.headerHeight - Configuration.footerHeight));
            this.clickableRegions.add(ClickableRegion.create("advancement_criterias_scrollbar", this.width - 6, Configuration.headerHeight + 1, 6, this.height - Configuration.footerHeight));
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void removed() {
        this.advancementHandler.setListener(null);
        ClientPacketListener clientPlayNetworkHandler = this.minecraft.getConnection();
        if (clientPlayNetworkHandler != null) {
            clientPlayNetworkHandler.send(ServerboundSeenAdvancementsPacket.closedScreen());
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (event.button() == 0) {
            ClickableRegion.foundRegions(this.clickableRegions, event.x(), event.y()).forEach(region -> region.setClicked(true));
            int j = Configuration.headerHeight;
            for (AdvancementReloadedTab advancementTab : this.tabs.values()) {
                AdvancementReloadedWidget clickedWidget;
                if (advancementTab == this.selectedTab.orElse(null) && (clickedWidget = advancementTab.clickOnWidget(0, j, event.x(), event.y())) != null) {
                    this.setSelectedWidget(clickedWidget);
                }
                if (!advancementTab.isClickOnTab(0, j, event.x(), event.y())) continue;
                this.advancementHandler.setSelectedTab(advancementTab.getRoot().holder(), true);
                break;
            }
            if (this.needScrollbarOnCriterias()) {
                ClickableRegion.findRegion(this.clickableRegions, region -> region.getName().equals("advancement_criterias_scrollbar") && region.isClicked()).ifPresent(region -> this.moveScrollbarTo(event.y()));
            }
        }
        return super.mouseClicked(event, false);
    }

    private void moveScrollbarTo(double mouseY) {
        int viewableHeight = this.height - Configuration.headerHeight - Configuration.footerHeight;
        int scrollbarStart = Configuration.headerHeight + 1;
        int scrollbarEnd = this.height - Configuration.footerHeight - 1;
        int scrollRange = this.contentHeight - viewableHeight;
        double relativeMouseY = mouseY - scrollbarStart;
        int newScrollOffset = (int)(relativeMouseY / (double)(scrollbarEnd - scrollbarStart) * scrollRange);
        newScrollOffset = Math.max(0, Math.min(newScrollOffset, scrollRange));
        this.setScrollOffset(newScrollOffset);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) {
            ClickableRegion.foundClickedRegions(this.clickableRegions).forEach(region -> region.setClicked(false));
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        ClickableRegion.foundClickedRegions(this.clickableRegions).forEach(region -> {
            switch (region.getName()) {
                case "advancement_tree": {
                    if (!this.selectedTab.isPresent()) break;
                    this.selectedTab.get().move(deltaX, deltaY);
                    break;
                }
                case "advancement_criterias": {
                    this.setScrollOffset(this.scrollOffset - (int)deltaY);
                    break;
                }
                case "advancement_criterias_scrollbar": {
                    this.moveScrollbarTo(event.y());
                    break;
                }
            }
        });
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        ClickableRegion.foundRegions(this.clickableRegions, mouseX, mouseY).forEach(region -> {
            switch (region.getName()) {
                case "advancement_tree": {
                    if (!this.selectedTab.isPresent()) break;
                    this.selectedTab.get().move(horizontalAmount * 16.0, verticalAmount * 16.0);
                    break;
                }
                case "advancement_criterias": 
                case "advancement_criterias_scrollbar": {
                    this.setScrollOffset(this.scrollOffset - (int)verticalAmount * 16);
                }
            }
        });
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.searchBox.isFocused() && this.minecraft.options.keyAdvancements.matches(event)) {
            return true;
        }
        if (this.minecraft.options.keyAdvancements.matches(event)) {
            this.minecraft.setScreen(this.parent);
            this.minecraft.mouseHandler.grabMouse();
            return true;
        }
        if (256 == event.key()) {
            if (this.searchBox.isFocused()) {
                this.searchBox.setFocused(false);
                return true;
            }
            if (this.isSearching) {
                this.searchText = "";
                this.searchBox.setValue("");
                this.isSearching = false;
                return true;
            }
            if (this.hasVisibleSidebar()) {
                this.setSelectedWidget(null);
                this.init();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int headerOffset = Configuration.headerHeight + 1;
        context.nextStratum();
        this.renderAdvancementTree(context, mouseX, mouseY, 0, headerOffset);
        context.nextStratum();
        this.renderAdvancementCriterias(context, 0, headerOffset);
        context.nextStratum();
        this.renderWidgetTooltip(context, mouseX, mouseY, 0, headerOffset);
        context.nextStratum();
        this.renderWindow(context, 0, headerOffset);
        context.nextStratum();
        this.renderRenderable(context, mouseX, mouseY, delta);
    }

    public void renderRenderable(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        for (Renderable renderable : ((com.thelads.core.mixin.ScreenAccessor) this).getRenderables()) {
            renderable.extractRenderState(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        switch (Configuration.backgroundStyle) {
            case TRANSPARENT: {
                super.extractBackground(context, mouseX, mouseY, delta);
                break;
            }
            case BLACK: {
                context.fill(0, 0, this.width, this.height, -16777216);
                break;
            }
            case ACHIEVEMENT: {
                this.selectedTab.ifPresent(tab -> {
                    Identifier textureResourceLocation = tab.getDisplay().getBackground().map(ClientAsset.ResourceTexture::texturePath).orElse(Utils.INTENTIONAL_MISSING_TEXTURE.texturePath());
                    context.blit(this.renderTypeGui, textureResourceLocation, 0, 0, 0.0f, 0.0f, this.width, this.height, 16, 16);
                });
                context.fill(0, 0, this.width, this.height, Mth.floor(178.5) << 24);
            }
        }
    }

    private void renderAdvancementTree(GuiGraphicsExtractor context, int mouseX, int mouseY, int x, int y) {
        if (this.selectedTab.isEmpty()) {
            int n = this.width / 2;
            int n2 = this.height / 2;
            Objects.requireNonNull(this.font);
            context.centeredText(this.font, EMPTY_TEXT, n, n2 - 9 * 2, -1);
            int n3 = this.width / 2;
            int n4 = this.height / 2;
            Objects.requireNonNull(this.font);
            context.centeredText(this.font, SAD_LABEL_TEXT, n3, n4 + 9 * 2, -1);
        } else {
            this.selectedTab.get().render(context, x, y);
        }
    }

    public void renderAdvancementCriterias(GuiGraphicsExtractor context, int x, int y) {
        if (!this.hasVisibleSidebar() || Configuration.criteriasWidth == 0) {
            return;
        }
        int paddingTop = Configuration.headerHeight + 6;
        int sidebarXOffset = this.width - Configuration.criteriasWidth + 8;
        int maxTextWidth = Configuration.criteriasWidth - (this.needScrollbarOnCriterias() ? 6 : 0) - 12;
        Component title = getSelectedWidget().getAdvancement().name().orElse(Component.literal(""));
        Component description = ((DisplayInfo)this.getSelectedWidget().getAdvancement().display().get()).getDescription();
        context.fill(this.width - Configuration.criteriasWidth, Configuration.headerHeight, this.width, this.height - Configuration.footerHeight, Mth.floor(127.5f) << 24);
        context.blit(this.renderTypeGui, criteriasSeparator, this.width - Configuration.criteriasWidth, Configuration.headerHeight + 1, 0.0f, 0.0f, 2, this.height - Configuration.headerHeight - Configuration.footerHeight - 2, 2, 32);
        Matrix3x2fStack postStack = context.pose();
        postStack.pushMatrix();
        postStack.translate(0.0f, -this.scrollOffset);
        this.contentHeight = 6;
        context.textWithWordWrap(this.font, title, sidebarXOffset, paddingTop, maxTextWidth, -1);
        Objects.requireNonNull(this.font);
        paddingTop += 9 * this.font.split(title, maxTextWidth).size() + 4;
        Objects.requireNonNull(this.font);
        this.contentHeight += 9 * this.font.split(title, maxTextWidth).size() + 4;
        if (Configuration.displayDescription && description != null) {
            context.textWithWordWrap(this.font, description, sidebarXOffset, paddingTop, maxTextWidth, ARGB.opaque(((DisplayInfo)this.getSelectedWidget().getAdvancement().display().get()).getType().getChatColor().getColor()));
            Objects.requireNonNull(this.font);
            paddingTop += 9 * this.font.split(description, maxTextWidth).size() + 4;
            Objects.requireNonNull(this.font);
            this.contentHeight += 9 * this.font.split(description, maxTextWidth).size() + 4;
        }
        context.horizontalLine(sidebarXOffset, this.width - 12, paddingTop, -6250336);
        paddingTop += 5;
        this.contentHeight += 5;
        for (ReloadedCriterionProgress step : this.getSelectedWidget().getSteps()) {
            Component stepTitle = step.getHumanCriterionName();
            int lineNeeded = this.font.split(stepTitle, maxTextWidth).size();
            context.textWithWordWrap(this.font, stepTitle, sidebarXOffset, paddingTop, maxTextWidth, step.getColor());
            Objects.requireNonNull(this.font);
            paddingTop += 9 * lineNeeded + 4;
            Objects.requireNonNull(this.font);
            this.contentHeight += 9 * lineNeeded + 4;
        }
        postStack.popMatrix();
        this.drawAdvancementCriteriaScrollbar(context, x, y);
    }

    private void drawAdvancementCriteriaScrollbar(GuiGraphicsExtractor context, int x, int y) {
        if (!this.needScrollbarOnCriterias()) {
            return;
        }
        int drawingHeight = this.height - Configuration.headerHeight - Configuration.footerHeight;
        context.blitSprite(this.renderTypeGui, SCROLLER_BACKGROUND_TEXTURE, this.width - 6, Configuration.headerHeight, 6, drawingHeight);
        int scrollBarHeight = (int)((double)(drawingHeight * drawingHeight) / (double)this.contentHeight);
        int scrollBarY = Configuration.headerHeight + (int)((double)(drawingHeight - scrollBarHeight) * ((double)this.scrollOffset / (double)(this.contentHeight - drawingHeight)));
        context.blitSprite(this.renderTypeGui, SCROLLER_TEXTURE, this.width - 6, scrollBarY, 6, scrollBarHeight);
    }

    private boolean needScrollbarOnCriterias() {
        return this.contentHeight > this.height - Configuration.headerHeight - Configuration.footerHeight;
    }

    public boolean hasVisibleSidebar() {
        return this.getSelectedWidget() != null && Configuration.displaySidebar;
    }

    public void renderWindow(GuiGraphicsExtractor context, int x, int y) {
        if (this.selectedTab.isPresent()) {
            ReloadedDisplayInfo display = this.selectedTab.get().getDisplay();
            Identifier textureResourceLocation = display.getBackground().map(ClientAsset.ResourceTexture::texturePath).orElse(TextureManager.INTENTIONAL_MISSING_TEXTURE);
            int headerDrawHeight = Configuration.headerHeight / 16 + 1;
            for (int m = 0; m <= this.width / 16; ++m) {
                for (int n = 0; n < headerDrawHeight; ++n) {
                    int textureHeight = 16;
                    if (n == headerDrawHeight - 1) {
                        textureHeight = Configuration.headerHeight % 16;
                    }
                    context.blit(this.renderTypeGui, textureResourceLocation, 16 * m, 16 * n, 0.0f, 0.0f, 16, textureHeight, 16, 16);
                }
            }
            context.fill(0, 0, this.width, Configuration.headerHeight, Mth.floor(76.5f) << 24);
            int footerDrawHeight = Configuration.footerHeight / 16 + 1;
            for (int m = 0; m <= this.width / 16; ++m) {
                for (int n = 0; n < footerDrawHeight; ++n) {
                    int textureHeight = 16;
                    if (n == footerDrawHeight - 1) {
                        textureHeight = Configuration.footerHeight % 16;
                    }
                    context.blit(this.renderTypeGui, textureResourceLocation, 16 * m, this.height - Configuration.footerHeight + 16 * n, 0.0f, 0.0f, 16, textureHeight, 16, 16);
                }
            }
            context.fill(0, this.height - Configuration.footerHeight, this.width, this.height, Mth.floor(76.5f) << 24);
            this.drawSeparators(context, 0.7f);
            Component component = display.getTitle();
            int n = this.width / 2;
            int n2 = (Configuration.headerHeight - 20) / 2;
            Objects.requireNonNull(this.font);
            context.centeredText(this.font, component, n, n2 - 9 / 2, 0xFFFFFF);
        }
        if (this.tabs.size() > 0) {
            for (AdvancementReloadedTab advancementTab : this.tabs.values()) {
                y = advancementTab.getTabPlacement() == TabPlacement.ABOVE ? Configuration.headerHeight + 1 : this.height - Configuration.footerHeight - 1;
                advancementTab.setPos(x + 4, y);
                advancementTab.drawBackground(context, advancementTab == this.selectedTab.orElse(null));
                advancementTab.drawIcon(context);
                if (advancementTab.isAnyWidgetMatchSearch()) continue;
                advancementTab.drawBackground(context, advancementTab == this.selectedTab.orElse(null), 0.6f);
            }
        }
    }

    private void drawSeparators(GuiGraphicsExtractor context, float alpha) {
        context.blit(this.renderTypeGui, Screen.INWORLD_HEADER_SEPARATOR, 0, Configuration.headerHeight - 1, 0.0f, 0.0f, this.width, 2, 32, 2);
        context.blit(this.renderTypeGui, Screen.INWORLD_FOOTER_SEPARATOR, 0, this.height - Configuration.footerHeight - 1, 0.0f, 0.0f, this.width, 2, 32, 2);
    }

    private void renderWidgetTooltip(GuiGraphicsExtractor context, int mouseX, int mouseY, int x, int y) {
        if (this.selectedTab.isPresent()) {
            context.pose().pushMatrix();
            context.pose().translate(x, y);
            this.selectedTab.get().drawWidgetTooltip(context, mouseX - x, mouseY - y, x, y);
            context.pose().popMatrix();
        }
        if (this.tabs.size() > 1) {
            for (AdvancementReloadedTab advancementTab : this.tabs.values()) {
                if (!advancementTab.isClickOnTab(x, y, mouseX, mouseY)) continue;
                context.setTooltipForNextFrame(this.font, advancementTab.getTitle(), mouseX, mouseY);
            }
        }
    }

    private void sortTabs() {
        ArrayList<AdvancementReloadedTab> sortedTabs = new ArrayList<>(this.tabs.values());
        switch (Configuration.tabsOrder) {
            case NONE: {
                break;
            }
            case ALPHABETIC: {
                sortedTabs.sort(Comparator.comparing(AdvancementReloadedTab::getDisplayName));
                break;
            }
            case CONFIGURED_ORDER: {
                sortedTabs.sort(this::compareTabsByConfiguredOrder);
            }
        }
        this.applyTabOrder(sortedTabs);
    }

    private int compareTabsByConfiguredOrder(AdvancementReloadedTab tab1, AdvancementReloadedTab tab2) {
        String tab1Id = tab1.getRoot().holder().id().toString();
        String tab2Id = tab2.getRoot().holder().id().toString();
        int tab1Position = Configuration.customTabsOrder.indexOf(tab1Id);
        int tab2Position = Configuration.customTabsOrder.indexOf(tab2Id);
        if (tab1Position != -1 && tab2Position != -1) {
            return Integer.compare(tab1Position, tab2Position);
        }
        if (tab1Position != -1) {
            return -1;
        }
        if (tab2Position != -1) {
            return 1;
        }
        return tab1.getDisplayName().compareTo(tab2.getDisplayName());
    }

    private void applyTabOrder(List<AdvancementReloadedTab> sortedTabs) {
        this.tabs.clear();
        for (int index = 0; index < sortedTabs.size(); ++index) {
            AdvancementReloadedTab tab = sortedTabs.get(index);
            tab.setIndex(index);
            tab.setTabPlacement(Configuration.aboveWidgetLimit > index ? TabPlacement.ABOVE : TabPlacement.BELOW);
            this.tabs.put(tab.getRoot().holder(), tab);
        }
    }

    public void onRootAdded(AdvancementNode root) {
        AdvancementReloadedTab advancementTab = AdvancementReloadedTab.create(this.minecraft, this, this.tabs.size(), root);
        if (advancementTab != null) {
            this.tabs.put(root.holder(), advancementTab);
            this.sortTabs();
        }
    }

    public void onRootRemoved(AdvancementNode root) {
    }

    public void onDependentAdded(AdvancementNode dependent) {
        AdvancementReloadedTab advancementTab = this.getTab(dependent);
        if (advancementTab != null) {
            advancementTab.addAdvancement(dependent);
        }
    }

    public void onDependentRemoved(AdvancementNode dependent) {
    }

    @Override
    public void onUpdateAdvancementProgress(AdvancementNode advancement, AdvancementProgress progress) {
        AdvancementReloadedWidget advancementWidget = this.getAdvancementWidget(advancement);
        if (advancementWidget != null) {
            advancementWidget.setProgress(progress);
        }
    }

    @Override
    public void onSelectedTabChanged(@Nullable AdvancementHolder advancement) {
        this.setSelectedTab(this.tabs.get(advancement));
    }

    public void setSelectedTab(AdvancementReloadedTab tab) {
        this.setSelectedTab(Optional.ofNullable(tab));
    }

    public void setSelectedTab(Optional<AdvancementReloadedTab> tab) {
        this.selectedTab = tab;
        this.initClickableRegions();
        tab.ifPresentOrElse(t -> Memory.setTabId(t.getRoot().holder().id().toString()), () -> Memory.setTabId(null));
    }

    public void setSelectedWidget(AdvancementReloadedWidget widget) {
        this.selectedWidget = widget;
        this.scrollOffset = 0;
        Memory.setWidget(widget);
        this.initClickableRegions();
    }

    private void setScrollOffset(int value) {
        if (!this.needScrollbarOnCriterias()) {
            return;
        }
        int max = this.contentHeight - (this.height - Configuration.headerHeight - Configuration.footerHeight);
        this.scrollOffset = Mth.clamp(value, 0, max);
    }

    @Nullable
    public AdvancementReloadedWidget getSelectedWidget() {
        return this.selectedWidget;
    }

    public void onClear() {
        this.tabs.clear();
        this.selectedTab = Optional.empty();
    }

    @Nullable
    public AdvancementReloadedWidget getAdvancementWidget(AdvancementNode advancement) {
        AdvancementReloadedTab advancementTab = this.getTab(advancement);
        return advancementTab == null ? null : advancementTab.getWidget(advancement.holder());
    }

    @Nullable
    private AdvancementReloadedTab getTab(AdvancementNode advancement) {
        AdvancementNode placedAdvancement = advancement.root();
        return this.tabs.get(placedAdvancement.holder());
    }

    @Override
    public void onAddAdvancementRoot(AdvancementNode advancement) {
        AdvancementReloadedTab advancementTab = AdvancementReloadedTab.create(this.minecraft, this, this.tabs.size(), advancement);
        if (advancementTab != null) {
            this.tabs.put(advancement.holder(), advancementTab);
            this.sortTabs();
            String storedTabId = Memory.getTabId();
            if (storedTabId != null && advancement.holder().id().toString().equals(storedTabId)) {
                this.advancementHandler.setSelectedTab(advancement.holder(), true);
                Memory.clearTabId();
            }
        }
    }

    @Override
    public void onRemoveAdvancementRoot(AdvancementNode advancement) {
    }

    @Override
    public void onAddAdvancementTask(AdvancementNode advancement) {
        AdvancementReloadedTab advancementTab = this.getTab(advancement);
        if (advancementTab != null) {
            advancementTab.addAdvancement(advancement);
        }
    }

    @Override
    public void onRemoveAdvancementTask(AdvancementNode advancement) {
    }

    @Override
    public void onAdvancementsCleared() {
        this.selectedTab.ifPresent(tab -> Memory.setTabId(tab.getRoot().holder().id().toString()));
        this.tabs.clear();
        this.selectedTab = Optional.empty();
    }

    private void onSearchTextChanged(String text) {
        this.searchText = text;
        this.isSearching = !text.isEmpty();
        for (AdvancementReloadedTab tab : this.tabs.values()) {
            tab.updateDoesWidgetMatchSearch();
        }
        this.initClickableRegions();
    }

    public boolean isSearching() {
        return this.isSearching;
    }

    public String getSearchText() {
        return this.searchText;
    }
}
