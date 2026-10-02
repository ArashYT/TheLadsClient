// SPDX-License-Identifier: Apache-2.0
// Toast layout/behavior adapted from SignalLoss 1.2.1+26.2, Copyright Hexandcube (as 26.x NativeConnectionStatus).
package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.SignalLossPolicy;
import com.thelads.core.config.HudSettings;
import com.thelads.core.modules.SignalLossModule;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.network.NetworkManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/**
 * SignalLoss on 1.8.9: the shared SignalLossPolicy fed by the time the server connection last received a packet
 * (NetworkManagerMixin, as 26.x ConnectionActivityMixin), drawn over the HUD (Forge's Post ALL). 1.8.9 has no SignalLoss mod
 * whose settings could be imported and no client commands; the Lads menu holds every setting.
 */
public final class SignalLoss189 {
    /** NetworkManagerMixin: when this connection last received a packet from the server (System.nanoTime, 0 before the first). */
    public interface PacketActivity { long ladsLastPacketNanos(); }

    private static final SignalLossPolicy POLICY = new SignalLossPolicy();
    private static long lastTenths = -1;
    private static String warning = "";
    /** QA only: a warning frame drawn instead of the live one. */
    static SignalLossPolicy.Frame qaFrame;

    static SignalLossModule module() { return (SignalLossModule) Options189.module("SignalLoss"); }

    /** This frame's warning (26.x: NativeConnectionStatus.frame); a module switched off, F1 or a lone singleplayer world shows none. */
    static SignalLossPolicy.Frame frame() {
        Minecraft mc = Minecraft.getMinecraft();
        SignalLossModule module = module();
        NetHandlerPlayClient handler = mc.getNetHandler();
        NetworkManager connection = handler == null ? null : handler.getNetworkManager();
        boolean eligible = module.isEnabled() && mc.theWorld != null && mc.thePlayer != null && !mc.gameSettings.hideGUI
            && connection != null && connection.isChannelOpen() && (module.singleplayer.get() || !mc.isSingleplayer());
        long received = connection instanceof PacketActivity ? ((PacketActivity) connection).ladsLastPacketNanos() : 0;
        // 1.8.9 has no reduced-motion setting (26.x: Screen Effect Scale 0).
        return POLICY.update(connection, System.nanoTime(), received, eligible, mc.isGamePaused(), false,
            new SignalLossPolicy.Settings(SignalLossModule.milliseconds(module.timeout, 2000),
                SignalLossModule.milliseconds(module.minimum, 2000), SignalLossModule.milliseconds(module.linger, 1000)));
    }

    @SubscribeEvent
    public void overlay(RenderGameOverlayEvent.Post event) {
        if (event.type == RenderGameOverlayEvent.ElementType.ALL) renderFrame(qaFrame != null ? qaFrame : frame(), event.resolution.getScaledWidth());
    }

    static void renderFrame(SignalLossPolicy.Frame frame, int guiWidth) {
        if (frame.progress() <= 0) return;
        SignalLossModule module = module();
        FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
        long tenths = Math.round(frame.seconds() * 10);
        if (tenths != lastTenths) { lastTenths = tenths; warning = String.format(Locale.ROOT, "⚠ Waiting for server... (%.1fs)", frame.seconds()); }
        int width = font.getStringWidth(warning), height = font.FONT_HEIGHT;
        int position = module.position.getIndex();
        int x = position == 0 ? 10 : position == 2 ? guiWidth - width - 10 : (guiWidth - width) / 2;
        float eased = 1 - (1 - frame.progress()) * (1 - frame.progress());
        int hiddenY = -height - 17, y = Math.round(hiddenY + (10 - hiddenY) * eased);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        if (module.background.get()) Gui.drawRect(x - 6, y - 6, x + width + 6, y + height + 6,
            module.backgroundColor.isUseGlobal() ? HudSettings.getInstance().getGlobalBackground() : module.backgroundColor.getColor());
        font.drawStringWithShadow(warning, x, y, module.textColor.isUseGlobal() ? HudSettings.getInstance().getGlobalColor() : module.textColor.getColor());
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
    }
}
