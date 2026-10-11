package com.thelads.core.modules;

import com.thelads.core.client.ChatHeads;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;

/** The Chat module. Its Chat Heads options show only while Chat Heads is on, and not at all while a Chat Heads jar draws the heads. */
public final class ChatModule extends Module {
    public ChatModule(String description) { super("Chat", description); }

    public boolean hidden(Option option) {
        String name = option.getName();
        if (name.equals(ChatHeads.ENABLED)) return ChatHeads.external();
        boolean headOption = name.equals(ChatHeads.POSITION) || name.equals(ChatHeads.BY_NAME) || name.equals(ChatHeads.ALIGNED);
        return headOption && !ChatHeads.enabled();
    }
}
