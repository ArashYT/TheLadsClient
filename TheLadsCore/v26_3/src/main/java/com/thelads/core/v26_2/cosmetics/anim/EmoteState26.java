package com.thelads.core.v26_2.cosmetics.anim;

import com.thelads.core.client.cosmetics.anim.EmotePose;

/** AvatarRenderState carries the player's emote pose from extractRenderState to the model (EmoteAvatarStateMixin26). */
public interface EmoteState26 {
    EmotePose lads$emote();
    void lads$setEmote(EmotePose pose);
}
