package com.thelads.core.v1_8_9.social;

import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * In-game HUD toasts for friends and P2P session events on 1.8.9.
 */
public final class SocialToasts189 {
    public static final class ToastItem {
        public final String title;
        public final String body;
        public final String acceptAction;
        public final String denyAction;
        public final long expireAt;

        public ToastItem(String title, String body, String acceptAction, String denyAction) {
            this.title = title != null ? title : "";
            this.body = body != null ? body : "";
            this.acceptAction = acceptAction;
            this.denyAction = denyAction;
            this.expireAt = System.currentTimeMillis() + 15000L;
        }
    }

    private static final List<ToastItem> TOASTS = new ArrayList<>();

    public static synchronized void show(String title, String body, String acceptAction, String denyAction) {
        // TODO(S4): SocialToasts.show(title, body, acceptAction, denyAction);
        TOASTS.add(0, new ToastItem(title, body, acceptAction, denyAction));
        while (TOASTS.size() > 5) {
            TOASTS.remove(TOASTS.size() - 1);
        }
    }

    public static synchronized void acceptNewest() {
        // TODO(S4): SocialToasts.acceptNewest();
        if (!TOASTS.isEmpty()) {
            ToastItem item = TOASTS.remove(0);
            if (item.acceptAction != null) {
                SocialBridge189.handleChatAction(item.acceptAction);
            }
        }
    }

    public static synchronized void denyNewest() {
        // TODO(S4): SocialToasts.denyNewest();
        if (!TOASTS.isEmpty()) {
            ToastItem item = TOASTS.remove(0);
            if (item.denyAction != null) {
                SocialBridge189.handleChatAction(item.denyAction);
            }
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.fontRendererObj == null) return;

        synchronized (SocialToasts189.class) {
            if (TOASTS.isEmpty()) return;
            long now = System.currentTimeMillis();
            Iterator<ToastItem> it = TOASTS.iterator();
            while (it.hasNext()) {
                if (now > it.next().expireAt) {
                    it.remove();
                }
            }
            if (TOASTS.isEmpty()) return;

            int w = event.resolution.getScaledWidth();
            int h = event.resolution.getScaledHeight();
            GuiLadsAdapter g = new GuiLadsAdapter(mc.fontRendererObj, w, h);

            int toastW = 160;
            int toastH = 40;
            int x = w - toastW - 8;
            int y = 8;

            for (ToastItem toast : TOASTS) {
                g.fill(x, y, x + toastW, y + toastH, 0xD0111116);
                g.fill(x, y, x + 3, y + toastH, 0xFFCF1535);

                g.drawText(toast.title, x + 8, y + 6, 0xFFFFFFFF, true);
                g.drawText(toast.body, x + 8, y + 18, 0xFFAAAAAA, true);

                if (toast.acceptAction != null || toast.denyAction != null) {
                    String actions = (toast.acceptAction != null ? "[Accept] " : "") + (toast.denyAction != null ? "[Deny]" : "");
                    g.drawText(actions, x + 8, y + 28, 0xFF55FF55, true);
                }

                y += toastH + 6;
                if (y + toastH > h) break;
            }
        }
    }
}
