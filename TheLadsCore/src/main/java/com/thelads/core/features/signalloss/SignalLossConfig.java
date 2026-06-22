/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.GsonBuilder
 *  net.fabricmc.loader.api.FabricLoader
 */
package com.thelads.core.features.signalloss;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.thelads.core.features.signalloss.SignalLossClient;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import net.fabricmc.loader.api.FabricLoader;

public class SignalLossConfig {
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("signalloss.json").toFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static SignalLossConfig INSTANCE;
    public boolean enabled = true;
    public int timeoutThreshold = 2000;
    public int minWarningTime = 2000;
    public int lingerTime = 1000;
    public boolean drawBackground = true;
    public boolean showInSingleplayer = false;
    public ToastPosition toastPosition = ToastPosition.CENTER;
    public int textColor = -43691;
    public int backgroundColor = -1610612736;

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE);){
                INSTANCE = (SignalLossConfig)GSON.fromJson((Reader)reader, SignalLossConfig.class);
            }
            catch (IOException e) {
                SignalLossClient.LOGGER.error("Failed to load SignalLoss config! Defaulting to standard settings.", (Throwable)e);
                INSTANCE = new SignalLossConfig();
            }
        } else {
            INSTANCE = new SignalLossConfig();
            SignalLossConfig.save();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE);){
            GSON.toJson((Object)INSTANCE, (Appendable)writer);
        }
        catch (IOException e) {
            SignalLossClient.LOGGER.error("Failed to save SignalLoss config!", (Throwable)e);
        }
    }

    public void reset() {
        this.enabled = true;
        this.timeoutThreshold = 2000;
        this.minWarningTime = 2000;
        this.lingerTime = 1000;
        this.drawBackground = true;
        this.showInSingleplayer = false;
        this.toastPosition = ToastPosition.CENTER;
        this.textColor = -43691;
        this.backgroundColor = -1610612736;
    }

    public static enum ToastPosition {
        LEFT,
        CENTER,
        RIGHT;

    }
}

