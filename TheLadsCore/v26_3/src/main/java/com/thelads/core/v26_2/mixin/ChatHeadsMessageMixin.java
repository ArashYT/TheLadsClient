package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeChatHeads;
import java.util.function.Supplier;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Chat Heads (NativeChatHeads): a message keeps its sender's skin, and wraps narrower by the room its head takes. */
@Mixin(GuiMessage.class)
public abstract class ChatHeadsMessageMixin implements NativeChatHeads.Sender {
    @Unique private Supplier<PlayerSkin> ladsHead;

    @Override public Supplier<PlayerSkin> lads$head() { return ladsHead; }
    @Override public void lads$head(Supplier<PlayerSkin> head) { ladsHead = head; }

    @ModifyVariable(method = "splitLines", at = @At("HEAD"), argsOnly = true, require = 1)
    private int lads$roomForHead(int maxWidth) {
        return maxWidth - NativeChatHeads.offset((GuiMessage) (Object) this);
    }
}
