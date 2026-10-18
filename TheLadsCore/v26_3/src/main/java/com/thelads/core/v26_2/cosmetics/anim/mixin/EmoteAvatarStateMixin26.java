package com.thelads.core.v26_2.cosmetics.anim.mixin;

import com.thelads.core.client.cosmetics.anim.EmotePose;
import com.thelads.core.v26_2.cosmetics.anim.EmoteState26;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public class EmoteAvatarStateMixin26 implements EmoteState26 {
    @Unique private EmotePose ladsEmote;

    @Override public EmotePose lads$emote() { return ladsEmote; }
    @Override public void lads$setEmote(EmotePose pose) { ladsEmote = pose; }
}
