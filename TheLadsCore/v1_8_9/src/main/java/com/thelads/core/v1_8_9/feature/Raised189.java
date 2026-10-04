package com.thelads.core.v1_8_9.feature;

import com.thelads.core.modules.RaisedModule;
import java.util.EnumSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Raised on 1.8.9 ({@link RaisedModule}): Forge's overlay events move the hotbar and the bars on it, GuiIngameForgeMixin the held
 * item name and action bar, Forge's chat event the chat (GuiNewChatMixin keeps its clicks on the lines), GuiIngameMixin draws the
 * selection frame's last rows. Raised has no 1.8.9 release, so there is no installed mod to stand down for.
 */
public final class Raised189 {
    private static final EnumSet<ElementType> HOTBAR = EnumSet.of(ElementType.HOTBAR, ElementType.HEALTH, ElementType.ARMOR,
        ElementType.FOOD, ElementType.AIR, ElementType.HEALTHMOUNT, ElementType.EXPERIENCE, ElementType.JUMPBAR);
    /** A part's matrix is pushed (Forge draws the parts one after another, never nested). */
    private boolean pushed;

    /** GUI pixels the hotbar, the bars on it, the held item name and the action bar move up now. */
    public static int hotbar() {
        RaisedModule raised = RaisedModule.get();
        return raised == null ? 0 : raised.hotbarLift(Minecraft.getMinecraft().currentScreen instanceof GuiChat);
    }

    /** GUI pixels chat moves up. */
    public static int chat() {
        RaisedModule raised = RaisedModule.get();
        return raised == null ? 0 : raised.chatLift();
    }

    /** Starts drawing a hotbar part raised; {@link GlStateManager#popMatrix()} ends it. */
    public static void push() {
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, -hotbar(), 0);
    }

    // First, so other mods drawing in the same Pre (Lads AppleSkin's exhaustion bar) move with the part.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void raise(RenderGameOverlayEvent.Pre event) {
        if (!HOTBAR.contains(event.type)) return;
        push();
        pushed = true;
    }

    // A cancelled part never reaches Post: its matrix ends here instead.
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void cancelled(RenderGameOverlayEvent.Pre event) {
        if (event.isCanceled() && pushed && HOTBAR.contains(event.type)) lower();
    }

    // Last, so other mods drawing on a part in its Post move with it.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void lower(RenderGameOverlayEvent.Post event) {
        if (pushed && HOTBAR.contains(event.type)) lower();
    }

    private void lower() {
        GlStateManager.popMatrix();
        pushed = false;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void chat(RenderGameOverlayEvent.Chat event) {
        event.posY -= chat();
    }
}
