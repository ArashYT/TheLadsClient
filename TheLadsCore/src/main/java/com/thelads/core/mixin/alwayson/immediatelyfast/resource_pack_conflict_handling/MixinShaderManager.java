/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.shaders.ShaderType
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.renderer.ShaderManager
 *  net.minecraft.client.renderer.ShaderManager$Configs
 *  net.minecraft.server.packs.PackResources
 *  net.minecraft.server.packs.resources.Resource
 *  net.minecraft.server.packs.resources.ResourceManager
 *  net.minecraft.util.profiling.ProfilerFiller
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.alwayson.immediatelyfast.resource_pack_conflict_handling;

import com.mojang.blaze3d.shaders.ShaderType;
import java.io.IOException;
import java.util.HashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import com.thelads.core.features.alwayson.immediatelyfast.ImmediatelyFast;
import com.thelads.core.features.alwayson.immediatelyfast.feature.core.ImmediatelyFastResourcePackMetadata;
import com.thelads.core.features.alwayson.immediatelyfast.feature.resource_pack_conflict_handling.CoreShaderBlacklist;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ShaderManager.class})
public abstract class MixinShaderManager {
    @Inject(method={"apply(Lnet/minecraft/client/renderer/ShaderManager$Configs;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V"}, at={@At(value="RETURN")})
    private void checkForCoreShaderModifications(ShaderManager.Configs configs, ResourceManager resourceManager, ProfilerFiller profilerFiller, CallbackInfo ci) {
        PackResources resourcePackWhichBreaksFontAtlasResizing = null;
        try {
            HashSet<PackResources> breakingResourcePacks = new HashSet<PackResources>();
            for (Identifier shaderIdentifier : CoreShaderBlacklist.getBlacklist()) {
                Identifier fragmentShaderIdentifier;
                PackResources fragmentShaderResourcePack;
                Identifier vertexShaderIdentifier = ShaderType.VERTEX.idConverter().idToFile(shaderIdentifier);
                PackResources vertexShaderResourcePack = resourceManager.getResource(vertexShaderIdentifier).map(Resource::source).orElse(null);
                if (vertexShaderResourcePack != null && !vertexShaderResourcePack.equals((Object)Minecraft.getInstance().getVanillaPackResources())) {
                    breakingResourcePacks.add(vertexShaderResourcePack);
                }
                if ((fragmentShaderResourcePack = (PackResources)resourceManager.getResource(fragmentShaderIdentifier = ShaderType.FRAGMENT.idConverter().idToFile(shaderIdentifier)).map(Resource::source).orElse(null)) == null || fragmentShaderResourcePack.equals((Object)Minecraft.getInstance().getVanillaPackResources())) continue;
                breakingResourcePacks.add(fragmentShaderResourcePack);
            }
            for (PackResources resourcePack : breakingResourcePacks) {
                ImmediatelyFastResourcePackMetadata metadata = (ImmediatelyFastResourcePackMetadata)resourcePack.getMetadataSection(ImmediatelyFastResourcePackMetadata.SERIALIZER);
                if (metadata == null) {
                    metadata = ImmediatelyFastResourcePackMetadata.DEFAULT;
                }
                if (metadata.compatibleFeatures().contains("font_atlas_resizing")) continue;
                resourcePackWhichBreaksFontAtlasResizing = resourcePack;
            }
        }
        catch (IOException e) {
            ImmediatelyFast.LOGGER.error("Failed to check for core shader modifications", (Throwable)e);
        }
        if (ImmediatelyFast.config.font_atlas_resizing) {
            if (resourcePackWhichBreaksFontAtlasResizing != null) {
                ImmediatelyFast.LOGGER.warn("Resource pack " + resourcePackWhichBreaksFontAtlasResizing.packId() + " is not compatible with font atlas resizing. Temporarily disabling font atlas resizing.");
                if (ImmediatelyFast.runtimeConfig.font_atlas_resizing) {
                    ImmediatelyFast.runtimeConfig.font_atlas_resizing = false;
                    this.immediatelyFast$reloadFontStorages();
                }
            } else if (!ImmediatelyFast.runtimeConfig.font_atlas_resizing) {
                ImmediatelyFast.LOGGER.info("Re-enabling font atlas resizing because no incompatible resource packs are loaded.");
                ImmediatelyFast.runtimeConfig.font_atlas_resizing = true;
                this.immediatelyFast$reloadFontStorages();
            }
        }
    }

    @Unique
    private void immediatelyFast$reloadFontStorages() {
        Minecraft.getInstance().fontManager.updateOptions(Minecraft.getInstance().options);
    }
}

