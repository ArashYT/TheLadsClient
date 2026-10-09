package com.thelads.core.v1_20_1.perf;

import com.thelads.core.v1_20_1.compat.ModPresence1201;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Plugin of an embedded feature's mixin config: none of its mixins apply while the original mod is installed, on plain
 * Fabric or as a Forge jar under Connector (see {@link ModPresence1201}). Keep this class free of Minecraft types: it
 * runs before any game class is transformed.
 */
public abstract class StandDownMixinPlugin1201 implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-StandDown");
    private boolean active = true;

    /** Short name for the log. */
    protected abstract String feature();

    /** Ids of the original mod (Fabric id and Forge id when they differ). */
    protected abstract String[] originals();

    /** Hook for subclasses that also depend on something else being absent. */
    protected boolean extraCondition() {
        return true;
    }

    public final boolean active() {
        return active;
    }

    @Override
    public void onLoad(String mixinPackage) {
        String found = null;
        for (String id : originals()) {
            if (ModPresence1201.loaded(id)) {
                found = id;
                break;
            }
        }
        active = found == null && extraCondition();
        if (found != null) LOGGER.info("Lads {}: standing down, '{}' is installed ({})", feature(), found, ModPresence1201.where(found));
        else if (active) LOGGER.info("Lads {}: active (original mod not installed)", feature());
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return active; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
