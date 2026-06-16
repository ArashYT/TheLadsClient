package com.thelads.core.features.alwayson.letmedespawn;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LetMeDespawnConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Set<String> mobNames = new HashSet<>(List.of("corpse:corpse"));
    private final Set<String> persistenceEnablers = new HashSet<>();

    public Set<String> getMobNames() {
        return this.mobNames;
    }

    public boolean addMobName(String mobName) {
        if (this.mobNames.add(mobName)) {
            this.save();
            return true;
        }
        return false;
    }

    public boolean removeMobName(String mobName) {
        if (this.mobNames.remove(mobName)) {
            this.save();
            return true;
        }
        return false;
    }

    public Set<String> getPersistenceEnablers() {
        return this.persistenceEnablers;
    }

    public boolean addPersistenceEnabler(String persistenceEnabler) {
        if (this.persistenceEnablers.add(persistenceEnabler)) {
            this.save();
            return true;
        }
        return false;
    }

    public boolean removePersistenceEnabler(String persistenceEnabler) {
        if (this.persistenceEnablers.remove(persistenceEnabler)) {
            this.save();
            return true;
        }
        return false;
    }

    public static LetMeDespawnConfig load() {
        File file = LetMeDespawn.CONFIG_FILE;
        if (!file.exists()) {
            LetMeDespawnConfig config = new LetMeDespawnConfig();
            config.save();
            return config;
        }
        try (FileReader reader = new FileReader(file)) {
            LetMeDespawnConfig config = GSON.fromJson(reader, LetMeDespawnConfig.class);
            return config != null ? config : new LetMeDespawnConfig();
        } catch (IOException e) {
            LetMeDespawn.logger.error("Failed to load LetMeDespawn config", e);
            return new LetMeDespawnConfig();
        }
    }

    public void save() {
        File file = LetMeDespawn.CONFIG_FILE;
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            LetMeDespawn.logger.error("Failed to save LetMeDespawn config", e);
        }
    }
}
