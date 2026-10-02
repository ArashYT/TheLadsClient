package com.thelads.core.v1_21_1.embedded.playtime;

import com.thelads.core.v1_21_1.embedded.EmbeddedMixinPlugin;

public final class PlayTimeMixinPlugin extends EmbeddedMixinPlugin {
    @Override protected String originalModId() { return WorldPlayTime.MOD_ID; }
}
