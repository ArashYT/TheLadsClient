package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.title.TitleScreenTheme;
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
    @Unique private TitleScreenTheme.Layout ladsTitleLayout;
    @Unique private boolean ladsCustomTitle;
    @Unique private int ladsLayoutWidth;
    @Unique private int ladsLayoutHeight;
    @Unique private Button ladsSettingsButton;
    @Unique private Button ladsSwitchButton;
    @Unique private Button ladsFullscreenButton;
    @Unique private Button ladsLanguageButton;
    @Unique private Button ladsReplaysButton;
    @Unique private Button ladsFriendsButton;
    @Unique private Button ladsMoreButton;
    @Unique private Button ladsQuitButton;
    @Unique private Button ladsSwitchVersionButton;
    @Unique private List<AbstractWidget> ladsExtraWidgets = new ArrayList<>();
    @Unique private com.thelads.core.v26_2.gui.EssentialActions.Action ladsCapturedFriendsAction;
    @Unique private long ladsLastFrameNanos;
    @Unique private double ladsAnimationSeconds;

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
        ladsCapturedFriendsAction = null;
        ladsCustomTitle = com.thelads.core.v26_2.feature.NativeFeatures.enabled("TitleScreen");
        if (!ladsCustomTitle) return;

        ladsSettingsButton = addRenderableWidget(Button.builder(Component.literal("Lads Mods"),
            button -> minecraft.setScreenAndShow(new LadsSettingsScreen26(this))).bounds(0, 0, 1, 1).build());
        ladsMoreButton = addRenderableWidget(Button.builder(Component.literal("More..."),
            button -> minecraft.setScreenAndShow(new TitleExtrasScreen26(this, ladsExtraWidgets))).bounds(0, 0, 1, 1).build());
        ladsReplaysButton = addRenderableWidget(Button.builder(Component.literal("Replays"),
            button -> com.thelads.core.v26_2.gui.FlashbackScreens.open(this)).bounds(0, 0, 1, 1).build());
        ladsFriendsButton = addRenderableWidget(Button.builder(Component.literal("Friends"),
            button -> ladsOpenFriends()).bounds(0, 0, 1, 1).build());
        ladsQuitButton = addRenderableWidget(Button.builder(Component.translatable("menu.quit"),
            button -> minecraft.setScreenAndShow(new com.thelads.core.v26_2.gui.QuitConfirmScreen26(this))).bounds(0, 0, 1, 1).build());
        ladsSwitchVersionButton = addRenderableWidget(Button.builder(Component.literal("Switch Versions"),
            button -> minecraft.setScreenAndShow(new com.thelads.core.v26_2.gui.VersionSwitchScreen26(this))).bounds(0, 0, 1, 1).build());

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

        ladsLastFrameNanos = 0;
        com.thelads.core.config.BuiltInIntegrations.verifyLoadedAdapters();
        com.thelads.core.config.IntegrationRuntimeProbe.run();
    }

    @Unique
    private void ladsOpenFriends() {
        if (ladsCapturedFriendsAction != null) {
            try {
                ladsCapturedFriendsAction.press().run();
                return;
            } catch (Throwable ignored) {}
        }
        try {
            Class<?> util = Class.forName("gg.essential.util.GuiUtil");
            Object inst = util.getField("INSTANCE").get(null);
            Class<?> screenCls = Class.forName("gg.essential.gui.friends.SocialScreen");
            Object scr = screenCls.getConstructor().newInstance();
            util.getMethod("openScreen", net.minecraft.client.gui.screens.Screen.class).invoke(inst, scr);
            return;
        } catch (Throwable ignored) {}
        try {
            minecraft.setScreenAndShow(new net.minecraft.client.gui.screens.social.SocialInteractionsScreen());
        } catch (Throwable ignored) {}
    }

    @Unique
    private int ladsTargetWidth() {
        if (minecraft != null && minecraft.getWindow() != null && minecraft.getWindow().getWidth() > 0) {
            return (int) Math.round(minecraft.getWindow().getWidth() / 3.0);
        }
        return width;
    }

    @Unique
    private int ladsTargetHeight() {
        if (minecraft != null && minecraft.getWindow() != null && minecraft.getWindow().getHeight() > 0) {
            return (int) Math.round(minecraft.getWindow().getHeight() / 3.0);
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
        int targetW = ladsTargetWidth();
        int targetH = ladsTargetHeight();
        boolean changed = ladsTitleLayout == null || ladsLayoutWidth != targetW || ladsLayoutHeight != targetH;
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
                || widget == ladsReplaysButton || widget == ladsFriendsButton
                || widget == ladsQuitButton || widget == ladsSwitchVersionButton
                || ladsIsModMenuWidget(widget)
                || key.equals("menu.singleplayer") || key.equals("menu.playdemo") || key.equals("menu.multiplayer")
                || key.equals("menu.options") || key.equals("screen.lads_screenshots.manage_screenshots");
            if (!main) {
                ladsTitleWidgets.remove(widget);
                removeWidget(widget);
                if (widget.getClass().getName().startsWith("gg.essential.")) {
                    widget.visible = false;
                    var action = com.thelads.core.v26_2.gui.EssentialActions.capture(this, widget);
                    if (action != null && (action.label().equalsIgnoreCase("Social") || action.label().equalsIgnoreCase("Friends"))) {
                        ladsCapturedFriendsAction = action;
                    }
                } else if (!ladsExtraWidgets.contains(widget) && !key.startsWith("fancymenu.widgetified_screens.") && !key.equals("menu.quit")) {
                    ladsExtraWidgets.add(widget);
                }
            }
        }
        ladsTitleWidgets.sort(Comparator.comparingInt(this::ladsButtonOrder));
        ladsTitleLayout = TitleScreenTheme.layout(targetW, targetH, ladsTitleWidgets.size(), false);
        ladsLayoutWidth = targetW;
        ladsLayoutHeight = targetH;

        ladsFullscreenButton.setRectangle(20, 20, targetW - 26, 6);
        ladsLanguageButton.setRectangle(20, 20, targetW - 50, 6);

        boolean primaryAssigned = false;
        for (AbstractWidget widget : ladsTitleWidgets) removeWidget(widget);
        for (int i = 0; i < ladsTitleWidgets.size(); i++) {
            AbstractWidget widget = ladsTitleWidgets.get(i);
            TitleScreenTheme.Rect rect = ladsTitleLayout.buttons().get(i);
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
        return widget == ladsSwitchButton || widget == ladsFullscreenButton || widget == ladsLanguageButton;
    }

    @Unique
    private static String ladsMessageKey(AbstractWidget widget) {
        Component message = widget.getMessage();
        return message != null && message.getContents() instanceof TranslatableContents contents ? contents.getKey() : "";
    }

    @Unique
    private int ladsButtonOrder(AbstractWidget widget) {
        if (widget == ladsSettingsButton) return 2;
        if (ladsIsModMenuWidget(widget)) return 3;
        if (widget == ladsReplaysButton) return 6;
        if (widget == ladsFriendsButton) return 7;
        if (widget == ladsMoreButton) return 8;
        if (widget == ladsQuitButton || "menu.quit".equals(ladsMessageKey(widget))) return 9;
        if (widget == ladsSwitchVersionButton) return 10;
        return switch (ladsMessageKey(widget)) {
            case "menu.singleplayer", "menu.playdemo" -> 0;
            case "menu.multiplayer" -> 1;
            case "menu.options" -> 4;
            case "screen.lads_screenshots.manage_screenshots" -> 5;
            default -> 11;
        };
    }

    @Unique
    private String ladsButtonIcon(AbstractWidget widget) {
        if (widget == ladsSettingsButton) return "lads";
        if (widget == ladsMoreButton) return "dots";
        if (widget == ladsReplaysButton) return "replay";
        if (widget == ladsFriendsButton) return "friends";
        if (widget == ladsQuitButton) return "quit";
        if (widget == ladsSwitchVersionButton) return "switch";
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

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
        at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsRenderTitle(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!ladsCustomTitle) return;
        com.thelads.core.v26_2.gui.EssentialActions.suppressOverlay(this);
        long now = System.nanoTime();
        ladsEnsureTitleLayout();
        float elapsed = ladsLastFrameNanos == 0 ? 0 : (float) Math.clamp((now - ladsLastFrameNanos) / 1.0e9, 0.0, 0.1);
        ladsLastFrameNanos = now;
        double panoramaSpeed = minecraft.options.panoramaSpeed().get();
        boolean reducedMotion = panoramaSpeed <= 0 || minecraft.options.screenEffectScale().get() <= 0;
        if (!reducedMotion) ladsAnimationSeconds += elapsed * panoramaSpeed;

        float matrixScale = ladsMatrixScale();
        int scaledMouseX = (int) Math.round(mouseX / matrixScale);
        int scaledMouseY = (int) Math.round(mouseY / matrixScale);

        graphics.pose().pushMatrix();
        graphics.pose().scale(matrixScale, matrixScale);

        var adapter = new GuiGraphicsExtractorLadsAdapter(graphics, font);
        TitleScreenTheme.Rect switchBox = TitleScreenTheme.renderBackground(adapter, ladsTitleLayout, minecraft.getUser().getName(),
            "26.3" + (minecraft.isDemo() ? " Demo" : ""), false, ladsAnimationSeconds, true);
        if (switchBox != null) {
            ladsSwitchButton.setRectangle(switchBox.width(), switchBox.height(), switchBox.x(), switchBox.y());
        }

        TitleWidgetRegistry.beginFrame(this, adapter, elapsed, reducedMotion);
        try {
            super.extractRenderState(graphics, scaledMouseX, scaledMouseY, partialTick);
        } finally {
            TitleWidgetRegistry.endFrame();
        }
        graphics.pose().popMatrix();
        ci.cancel();
    }

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
        at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsClickVisibleTitle(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (ladsCustomTitle) {
            ladsEnsureTitleLayout();
            float matrixScale = ladsMatrixScale();
            MouseButtonEvent scaledEvent = matrixScale != 1.0f
                ? new MouseButtonEvent(event.x() / matrixScale, event.y() / matrixScale, event.buttonInfo())
                : event;
            cir.setReturnValue(super.mouseClicked(scaledEvent, doubleClick));
        }
    }
}
