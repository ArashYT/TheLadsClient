package com.thelads.core.v1_21_11.feature.qa.mixin;

import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** QA: the stored chat messages, to check what the Chat module put on display. */
@Mixin(ChatComponent.class)
public interface ChatQaAccessor {
    @Accessor("allMessages") List<GuiMessage> ladsQaMessages();
}
