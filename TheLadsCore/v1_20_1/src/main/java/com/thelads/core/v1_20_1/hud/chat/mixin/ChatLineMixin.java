package com.thelads.core.v1_20_1.hud.chat.mixin;

import com.thelads.core.v1_20_1.hud.chat.ChatFeatures1201;
import com.thelads.core.v1_20_1.hud.chat.NativeChatHeads1201;
import java.util.function.Supplier;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A chat line (1.20.1 lines have no parent message). Chat Heads (NativeChatHeads1201): it keeps its message's sender and whether it is
 * the message's first line. Chat's Hide Signing Indicators: the line presentation only; the message keeps its signature and tag.
 */
@Mixin(GuiMessage.Line.class)
public abstract class ChatLineMixin implements NativeChatHeads1201.Data, com.thelads.core.v1_20_1.hud.chat.ChatTweaks1201.Parent {
    /** Copy and Delete Buttons (ChatTweaks1201): the stored message this line was wrapped from. */
    @Unique private GuiMessage ladsParent;

    @Override public GuiMessage lads$parent() { return ladsParent; }
    @Override public void lads$parent(GuiMessage message) { ladsParent = message; }

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

    @Inject(method = "tag", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$hideIndicator(CallbackInfoReturnable<GuiMessageTag> cir) {
        if (ChatFeatures1201.hideChatSigning()) cir.setReturnValue(null);
    }
}
