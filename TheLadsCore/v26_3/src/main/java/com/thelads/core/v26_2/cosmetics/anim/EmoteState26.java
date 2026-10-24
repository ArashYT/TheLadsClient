package com.thelads.core.v26_2.cosmetics.anim;

import com.thelads.core.client.cosmetics.anim.EmotePose;
import java.util.UUID;

/** AvatarRenderState carries the player's emote pose and UUID from extractRenderState to the model/layers (EmoteAvatarStateMixin26). */
public interface EmoteState26 {
    EmotePose lads$emote();
    void lads$setEmote(EmotePose pose);

    UUID lads$uuid();
    void lads$setUuid(UUID uuid);
}
