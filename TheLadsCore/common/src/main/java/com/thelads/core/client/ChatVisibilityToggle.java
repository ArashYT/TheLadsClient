package com.thelads.core.client;

/**
 * Toggle chat hotkey state.
 */
public final class ChatVisibilityToggle {
    private static volatile boolean chatHidden = false;

    private ChatVisibilityToggle() {}

    public static boolean isChatHidden() {
        return chatHidden;
    }

    public static void setChatHidden(boolean hidden) {
        chatHidden = hidden;
    }

    public static boolean toggle() {
        chatHidden = !chatHidden;
        return chatHidden;
    }
}
