package com.thelads.core.v1_20_1.game.survival.mixin;

import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** QA only (ParityProbe1201): how many messages and wrapped lines the chat keeps. */
@Mixin(ChatComponent.class)
public interface ChatHistoryQaAccessor1201 {
    @Accessor("allMessages")
    List<GuiMessage> lads$allMessages();

    @Accessor("trimmedMessages")
    List<GuiMessage.Line> lads$trimmedMessages();
}
