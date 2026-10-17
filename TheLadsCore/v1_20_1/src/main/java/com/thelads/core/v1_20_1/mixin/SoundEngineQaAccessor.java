package com.thelads.core.v1_20_1.mixin;

import java.util.Map;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** QA only (SoundBreakProbe1201): the playing channels. */
@Mixin(SoundEngine.class)
public interface SoundEngineQaAccessor {
    @Accessor("instanceToChannel") Map<SoundInstance, ChannelAccess.ChannelHandle> lads$channels();
}
