package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter;
import com.thelads.core.v26_2.gui.AccountSwitcherScreen26;
import com.thelads.core.v26_2.gui.LadsSettingsScreen26;
import com.thelads.core.v26_2.gui.TitleWidgetRegistry;
import com.thelads.core.v26_2.gui.TitleExtrasScreen26;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
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
    @Unique private Button ladsAccountsButton;
    @Unique private Button ladsMoreButton;
    @Unique private List<AbstractWidget> ladsExtraWidgets = new ArrayList<>();
    @Unique private long ladsLastFrameNanos;
    @Unique private double ladsAnimationSeconds;

    protected TitleScreenMixin() {
        super(Component.empty());
    }

    @Inject(method = "init()V", at = @At("TAIL"), require = 1)
    private void ladsArrangeTitle(CallbackInfo ci) {
        for (AbstractWidget widget : ladsTitleWidgets) TitleWidgetRegistry.unregister(widget);
        ladsTitleWidgets = new ArrayList<>();
        ladsTitleLayout = null;
        ladsExtraWidgets = new ArrayList<>();
        ladsCustomTitle = com.thelads.core.v26_2.feature.NativeFeatures.enabled("TitleScreen");
        if (!ladsCustomTitle) return;
        ladsSettingsButton = addRenderableWidget(Button.builder(Component.literal("Lads Mods"),
            button -> minecraft.setScreenAndShow(new LadsSettingsScreen26(this))).bounds(0, 0, 1, 1).build());
        ladsAccountsButton = addRenderableWidget(Button.builder(Component.literal("Accounts"),
            button -> minecraft.setScreenAndShow(new AccountSwitcherScreen26(this))).bounds(0, 0, 1, 1).build());
        ladsMoreButton = addRenderableWidget(Button.builder(Component.literal("More..."),
            button -> minecraft.setScreenAndShow(new TitleExtrasScreen26(this, ladsExtraWidgets))).bounds(0, 0, 1, 1).build());

        // Fabric screen events and other init mixins may still add or move widgets after this callback.
        // Leave their native geometry intact until the first draw has the complete widget set.
        ladsLastFrameNanos = 0;
        com.thelads.core.config.BuiltInIntegrations.verifyLoadedAdapters();
        com.thelads.core.config.IntegrationRuntimeProbe.run();
    }

    @Unique
    private void ladsEnsureTitleLayout() {
        boolean changed = ladsTitleLayout == null || ladsLayoutWidth != width || ladsLayoutHeight != height;
        if (!changed) {
            int index = 0;
            for (GuiEventListener child : children()) {
                if (!(child instanceof AbstractWidget widget)) continue;
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
            if (child instanceof AbstractWidget widget) ladsTitleWidgets.add(widget);
        }
        // Keep the home screen focused. All secondary native/mod actions remain in More.
        for (AbstractWidget widget : List.copyOf(ladsTitleWidgets)) {
            String key = ladsMessageKey(widget);
            boolean main = widget == ladsSettingsButton || widget == ladsMoreButton
                || key.equals("menu.singleplayer") || key.equals("menu.playdemo")
                || key.equals("menu.multiplayer") || key.equals("menu.options") || key.equals("menu.quit");
            if (!main) {
                if (!ladsExtraWidgets.contains(widget)) ladsExtraWidgets.add(widget);
                ladsTitleWidgets.remove(widget);
                removeWidget(widget);
            }
        }
        ladsTitleWidgets.sort(Comparator.comparingInt(this::ladsButtonOrder));
        ladsTitleLayout = TitleScreenTheme.layout(width, height, ladsTitleWidgets.size());
        ladsLayoutWidth = width;
        ladsLayoutHeight = height;
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
        LoggerFactory.getLogger("TheLadsCore-26.2").info(
            "custom title initialized for Minecraft 26.2: {} native widgets", ladsTitleWidgets.size());
    }

    @Unique
    private static String ladsMessageKey(AbstractWidget widget) {
        Component message = widget.getMessage();
        return message != null && message.getContents() instanceof TranslatableContents contents ? contents.getKey() : "";
    }

    @Unique
    private int ladsButtonOrder(AbstractWidget widget) {
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

    @Unique
    private static boolean ladsIsModMenuWidget(AbstractWidget widget) {
        String type = widget.getClass().getName();
        return type.equals("com.terraformersmc.modmenu.gui.widget.ModMenuButtonWidget")
            || type.equals("com.terraformersmc.modmenu.gui.widget.SmallModMenuButtonWidget");
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
        at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsRenderTitle(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!ladsCustomTitle) return;
        ladsEnsureTitleLayout();
        long now = System.nanoTime();
        float elapsed = ladsLastFrameNanos == 0 ? 0 : (float) Math.clamp((now - ladsLastFrameNanos) / 1.0e9, 0.0, 0.1);
        ladsLastFrameNanos = now;
        double panoramaSpeed = minecraft.options.panoramaSpeed().get();
        boolean reducedMotion = panoramaSpeed <= 0 || minecraft.options.screenEffectScale().get() <= 0;
        if (!reducedMotion) ladsAnimationSeconds += elapsed * panoramaSpeed;
        var adapter = new GuiGraphicsExtractorLadsAdapter(graphics, font);
        TitleScreenTheme.renderBackground(adapter, ladsTitleLayout, minecraft.getUser().getName(),
            "26.2" + (minecraft.isDemo() ? " Demo" : ""), false, ladsAnimationSeconds);
        TitleWidgetRegistry.beginFrame(this, adapter, elapsed, reducedMotion);
        try {
            // Calls Screen, not TitleScreen: only native widgets and extra renderables, never vanilla artwork.
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        } finally {
            TitleWidgetRegistry.endFrame();
        }
        ci.cancel();
    }

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
        at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsClickVisibleTitle(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (ladsCustomTitle) {
            ladsEnsureTitleLayout();
            // Vanilla also forwards clicks to its separate, now hidden Realms notification overlay.
            cir.setReturnValue(super.mouseClicked(event, doubleClick));
        }
    }
}
