package com.thelads.core.v1_8_9.ladsapi.ui;

import com.thelads.core.client.hud.DialogOverlay;
import com.thelads.core.client.ladsapi.LadsApiClient;
import com.thelads.core.client.ladsapi.minigame.Caps;
import com.thelads.core.client.ladsapi.minigame.Hooks;
import com.thelads.core.client.ladsapi.minigame.LockState.Lock;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import com.thelads.core.config.SessionProfiles;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * The input and 2D half of the minigame API on 1.8.9 (Lads API sections 8-23): the per-tick plumbing, lock, server action keys,
 * dialogs, audio, profile switch and the input device. The HUD widgets and the run timer are common HUD elements already in the
 * HUD. Each cap is declared only after its hook is installed. No rumble sink exists on 1.8.9, so rumble is not declared; the
 * input cap reports keyboard and mouse only (no gamepad support here).
 */
public final class LadsApiUi189 {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    private static Audio189 audio;
    private static ServerKeys189 keys;
    private static final boolean QA = System.getenv("LADS_QA_LADSUI") != null || System.getProperty("thelads.qaLadsUi") != null;

    private LadsApiUi189() {}

    public static void register() {
        audio = new Audio189();
        keys = new ServerKeys189();
        Hooks.input = new InputDevice189();
        Hooks.sound = audio;
        Hooks.profiles = new SessionProfiles();
        Hooks.keys = keys;
        MinecraftForge.EVENT_BUS.register(new LadsApiUi189());
        for (String cap : new String[] {Caps.LOCK, Caps.ACTION, Caps.DIALOG, Caps.HUD, Caps.RUN, Caps.PROFILE, Caps.INPUT, Caps.AUDIO}) {
            LadsApiClient.capability(cap);
        }
        if (QA) LadsApiUiQa189.init(audio, keys);
        LOG.info("Lads API 1.8.9 input/2D layer ready (keyboard and mouse only; no rumble sink on this version).");
    }

    // ---- lock (called by the mixins)

    /** Lock: whether the key reads as released now. Only ever removes input; the pause key, chat and the rest are not mapped. */
    public static boolean locksKey(KeyBinding k) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.currentScreen != null || mc.gameSettings == null) return false;
        if (!Minigame.lock.any(Minigame.now())) return false;
        GameSettings o = mc.gameSettings;
        if (k == o.keyBindForward || k == o.keyBindBack || k == o.keyBindLeft || k == o.keyBindRight) return Minigame.locked(Lock.MOVE);
        if (k == o.keyBindJump) return Minigame.locked(Lock.JUMP);
        if (k == o.keyBindSneak) return Minigame.locked(Lock.SNEAK);
        if (k == o.keyBindSprint) return Minigame.locked(Lock.SPRINT);
        if (k == o.keyBindAttack) return Minigame.locked(Lock.ATTACK);
        if (k == o.keyBindUseItem) return Minigame.locked(Lock.USE);
        if (k == o.keyBindDrop) return Minigame.locked(Lock.DROP);
        if (k == o.keyBindInventory) return Minigame.locked(Lock.INVENTORY);
        for (KeyBinding slot : o.keyBindsHotbar) if (k == slot) return Minigame.locked(Lock.HOTBAR);
        return false;
    }

    /** A queued press that must not reach the game: a locked key, or the number key that answers an open vote or modal. */
    public static boolean eatsPress(KeyBinding k) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.currentScreen != null || mc.gameSettings == null) return false;
        if (DialogOverlay.numbered() != null) {
            KeyBinding[] slots = mc.gameSettings.keyBindsHotbar;
            for (int i = 0; i < slots.length && i < 6; i++) if (k == slots[i] && DialogOverlay.choose(i)) return true;
        }
        return locksKey(k);
    }

    public static boolean locksLook(Entity e) {
        Minecraft mc = Minecraft.getMinecraft();
        return e == mc.thePlayer && e != null && mc.currentScreen == null && Minigame.lock.any(Minigame.now()) && Minigame.locked(Lock.LOOK);
    }

    public static boolean locksScroll(InventoryPlayer inv) {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.thePlayer != null && inv == mc.thePlayer.inventory && mc.currentScreen == null && Minigame.lock.any(Minigame.now())
            && Minigame.locked(Lock.HOTBAR);
    }

    // ---- events

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            LadsApiClient.pollInput();
            keys.tick(QA);
            audio.tick();
            if (mc.thePlayer != null && mc.currentScreen == null && DialogOverlay.modal() != null) mc.displayGuiScreen(new DialogScreen189());
            if (QA) LadsApiUiQa189.tick();
        } catch (RuntimeException e) {
            LOG.warn("Lads API client tick failed", e);
        }
    }

    /** Toasts and votes over the bare game (a modal opens its own screen). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void overlay(RenderGameOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || mc.currentScreen != null || !DialogOverlay.any()) return;
        ScaledResolution res = event.resolution;
        drawOverlay(new GuiLadsAdapter(mc.fontRendererObj, res.getScaledWidth(), res.getScaledHeight()), -1, -1);
    }

    /** Over other screens (chat, inventory ...) but never over the pause menu or the modal screen, which draws itself. */
    @SubscribeEvent
    public void overScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        GuiScreen gui = event.gui;
        if (gui instanceof GuiIngameMenu || gui instanceof DialogScreen189 || !DialogOverlay.any()) return;
        if (Minecraft.getMinecraft().theWorld == null) return;
        drawOverlay(new GuiLadsAdapter(Minecraft.getMinecraft().fontRendererObj, gui.width, gui.height), event.mouseX, event.mouseY);
    }

    private static void drawOverlay(GuiLadsAdapter g, int mx, int my) {
        GlStateManager.disableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        try {
            DialogOverlay.render(g, mx, my);
        } finally {
            GlStateManager.resetColor();
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            GlStateManager.enableTexture2D();
        }
    }

    /** A click on a card of another screen answers the dialog and goes no further. */
    @SubscribeEvent
    public void click(GuiScreenEvent.MouseInputEvent.Pre event) {
        InputDevice189.stamp();
        GuiScreen gui = event.gui;
        if (gui instanceof GuiIngameMenu || gui instanceof DialogScreen189 || !DialogOverlay.any()) return;
        if (Mouse.getEventButton() != 0 || !Mouse.getEventButtonState()) return;
        Minecraft mc = Minecraft.getMinecraft();
        int x = Mouse.getEventX() * gui.width / mc.displayWidth;
        int y = gui.height - Mouse.getEventY() * gui.height / mc.displayHeight - 1;
        if (DialogOverlay.click(x, y)) event.setCanceled(true);
    }

    @SubscribeEvent
    public void keyboardOnScreen(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        InputDevice189.stamp();
    }

    @SubscribeEvent
    public void keyInGame(InputEvent.KeyInputEvent event) {
        InputDevice189.stamp();
    }

    @SubscribeEvent
    public void mouseInGame(InputEvent.MouseInputEvent event) {
        InputDevice189.stamp();
    }
}
