package com.thelads.core.v1_20_1.mixin.menus;

import com.thelads.core.client.title.ButtonLift;
import com.thelads.core.client.title.PauseMenuLayout;
import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import com.thelads.core.v1_20_1.cosmetics.anim.CosmeticsScreen1201;
import com.thelads.core.v1_20_1.gui.CompactButton1201;
import com.thelads.core.v1_20_1.gui.LadsSettingsScreen1201;
import com.thelads.core.v1_20_1.gui.PauseMultiplayer1201;
import com.thelads.core.v1_20_1.gui.ReplayScreens1201;
import com.thelads.core.v1_20_1.gui.TitleExtrasScreen1201;
import com.thelads.core.v1_20_1.gui.Widgets1201;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Lads pause menu on 1.20.1 (same grouping as 26.3's, PauseMenuLayout): Lads Client and Multiplayer (and Replays) added
 * to vanilla's grid, other mods' small icon buttons gathered under Extras, everything laid out in
 * pairs over the Lads backdrop. Other mods' full-width buttons (Forge/Quark/Create/...) stay reachable as ordinary rows.
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    @Shadow @Final private boolean showPauseMenu;
    @Unique private final List<AbstractWidget> ladsExtras = new ArrayList<>();
    @Unique private Button ladsExtrasButton;
    @Unique private CompactButton1201 ladsFullscreenButton;
    @Unique private boolean ladsLayoutReady;

    protected PauseScreenMixin() {
        super(Component.empty());
    }

    @Unique
    private void ladsLayout() {
        if (!showPauseMenu) return;
        boolean changed = !ladsLayoutReady;

        for (var child : List.copyOf(children())) {
            if (child instanceof AbstractWidget widget && widget != ladsFullscreenButton) {
                if (widget.getClass().getName().startsWith("gg.essential.")) {
                    widget.visible = false;
                    removeWidget(widget);
                    changed = true;
                } else if (widget instanceof Button && widget != ladsExtrasButton && widget.getWidth() <= 30) {
                    if (!ladsExtras.contains(widget)) ladsExtras.add(widget);
                    widget.visible = false;
                    removeWidget(widget);
                    changed = true;
                }
            }
        }

        if (!changed) return;
        ladsLayoutReady = true;
        if (!ladsExtras.isEmpty() && ladsExtrasButton == null) {
            ladsExtrasButton = addRenderableWidget(Button.builder(Component.literal("Extras..."),
                b -> minecraft.setScreen(new TitleExtrasScreen1201(this, ladsExtras))).bounds(0, 0, 204, 20).build());
        }
        var buttons = children().stream().filter(c -> c instanceof Button && c != ladsFullscreenButton).map(c -> (Button) c).toList();
        int bottom = height - 32;
        int top = Math.max(62, 12 + Math.min(64, height / 6) + 18);
        var slots = buttons.stream().map(this::ladsSlot).toList();
        var boxes = PauseMenuLayout.arrange(slots, width, top, bottom);
        for (int i = 0; i < buttons.size(); i++) {
            var widget = buttons.get(i);
            var box = boxes.get(i);
            Widgets1201.setBounds(widget, box.x(), box.y(), box.width(), box.height());
            ButtonLift.enable(widget, PauseMenuLayout.icon(slots.get(i)));
        }
    }

    @Unique
    private PauseMenuLayout.Slot ladsSlot(Button button) {
        String text = button.getMessage().getString();
        if (button == ladsExtrasButton) return PauseMenuLayout.Slot.EXTRAS;
        if (text.equals("Lads Client")) return PauseMenuLayout.Slot.LADS;
        if (text.equals("Cosmetics")) return PauseMenuLayout.Slot.COSMETICS;
        if (text.equals("Screenshots")) return PauseMenuLayout.Slot.SCREENSHOTS;
        if (text.equals("Replays")) return PauseMenuLayout.Slot.REPLAYS;
        if (text.contains("Host") || text.contains("Invite")) return PauseMenuLayout.Slot.WORLD;
        String[][] keys = {{"menu.returnToGame", "BACK"}, {"gui.advancements", "ADVANCEMENTS"}, {"gui.stats", "STATS"}, {"menu.options", "OPTIONS"},
            {"menu.multiplayer", "MULTIPLAYER"}, {"menu.multiplayerOptions.button", "WORLD"}, {"options.worldOptions.button", "WORLD"},
            {"menu.worldOptions", "WORLD"}, {"menu.shareToLan", "WORLD"}, {"menu.returnToMenu", "QUIT"}, {"menu.disconnect", "QUIT"}};
        for (String[] key : keys) {
            if (text.equals(Component.translatable(key[0]).getString())) return PauseMenuLayout.Slot.valueOf(key[1]);
        }
        return PauseMenuLayout.Slot.OTHER;
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsPauseTheme(GuiGraphics g, int mx, int my, float dt, CallbackInfo ci) {
        if (!showPauseMenu) return;
        ladsLayout();
        g.fill(0, 0, width, height, 0xB8100B10);
        g.fill(0, 0, width, 2, 0xFFCF1535);
        var adapter = new GuiGraphicsLadsAdapter1201(g, font);
        TitleScreenTheme.renderLogo(adapter, width / 2, 12, Math.min(64, height / 6));
        g.fill(16, height - 29, width - 16, height - 28, 0x2944202A);
        TitleScreenTheme.renderAccount(adapter, height, minecraft.getUser().getName(), Math.min(170, width - 32));
        if (!com.thelads.core.client.ConfirmDisconnectHelper.isConfirming(System.currentTimeMillis())) {
            for (var child : children()) {
                if (child instanceof Button b && b.getMessage().getString().contains("Confirm Disconnect?")) {
                    b.setMessage(Component.translatable(minecraft != null && !minecraft.isLocalServer() ? "menu.disconnect" : "menu.returnToMenu"));
                }
            }
        }
        super.render(g, mx, my, dt);
        ci.cancel();
    }

    @Inject(method = "init()V", at = @At("TAIL"), require = 1)
    private void ladsRemoveReportButtons(CallbackInfo ci) {
        ladsLayoutReady = false;
        ladsExtras.clear();
        ladsExtrasButton = null;
        ladsFullscreenButton = null;
        if (showPauseMenu) {
            ladsFullscreenButton = addRenderableWidget(CompactButton1201.fullscreen());
            ladsFullscreenButton.placeFullscreen(font, width);
        }
        for (var child : List.copyOf(children())) {
            if (child instanceof AbstractWidget widget) {
                String text = widget.getMessage().getString();
                if (widget instanceof StringWidget) {
                    removeWidget(widget);
                    continue;
                }
                if (text.equals(Component.translatable("menu.sendFeedback").getString())
                    || text.equals(Component.translatable("menu.reportBugs").getString())
                    || text.equals(Component.translatable("menu.playerReporting").getString())) {
                    removeWidget(widget);
                }
            }
        }
        for (var child : List.copyOf(children())) {
            if (child instanceof Button btn && ladsSlot(btn) == PauseMenuLayout.Slot.QUIT) {
                removeWidget(btn);
                addRenderableWidget(Button.builder(btn.getMessage(), b -> {
                    boolean isMultiplayer = minecraft != null && !minecraft.isLocalServer();
                    com.thelads.core.config.Module mod = com.thelads.core.config.ModuleManager.getInstance().getModule("ConfirmDisconnect");
                    boolean enabled = mod instanceof com.thelads.core.modules.ConfirmDisconnectModule cdm && cdm.isEnabled();
                    long timeout = mod instanceof com.thelads.core.modules.ConfirmDisconnectModule cdm ? cdm.getTimeoutMillis() : 3000L;
                    if (!com.thelads.core.client.ConfirmDisconnectHelper.onClickDisconnect(System.currentTimeMillis(), timeout, isMultiplayer, enabled)) {
                        b.setMessage(Component.literal("\u00a7cConfirm Disconnect?"));
                        return;
                    }
                    btn.onPress();
                }).bounds(btn.getX(), btn.getY(), btn.getWidth(), btn.getHeight()).build());
            }
        }
    }

    @Inject(method = "createPauseMenu()V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/layouts/GridLayout;arrangeElements()V"),
        require = 1)
    private void ladsAddPauseButton(CallbackInfo ci, @Local GridLayout.RowHelper ladsPauseRows) {
        ladsPauseRows.addChild(Button.builder(Component.literal("Lads Client"),
            button -> minecraft.setScreen(new LadsSettingsScreen1201(this))).width(204).build(), 2);
        ladsPauseRows.addChild(Button.builder(Component.translatable("menu.multiplayer"),
            button -> PauseMultiplayer1201.open(this)).width(204).build(), 2);
        ladsPauseRows.addChild(Button.builder(Component.literal("Screenshots"),
            button -> com.thelads.core.v1_20_1.feature.screenshots.ScreenshotViewer1201.openFromMenu(this)).width(204).build(), 2);
        ladsPauseRows.addChild(Button.builder(Component.literal("Cosmetics"),
            button -> minecraft.setScreen(new CosmeticsScreen1201(this))).width(204).build(), 2);
        if (ReplayScreens1201.available()) {
            ladsPauseRows.addChild(Button.builder(Component.literal("Replays"),
                button -> ReplayScreens1201.open(this)).width(204).build(), 2);
        }
        LoggerFactory.getLogger("TheLadsCore").info("Lads Client pause-menu button initialized");
    }
}
