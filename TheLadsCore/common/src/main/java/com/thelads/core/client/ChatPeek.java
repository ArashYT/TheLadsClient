package com.thelads.core.client;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;

/**
 * Chat peek and Shift+Enter keep open controls.
 */
public final class ChatPeek {
    private static volatile boolean peeking = false;

    private ChatPeek() {}

    public static boolean keepOpenEnabled() {
        Module chat = ModuleManager.getInstance().getModule("Chat");
        return chat != null && chat.isEnabled() && chat.getOption("Shift+Enter Keep Open") instanceof BoolOption opt && opt.get();
    }

    public static boolean peekEnabled() {
        Module chat = ModuleManager.getInstance().getModule("Chat");
        return chat != null && chat.isEnabled() && chat.getOption("Chat Peek") instanceof BoolOption opt && opt.get();
    }

    public static boolean isPeeking() {
        return peekEnabled() && peeking;
    }

    public static void setPeeking(boolean p) {
        peeking = p;
    }
}
