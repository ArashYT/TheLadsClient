package com.thelads.core.v1_8_9.social;

import com.thelads.core.client.social.ui.SocialToasts;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Draws the common social toasts on 1.8.9's HUD layer (top right); keys: SocialKeybind189. */
public final class SocialToasts189 {
    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || SocialToasts.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        int w = event.resolution.getScaledWidth(), h = event.resolution.getScaledHeight();
        SocialToasts.render(new GuiLadsAdapter(mc.fontRendererObj, w, h), w, h, -1, -1);
    }
}
