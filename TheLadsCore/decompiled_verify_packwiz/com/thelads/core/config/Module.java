/*
 * Decompiled with CFR 0.152.
 */
package com.thelads.core.config;

import com.thelads.core.config.Option;
import java.util.ArrayList;
import java.util.List;

public class Module {
    private final String name;
    private final String description;
    private boolean enabled;
    private boolean favorite;
    private long lastModified;
    private final List<Option> options = new ArrayList<Option>();

    public Module(String name, String description) {
        this.name = name;
        this.description = description;
        this.enabled = false;
    }

    public <T extends Option> T addOption(T option) {
        this.options.add(option);
        return option;
    }

    public List<Option> getOptions() {
        return this.options;
    }

    public Option getOption(String name) {
        for (Option o : this.options) {
            if (!o.getName().equals(name)) continue;
            return o;
        }
        return null;
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            this.lastModified = System.currentTimeMillis();
            if (enabled) {
                this.onEnable();
            } else {
                this.onDisable();
            }
        }
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    public void toggle() {
        this.setEnabled(!this.enabled);
    }

    public boolean isFavorite() {
        return this.favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public long getLastModified() {
        return this.lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public void touch() {
        this.lastModified = System.currentTimeMillis();
    }
}

