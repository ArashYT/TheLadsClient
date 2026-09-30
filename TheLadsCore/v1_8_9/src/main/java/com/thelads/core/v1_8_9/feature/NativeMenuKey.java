package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.MenuKeyController;
import com.thelads.core.v1_8_9.gui.LadsSettingsScreen189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

/**
 * The Lads menu key (Right Shift, rebindable in Controls; a mouse button works too) opens the menu from gameplay, the title
 * screen and the pause menu, and closes it again unless the menu is editing text. 1.8.9 reads input in two places: an open
 * screen's GuiScreen.handleInput (Forge's KeyboardInputEvent/MouseInputEvent.Pre, cancelled when the key was used) and,
 * with no screen, Minecraft.runTick's input loop (InputEvent). Both see the current LWJGL event.
 */
public final class NativeMenuKey {
    public static final KeyBinding MODULES = new KeyBinding("key.theladscore.modules", Keyboard.KEY_RSHIFT, "key.category.theladscore.controls");
    private static final MenuKeyController CONTROLLER = new MenuKeyController();
    private static Object player;

    /** Every client tick: a focus or world change can lose the release event, and the next real press must work. */
    public static void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!Display.isActive() || player != mc.thePlayer) CONTROLLER.reset();
        player = mc.thePlayer;
    }

    @SubscribeEvent
    public void screenKey(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (key()) event.setCanceled(true);
    }

    @SubscribeEvent
    public void gameplayKey(InputEvent.KeyInputEvent event) {
        key();
    }

    @SubscribeEvent
    public void screenMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (mouse()) event.setCanceled(true);
    }

    @SubscribeEvent
    public void gameplayMouse(InputEvent.MouseInputEvent event) {
        mouse();
    }

    private static boolean key() {
        // Keys LWJGL cannot name arrive as character + 256, the code KeyBinding stores for them.
        int key = Keyboard.getEventKey() == 0 ? Keyboard.getEventCharacter() + 256 : Keyboard.getEventKey();
        return route(key, !Keyboard.getEventKeyState() ? MenuKeyController.Action.RELEASE
            : Keyboard.isRepeatEvent() ? MenuKeyController.Action.REPEAT : MenuKeyController.Action.PRESS, false);
    }

    private static boolean mouse() {
        int button = Mouse.getEventButton();
        // KeyBinding stores mouse buttons as button - 100.
        return button >= 0 && route(button - 100, Mouse.getEventButtonState() ? MenuKeyController.Action.PRESS : MenuKeyController.Action.RELEASE, true);
    }

    private static boolean route(int key, MenuKeyController.Action action, boolean mouse) {
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen screen = mc.currentScreen;
        boolean canOpen = screen == null ? mc.theWorld != null && mc.thePlayer != null : screen instanceof GuiMainMenu || screen instanceof GuiIngameMenu;
        MenuKeyController.Decision decision = CONTROLLER.key(key, 0, action, key == MODULES.getKeyCode(), canOpen, screen instanceof LadsSettingsScreen189);
        if (decision == MenuKeyController.Decision.PASS) return false;
        if (decision == MenuKeyController.Decision.OPEN) {
            player = mc.thePlayer;
            mc.displayGuiScreen(new LadsSettingsScreen189(screen));
        } else if (decision == MenuKeyController.Decision.TRY_CLOSE) {
            // The menu's own editing and navigation use the key first (Shift while typing), as on the other versions.
            LadsSettingsScreen189 menu = (LadsSettingsScreen189) screen;
            boolean used = mouse ? menu.ui().mouseClicked(Mouse.getEventX() * menu.width / mc.displayWidth,
                menu.height - Mouse.getEventY() * menu.height / mc.displayHeight - 1, key + 100) : menu.keyPressed(key);
            if (!used) menu.ui().close();
            if (mc.currentScreen != screen) CONTROLLER.capture(key, 0);
        }
        return true;
    }
}
