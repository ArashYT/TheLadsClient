package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.LadsNetwork;
import com.thelads.core.client.title.TitleNoir;
import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.modules.TitleScreenModule;
import com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter;
import com.thelads.core.v26_2.gui.AccountSwitcherScreen26;
import com.thelads.core.v26_2.gui.CompactButton26;
import com.thelads.core.v26_2.gui.LadsSettingsScreen26;
import com.thelads.core.v26_2.gui.TitleWidgetRegistry;
import com.thelads.core.v26_2.gui.TitleExtrasScreen26;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
    @Unique private Button ladsPanoramaButton;
    @Unique private Button ladsUpdateButton;
    @Unique private TitleScreenTheme.Layout ladsTitleLayout;
    @Unique private boolean ladsCustomTitle;
    @Unique private int ladsLayoutWidth;
    @Unique private int ladsLayoutHeight;
    @Unique private Button ladsSettingsButton;
    @Unique private Button ladsCosmeticsButton;
    @Unique private Button ladsFriendsButton;
    @Unique private Button ladsSwitchButton;
    @Unique private Button ladsFullscreenButton;
    @Unique private Button ladsLanguageButton;
    @Unique private Button ladsReplaysButton;
    @Unique private Button ladsMoreButton;
    @Unique private Button ladsQuitButton;
    @Unique private Button ladsSwitchVersionButton;
    @Unique private Button ladsNetworkButton;
    @Unique private List<AbstractWidget> ladsExtraWidgets = new ArrayList<>();
    @Unique private long ladsLastFrameNanos;
    @Unique private double ladsAnimationSeconds;
    @Unique private boolean ladsOverlayTyping;

    protected TitleScreenMixin() {
        super(Component.empty());
    }

    @Inject(method = "init()V", at = @At("TAIL"), require = 1)
    private void ladsArrangeTitle(CallbackInfo ci) {
        com.thelads.core.mods.ModInventoryModel.requestAtFirstTitleScreen();
        com.thelads.core.mods.ModInventoryModel.logAtFirstTitleScreen();
        for (AbstractWidget widget : ladsTitleWidgets) TitleWidgetRegistry.unregister(widget);
        ladsTitleWidgets = new ArrayList<>();
        ladsTitleLayout = null;
        ladsExtraWidgets = new ArrayList<>();
        ladsCustomTitle = com.thelads.core.v26_2.feature.NativeFeatures.enabled("TitleScreen");
        if (!ladsCustomTitle) return;

        ladsSettingsButton = addRenderableWidget(Button.builder(Component.literal("Lads Mods"),
            button -> minecraft.setScreenAndShow(new LadsSettingsScreen26(this))).bounds(0, 0, 1, 1).build());
        ladsFriendsButton = addRenderableWidget(Button.builder(com.thelads.core.v26_2.social.SocialBridge26.friendsButtonText(),
            button -> minecraft.setScreenAndShow(new com.thelads.core.v26_2.social.FriendsScreen26(this))).bounds(0, 0, 1, 1).build());
        ladsCosmeticsButton = addRenderableWidget(Button.builder(Component.literal("Cosmetics"),
            button -> minecraft.setScreenAndShow(new com.thelads.core.v26_2.cosmetics.anim.CosmeticsScreen26(this))).bounds(0, 0, 1, 1).build());
        ladsMoreButton = addRenderableWidget(Button.builder(Component.literal("More..."),
            button -> minecraft.setScreenAndShow(new TitleExtrasScreen26(this, ladsExtraWidgets))).bounds(0, 0, 1, 1).build());
        ladsReplaysButton = addRenderableWidget(Button.builder(Component.literal("Replays"),
            button -> com.thelads.core.v26_2.gui.FlashbackScreens.open(this)).bounds(0, 0, 1, 1).build());
        ladsQuitButton = addRenderableWidget(Button.builder(Component.translatable("menu.quit"),
            button -> minecraft.setScreenAndShow(new com.thelads.core.v26_2.gui.QuitConfirmScreen26(this))).bounds(0, 0, 1, 1).build());
        ladsSwitchVersionButton = addRenderableWidget(Button.builder(Component.literal("Switch Versions"),
            button -> minecraft.setScreenAndShow(new com.thelads.core.v26_2.gui.VersionSwitchScreen26(this))).bounds(0, 0, 1, 1).build());
        ladsNetworkButton = !LadsNetwork.titleButton() ? null : addRenderableWidget(Button.builder(Component.literal(LadsNetwork.TITLE_LABEL),
            button -> com.thelads.core.v26_2.gui.PauseMultiplayer.joinNetwork(new JoinMultiplayerScreen(this))).tooltip(Tooltip.create(Component.literal(LadsNetwork.TOOLTIP))).bounds(0, 0, 1, 1).build());

        // Beside the account name (placed each frame after it)
        ladsSwitchButton = addRenderableWidget(new CompactButton26(0, 0, 1, 1, Component.literal(TitleScreenTheme.SWITCH),
            () -> "switch", button -> minecraft.setScreenAndShow(new AccountSwitcherScreen26(this))));
        ladsSwitchButton.setTooltip(Tooltip.create(Component.literal("Switch account")));
        ladsSwitchButton.setTabOrderGroup(200);

        // Header controls (top right)
        ladsFullscreenButton = addRenderableWidget(new CompactButton26(width - 26, 6, 20, 20, Component.translatable("options.fullscreen"),
            () -> minecraft.options.fullscreen().get() ? "windowed" : "fullscreen", CompactButton26::toggleFullscreen));
        ladsFullscreenButton.setTabOrderGroup(201);

        ladsLanguageButton = addRenderableWidget(new CompactButton26(width - 50, 6, 20, 20, Component.translatable("options.language"),
            () -> "globe", button -> minecraft.setScreenAndShow(new net.minecraft.client.gui.screens.options.LanguageSelectScreen(this, minecraft.options, minecraft.getLanguageManager()))));
        ladsLanguageButton.setTooltip(Tooltip.create(Component.translatable("options.language")));
        ladsLanguageButton.setTabOrderGroup(202);

        ladsLayoutToggle = addRenderableWidget(new CompactButton26(0, 6, 20, 20, Component.literal(TitleScreenTheme.layoutToggleLabel()),
            () -> "layout", button -> TitleScreenModule.toggleLayout()));
        ladsLayoutToggle.setTabOrderGroup(203);

        ladsPanoramaButton = addRenderableWidget(new CompactButton26(0, 6, 20, 20, Component.literal(TitleScreenTheme.CHANGE_PANORAMA),
            () -> "mountain", button -> com.thelads.core.client.title.TitleOverlay.openPanorama(button.getX(), button.getY(), button.getWidth(), button.getHeight())));
        ladsPanoramaButton.setTabOrderGroup(204);
        ladsUpdateButton = addRenderableWidget(new CompactButton26(0, 6, 20, 20, Component.literal(TitleScreenTheme.UPDATE),
            () -> "download", button -> com.thelads.core.client.LauncherUpdate.press(() -> minecraft.stop())));
        ladsUpdateButton.setTabOrderGroup(205);
        ladsUpdateButton.setTooltip(Tooltip.create(Component.literal(TitleScreenTheme.UPDATE_TOOLTIP)));
        ladsUpdateButton.visible = false;

        com.thelads.core.client.title.TitleOverlay.reset(); // the Panorama list and the chat sidebar start closed
        com.thelads.core.client.title.TitleOverlay.enableChat();
        ladsOverlayTyping = false;
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
        ladsUpdateButton.visible = com.thelads.core.client.LauncherUpdate.available() != null;
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
        for (AbstractWidget widget : ladsTitleWidgets) TitleWidgetRegistry.unregister(widget);
        ladsTitleWidgets = new ArrayList<>();
        for (GuiEventListener child : children()) {
            if (child instanceof AbstractWidget widget && !ladsOwnControl(widget)) ladsTitleWidgets.add(widget);
        }
        for (AbstractWidget widget : List.copyOf(ladsTitleWidgets)) {
            String key = ladsMessageKey(widget);
            boolean main = widget == ladsSettingsButton || widget == ladsMoreButton
                || widget == ladsReplaysButton || widget == ladsCosmeticsButton || widget == ladsFriendsButton
                || widget == ladsQuitButton || widget == ladsSwitchVersionButton || widget == ladsNetworkButton
                || ladsIsModMenuWidget(widget)
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
        int networkIndex = ladsTitleWidgets.indexOf(ladsNetworkButton);
        ladsTitleLayout = TitleScreenTheme.layout(targetW, targetH, ladsTitleWidgets.size(), networkIndex);
        ladsNoirLayout = legacy ? null : TitleNoir.layout(targetW, targetH, ladsTitleWidgets.size(), networkIndex);
        ladsLayoutWidth = targetW;
        ladsLayoutHeight = targetH;

        ladsFullscreenButton.setRectangle(20, 20, targetW - 26, 6);
        ladsLanguageButton.setRectangle(20, 20, targetW - 50, 6);
        Component toggleLabel = Component.literal(TitleScreenTheme.layoutToggleLabel());
        int toggleWidth = TitleScreenTheme.layoutToggleWidth(targetW, Math.max(font.width(TitleScreenTheme.NEW_LAYOUT), font.width(TitleScreenTheme.LEGACY_LAYOUT)));
        ladsLayoutToggle.setMessage(toggleLabel);
        ladsLayoutToggle.setRectangle(toggleWidth, 20, targetW - 50 - TitleScreenTheme.HEADER_GAP - toggleWidth, 6);
        ladsLayoutToggle.setTooltip(toggleWidth <= 20 ? Tooltip.create(toggleLabel) : null);
        Component panoramaLabel = Component.literal(TitleScreenTheme.CHANGE_PANORAMA);
        int panoramaWidth = TitleScreenTheme.panoramaButtonWidth(targetW, font.width(panoramaLabel), ladsLegacy);
        ladsPanoramaButton.setRectangle(panoramaWidth, 20, targetW - 50 - 2 * TitleScreenTheme.HEADER_GAP - toggleWidth - panoramaWidth, 6);
        ladsPanoramaButton.setTooltip(panoramaWidth <= 20 ? Tooltip.create(panoramaLabel) : null);
        ladsPanoramaButton.visible = TitleScreenModule.panorama();
        int updateWidth = TitleScreenTheme.panoramaButtonWidth(targetW, font.width(TitleScreenTheme.UPDATE), ladsLegacy);
        ladsUpdateButton.setRectangle(updateWidth, 20, TitleScreenTheme.updateButtonX(ladsPanoramaButton.getX(), panoramaWidth, ladsPanoramaButton.visible, updateWidth), 6);

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
        LoggerFactory.getLogger("TheLadsCore-26.3").info(
            "custom title initialized for Minecraft 26.3: {} native widgets", ladsTitleWidgets.size());
    }

    @Unique
    private boolean ladsOwnControl(AbstractWidget widget) {
        return widget == ladsSwitchButton || widget == ladsFullscreenButton || widget == ladsLanguageButton || widget == ladsLayoutToggle || widget == ladsPanoramaButton || widget == ladsUpdateButton;
    }

    @Unique
    private static String ladsMessageKey(AbstractWidget widget) {
        Component message = widget.getMessage();
        return message != null && message.getContents() instanceof TranslatableContents contents ? contents.getKey() : "";
    }

    @Unique
    private int ladsButtonOrder(AbstractWidget widget) {
        if (widget == ladsNetworkButton) return 1; // after Multiplayer: the stable sort keeps the vanilla button (added first) ahead
        if (widget == ladsSettingsButton) return 2;
        if (ladsIsModMenuWidget(widget)) return 3;
        if (widget == ladsFriendsButton) return 4;
        if (widget == ladsCosmeticsButton) return 5;
        if (widget == ladsReplaysButton) return 7;
        if (widget == ladsMoreButton) return 8;
        if (widget == ladsQuitButton || "menu.quit".equals(ladsMessageKey(widget))) return 9;
        if (widget == ladsSwitchVersionButton) return 10;
        return switch (ladsMessageKey(widget)) {
            case "menu.singleplayer", "menu.playdemo" -> 0;
            case "menu.multiplayer" -> 1;
            case "menu.options" -> 5;
            case "screen.lads_screenshots.manage_screenshots" -> 6;
            default -> 11;
        };
    }

    @Unique
    private String ladsButtonIcon(AbstractWidget widget) {
        if (widget == ladsSettingsButton) return "lads";
        if (widget == ladsFriendsButton) return "friends";
        if (widget == ladsCosmeticsButton) return "wardrobe";
        if (widget == ladsMoreButton) return "dots";
        if (widget == ladsReplaysButton) return "replay";
        if (widget == ladsQuitButton) return "quit";
        if (widget == ladsSwitchVersionButton) return "switch";
        if (widget == ladsNetworkButton) return "server";
        if (ladsIsModMenuWidget(widget)) return "mods";
        return switch (ladsMessageKey(widget)) {
            case "menu.singleplayer", "menu.playdemo" -> "play";
            case "menu.multiplayer" -> "server";
            case "menu.options" -> "settings";
            case "screen.lads_screenshots.manage_screenshots" -> "camera";
            case "options.language" -> "globe";
            case "options.accessibility", "accessibility.onboarding.accessibility.button" -> "access";
            case "menu.quit" -> "quit";
            default -> "more";
        };
    }

    @Unique
    private static boolean ladsIsModMenuWidget(AbstractWidget widget) {
        return widget.getClass().getName().startsWith("com.terraformersmc.modmenu.") || ladsMessageKey(widget).equals("modmenu.title");
    }

    /** Panorama Version: the cube map reloads, and CubeMapTextureMixin hands it the picked release's images (or the normal ones). */
    @Unique
    private static void ladsApplyPanorama() {
        var location = net.minecraft.resources.Identifier.withDefaultNamespace("textures/gui/title/background/panorama");
        try {
            net.minecraft.client.Minecraft.getInstance().getTextureManager().registerAndLoad(location, new net.minecraft.client.renderer.texture.CubeMapTexture(location));
        } catch (RuntimeException failure) {
            org.slf4j.LoggerFactory.getLogger("TheLadsCore").warn("The picked panorama could not load", failure);
        }
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
        at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsRenderTitle(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!ladsCustomTitle) return;
        long now = System.nanoTime();
        ladsEnsureTitleLayout();
        if (com.thelads.core.client.title.PanoramaPicker.update()) ladsApplyPanorama();
        float elapsed = ladsLastFrameNanos == 0 ? 0 : (float) Math.clamp((now - ladsLastFrameNanos) / 1.0e9, 0.0, 0.1);
        ladsLastFrameNanos = now;
        double panoramaSpeed = minecraft.options.panoramaSpeed().get();
        boolean reducedMotion = panoramaSpeed <= 0 || minecraft.options.screenEffectScale().get() <= 0;
        if (!reducedMotion) ladsAnimationSeconds += elapsed * panoramaSpeed;

        float matrixScale = ladsMatrixScale();
        int scaledMouseX = (int) Math.round(mouseX / matrixScale);
        int scaledMouseY = (int) Math.round(mouseY / matrixScale);

        if (ladsNoirLayout != null && TitleScreenModule.panorama()) {
            if (minecraft.gameRenderer != null) {
                if (reducedMotion) minecraft.gameRenderer.panorama().holdSpin();
                else minecraft.gameRenderer.panorama().startSpin();
            }
            extractPanorama(graphics, partialTick);
        }
        graphics.pose().pushMatrix();
        try {
            graphics.pose().scale(matrixScale, matrixScale);

            var adapter = new GuiGraphicsExtractorLadsAdapter(graphics, font);
            String version = "26.3" + (minecraft.isDemo() ? " Demo" : "");
            TitleScreenTheme.Rect switchBox = ladsNoirLayout != null
                ? TitleNoir.renderBackdrop(adapter, ladsNoirLayout, minecraft.getUser().getName(), version, true, ladsAnimationSeconds)
                : TitleScreenTheme.renderBackground(adapter, ladsTitleLayout, minecraft.getUser().getName(), version, false, ladsAnimationSeconds, true);
            if (switchBox != null) {
                ladsSwitchButton.setRectangle(switchBox.width(), switchBox.height(), switchBox.x(), switchBox.y());
            }

            TitleWidgetRegistry.beginFrame(this, adapter, elapsed, reducedMotion);
            TitleNoir.beginFrame(ladsNoirLayout);
            try {
                super.extractRenderState(graphics, scaledMouseX, scaledMouseY, partialTick);
            } finally {
                TitleNoir.endFrame();
                TitleWidgetRegistry.endFrame();
            }
            com.thelads.core.client.title.TitleOverlay.render(adapter, ladsLayoutWidth, ladsLayoutHeight, scaledMouseX, scaledMouseY, elapsed, reducedMotion);
            ladsSyncTextInput();
            TitleScreenTheme.flushTooltips(ladsLayoutWidth, ladsLayoutHeight, () -> graphics.extractDeferredElements(scaledMouseX, scaledMouseY, partialTick));
        } finally {
            graphics.pose().popMatrix();
        }
        ci.cancel();
    }

    /** The chat sidebar's filter box takes typed text: 26.3 delivers characters only while the screen asks for them. */
    @Unique
    private void ladsSyncTextInput() {
        boolean typing = com.thelads.core.client.title.TitleOverlay.capturingText();
        if (typing == ladsOverlayTyping || minecraft == null) return;
        ladsOverlayTyping = typing;
        minecraft.textInputManager().onTextInputFocusChange(this, typing);
    }

    @Override
    public boolean isInputCaptured() {
        return com.thelads.core.client.title.TitleOverlay.capturingText() || super.isInputCaptured();
    }

    @Inject(method = "removed()V", at = @At("HEAD"), require = 1)
    private void ladsStopTextInput(CallbackInfo ci) {
        if (ladsOverlayTyping) minecraft.textInputManager().stopTextInput(this);
        ladsOverlayTyping = false;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (ladsCustomTitle && com.thelads.core.client.title.TitleOverlay.keyPressed(event.key())) {
            ladsSyncTextInput();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (ladsCustomTitle && event.codepoint() <= 0xFFFF && com.thelads.core.client.title.TitleOverlay.charTyped((char) event.codepoint())) return true;
        return super.charTyped(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (ladsCustomTitle && com.thelads.core.client.title.TitleOverlay.mouseScrolled(verticalAmount)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
        at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsClickVisibleTitle(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (ladsCustomTitle) {
            ladsEnsureTitleLayout();
            float matrixScale = ladsMatrixScale();
            double sx = event.x() / matrixScale, sy = event.y() / matrixScale;
            if (com.thelads.core.client.title.TitleOverlay.mouseClicked(sx, sy, event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT ? 0 : 1, ladsLayoutWidth, ladsLayoutHeight)) {
                ladsSyncTextInput();
                cir.setReturnValue(true);
                return;
            }
            if (event.button() == 0 && ladsNoirLayout != null && TitleNoir.clickNetworkPill(sx, sy)) {
                com.thelads.core.client.gui.LadsSettingsScreen.requestOpenNetworkHub();
                minecraft.setScreenAndShow(new LadsSettingsScreen26(this));
                cir.setReturnValue(true);
                return;
            }
            MouseButtonEvent scaledEvent = matrixScale != 1.0f
                ? new MouseButtonEvent(sx, sy, event.buttonInfo())
                : event;
            cir.setReturnValue(super.mouseClicked(scaledEvent, doubleClick));
        }
    }
}
