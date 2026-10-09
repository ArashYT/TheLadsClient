package com.thelads.core.v26_2.render;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import org.jspecify.annotations.Nullable;

/** Added to SpriteContents.AnimationState (AnimationStateAnimationMixin): what AnimationCopy26 needs from its private fields. */
public interface AnimationStateLink26 {
    boolean lads$interpolates();

    /** The GPU texture of the frame the animation is on now. */
    @Nullable GpuTextureView lads$currentFrame();

    /** QA: makes the next atlas upload draw this sprite whatever its frame did. */
    void lads$markDirty();
}
