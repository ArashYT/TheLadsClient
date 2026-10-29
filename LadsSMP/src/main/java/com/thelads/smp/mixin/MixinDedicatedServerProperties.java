package com.thelads.smp.mixin;

import com.thelads.smp.config.LadsConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.server.dedicated.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Consolidates 'default-server-properties' (dsp):
 * Automatically seeds default server properties on a fresh dedicated server deployment
 * from 'default-server.properties' and marks 'local/default-used.marker'.
 */
@Mixin(DedicatedServerProperties.class)
public abstract class MixinDedicatedServerProperties {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/DSP");

    @Inject(method = "fromFile", at = @At("HEAD"), cancellable = true)
    private static void ladssmp$seedDefaultServerProperties(Path path, CallbackInfoReturnable<DedicatedServerProperties> cir) {
        if (!LadsConfig.get().features.defaultServerProperties) return;

        try {
            if (Files.exists(path)) {
                return; // A/16: Existing server.properties must never be overwritten
            }

            Path gameDir = FabricLoader.getInstance().getGameDir();
            Path defaultSettingsPath = gameDir.resolve("default-server.properties");
            if (!Files.exists(defaultSettingsPath)) {
                return;
            }

            Path markerPath = gameDir.resolve("local").resolve("default-used.marker");
            if (Files.exists(markerPath)) {
                LOGGER.info("[Lads SMP/DSP] Default server properties already seeded; skipping.");
                return;
            }

            Properties props = Settings.loadFromFile(defaultSettingsPath);
            DedicatedServerProperties seeded = new DedicatedServerProperties(props);

            if (Files.notExists(markerPath.getParent())) {
                Files.createDirectories(markerPath.getParent());
            }
            Files.writeString(markerPath, "");

            LOGGER.info("[Lads SMP/DSP] Successfully seeded server properties from default-server.properties!");
            cir.setReturnValue(seeded);
        } catch (IOException e) {
            LOGGER.error("[Lads SMP/DSP] Failed to seed default server properties", e);
        }
    }
}
