package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.CpsTracker;
import com.thelads.core.client.FrameAnimation;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.client.hud.ScoreboardHudElement;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import com.thelads.core.v1_8_9.gui.DraggableHudScreen189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.boss.BossStatus;
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
        "CPS", "Keystrokes", "Biome", "Direction", "Health", "Hunger", "TexturePacks", "ArmorHUD", "Scoreboard",
        "Paperdoll", "BossBar", "Autohide", "Clock", "Stopwatch", "ItemCounter", "ReachDisplay", "ServerAddress", "PortalCoordinates"};
    /** QA only (CoreProbe): frames in which HudManager drew the Lads HUD. */
    public static long frames;
    /** QA only (Probe172HudFlicker): the time those frames spent in HudManager. */
    static long hudNanos;
    /** QA only: SmoothHotbar's last offset of the selected-slot frame from its slot, in GUI pixels. */
    public static float selectionOffset;
    private static final FrameAnimation SELECTION = new FrameAnimation();
    private static boolean hidSidebar;

    /** SmoothHotbar (GuiIngameMixin): where the selected-slot frame drawn at x is shown, relative to x; speeds as on 1.21.x. */
    public static float selectionOffset(int x) {
        Module module = ModuleManager.getInstance().getModule("SmoothHotbar");
        int speed = module != null && module.getOption("Speed") instanceof DropdownOption ? ((DropdownOption) module.getOption("Speed")).getIndex() : 1;
        selectionOffset = (float) (SELECTION.update(x, speed == 0 ? 10 : speed == 2 ? 28 : 18, System.nanoTime(), module != null && module.isEnabled()) - x);
        return selectionOffset;
    }

    /** The Lads Scoreboard replaces vanilla's sidebar and Edit HUD hides it, as ScoreboardMixin does on 1.21.x; otherwise other mods' choice stands. */
    @SubscribeEvent
    public void overlayStart(RenderGameOverlayEvent.Pre event) {
        if (event.type == RenderGameOverlayEvent.ElementType.BOSSHEALTH) {
            // The Lads Boss Bar replaces vanilla's (two bars before 1.7.0). Vanilla counts the bar's lifetime down as it draws it.
            if (!Options189.enabled("BossBar")) return;
            if (BossStatus.bossName != null && BossStatus.statusBarTime > 0) --BossStatus.statusBarTime;
            event.setCanceled(true);
            return;
        }
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
        // Autohide fades the Lads HUD with the hotbar (Autohide189).
        Autohide189.flush();
        float shown = Autohide189.shown;
        boolean faded = shown > 0 && shown < 1 && Autohide189.begin();
        try {
            long start = System.nanoTime();
            if (shown > 0) {
                GuiLadsAdapter g = new GuiLadsAdapter(mc.fontRendererObj, event.resolution.getScaledWidth(), event.resolution.getScaledHeight());
                if (!HudCache189.render(g)) HudManager.getInstance().render(g);
            }
            hudNanos += System.nanoTime() - start;
            frames++;
        } finally {
            if (faded) Autohide189.end(shown, event.resolution.getScaledWidth_double(), event.resolution.getScaledHeight_double());
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
