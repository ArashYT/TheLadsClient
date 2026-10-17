package com.thelads.core.v1_20_1.mixin;

import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** QA only (SoundBreakProbe1201): a channel's OpenAL source. */
@Mixin(Channel.class)
public interface ChannelQaAccessor {
    @Accessor("source") int lads$source();
}
