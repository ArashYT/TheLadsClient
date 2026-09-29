package com.thelads.core.v26_2.gui;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
/** Translate SDL input at the boundary of the game-independent, legacy-key UI. */
public final class CommonInput {
    private CommonInput() {}
    public static int button(int button) {
        return button == InputConstants.MOUSE_BUTTON_LEFT ? 0 : button == InputConstants.MOUSE_BUTTON_RIGHT ? 1 : button == InputConstants.MOUSE_BUTTON_MIDDLE ? 2 : button;
    }
    public static int modifiers(int mods) {
        return ((mods & InputConstants.MOD_SHIFT) != 0 ? 1 : 0) | ((mods & InputConstants.MOD_CONTROL) != 0 ? 2 : 0) | ((mods & InputConstants.MOD_ALT) != 0 ? 4 : 0) | ((mods & InputConstants.MOD_SUPER) != 0 ? 8 : 0);
    }
    public static int key(KeyEvent event) {
        return switch (event.key()) {
            case InputConstants.KEY_ESCAPE -> 256;
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> 257;
            case InputConstants.KEY_TAB -> 258;
            case InputConstants.KEY_BACKSPACE -> 259;
            case InputConstants.KEY_INSERT -> 260;
            case InputConstants.KEY_DELETE -> 261;
            case InputConstants.KEY_RIGHT -> 262;
            case InputConstants.KEY_LEFT -> 263;
            case InputConstants.KEY_DOWN -> 264;
            case InputConstants.KEY_UP -> 265;
            case InputConstants.KEY_PAGEUP -> 266;
            case InputConstants.KEY_PAGEDOWN -> 267;
            case InputConstants.KEY_HOME -> 268;
            case InputConstants.KEY_END -> 269;
            default -> Character.toUpperCase(event.shortcutKey());
        };
    }
}
