package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.ChatHeads189;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.network.NetworkPlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Chat Heads (ChatHeads189): a chat line keeps its message's sender. */
@Mixin(ChatLine.class)
public abstract class ChatLineMixin implements ChatHeads189.Line {
    @Unique private NetworkPlayerInfo ladsHead;
    @Unique private boolean ladsFirst;

    @Override public NetworkPlayerInfo ladsHead() { return ladsHead; }
    @Override public boolean ladsFirst() { return ladsFirst; }
    @Override public void ladsHead(NetworkPlayerInfo head, boolean first) { ladsHead = head; ladsFirst = first; }
}
