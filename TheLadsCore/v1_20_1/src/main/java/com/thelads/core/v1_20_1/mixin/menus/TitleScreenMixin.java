package com.thelads.core.v1_20_1.mixin.menus;

import com.thelads.core.client.LadsNetwork;
import com.thelads.core.client.title.TitleNoir;
import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.modules.TitleScreenModule;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import com.thelads.core.v1_20_1.cosmetics.anim.CosmeticsScreen1201;
import com.thelads.core.v1_20_1.feature.NativeFeatures1201;
import com.thelads.core.v1_20_1.gui.AccountSwitcherScreen1201;
import com.thelads.core.v1_20_1.gui.CompactButton1201;
import com.thelads.core.v1_20_1.gui.LadsSettingsScreen1201;
import com.thelads.core.v1_20_1.gui.QuitConfirmScreen1201;
import com.thelads.core.v1_20_1.gui.ReplayScreens1201;
import com.thelads.core.v1_20_1.gui.TitleExtrasScreen1201;
import com.thelads.core.v1_20_1.gui.TitleWidgetRegistry1201;
import com.thelads.core.v1_20_1.gui.VersionSwitchScreen1201;
import com.thelads.core.v1_20_1.gui.Widgets1201;
import com.thelads.core.v1_20_1.social.FriendsScreen1201;
import com.thelads.core.v1_20_1.social.SocialBridge1201;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.LanguageSelectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Lads title screen on 1.20.1, the same layout as 26.3: vanilla's widgets (plus whatever Forge, Mod Menu, and
 * other mods add) are collected on the first frame after init, stripped of their look and arranged by TitleScreenTheme in a
 * fixed 3x GUI scale; secondary ones go to the More screen so every mod entry point stays reachable.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    @Shadow @Final private PanoramaRenderer panorama;
    @Unique private List<AbstractWidget> ladsTitleWidgets = new ArrayList<>();
    @Unique private TitleNoir.Layout ladsNoirLayout;
    @Unique private List<net.minecraft.util.FormattedCharSequence> ladsTooltip;
    @Unique private net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner ladsTooltipPositioner;
    @Unique private boolean ladsLegacy;
    @Unique private CompactButton1201 ladsLayoutToggle;
    @Unique private TitleScreenTheme.Layout ladsTitleLayout;
    @Unique private boolean ladsCustomTitle;
    @Unique private int ladsLayoutWidth;
    @Unique private int ladsLayoutHeight;
    @Unique private Button ladsSettingsButton;
    @Unique private Button ladsCosmeticsButton;
    @Unique private Button ladsFriendsButton;
    @Unique private Button ladsSwitchButton;
    @Unique private CompactButton1201 ladsFullscreenButton;
    @Unique private Button ladsLanguageButton;
    @Unique private Button ladsReplaysButton;
    @Unique private Button ladsMoreButton;
    @Unique private Button ladsQuitButton;
    @Unique private Button ladsSwitchVersionButton;
    @Unique private Button ladsNetworkButton;
    @Unique private List<AbstractWidget> ladsExtraWidgets = new ArrayList<>();
    @Unique private long ladsLastFrameNanos;
    @Unique private double ladsAnimationSeconds;

    protected TitleScreenMixin() {
        super(Component.empty());
    }

    @Inject(method = "init()V", at = @At("TAIL"), require = 1)
    private void ladsArrangeTitle(CallbackInfo ci) {
        com.thelads.core.mods.ModInventoryModel.requestAtFirstTitleScreen();
        com.thelads.core.mods.ModInventoryModel.logAtFirstTitleScreen();
        for (AbstractWidget widget : ladsTitleWidgets) TitleWidgetRegistry1201.unregister(widget);
        ladsTitleWidgets = new ArrayList<>();
        ladsTitleLayout = null;
        ladsExtraWidgets = new ArrayList<>();
        ladsReplaysButton = null;
        ladsCosmeticsButton = null;
        ladsFriendsButton = null;
        ladsCustomTitle = NativeFeatures1201.enabled("TitleScreen");
        if (!ladsCustomTitle) return;

        ladsSettingsButton = addRenderableWidget(Button.builder(Component.literal("Lads Mods"),
            button -> minecraft.setScreen(new LadsSettingsScreen1201(this))).bounds(0, 0, 1, 1).build());
        ladsCosmeticsButton = addRenderableWidget(Button.builder(Component.literal("Cosmetics"),
            button -> minecraft.setScreen(new CosmeticsScreen1201(this))).bounds(0, 0, 1, 1).build());
        ladsFriendsButton = addRenderableWidget(Button.builder(SocialBridge1201.friendsButtonLabel(),
            button -> minecraft.setScreen(new FriendsScreen1201(this))).bounds(0, 0, 1, 1).build());
        ladsMoreButton = addRenderableWidget(Button.builder(Component.literal("More..."),
            button -> minecraft.setScreen(new TitleExtrasScreen1201(this, ladsExtraWidgets))).bounds(0, 0, 1, 1).build());
        // Only where a replay mod is installed (Replay Mod on 1.20.1): a button that opens nothing helps nobody.
        if (ReplayScreens1201.available()) {
            ladsReplaysButton = addRenderableWidget(Button.builder(Component.literal("Replays"),
                button -> ReplayScreens1201.open(this)).bounds(0, 0, 1, 1).build());
        }
        ladsQuitButton = addRenderableWidget(Button.builder(Component.translatable("menu.quit"),
            button -> minecraft.setScreen(new QuitConfirmScreen1201(this))).bounds(0, 0, 1, 1).build());
        ladsSwitchVersionButton = addRenderableWidget(Button.builder(Component.literal("Switch Versions"),
            button -> minecraft.setScreen(new VersionSwitchScreen1201(this))).bounds(0, 0, 1, 1).build());
        ladsNetworkButton = !LadsNetwork.titleButton() ? null : addRenderableWidget(Button.builder(Component.literal(LadsNetwork.TITLE_LABEL),
            button -> com.thelads.core.v1_20_1.gui.PauseMultiplayer1201.joinNetwork(new JoinMultiplayerScreen(this))).tooltip(Tooltip.create(Component.literal(LadsNetwork.TOOLTIP))).bounds(0, 0, 1, 1).build());

        // Beside the account name (placed each frame after it)
        ladsSwitchButton = addRenderableWidget(new CompactButton1201(0, 0, 1, 1, Component.literal(TitleScreenTheme.SWITCH),
            () -> "switch", button -> minecraft.setScreen(new AccountSwitcherScreen1201(this))));
        ladsSwitchButton.setTooltip(Tooltip.create(Component.literal("Switch account")));
        ladsSwitchButton.setTabOrderGroup(200);

        // Header controls (top right)
        ladsFullscreenButton = addRenderableWidget(CompactButton1201.fullscreen());
        ladsFullscreenButton.placeFullscreen(font, width);
        ladsFullscreenButton.setTabOrderGroup(201);

        ladsLanguageButton = addRenderableWidget(new CompactButton1201(width - 50, 6, 20, 20, Component.translatable("options.language"),
            () -> "globe", button -> minecraft.setScreen(new LanguageSelectScreen(this, minecraft.options, minecraft.getLanguageManager()))));
        ladsLanguageButton.setTooltip(Tooltip.create(Component.translatable("options.language")));
        ladsLanguageButton.setTabOrderGroup(202);

        ladsLayoutToggle = addRenderableWidget(new CompactButton1201(0, 6, 20, 20, Component.literal(TitleScreenTheme.layoutToggleLabel()),
            () -> "layout", button -> TitleScreenModule.toggleLayout()));
        ladsLayoutToggle.setTabOrderGroup(203);

        ladsLastFrameNanos = 0;
        com.thelads.core.config.BuiltInIntegrations.verifyLoadedAdapters();
        com.thelads.core.config.IntegrationRuntimeProbe.run();
    }

    /** Legacy: GUI scale 3 everywhere; the new layout picks its own. */
    @Unique
    private double ladsWindowPerPixel() {
        return ladsLegacy ? 3.0 : TitleNoir.windowPerCanvasPixel(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
    }

    @Unique
    private int ladsTargetWidth() {
        if (minecraft != null && minecraft.getWindow() != null && minecraft.getWindow().getWidth() > 0) {
            return (int) Math.round(minecraft.getWindow().getWidth() / ladsWindowPerPixel());
        }
        return width;
    }

    @Unique
    private int ladsTargetHeight() {
        if (minecraft != null && minecraft.getWindow() != null && minecraft.getWindow().getHeight() > 0) {
            return (int) Math.round(minecraft.getWindow().getHeight() / ladsWindowPerPixel());
        }
        return height;
    }

    @Unique
    private float ladsMatrixScale() {
        int targetH = ladsTargetHeight();
        return (float) height / (float) Math.max(1, targetH);
    }

    @Unique
    private void ladsEnsureTitleLayout() {
        boolean legacy = TitleScreenModule.legacyLayout(), switched = legacy != ladsLegacy;
        ladsLegacy = legacy;
        int targetW = ladsTargetWidth();
        int targetH = ladsTargetHeight();
        boolean changed = ladsTitleLayout == null || switched || ladsLayoutWidth != targetW || ladsLayoutHeight != targetH;
        if (!changed) {
            int index = 0;
            for (GuiEventListener child : children()) {
                if (!(child instanceof AbstractWidget widget) || ladsOwnControl(widget)) continue;
                if (index >= ladsTitleWidgets.size() || ladsTitleWidgets.get(index) != widget) {
                    changed = true;
                    break;
                }
                index++;
            }
            changed |= index != ladsTitleWidgets.size();
        }
        if (!changed) return;
        for (AbstractWidget widget : ladsTitleWidgets) TitleWidgetRegistry1201.unregister(widget);
        ladsTitleWidgets = new ArrayList<>();
        for (GuiEventListener child : children()) {
            if (child instanceof AbstractWidget widget && !ladsOwnControl(widget)) ladsTitleWidgets.add(widget);
        }
        for (AbstractWidget widget : List.copyOf(ladsTitleWidgets)) {
            String key = TitleWidgetRegistry1201.messageKey(widget);
            boolean main = widget == ladsSettingsButton || widget == ladsCosmeticsButton || widget == ladsFriendsButton || widget == ladsMoreButton
                || widget == ladsReplaysButton
                || widget == ladsQuitButton || widget == ladsSwitchVersionButton || widget == ladsNetworkButton
                || ladsIsModsEntry(widget)
                || key.equals("menu.singleplayer") || key.equals("menu.playdemo") || key.equals("menu.multiplayer")
                || key.equals("menu.options") || key.equals("screen.lads_screenshots.manage_screenshots");
            if (!main) {
                ladsTitleWidgets.remove(widget);
                removeWidget(widget);
                if (widget.getClass().getName().startsWith("gg.essential.")) {
                    widget.visible = false;
                } else if (!ladsExtraWidgets.contains(widget) && !key.startsWith("fancymenu.widgetified_screens.") && !key.equals("menu.quit")) {
                    ladsExtraWidgets.add(widget);
                }
            }
        }
        ladsTitleWidgets.sort(Comparator.comparingInt(this::ladsButtonOrder));
        ladsTitleLayout = TitleScreenTheme.layout(targetW, targetH, ladsTitleWidgets.size(), false);
        ladsNoirLayout = legacy ? null : TitleNoir.layout(targetW, targetH, ladsTitleWidgets.size(), ladsTitleWidgets.indexOf(ladsNetworkButton));
        ladsLayoutWidth = targetW;
        ladsLayoutHeight = targetH;

        int fullscreenWidth = ladsFullscreenButton.placeFullscreen(font, targetW);
        int languageX = TitleScreenTheme.languageX(targetW, fullscreenWidth);
        ((CompactButton1201) ladsLanguageButton).setBounds(languageX, 6, 20, 20);
        Component toggleLabel = Component.literal(TitleScreenTheme.layoutToggleLabel());
        int toggleWidth = TitleScreenTheme.layoutToggleWidth(targetW, Math.max(font.width(TitleScreenTheme.NEW_LAYOUT), font.width(TitleScreenTheme.LEGACY_LAYOUT)));
        ladsLayoutToggle.setMessage(toggleLabel);
        ladsLayoutToggle.setBounds(languageX - TitleScreenTheme.HEADER_GAP - toggleWidth, 6, toggleWidth, 20);
        ladsLayoutToggle.setTooltip(toggleWidth <= 20 ? Tooltip.create(toggleLabel) : null);

        boolean primaryAssigned = false;
        for (AbstractWidget widget : ladsTitleWidgets) removeWidget(widget);
        for (int i = 0; i < ladsTitleWidgets.size(); i++) {
            AbstractWidget widget = ladsTitleWidgets.get(i);
            TitleScreenTheme.Rect rect = (ladsNoirLayout != null ? ladsNoirLayout.buttons() : ladsTitleLayout.buttons()).get(i);
            Widgets1201.setBounds(widget, rect.x(), rect.y(), rect.width(), rect.height());
            widget.setTabOrderGroup(i);
            addRenderableWidget(widget);
            boolean primary = !primaryAssigned && "menu.singleplayer".equals(TitleWidgetRegistry1201.messageKey(widget));
            primaryAssigned |= primary;
            TitleWidgetRegistry1201.register(this, widget, ladsButtonIcon(widget), primary);
        }
        ladsLastFrameNanos = 0;
        LoggerFactory.getLogger("TheLadsCore-1.20.1").info(
            "custom title initialized for Minecraft 1.20.1: {} native widgets", ladsTitleWidgets.size());
    }

    @Unique
    private boolean ladsOwnControl(AbstractWidget widget) {
        return widget == ladsSwitchButton || widget == ladsFullscreenButton || widget == ladsLanguageButton || widget == ladsLayoutToggle;
    }

    @Unique
    private int ladsButtonOrder(AbstractWidget widget) {
        if (widget == ladsNetworkButton) return 1; // after Multiplayer: the stable sort keeps the vanilla button (added first) ahead
        if (widget == ladsSettingsButton) return 2;
        if (ladsIsModsEntry(widget)) return 3;
        if (widget == ladsCosmeticsButton) return 4;
        if (widget == ladsFriendsButton) return 5;
        if (widget == ladsReplaysButton) return 8;
        if (widget == ladsMoreButton) return 9;
        if (widget == ladsQuitButton || "menu.quit".equals(TitleWidgetRegistry1201.messageKey(widget))) return 10;
        if (widget == ladsSwitchVersionButton) return 11;
        return switch (TitleWidgetRegistry1201.messageKey(widget)) {
            case "menu.singleplayer", "menu.playdemo" -> 0;
            case "menu.multiplayer" -> 1;
            case "menu.options" -> 6;
            case "screen.lads_screenshots.manage_screenshots" -> 7;
            default -> 12;
        };
    }

    @Unique
    private String ladsButtonIcon(AbstractWidget widget) {
        if (widget == ladsSettingsButton) return "lads";
        if (widget == ladsCosmeticsButton) return "wardrobe";
        if (widget == ladsFriendsButton) return "user";
        if (widget == ladsMoreButton) return "dots";
        if (widget == ladsReplaysButton) return "replay";
        if (widget == ladsQuitButton) return "quit";
        if (widget == ladsSwitchVersionButton) return "switch";
        if (widget == ladsNetworkButton) return "server";
        if (ladsIsModsEntry(widget)) return "mods";
        if (widget instanceof PlainTextButton) return "more";
        return switch (TitleWidgetRegistry1201.messageKey(widget)) {
            case "menu.singleplayer", "menu.playdemo" -> "play";
            case "menu.multiplayer" -> "server";
            case "menu.options" -> "settings";
            case "screen.lads_screenshots.manage_screenshots" -> "camera";
            case "options.language", "narrator.button.language" -> "globe";
            case "options.accessibility", "narrator.button.accessibility", "accessibility.onboarding.accessibility.button" -> "access";
            case "menu.quit" -> "quit";
            default -> "more";
        };
    }

    /** Mod Menu's button, and Forge's own Mods button (Zero SMP): the entry point of the installed mods stays on the main list. */
    @Unique
    private static boolean ladsIsModsEntry(AbstractWidget widget) {
        String key = TitleWidgetRegistry1201.messageKey(widget);
        return widget.getClass().getName().startsWith("com.terraformersmc.modmenu.")
            || key.equals("modmenu.title") || key.equals("fml.menu.mods");
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
        at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsRenderTitle(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!ladsCustomTitle) return;
        long now = System.nanoTime();
        ladsEnsureTitleLayout();
        float elapsed = ladsLastFrameNanos == 0 ? 0 : (float) Math.max(0.0, Math.min((now - ladsLastFrameNanos) / 1.0e9, 0.1));
        ladsLastFrameNanos = now;
        double panoramaSpeed = minecraft.options.panoramaSpeed().get();
        boolean reducedMotion = panoramaSpeed <= 0 || minecraft.options.screenEffectScale().get() <= 0;
        if (!reducedMotion) ladsAnimationSeconds += elapsed * panoramaSpeed;

        float matrixScale = ladsMatrixScale();
        int scaledMouseX = (int) Math.round(mouseX / matrixScale);
        int scaledMouseY = (int) Math.round(mouseY / matrixScale);

        if (ladsNoirLayout != null) panorama.render(reducedMotion ? 0.0f : minecraft.getDeltaFrameTime(), 1.0f);
        graphics.pose().pushPose();
        try {
            graphics.pose().scale(matrixScale, matrixScale, 1.0f);

            var adapter = new GuiGraphicsLadsAdapter1201(graphics, font);
            TitleScreenTheme.Rect[] box = new TitleScreenTheme.Rect[1];
            String version = SharedConstants.getCurrentVersion().getName() + (minecraft.isDemo() ? " Demo" : "");
            adapter.managed(() -> box[0] = ladsNoirLayout != null
                ? TitleNoir.renderBackdrop(adapter, ladsNoirLayout, minecraft.getUser().getName(), version, true)
                : TitleScreenTheme.renderBackground(adapter, ladsTitleLayout, minecraft.getUser().getName(), version, false, ladsAnimationSeconds, true));
            TitleScreenTheme.Rect switchBox = box[0];
            if (switchBox != null) {
                ((CompactButton1201) ladsSwitchButton).setBounds(switchBox.x(), switchBox.y(), switchBox.width(), switchBox.height());
            }

            TitleWidgetRegistry1201.beginFrame(this, adapter, elapsed, reducedMotion);
            TitleNoir.beginFrame(ladsNoirLayout);
            try {
                super.render(graphics, scaledMouseX, scaledMouseY, partialTick);
            } finally {
                TitleNoir.endFrame();
                TitleWidgetRegistry1201.endFrame();
            }
            if (ladsTooltip != null) { // inside the canvas pose, at the canvas pointer, kept on the canvas (TitleScreenTheme.flushTooltips)
                var positioner = ladsTooltipPositioner;
                graphics.renderTooltip(font, ladsTooltip, (screenWidth, screenHeight, x, y, w, h) -> positioner.positionTooltip(ladsLayoutWidth, ladsLayoutHeight, x, y, w, h), scaledMouseX, scaledMouseY);
                ladsTooltip = null;
            }
        } finally {
            graphics.pose().popPose();
        }
        ci.cancel();
    }

    /** The title's tooltips wait for ladsRenderTitle: Screen would draw them after its pose. */
    @Override
    public void setTooltipForNextRenderPass(List<net.minecraft.util.FormattedCharSequence> lines, net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner positioner, boolean replace) {
        if (!ladsCustomTitle) {
            super.setTooltipForNextRenderPass(lines, positioner, replace);
        } else if (ladsTooltip == null || replace) {
            ladsTooltip = lines;
            ladsTooltipPositioner = positioner;
        }
    }

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsClickVisibleTitle(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (ladsCustomTitle) {
            ladsEnsureTitleLayout();
            float matrixScale = ladsMatrixScale();
            double sx = mouseX / matrixScale, sy = mouseY / matrixScale;
            if (button == 0 && ladsNoirLayout != null && TitleNoir.clickNetworkPill(sx, sy)) {
                com.thelads.core.client.gui.LadsSettingsScreen.requestOpenNetworkHub();
                minecraft.setScreen(new LadsSettingsScreen1201(this));
                cir.setReturnValue(true);
                return;
            }
            cir.setReturnValue(super.mouseClicked(sx, sy, button));
        }
    }
}
