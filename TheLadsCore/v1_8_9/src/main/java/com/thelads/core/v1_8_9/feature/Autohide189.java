package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.hud.AutohideFade;
import java.util.EnumSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Autohide on 1.8.9 (1.7.0; the module was listed since 1.4.1 but drew nothing): as NativeAutohide on 26.x, the hotbar (frame,
 * selection, items, counts, durability bars), the status bars, the experience or jump bar and the Lads HUD fade out together while
 * idle. 1.8.9 draws items with opaque vertex colours, so a GL colour cannot fade them: while fading, each of these overlay elements
 * draws into an offscreen framebuffer that is then blended in at the fade's opacity. Hidden elements are skipped; shown ones draw directly.
 */
public final class Autohide189 {
    private static final EnumSet<ElementType> FADED = EnumSet.of(ElementType.HOTBAR, ElementType.HEALTH, ElementType.ARMOR,
        ElementType.FOOD, ElementType.AIR, ElementType.HEALTHMOUNT, ElementType.JUMPBAR, ElementType.EXPERIENCE);
    private static final java.nio.IntBuffer VIEWPORT = BufferUtils.createIntBuffer(16);
    private static Object player;
    private static long activity, frame;
    private static float health, opacity = 1;
    private static int food, air, slot, xp;
    /** This frame's opacity, stepped once per frame at the start of Forge's overlay. */
    public static float shown = 1;
    private static Framebuffer buffer;
    private static ElementType capturing;
    private static int previous;
    /** previous and VIEWPORT are read once per frame: every element ends back on the framebuffer and viewport it began on. */
    private static boolean queried;

    /** QA (Probe170Hud): idle for a minute, at this opacity now. */
    static void idle(float now) {
        activity = System.nanoTime() - 60_000_000_000L;
        frame = System.nanoTime();
        opacity = now;
    }

    /** Same signals as NativeAutohide.update on 26.x. */
    static float update() {
        Minecraft mc = Minecraft.getMinecraft();
        long now = System.nanoTime();
        if (mc.thePlayer == null || !Options189.enabled("Autohide")) {
            player = null;
            activity = frame = now;
            return opacity = 1;
        }
        EntityPlayerSP p = mc.thePlayer;
        int nextFood = p.getFoodStats().getFoodLevel(), nextSlot = p.inventory.currentItem;
        boolean changed = player != p || health != p.getHealth() || food != nextFood || air != p.getAir() || slot != nextSlot || xp != p.experienceTotal;
        player = p;
        health = p.getHealth();
        food = nextFood;
        air = p.getAir();
        slot = nextSlot;
        xp = p.experienceTotal;
        boolean action = mc.currentScreen != null || p.isUsingItem() || mc.gameSettings.keyBindAttack.isKeyDown() || mc.gameSettings.keyBindUseItem.isKeyDown();
        if (Options189.bool("Autohide", "Show while moving", false)) action |= p.motionX * p.motionX + p.motionZ * p.motionZ > .001 || !p.onGround;
        if (Options189.bool("Autohide", "Show when hurt or hungry", true)) action |= health < p.getMaxHealth() || food < 20 || air < 300;
        if (changed || action || activity == 0) activity = now;
        float target = (now - activity) / 1e9 < Options189.number("Autohide", "Hide after seconds", 4) ? 1 : 0;
        opacity = AutohideFade.step(opacity, target, (now - frame) / 1e9, Options189.number("Autohide", "Fade milliseconds", 350) / 1000);
        frame = now;
        return opacity;
    }

    /** Last, so a mod that cancels an element first leaves nothing captured. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void pre(RenderGameOverlayEvent.Pre event) {
        if (event.type == ElementType.ALL) {
            end(null);
            shown = update();
            queried = false;
        } else if (FADED.contains(event.type)) {
            end(null);
            if (shown <= 0) event.setCanceled(true);
            else if (shown < 1 && begin()) capturing = event.type;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void post(RenderGameOverlayEvent.Post event) {
        if (event.type == capturing || event.type == ElementType.ALL) end(event);
    }

    /** Draws what follows into the cleared offscreen framebuffer; false without framebuffers (the element then draws unfaded). */
    public static boolean begin() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!OpenGlHelper.isFramebufferEnabled()) return false;
        // Before creating or resizing the buffer, which binds framebuffer 0 when done.
        if (!queried) {
            queried = true;
            previous = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT);
        }
        boolean depth = GlState189.depth(); // creating the buffer turns depth testing on
        if (buffer == null) {
            buffer = new Framebuffer(mc.displayWidth, mc.displayHeight, true);
            buffer.setFramebufferColor(0, 0, 0, 0); // transparent black: the composite is premultiplied
        } else if (buffer.framebufferWidth != mc.displayWidth || buffer.framebufferHeight != mc.displayHeight) {
            buffer.createBindFramebuffer(mc.displayWidth, mc.displayHeight);
        }
        if (!depth) GlStateManager.disableDepth();
        buffer.framebufferClear();
        buffer.bindFramebuffer(true);
        return true;
    }

    /** Ends an element capture whose Post never came (a mod cancelled its Pre after this one ran). */
    public static void flush() { end(null); }

    private static void end(RenderGameOverlayEvent event) {
        if (capturing == null) return;
        capturing = null;
        Minecraft mc = Minecraft.getMinecraft();
        net.minecraft.client.gui.ScaledResolution res = event != null ? event.resolution : new net.minecraft.client.gui.ScaledResolution(mc);
        end(shown, res.getScaledWidth_double(), res.getScaledHeight_double());
    }

    /** Back to the framebuffer drawn before begin(), with the captured pixels blended in at alpha over the scaled GUI area. */
    public static void end(float alpha, double width, double height) {
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, previous);
        GL11.glViewport(VIEWPORT.get(0), VIEWPORT.get(1), VIEWPORT.get(2), VIEWPORT.get(3));
        boolean depth = GlState189.depth();
        GlStateManager.disableDepth();
        GlStateManager.disableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        // Drawn with straight alpha onto transparent black, the captured colours are premultiplied.
        GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(alpha, alpha, alpha, alpha);
        buffer.bindFramebufferTexture();
        float u = buffer.framebufferWidth / (float) buffer.framebufferTextureWidth, v = buffer.framebufferHeight / (float) buffer.framebufferTextureHeight;
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer quad = tessellator.getWorldRenderer();
        // The overlay's own matrix (EntityRenderer.setupOverlayRendering): an element can end with its matrix still pushed (Raised189
        // pops in the same Post), and the captured pixels already sit where that matrix drew them.
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.translate(0.0F, 0.0F, -2000.0F);
        quad.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        quad.pos(0, height, 0).tex(0, 0).endVertex();
        quad.pos(width, height, 0).tex(u, 0).endVertex();
        quad.pos(width, 0, 0).tex(u, v).endVertex();
        quad.pos(0, 0, 0).tex(0, v).endVertex();
        tessellator.draw();
        GlStateManager.popMatrix();
        buffer.unbindFramebufferTexture();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.enableAlpha();
        if (depth) GlStateManager.enableDepth();
    }
}
