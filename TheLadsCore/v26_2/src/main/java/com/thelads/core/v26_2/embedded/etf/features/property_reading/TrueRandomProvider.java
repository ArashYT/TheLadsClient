package com.thelads.core.v26_2.embedded.etf.features.property_reading;


import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.jetbrains.annotations.Nullable;
import com.thelads.core.v26_2.embedded.etf.ETF;
import com.thelads.core.v26_2.embedded.etf.ETFApi;
import com.thelads.core.v26_2.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v26_2.embedded.etf.features.texture_handlers.ETFDirectory;
import com.thelads.core.v26_2.embedded.etf.utils.ETFEntity;
import com.thelads.core.v26_2.embedded.etf.utils.ETFUtils2;

import java.util.*;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public class TrueRandomProvider implements ETFApi.ETFVariantSuffixProvider {


    private final int[] suffixes;
    private final String packname;
    @SuppressWarnings("removal")
    protected EntityRandomSeedFunction entityRandomSeedFunction = ETFEntityRenderState::optifineId;

    private TrueRandomProvider(String secondPack, int[] suffixes) {
        this.suffixes = suffixes;
        this.packname = secondPack;
    }

    @Nullable
    public static TrueRandomProvider of(Identifier vanillaIdentifier) {
        ResourceManager resources = Minecraft.getInstance().getResourceManager();

        Identifier second = ETFDirectory.getDirectoryVersionOf(ETFUtils2.addVariantNumberSuffix(vanillaIdentifier, 2));
        if (second == null) return null;

        @Nullable String secondPack = resources.getResource(second).map(Resource::sourcePackId).orElse(null);
        @Nullable String vanillaPack = resources.getResource(vanillaIdentifier).map(Resource::sourcePackId).orElse(null);

        if (secondPack == null || !secondPack.equals(ETFUtils2.returnNameOfHighestPackFromTheseTwo(secondPack, vanillaPack))) {
            return null;
        }
        List<Integer> suffixes = new ArrayList<>();
        suffixes.add(1);
        suffixes.add(2);

        // matches optifines weird behaviour
        boolean notAllowSkip = !ETF.config().getConfig().optifine_allowWeirdSkipsInTrueRandom;

        for (int i = 3; i < suffixes.size() + 10; i++) {
            if (ETFDirectory.getDirectoryVersionOf(ETFUtils2.addVariantNumberSuffix(vanillaIdentifier, i)) != null) {
                suffixes.add(i);
            }else if (notAllowSkip){
                 break;
            }
        }

        if (suffixes.get(suffixes.size() - 1) != suffixes.size()) {
            ETFUtils2.logWarn("Random suffixes ["+suffixes+"] are not sequential for " + vanillaIdentifier + " in pack " + secondPack +
                    " this is not recommended but has been enabled in the optifine compat settings.");
        }

        return new TrueRandomProvider(secondPack, suffixes.stream().mapToInt(i -> i).toArray());
    }

    public @Nullable String getPackName() {
        return packname;
    }

    @Override
    public boolean entityCanUpdate(UUID uuid) {
        return false;
    }

    @SuppressWarnings("unused")
    @Override
    public Set<Integer> getAllSuffixes() {
        return Arrays.stream(suffixes).boxed().collect(Collectors.toSet());
    }

    @Override
    public int size() {
        return 1;
    }

    @SuppressWarnings({"unused", "removal"})
    @Override
    public int getSuffixForETFEntity(ETFEntityRenderState entityToBeTested) {
        if (entityToBeTested == null) return 0;
        return suffixes[Math.abs(entityRandomSeedFunction.toInt(entityToBeTested)) % suffixes.length] ;
    }

    @Override
    @SuppressWarnings("removal")
    public void setRandomSupplier(final EntityRandomSeedFunction entityRandomSeedFunction) {
        if (entityRandomSeedFunction != null) {
            this.entityRandomSeedFunction = entityRandomSeedFunction;
        }
    }
}
