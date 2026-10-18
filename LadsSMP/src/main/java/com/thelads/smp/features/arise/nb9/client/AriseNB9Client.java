package com.thelads.smp.features.arise.nb9.client;

import com.thelads.smp.features.arise.nb9.AriseNB9Features;
import com.thelads.smp.features.arise.nb9.entity.DyedCauldronBlockEntity;
import com.thelads.smp.features.arise.nb9.entity.PotionCauldronBlockEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint for Arise NB9 (Amendments-Style Vanilla Block Upgrades).
 * Strictly isolated behind Fabric client entrypoint so dedicated servers never load it.
 */
@Environment(EnvType.CLIENT)
public final class AriseNB9Client implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP-NB9-Client");

    @Override
    public void onInitializeClient() {
        // Register block color tint providers for cauldrons
        BlockColorRegistry.register((state, getter, pos, tints) -> {
            int color = 0x3F76E4; // default water blue
            if (getter != null && pos != null && getter.getBlockEntity(pos) instanceof DyedCauldronBlockEntity be) {
                color = be.getColor();
            }
            tints.add(color);
        }, AriseNB9Features.DYED_CAULDRON);

        BlockColorRegistry.register((state, getter, pos, tints) -> {
            int color = 0x385DC6; // default potion purple/blue
            if (getter != null && pos != null && getter.getBlockEntity(pos) instanceof PotionCauldronBlockEntity be) {
                color = be.getPotionContents().getColor();
            }
            tints.add(color);
        }, AriseNB9Features.POTION_CAULDRON);

        LOGGER.info("[LadsSMP-NB9-Client] Initialized client renderer enhancements for Amendments block upgrades");
    }
}
