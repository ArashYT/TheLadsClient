package com.thelads.core.v26_2.embedded.jade;

import com.thelads.core.v26_2.embedded.EmbeddedMixinPlugin;
import org.slf4j.LoggerFactory;

/**
 * Plugin of Core's Jade mixin config (theladscore.jade.mixins.json): applies none of the port's mixins while the real Jade is
 * installed, so that Jade's own jade.mixins.json is the only one. The config has Core's own name because Fabric refuses to start
 * when two mods use the same mixin config name.
 */
public final class JadeMixinPlugin extends EmbeddedMixinPlugin {
    /** Never "active" by the plain rule: the nested identity jar has the id jade too, see {@link JadeStandDown}. */
    @Override protected String originalModId() { return "jade"; }

    @Override
    public void onLoad(String mixinPackage) {
        if (JadeStandDown.external()) {
            LoggerFactory.getLogger("TheLadsCore-Embedded").info("Lads embedded jade: standing down, Jade {} is installed", JadeStandDown.externalVersion());
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !JadeStandDown.external();
    }
}
