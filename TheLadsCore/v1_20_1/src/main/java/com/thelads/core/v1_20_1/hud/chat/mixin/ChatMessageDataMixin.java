package com.thelads.core.v1_20_1.hud.chat.mixin;

import com.thelads.core.v1_20_1.hud.chat.NativeChatHeads1201;
import java.util.function.Supplier;
import net.minecraft.client.GuiMessage;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Chat Heads (NativeChatHeads1201): a stored chat message keeps its sender's skin and where the sender's name starts. */
@Mixin(GuiMessage.class)
public abstract class ChatMessageDataMixin implements NativeChatHeads1201.Data {
    @Unique private Supplier<ResourceLocation> ladsHead;
    @Unique private int ladsAt;
    @Unique private boolean ladsFirst;

    @Override public Supplier<ResourceLocation> lads$head() { return ladsHead; }
    @Override public int lads$at() { return ladsAt; }
    @Override public boolean lads$first() { return ladsFirst; }
    @Override public void lads$set(Supplier<ResourceLocation> head, int at, boolean first) {
        ladsHead = head;
        ladsAt = at;
        ladsFirst = first;
    }
}
