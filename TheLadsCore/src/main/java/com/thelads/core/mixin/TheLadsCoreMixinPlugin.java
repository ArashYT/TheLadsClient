package com.thelads.core.mixin;

import com.thelads.core.features.alwayson.immediatelyfast.ImmediatelyFast;
import com.thelads.core.features.alwayson.hyperlaunch.HyperLaunch;
import com.thelads.core.features.alwayson.vmp.common.config.Config;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.util.List;
import java.util.Set;

public class TheLadsCoreMixinPlugin implements IMixinConfigPlugin {
    private boolean hasSodium;
    private boolean hasCarpet;
    private boolean hasKrypton;
    private boolean hasRaknetify;

    @Override
    public void onLoad(String mixinPackage) {
        this.hasSodium = FabricLoader.getInstance().isModLoaded("sodium");
        this.hasCarpet = FabricLoader.getInstance().isModLoaded("carpet");
        this.hasKrypton = FabricLoader.getInstance().isModLoaded("krypton");
        this.hasRaknetify = FabricLoader.getInstance().isModLoaded("raknetify");
        
        // Trigger early initialization of ImmediatelyFast
        ImmediatelyFast.earlyInit();
        
        // Trigger early initialization of HyperLaunch
        HyperLaunch.init();
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // 1. ImmediatelyFast
        if (mixinClassName.contains(".alwayson.immediatelyfast.")) {
            if (mixinClassName.contains(".print_additional_error_information.")) {
                return false;
            }
            return true;
        }

        // 2. Better Render Distance (BRD)
        if (mixinClassName.contains(".alwayson.betterrenderdistance.")) {
            if (mixinClassName.contains(".sodium.")) {
                return this.hasSodium;
            }
            if (mixinClassName.endsWith("LevelRendererCullMixin")) {
                return !this.hasSodium;
            }
            return true;
        }

        // 3. Very Many Players (VMP)
        if (mixinClassName.contains(".alwayson.vmp.")) {
            if (mixinClassName.contains(".carpet.")) {
                return this.hasCarpet;
            }
            if (mixinClassName.contains(".networking.eventloops.")) {
                return false;
            }
            if (mixinClassName.contains(".playerwatching.optimize_nearby_entity_tracking_lookups.")) {
                return Config.USE_OPTIMIZED_ENTITY_TRACKING;
            }
            if (mixinClassName.contains(".chunk.loading.async_chunk_on_player_login.")) {
                return Config.USE_ASYNC_CHUNKS_ON_LOGIN && !isClassExist("com.ishland.c2me.opts.chunkio.common.async_chunk_on_player_login.IAsyncChunkPlayer");
            }
            if (mixinClassName.contains(".chunk.loading.command.")) {
                return Config.USE_ASYNC_CHUNKS_ON_SOME_COMMANDS;
            }
            if (mixinClassName.endsWith("MixinTACSCancelSendingKrypton")) {
                return this.hasKrypton;
            }
            if (mixinClassName.contains(".networking.avoid_deadlocks.")) {
                return !this.hasRaknetify;
            }
            return true;
        }

        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private static boolean isClassExist(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
