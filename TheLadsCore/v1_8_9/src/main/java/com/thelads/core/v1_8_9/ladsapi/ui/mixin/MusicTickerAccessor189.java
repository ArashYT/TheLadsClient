package com.thelads.core.v1_8_9.ladsapi.ui.mixin;

import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.MusicTicker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The background music state, so a cue with music_pause can hold vanilla music quiet and let it come back after. */
@Mixin(MusicTicker.class)
public interface MusicTickerAccessor189 {
    @Accessor("currentMusic") ISound ladsCurrentMusic();
    @Accessor("timeUntilNextMusic") int ladsTimeUntilNext();
    @Accessor("timeUntilNextMusic") void ladsSetTimeUntilNext(int ticks);
}
