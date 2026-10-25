package com.thelads.core.v1_8_9.social;

import com.thelads.core.client.social.ui.SocialToasts;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;

/** The common social toasts on 1.8.9, top right: on the HUD, and over every screen (menus too), where they take clicks; keys: SocialKeybind189. */
public final class SocialToasts189 {
    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || SocialToasts.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null) return; // drawn over the screen instead
        int w = event.resolution.getScaledWidth(), h = event.resolution.getScaledHeight();
        SocialToasts.render(new GuiLadsAdapter(mc.fontRendererObj, w, h), w, h, -1, -1);
    }

    @SubscribeEvent
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (SocialToasts.isEmpty()) return;
        int w = event.gui.width, h = event.gui.height;
        SocialToasts.render(new GuiLadsAdapter(Minecraft.getMinecraft().fontRendererObj, w, h), w, h, event.mouseX, event.mouseY);
    }

    @SubscribeEvent
    public void onScreenMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (SocialToasts.isEmpty() || !Mouse.getEventButtonState() || Mouse.getEventButton() != 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        int w = event.gui.width, h = event.gui.height;
        int x = Mouse.getEventX() * w / mc.displayWidth, y = h - Mouse.getEventY() * h / mc.displayHeight - 1;
        if (SocialToasts.mouseClicked(x, y, 0, w, h)) event.setCanceled(true);
    }
}
