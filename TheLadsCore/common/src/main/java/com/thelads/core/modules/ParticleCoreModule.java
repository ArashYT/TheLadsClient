package com.thelads.core.modules;

import com.thelads.core.client.ParticleCore;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ParticleReductionOption;
import com.thelads.core.config.SliderOption;

/**
 * Particle Core: particles outside your view are not drawn, the rest are drawn cheaper, any particle type can be cut down or
 * switched off, and potion swirls can be hidden. The module only holds the settings; the game versions' hooks read them through
 * {@link ParticleCore}. It also carries what the retired ParticleBudget module did (a per-tick quota for decorative particles).
 */
public final class ParticleCoreModule extends Module {
    public final BoolOption culling, optimized, hideOwn, hideOthers, limit, weatherCulling;
    public final ParticleReductionOption rates, sizes, colors, fade;
    public final SliderOption perTick, distance;

    public ParticleCoreModule() {
        super(ParticleCore.MODULE, "Skips drawing particles outside your view and draws the rest cheaper, with fewer lightmap and vertex lookups. "
            + "Set how much of any particle type still spawns (0% turns it off), or hide potion swirls on you or on others.");
        culling = addOption(new BoolOption(ParticleCore.CULLING, true));
        optimized = addOption(new BoolOption(ParticleCore.OPTIMIZED, true));
        hideOwn = addOption(new BoolOption(ParticleCore.HIDE_OWN, false));
        hideOthers = addOption(new BoolOption(ParticleCore.HIDE_OTHERS, false));
        rates = addOption(new ParticleReductionOption(ParticleCore.RATES));
        limit = addOption(new BoolOption(ParticleCore.LIMIT, false));
        perTick = addOption(new SliderOption(ParticleCore.PER_TICK, 64, 8, 512, 8));
        distance = addOption(new SliderOption(ParticleCore.DISTANCE, 48, 16, 128, 8));
        weatherCulling = addOption(new BoolOption(ParticleCore.WEATHER_CULLING, true));
        // OverflowParticles-style looks per type: size, color, fade out over the particle's life.
        sizes = addOption(new ParticleReductionOption(ParticleCore.SIZES, 25, 400, 100, "Drag a bar: 100% is the vanilla size. Right-click a row to reset it."));
        colors = addOption(ParticleReductionOption.colors(ParticleCore.COLORS, "Click a row to pick its color. Right-click a row to reset it."));
        fade = addOption(new ParticleReductionOption(ParticleCore.FADE, 0, 100, 0, "Drag a bar: how far a particle fades out over its life. 0% is vanilla."));
        setEnabled(true);
    }
}
