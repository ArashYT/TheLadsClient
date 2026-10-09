package com.thelads.core.v1_20_1.embedded.lazyai;

import com.thelads.core.v1_20_1.perf.StandDownMixinPlugin1201;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

/** Client only: a dedicated server running Core keeps vanilla AI; the integrated server is the target. */
public final class LazyAiMixinPlugin extends StandDownMixinPlugin1201 {
    @Override protected String feature() { return "LazyAI"; }
    @Override protected String[] originals() { return new String[] {"lazy_ai_pixelindiedev"}; }
    @Override protected boolean extraCondition() { return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT; }
}
