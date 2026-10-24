package com.thelads.core.v26_2.social;

import com.thelads.core.client.gui.LadsPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * HUD toast for friends and P2P social notifications on 26.3.
 */
public class SocialToast26 implements Toast {
    private static final CopyOnWriteArrayList<SocialToast26> ACTIVE = new CopyOnWriteArrayList<>();

    private final Component title;
    private final Component body;
    private final String acceptAction;
    private final String denyAction;
    private long firstRender = 0;
    private Visibility visibility = Visibility.SHOW;

    public SocialToast26(String title, String body, String acceptAction, String denyAction) {
        this.title = Component.literal(title != null ? title : "");
        this.body = Component.literal(body != null ? body : "");
        this.acceptAction = acceptAction;
        this.denyAction = denyAction;
    }

    public static void show(String title, String body, String acceptAction, String denyAction) {
        SocialToast26 toast = new SocialToast26(title, body, acceptAction, denyAction);
        ACTIVE.add(toast);
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> {
                    if (mc.gui != null && mc.gui.toastManager() != null) {
                        mc.gui.toastManager().addToast(toast);
                    }
                });
            }
        } catch (Throwable ignored) {}
    }

    public static boolean acceptNewest() {
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            SocialToast26 toast = ACTIVE.get(i);
            if (toast.acceptAction != null && !toast.acceptAction.isEmpty()) {
                toast.visibility = Visibility.HIDE;
                ACTIVE.remove(i);
                SocialBridge26.onChatActionClicked(toast.acceptAction);
                return true;
            }
        }
        return false;
    }

    public static boolean denyNewest() {
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            SocialToast26 toast = ACTIVE.get(i);
            if (toast.denyAction != null && !toast.denyAction.isEmpty()) {
                toast.visibility = Visibility.HIDE;
                ACTIVE.remove(i);
                SocialBridge26.onChatActionClicked(toast.denyAction);
                return true;
            }
        }
        return false;
    }

    @Override
    public int width() {
        return 200;
    }

    @Override
    public int height() {
        return (acceptAction != null || denyAction != null) ? 44 : 34;
    }

    @Override
    public @NotNull Visibility getWantedVisibility() {
        return visibility;
    }

    @Override
    public void update(ToastManager toastManager, long currentTime) {
        try {
            if (com.thelads.core.v26_2.feature.NativeKeyBindings.TOAST_ACCEPT.consumeClick()) {
                acceptNewest();
            } else if (com.thelads.core.v26_2.feature.NativeKeyBindings.TOAST_DENY.consumeClick()) {
                denyNewest();
            }
        } catch (Throwable ignored) {}

        if (firstRender == 0) return;
        long duration = (long) (6000L * toastManager.getNotificationDisplayTimeMultiplier());
        if (currentTime - firstRender >= duration) {
            visibility = Visibility.HIDE;
        }
    }

    @Override
    public void onFinishedRendering() {
        ACTIVE.remove(this);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long currentTime) {
        if (firstRender == 0) firstRender = currentTime;

        int w = width();
        int h = height();

        // Background card
        graphics.fill(0, 0, w, h, 0xF0130B10);
        graphics.fill(1, 1, w - 1, h - 1, 0xF01E1219);
        // Top accent line
        graphics.fill(0, 0, w, 2, LadsPalette.ACCENT);

        graphics.text(font, title, 8, 6, LadsPalette.TEXT, false);
        graphics.text(font, body, 8, 18, LadsPalette.MUTED, false);

        if (acceptAction != null || denyAction != null) {
            StringBuilder actions = new StringBuilder();
            if (acceptAction != null) {
                String acceptKey;
                try {
                    acceptKey = com.thelads.core.v26_2.feature.NativeKeyBindings.TOAST_ACCEPT.isUnbound()
                        ? "Accept"
                        : "[" + com.thelads.core.v26_2.feature.NativeKeyBindings.TOAST_ACCEPT.getTranslatedKeyMessage().getString() + "] Accept";
                } catch (Throwable t) {
                    acceptKey = "Accept";
                }
                actions.append(acceptKey).append(" ");
            }
            if (denyAction != null) {
                String denyKey;
                try {
                    denyKey = com.thelads.core.v26_2.feature.NativeKeyBindings.TOAST_DENY.isUnbound()
                        ? "Deny"
                        : "[" + com.thelads.core.v26_2.feature.NativeKeyBindings.TOAST_DENY.getTranslatedKeyMessage().getString() + "] Deny";
                } catch (Throwable t) {
                    denyKey = "Deny";
                }
                actions.append(denyKey);
            }
            graphics.text(font, Component.literal(actions.toString().trim()), 8, 30, LadsPalette.PRIMARY, false);
        }
    }
}
