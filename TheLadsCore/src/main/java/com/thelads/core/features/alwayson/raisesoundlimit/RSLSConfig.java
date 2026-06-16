package com.thelads.core.features.alwayson.raisesoundlimit;

import com.thelads.core.features.alwayson.raisesoundlimit.common.SourcesLimitProber;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

public class RSLSConfig {
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("rsls.properties");
    public static final int probedMaxSourcesCount;
    public static int maxSourcesCount;
    public static int maxStreamingSources = 8;

    static {
        int count;
        try {
            count = SourcesLimitProber.probeSourcesLimit();
        } catch (Throwable t) {
            System.err.println("Failed to probe max sources count, falling back to default value.");
            t.printStackTrace();
            count = 4095;
        }
        probedMaxSourcesCount = count;
        maxSourcesCount = count;
        loadConfig();
    }

    public static void init() {
    }

    public static void loadConfig() {
        Properties properties = new Properties();
        if (Files.isRegularFile(CONFIG_FILE)) {
            try (InputStream in = Files.newInputStream(CONFIG_FILE, StandardOpenOption.CREATE)) {
                properties.load(in);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        maxSourcesCount = Mth.clamp(getInt(properties, "maxSourcesCount", probedMaxSourcesCount), 32, probedMaxSourcesCount);
        maxStreamingSources = Mth.clamp(getInt(properties, "maxStreamingSources", 8), 8, probedMaxSourcesCount);
        saveConfig();
    }

    public static void saveConfig() {
        Properties properties = new Properties();
        properties.setProperty("maxSourcesCount", String.valueOf(maxSourcesCount));
        properties.setProperty("maxStreamingSources", String.valueOf(maxStreamingSources));
        try (OutputStream out = Files.newOutputStream(CONFIG_FILE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            properties.store(out, "Configuration file for Raise Sound Limit Simplified");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static int getInt(Properties properties, String key, int def) {
        try {
            int i = Integer.parseInt(properties.getProperty(key));
            properties.setProperty(key, String.valueOf(i));
            return i;
        } catch (NumberFormatException e) {
            properties.setProperty(key, String.valueOf(def));
            return def;
        }
    }
}
