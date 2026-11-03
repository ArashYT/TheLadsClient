package com.thelads.core.v1_21_11.mixin;

import com.thelads.core.client.LadsNetwork;
import com.thelads.core.client.title.TitleNoir;
import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.modules.TitleScreenModule;
import com.thelads.core.v1_21_11.adapter.GuiGraphicsLadsAdapter;
import com.thelads.core.v1_21_11.gui.AccountSwitcherScreen12111;
import com.thelads.core.v1_21_11.gui.CompactButton12111;
import com.thelads.core.v1_21_11.gui.LadsSettingsScreen12111;
import com.thelads.core.v1_21_11.gui.TitleExtrasScreen12111;
import com.thelads.core.v1_21_11.gui.TitleWidgetRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    @Unique private List<AbstractWidget> ladsTitleWidgets = new ArrayList<>();
    @Unique private TitleNoir.Layout ladsNoirLayout;
    @Unique private boolean ladsLegacy;
    @Unique private Button ladsLayoutToggle;
    @Unique private TitleScreenTheme.Layout ladsTitleLayout;
    @Unique private boolean ladsCustomTitle;
    @Unique private int ladsLayoutWidth;
    @Unique private int ladsLayoutHeight;
    @Unique private Button ladsSettingsButton;
    @Unique private Button ladsAccountsButton;
    @Unique private Button ladsNetworkButton;
    @Unique private Button ladsSwitchButton;
    @Unique private Button ladsMoreButton;
    @Unique private List<AbstractWidget> ladsExtraWidgets = new ArrayList<>();
    @Unique private long ladsLastFrameNanos;
    @Unique private double ladsAnimationSeconds;

    protected TitleScreenMixin() {
        super(Component.empty());
    }

    @Inject(method = "init()V", at = @At("TAIL"), require = 1)
    private void ladsArrangeTitle(CallbackInfo ci) {
        com.thelads.core.v1_21_11.feature.SharedContentProbe.runOnce();
        com.thelads.core.mods.ModInventoryModel.requestAtFirstTitleScreen();
        com.thelads.core.mods.ModInventoryModel.logAtFirstTitleScreen();
        for (AbstractWidget widget : ladsTitleWidgets) TitleWidgetRegistry.unregister(widget);
        ladsTitleWidgets = new ArrayList<>();
        ladsTitleLayout = null;
        ladsExtraWidgets = new ArrayList<>();
        ladsCustomTitle = com.thelads.core.v1_21_11.feature.NativeFeatures.enabled("TitleScreen");
        if (!ladsCustomTitle) return;
        ladsSettingsButton = addRenderableWidget(Button.builder(Component.literal("Lads Mods"),
            button -> minecraft.setScreen(new LadsSettingsScreen12111(this))).bounds(0, 0, 1, 1).build());
        ladsAccountsButton = addRenderableWidget(Button.builder(Component.literal("Accounts"),
            button -> minecraft.setScreen(new AccountSwitcherScreen12111(this))).bounds(0, 0, 1, 1).build());
        ladsMoreButton = addRenderableWidget(Button.builder(Component.literal("More..."),
            button -> minecraft.setScreen(new TitleExtrasScreen12111(this, ladsExtraWidgets))).bounds(0, 0, 1, 1).build());
        ladsNetworkButton = !LadsNetwork.titleButton() ? null : addRenderableWidget(Button.builder(Component.literal(LadsNetwork.TITLE_LABEL),
            button -> ConnectScreen.startConnecting(new JoinMultiplayerScreen(this), minecraft, ServerAddress.parseString(LadsNetwork.ADDRESS),
                new ServerData(LadsNetwork.NAME, LadsNetwork.ADDRESS, ServerData.Type.OTHER), false, null)).tooltip(Tooltip.create(Component.literal(LadsNetwork.TOOLTIP))).bounds(0, 0, 1, 1).build());
        // Beside the account name (placed each frame after it)
        ladsSwitchButton = addRenderableWidget(new CompactButton12111(0, 0, 1, 1, Component.literal(TitleScreenTheme.SWITCH),
            () -> "switch", button -> minecraft.setScreen(new AccountSwitcherScreen12111(this))));
        ladsSwitchButton.setTooltip(Tooltip.create(Component.literal("Switch account")));
        ladsSwitchButton.setTabOrderGroup(200);
        ladsLayoutToggle = addRenderableWidget(new CompactButton12111(0, 6, 20, 20, Component.literal(TitleScreenTheme.layoutToggleLabel()),
            () -> "layout", button -> TitleScreenModule.toggleLayout()));
        ladsLayoutToggle.setTabOrderGroup(203);

        // Fabric screen events and other init mixins may still add or move widgets after this callback.
        // Leave their native geometry intact until the first draw has the complete widget set.
        ladsLastFrameNanos = 0;
        com.thelads.core.config.BuiltInIntegrations.verifyLoadedAdapters();
        com.thelads.core.config.IntegrationRuntimeProbe.run();
    }

    /** Legacy: the GUI's own coordinates; the new layout picks its own canvas. */
    @Unique
    private int ladsTargetWidth() {
        if (ladsLegacy || minecraft.getWindow().getWidth() <= 0) return width;
        return (int) Math.round(minecraft.getWindow().getWidth() / TitleNoir.windowPerCanvasPixel(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight()));
    }

    @Unique
    private int ladsTargetHeight() {
        if (ladsLegacy || minecraft.getWindow().getHeight() <= 0) return height;
        return (int) Math.round(minecraft.getWindow().getHeight() / TitleNoir.windowPerCanvasPixel(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight()));
    }

    @Unique
    private float ladsMatrixScale() {
        return ladsLegacy ? 1.0f : (float) height / (float) Math.max(1, ladsTargetHeight());
    }

    @Unique
    private void ladsEnsureTitleLayout() {
        boolean legacy = TitleScreenModule.legacyLayout(), switched = legacy != ladsLegacy;
        ladsLegacy = legacy;
        int targetW = ladsTargetWidth(), targetH = ladsTargetHeight();
        boolean changed = ladsTitleLayout == null || switched || ladsLayoutWidth != targetW || ladsLayoutHeight != targetH;
        if (!changed) {
            int index = 0;
            for (GuiEventListener child : children()) {
                if (!(child instanceof AbstractWidget widget) || ladsOverlayTool(widget) || widget == ladsLayoutToggle || widget == ladsSwitchButton) continue;
                if (index >= ladsTitleWidgets.size() || ladsTitleWidgets.get(index) != widget) {
                    changed = true;
                    break;
                }
                index++;
            }
            changed |= index != ladsTitleWidgets.size();
        }
        if (!changed) return;
        for (AbstractWidget widget : ladsTitleWidgets) TitleWidgetRegistry.unregister(widget);
        ladsTitleWidgets = new ArrayList<>();
        // Reuse every original widget, including restricted multiplayer, demo and late mod actions.
        for (GuiEventListener child : children()) {
            if (child instanceof AbstractWidget widget && !ladsOverlayTool(widget) && widget != ladsLayoutToggle && widget != ladsSwitchButton) ladsTitleWidgets.add(widget);
        }
        // Keep the home screen focused. All secondary native/mod actions remain in More.
        for (AbstractWidget widget : List.copyOf(ladsTitleWidgets)) {
            String key = ladsMessageKey(widget);
            boolean main = widget == ladsSettingsButton || widget == ladsMoreButton || widget == ladsNetworkButton
                || key.equals("menu.singleplayer") || key.equals("menu.playdemo")
                || key.equals("menu.multiplayer") || key.equals("menu.options") || key.equals("menu.quit");
            if (!main) {
                ladsTitleWidgets.remove(widget);
                removeWidget(widget);
                // The Lads theme draws its own artwork: FancyMenu's widgetified vanilla logo, splash, branding and Realms icons go.
                if (ladsVanillaArtwork(widget)) { widget.visible = false; continue; }
                if (widget.getClass().getName().startsWith("gg.essential.")) {
                    widget.visible = false;
                } else if (!ladsExtraWidgets.contains(widget)) ladsExtraWidgets.add(widget);
            }
        }
        ladsTitleWidgets.sort(Comparator.comparingInt(this::ladsButtonOrder));
        ladsTitleLayout = TitleScreenTheme.layout(targetW, targetH, ladsTitleWidgets.size(), false);
        ladsNoirLayout = legacy ? null : TitleNoir.layout(targetW, targetH, ladsTitleWidgets.size(), ladsTitleWidgets.indexOf(ladsNetworkButton));
        ladsLayoutWidth = targetW;
        ladsLayoutHeight = targetH;
        Component toggleLabel = Component.literal(TitleScreenTheme.layoutToggleLabel());
        int toggleWidth = TitleScreenTheme.layoutToggleWidth(targetW, Math.max(font.width(TitleScreenTheme.NEW_LAYOUT), font.width(TitleScreenTheme.LEGACY_LAYOUT)));
        ladsLayoutToggle.setMessage(toggleLabel);
        ladsLayoutToggle.setRectangle(toggleWidth, 20, targetW - 6 - toggleWidth, 6);
        ladsLayoutToggle.setTooltip(toggleWidth <= 20 ? Tooltip.create(toggleLabel) : null);
        boolean primaryAssigned = false;
        for (AbstractWidget widget : ladsTitleWidgets) removeWidget(widget);
        for (int i = 0; i < ladsTitleWidgets.size(); i++) {
            AbstractWidget widget = ladsTitleWidgets.get(i);
            TitleScreenTheme.Rect rect = (ladsNoirLayout != null ? ladsNoirLayout.buttons() : ladsTitleLayout.buttons()).get(i);
            widget.setX(rect.x());
            widget.setY(rect.y());
            widget.setWidth(rect.width());
            widget.setHeight(rect.height());
            widget.setTabOrderGroup(i);
            addRenderableWidget(widget);
            boolean primary = !primaryAssigned && "menu.singleplayer".equals(ladsMessageKey(widget));
            primaryAssigned |= primary;
            TitleWidgetRegistry.register(this, widget, ladsButtonIcon(widget), primary);
        }
        ladsLastFrameNanos = 0;
        LoggerFactory.getLogger("TheLadsCore-1.21.11").info(
            "custom title initialized for Minecraft 1.21.11: {} native widgets", ladsTitleWidgets.size());
    }

    @Unique
    private static String ladsMessageKey(AbstractWidget widget) {
        Component message = widget.getMessage();
        return message != null && message.getContents() instanceof TranslatableContents contents ? contents.getKey() : "";
    }

    @Unique
    private int ladsButtonOrder(AbstractWidget widget) {
        if (widget == ladsNetworkButton) return 1; // after Multiplayer: the stable sort keeps the vanilla button (added first) ahead
        if (widget == ladsSettingsButton) return 3;
        if (widget == ladsMoreButton) return 7;
        if (widget == ladsAccountsButton) return 4;
        if (ladsIsModMenuWidget(widget)) return 3;
        return switch (ladsMessageKey(widget)) {
            case "menu.singleplayer", "menu.playdemo" -> 0;
            case "menu.multiplayer" -> 1;
            case "menu.online" -> 2;
            case "modmenu.title", "fml.menu.mods" -> 3;
            case "menu.options" -> 5;
            case "options.language" -> 6;
            case "options.accessibility", "accessibility.onboarding.accessibility.button" -> 7;
            case "menu.quit" -> 8;
            default -> 9;
        };
    }

    @Unique
    private String ladsButtonIcon(AbstractWidget widget) {
        if (widget == ladsSettingsButton) return "mods";
        if (widget == ladsAccountsButton) return "user";
        if (widget == ladsNetworkButton) return "server";
        if (ladsIsModMenuWidget(widget)) return "mods";
        return switch (ladsMessageKey(widget)) {
            case "menu.singleplayer", "menu.playdemo" -> "play";
            case "menu.multiplayer" -> "server";
            case "menu.online" -> "realms";
            case "modmenu.title", "fml.menu.mods" -> "mods";
            case "menu.options" -> "settings";
            case "options.language" -> "language";
            case "options.accessibility", "accessibility.onboarding.accessibility.button" -> "access";
            case "menu.quit" -> "quit";
            default -> "more";
        };
    }

    /**
     * FancyMenu customization-overlay tools (Drippy's edit tab, a player's FancyMenu layout elements) stay where their mods
     * keep them: Drippy re-adds its tab on every frame it is missing, which made the layout rebuild every frame (1.21.11).
     */
    @Unique
    private static boolean ladsOverlayTool(AbstractWidget widget) {
        return widget.getClass().getName().startsWith("de.keksuccino.") && !ladsVanillaArtwork(widget);
    }

    /** FancyMenu's widgetified vanilla title artwork (logo, splash, branding, Realms icons): not actions, hidden with the Lads theme. */
    @Unique
    private static boolean ladsVanillaArtwork(AbstractWidget widget) {
        return widget.getClass().getName().equals("de.keksuccino.fancymenu.util.rendering.ui.widget.RendererWidget");
    }

    @Unique
    private static boolean ladsIsModMenuWidget(AbstractWidget widget) {
        String type = widget.getClass().getName();
        return type.equals("com.terraformersmc.modmenu.gui.widget.ModMenuButtonWidget")
            || type.equals("com.terraformersmc.modmenu.gui.widget.SmallModMenuButtonWidget");
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
        at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsRenderTitle(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!ladsCustomTitle) return;
        long now = System.nanoTime();
        ladsEnsureTitleLayout();
        float elapsed = ladsLastFrameNanos == 0 ? 0 : (float) Math.clamp((now - ladsLastFrameNanos) / 1.0e9, 0.0, 0.1);
        ladsLastFrameNanos = now;
        double panoramaSpeed = minecraft.options.panoramaSpeed().get();
        boolean reducedMotion = panoramaSpeed <= 0 || minecraft.options.screenEffectScale().get() <= 0;
        if (!reducedMotion) ladsAnimationSeconds += elapsed * panoramaSpeed;
        var adapter = new GuiGraphicsLadsAdapter(graphics, font);
        String version = "1.21.11" + (minecraft.isDemo() ? " Demo" : "");
        float matrixScale = ladsMatrixScale();
        TitleScreenTheme.Rect switchBox;
        if (ladsNoirLayout != null) {
            renderPanorama(graphics, partialTick);
            graphics.pose().pushMatrix();
            graphics.pose().scale(matrixScale, matrixScale);
            switchBox = TitleNoir.renderBackdrop(adapter, ladsNoirLayout, minecraft.getUser().getName(), version, true);
        } else {
            switchBox = TitleScreenTheme.renderBackground(adapter, ladsTitleLayout, minecraft.getUser().getName(), version, false, ladsAnimationSeconds, true);
        }
        if (switchBox != null) ladsSwitchButton.setRectangle(switchBox.width(), switchBox.height(), switchBox.x(), switchBox.y());
        TitleWidgetRegistry.beginFrame(this, adapter, elapsed, reducedMotion);
        TitleNoir.beginFrame(ladsNoirLayout);
        try {
            // Calls Screen, not TitleScreen: only native widgets and extra renderables, never vanilla artwork.
            super.render(graphics, Math.round(mouseX / matrixScale), Math.round(mouseY / matrixScale), partialTick);
        } finally {
            TitleNoir.endFrame();
            TitleWidgetRegistry.endFrame();
        }
        if (ladsNoirLayout != null) graphics.pose().popMatrix();
        ci.cancel();
    }

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
        at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsClickVisibleTitle(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (ladsCustomTitle) {
            ladsEnsureTitleLayout();
            // Vanilla also forwards clicks to its separate, now hidden Realms notification overlay.
            float matrixScale = ladsMatrixScale();
            cir.setReturnValue(super.mouseClicked(matrixScale != 1.0f
                ? new MouseButtonEvent(event.x() / matrixScale, event.y() / matrixScale, event.buttonInfo()) : event, doubleClick));
        }
    }
}
