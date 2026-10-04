package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeChatHeads;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Chat Heads (NativeChatHeads): a message keeps its sender's skin and where the sender's name starts, wraps narrower by the room
 * its head takes, and remembers its first wrapped line (only ChatComponent wraps messages, so that is the line it shows first).
 */
@Mixin(GuiMessage.class)
public abstract class ChatHeadsMessageMixin implements NativeChatHeads.Sender {
    @Unique private Supplier<PlayerSkin> ladsHead;
    @Unique private int ladsAt;
    @Unique private FormattedCharSequence ladsFirst;

    @Override public Supplier<PlayerSkin> lads$head() { return ladsHead; }
    @Override public int lads$at() { return ladsAt; }
    @Override public void lads$head(Supplier<PlayerSkin> head, int at) { ladsHead = head; ladsAt = at; }
    @Override public FormattedCharSequence lads$first() { return ladsFirst; }

    @ModifyVariable(method = "splitLines", at = @At("HEAD"), argsOnly = true, require = 1)
    private int lads$roomForHead(int maxWidth) {
        return maxWidth - NativeChatHeads.offset((GuiMessage) (Object) this);
    }

    @Inject(method = "splitLines", at = @At("RETURN"), require = 1)
    private void lads$firstLine(CallbackInfoReturnable<List<FormattedCharSequence>> cir) {
        ladsFirst = cir.getReturnValue().isEmpty() ? null : cir.getReturnValue().getFirst();
    }
}
