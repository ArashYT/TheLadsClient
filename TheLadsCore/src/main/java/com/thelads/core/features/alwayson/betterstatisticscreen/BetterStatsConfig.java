package com.thelads.core.features.alwayson.betterstatisticscreen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.loader.api.FabricLoader;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public final class BetterStatsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @SerializedName("common-registerCommands")
    private boolean commands = true;

    @SerializedName("common-apiEndpoint")
    private String apiEndpointStr = "https://api.thecsdev.com/";

    @SerializedName("client-allowChatPsaMessages")
    private boolean allowChatPsa = true;

    @SerializedName("client-guiMobsFollowCursor")
    private boolean mobsFollowCursor = true;

    private transient URI apiEndpoint = URI.create(this.apiEndpointStr);

    public BetterStatsConfig() {
    }

    public final boolean canRegisterCommands() {
        return this.commands;
    }

    @NotNull
    public final URI getApiEndpoint() {
        if (this.apiEndpoint == null) {
            try {
                this.apiEndpoint = URI.create(this.apiEndpointStr);
            } catch (Exception e) {
                this.apiEndpoint = URI.create("https://api.thecsdev.com/");
            }
        }
        return this.apiEndpoint;
    }

    public final boolean getGuiMobsFollowCursor() {
        return this.mobsFollowCursor;
    }

    public final boolean allowsChatPsaMessages() {
        return this.allowChatPsa;
    }

    public final void setRegisterCommands(boolean value) {
        this.commands = value;
    }

    public final void setApiEndpoint(@NotNull URI value) throws NullPointerException {
        Objects.requireNonNull(value);
        this.apiEndpoint = value;
        this.apiEndpointStr = value.toString();
    }

    public final void setGuiMobsFollowCursor(boolean value) {
        this.mobsFollowCursor = value;
    }

    public final void setAllowChatPsaMessages(boolean value) {
        this.allowChatPsa = value;
    }

    public void loadFromFile() {
        File file = getConfigFile();
        if (!file.exists()) {
            saveToFile();
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            BetterStatsConfig loaded = GSON.fromJson(reader, BetterStatsConfig.class);
            if (loaded != null) {
                this.commands = loaded.commands;
                this.apiEndpointStr = loaded.apiEndpointStr;
                this.allowChatPsa = loaded.allowChatPsa;
                this.mobsFollowCursor = loaded.mobsFollowCursor;
                try {
                    this.apiEndpoint = URI.create(this.apiEndpointStr);
                } catch (Exception e) {
                    this.apiEndpoint = URI.create("https://api.thecsdev.com/");
                    this.apiEndpointStr = this.apiEndpoint.toString();
                }
            }
        } catch (Exception e) {
            BetterStats.LOGGER.error("Failed to load configuration file", e);
        }
    }

    public void saveToFile() {
        File file = getConfigFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            BetterStats.LOGGER.error("Failed to save configuration file", e);
        }
    }

    private File getConfigFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("betterstats.json").toFile();
    }
}
