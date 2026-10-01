package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.CpsTracker;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.client.hud.ScoreboardHudElement;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import com.thelads.core.v1_8_9.gui.DraggableHudScreen189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.GuiIngameForge;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * The Lads HUD on 1.8.9 through Forge events only (OptiFine patches GuiIngame, fonts and item rendering, so no mixin): drawn after
 * Forge's whole overlay (Post ALL), as the other versions draw it at Gui.render TAIL. F1 skips the overlay, HudManager hides it
 * under a screen while F1 is on, and the HUD editor draws its own previews instead.
 */
public final class NativeHud {
    /** The HUD modules drawn here with VanillaGameBridge189 data: built in, as on the other versions. */
    public static final String[] MODULES = {"FPS", "Coordinates", "PingHUD", "Memory", "Speed", "Day", "Time", "XP", "Potion Effects",
        "CPS", "Keystrokes", "Biome", "Direction", "Health", "Hunger", "TexturePacks", "ArmorHUD", "Scoreboard"};
    /** QA only (CoreProbe): frames in which HudManager drew the Lads HUD. */
    public static long frames;
    private static boolean hidSidebar;

    /** The Lads Scoreboard replaces vanilla's sidebar and Edit HUD hides it, as ScoreboardMixin does on 1.21.x; otherwise other mods' choice stands. */
    @SubscribeEvent
    public void overlayStart(RenderGameOverlayEvent.Pre event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        boolean hide = Minecraft.getMinecraft().currentScreen instanceof DraggableHudScreen189 || ScoreboardHudElement.shouldReplaceVanillaScoreboard();
        if (hide || hidSidebar) GuiIngameForge.renderObjective = !hide;
        hidSidebar = hide;
    }

    @SubscribeEvent
    public void overlayEnd(RenderGameOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || mc.currentScreen instanceof DraggableHudScreen189) return;
        // 2D over everything, as on the other versions. Blending and depth go back as found, the rest to the state Forge ends its overlay with.
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        GlStateManager.disableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        try {
            HudManager.getInstance().render(new GuiLadsAdapter(mc.fontRendererObj, event.resolution.getScaledWidth(), event.resolution.getScaledHeight()));
            frames++;
        } finally {
            GlStateManager.resetColor();
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            GlStateManager.disableLighting();
            GlStateManager.enableAlpha();
            GlStateManager.enableTexture2D();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
            if (depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
        }
    }

    /** CPS counts gameplay press events, several between two frames included (1.21.x: MouseHandlerMixin). */
    @SubscribeEvent
    public void mouse(InputEvent.MouseInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!Mouse.getEventButtonState() || mc.currentScreen != null || mc.thePlayer == null) return;
        if (Mouse.getEventButton() == 0) CpsTracker.get().recordLeftClick();
        else if (Mouse.getEventButton() == 1) CpsTracker.get().recordRightClick();
    }
}
