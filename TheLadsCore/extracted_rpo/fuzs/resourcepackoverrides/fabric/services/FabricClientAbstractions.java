/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.base.Predicates
 *  net.fabricmc.fabric.impl.resource.pack.FabricPack
 *  net.fabricmc.loader.api.FabricLoader
 *  net.minecraft.network.chat.Component
 *  net.minecraft.server.packs.repository.Pack
 *  net.minecraft.server.packs.repository.Pack$Metadata
 *  net.minecraft.server.packs.repository.PackCompatibility
 *  net.minecraft.world.flag.FeatureFlagSet
 */
package fuzs.resourcepackoverrides.fabric.services;

import com.google.common.base.Predicates;
import fuzs.resourcepackoverrides.common.services.ClientAbstractions;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.impl.resource.pack.FabricPack;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.world.flag.FeatureFlagSet;

public final class FabricClientAbstractions
implements ClientAbstractions {
    @Override
    public ClientAbstractions.ModLoader getModLoader() {
        return ClientAbstractions.ModLoader.FABRIC;
    }

    @Override
    public Path getConfigDirectory() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isPackHidden(Pack pack) {
        return ((FabricPack)pack).fabric$isHidden();
    }

    @Override
    public void setPackHidden(Pack pack, boolean isHidden) {
        if (isHidden && !this.isPackHidden(pack)) {
            ((FabricPack)pack).fabric$setParentsPredicate((Predicate)(pack.isRequired() ? Predicates.alwaysTrue() : Predicates.alwaysFalse()));
        }
    }

    @Override
    public Pack.Metadata createPackInfo(Component descriptionComponent, PackCompatibility packCompatibility, FeatureFlagSet featureFlagSet, List<String> overlays, boolean isHidden) {
        return new Pack.Metadata(descriptionComponent, packCompatibility, featureFlagSet, overlays);
    }
}

