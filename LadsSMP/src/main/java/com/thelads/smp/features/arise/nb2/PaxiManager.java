package com.thelads.smp.features.arise.nb2;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.world.level.validation.DirectoryValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Handles automatic discovery and application of global datapacks and resource packs (Paxi).
 */
public final class PaxiManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/Paxi");
    public static final PackSource PAXI_SOURCE = PackSource.create(
            name -> net.minecraft.network.chat.Component.literal("[Paxi] ").append(name),
            true
    );

    private PaxiManager() {}

    public static boolean isPaxiEnabled() {
        return NB2Config.get().paxi;
    }

    public static Path getDatapacksDir() {
        String configured = NB2Config.get().datapacksPath;
        if (configured != null && !configured.isBlank()) {
            Path p = Path.of(configured);
            if (p.isAbsolute()) {
                return p;
            }
            try {
                if (FabricLoader.getInstance() != null && FabricLoader.getInstance().getGameDir() != null) {
                    return FabricLoader.getInstance().getGameDir().resolve(configured);
                }
            } catch (Throwable ignored) {}
            return Path.of(configured);
        }
        try {
            if (FabricLoader.getInstance() != null && FabricLoader.getInstance().getConfigDir() != null) {
                return FabricLoader.getInstance().getConfigDir().resolve("paxi").resolve("datapacks");
            }
        } catch (Throwable ignored) {}
        return Path.of("config", "paxi", "datapacks");
    }

    public static Path getResourcepacksDir() {
        String configured = NB2Config.get().resourcepacksPath;
        if (configured != null && !configured.isBlank()) {
            Path p = Path.of(configured);
            if (p.isAbsolute()) {
                return p;
            }
            try {
                if (FabricLoader.getInstance() != null && FabricLoader.getInstance().getGameDir() != null) {
                    return FabricLoader.getInstance().getGameDir().resolve(configured);
                }
            } catch (Throwable ignored) {}
            return Path.of(configured);
        }
        try {
            if (FabricLoader.getInstance() != null && FabricLoader.getInstance().getConfigDir() != null) {
                return FabricLoader.getInstance().getConfigDir().resolve("paxi").resolve("resourcepacks");
            }
        } catch (Throwable ignored) {}
        return Path.of("config", "paxi", "resourcepacks");
    }

    public static void ensureDirectoriesExist() {
        try {
            Files.createDirectories(getDatapacksDir());
            Files.createDirectories(getResourcepacksDir());
        } catch (IOException e) {
            LOGGER.warn("[Paxi] Could not create global pack directories: {}", e.getMessage());
        }
    }

    public static void injectPaxiSources(Set<RepositorySource> sources) {
        if (!isPaxiEnabled()) {
            return;
        }
        ensureDirectoriesExist();

        DirectoryValidator validator = new DirectoryValidator(path -> true);

        // Check whether this repository is for server data (datapacks) or client resources
        boolean hasServerData = false;
        boolean hasClientResources = false;

        for (RepositorySource src : sources) {
            String srcName = src.getClass().getName();
            if (srcName.contains("Server") || srcName.contains("Vanilla") || srcName.contains("DataPack")) {
                hasServerData = true;
            }
            if (srcName.contains("Client") || srcName.contains("ResourcePack")) {
                hasClientResources = true;
            }
        }

        // Add datapacks folder if server data repository
        if (hasServerData || !hasClientResources) {
            Path dpPath = getDatapacksDir();
            if (Files.exists(dpPath)) {
                sources.add(new FolderRepositorySource(dpPath, PackType.SERVER_DATA, PAXI_SOURCE, validator));
                LOGGER.info("[Paxi] Registered global datapacks source from {}", dpPath);
            }
        }

        // Add resourcepacks folder if client resources repository
        if (hasClientResources || !hasServerData) {
            Path rpPath = getResourcepacksDir();
            if (Files.exists(rpPath)) {
                sources.add(new FolderRepositorySource(rpPath, PackType.CLIENT_RESOURCES, PAXI_SOURCE, validator));
                LOGGER.info("[Paxi] Registered global resourcepacks source from {}", rpPath);
            }
        }
    }

    public static RepositorySource[] appendSources(RepositorySource[] original) {
        if (!isPaxiEnabled() || original == null) {
            return original;
        }
        ensureDirectoriesExist();

        DirectoryValidator validator = new DirectoryValidator(path -> true);
        boolean hasServerData = false;
        boolean hasClientResources = false;

        for (RepositorySource src : original) {
            if (src == null) continue;
            String srcName = src.getClass().getName();
            if (srcName.contains("Server") || srcName.contains("Vanilla") || srcName.contains("DataPack")) {
                hasServerData = true;
            }
            if (srcName.contains("Client") || srcName.contains("ResourcePack")) {
                hasClientResources = true;
            }
        }

        java.util.List<RepositorySource> list = new java.util.ArrayList<>(java.util.Arrays.asList(original));

        if (hasServerData || !hasClientResources) {
            Path dpPath = getDatapacksDir();
            list.add(new FolderRepositorySource(dpPath, PackType.SERVER_DATA, PAXI_SOURCE, validator));
        }

        if (hasClientResources || !hasServerData) {
            Path rpPath = getResourcepacksDir();
            list.add(new FolderRepositorySource(rpPath, PackType.CLIENT_RESOURCES, PAXI_SOURCE, validator));
        }

        return list.toArray(new RepositorySource[0]);
    }
}
