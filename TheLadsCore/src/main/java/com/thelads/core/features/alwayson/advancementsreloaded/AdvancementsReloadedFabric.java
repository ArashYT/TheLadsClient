package com.thelads.core.features.alwayson.advancementsreloaded;

import com.thelads.core.features.alwayson.advancementsreloaded.config.ModConfigurationFile;
import com.thelads.core.features.alwayson.advancementsreloaded.utils.Utils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdvancementsReloadedFabric implements ClientModInitializer {
    public final Logger logger = LoggerFactory.getLogger("advancements_reloaded");

    @Override
    public void onInitializeClient() {
        this.logger.info("[Advancements Reloaded] Loading...");
        Utils.modVersion(this.modVersion());
        ModConfigurationFile.load(ModConfigurationFile.FileType.JSON);
        this.logger.info("[Advancements Reloaded] All done!");
    }

    private String modVersion() {
        return FabricLoader.getInstance().getModContainer("theladscore")
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("1.0.0");
    }
}
