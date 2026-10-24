package com.thelads.core.v26_2.cosmetics.anim.mixin;

import com.thelads.core.client.cosmetics.anim.EmotePose;
import com.thelads.core.v26_2.cosmetics.anim.EmoteState26;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import java.util.UUID;

@Mixin(AvatarRenderState.class)
public class EmoteAvatarStateMixin26 implements EmoteState26 {
    @Unique private EmotePose ladsEmote;
    @Unique private UUID ladsUuid;

    @Override public EmotePose lads$emote() { return ladsEmote; }
    @Override public void lads$setEmote(EmotePose pose) { ladsEmote = pose; }

    @Override public UUID lads$uuid() { return ladsUuid; }
    @Override public void lads$setUuid(UUID uuid) { ladsUuid = uuid; }
}
