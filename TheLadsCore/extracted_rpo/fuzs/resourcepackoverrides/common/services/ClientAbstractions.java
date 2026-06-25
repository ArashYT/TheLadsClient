/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.chat.Component
 *  net.minecraft.server.packs.repository.Pack
 *  net.minecraft.server.packs.repository.Pack$Metadata
 *  net.minecraft.server.packs.repository.PackCompatibility
 *  net.minecraft.world.flag.FeatureFlagSet
 */
package fuzs.resourcepackoverrides.common.services;

import fuzs.resourcepackoverrides.common.services.ServiceProviderLoader;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.world.flag.FeatureFlagSet;

public interface ClientAbstractions {
    public static final ClientAbstractions INSTANCE = ServiceProviderLoader.load(ClientAbstractions.class);

    public ModLoader getModLoader();

    public Path getConfigDirectory();

    public boolean isPackHidden(Pack var1);

    public void setPackHidden(Pack var1, boolean var2);

    public Pack.Metadata createPackInfo(Component var1, PackCompatibility var2, FeatureFlagSet var3, List<String> var4, boolean var5);

    public static enum ModLoader {
        FABRIC,
        NEOFORGE;

    }
}

