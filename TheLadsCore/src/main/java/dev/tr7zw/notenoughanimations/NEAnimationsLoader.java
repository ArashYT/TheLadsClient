/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.tr7zw.transition.loader.ModLoaderUtil
 */
package dev.tr7zw.notenoughanimations;

import dev.tr7zw.notenoughanimations.config.ConfigScreenProvider;
import dev.tr7zw.notenoughanimations.logic.AnimationProvider;
import dev.tr7zw.notenoughanimations.logic.HeldItemHandler;
import dev.tr7zw.notenoughanimations.logic.PlayerTransformer;
import dev.tr7zw.notenoughanimations.versionless.NEABaseMod;
import dev.tr7zw.notenoughanimations.versionless.config.Config;
import dev.tr7zw.notenoughanimations.versionless.config.ConfigUpgrader;
import dev.tr7zw.transition.loader.ModLoaderUtil;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public abstract class NEAnimationsLoader
extends NEABaseMod {
    public static NEAnimationsLoader INSTANCE;
    public PlayerTransformer playerTransformer;
    public HeldItemHandler heldItemHandler;
    public AnimationProvider animationProvider;
    private boolean lateInitCompleted = false;

    protected NEAnimationsLoader() {
        INSTANCE = this;
        ModLoaderUtil.disableDisplayTest();
        ModLoaderUtil.registerConfigScreen(ConfigScreenProvider::createConfigScreen);
    }

    public void onEnable() {
        if (this.settingsFile.exists()) {
            try {
                config = (Config)this.gson.fromJson(new String(Files.readAllBytes(this.settingsFile.toPath()), StandardCharsets.UTF_8), Config.class);
            }
            catch (Exception ex) {
                System.out.println("Error while loading config! Creating a new one!");
                ex.printStackTrace();
            }
        }
        if (config == null) {
            config = new Config();
            this.setupConfig();
            this.writeConfig();
        } else if (ConfigUpgrader.upgradeConfig(config)) {
            this.writeConfig();
        }
        this.enable();
    }

    private void enable() {
        this.playerTransformer = new PlayerTransformer();
        this.heldItemHandler = new HeldItemHandler();
        this.animationProvider = new AnimationProvider();
        this.heldItemHandler.onLoad();
    }

    private void lateInit() {
        this.animationProvider.refreshEnabledAnimations();
    }

    public void clientTick() {
        if (!this.lateInitCompleted) {
            this.lateInitCompleted = true;
            this.lateInit();
        }
        this.playerTransformer.nextTick();
    }
}

