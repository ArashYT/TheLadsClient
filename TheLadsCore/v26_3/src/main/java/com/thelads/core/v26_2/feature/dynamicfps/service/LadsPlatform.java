package com.thelads.core.v26_2.feature.dynamicfps.service;
import com.thelads.core.v26_2.feature.dynamicfps.util.Version;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
final class LadsPlatform implements Platform {
    public String getName() { return "Lads / Fabric"; }
    public Path getCacheDir() { return ensure(FabricLoader.getInstance().getGameDir().resolve(".cache/thelads/dynamicfps")); }
    public Path getConfigDir() { return ensure(FabricLoader.getInstance().getConfigDir().resolve("thelads")); }
    public boolean isDevelopmentEnvironment() { return FabricLoader.getInstance().isDevelopmentEnvironment(); }
    public boolean isModLoaded(String id) { return FabricLoader.getInstance().isModLoaded(id); }
    public Optional<Version> getModVersion(String id) {
        return FabricLoader.getInstance().getModContainer(id).flatMap(mod -> {
            try { return Optional.of(Version.of(mod.getMetadata().getVersion().getFriendlyString())); }
            catch (Version.VersionParseException ignored) { return Optional.empty(); }
        });
    }
    public void registerStartTickEvent(StartTickEvent event) { ClientTickEvents.START_CLIENT_TICK.register(mc -> event.onStartTick()); }
    private static Path ensure(Path path) {
        try { Files.createDirectories(path); return path; }
        catch (IOException failure) { throw new IllegalStateException("Could not create Lads background policy directory", failure); }
    }
}
