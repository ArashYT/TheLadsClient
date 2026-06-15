package com.thelads.core.config;

import java.util.ArrayList;
import java.util.List;

public class Module {
    private final String name;
    private final String description;
    private boolean enabled;
    private boolean favorite;
    private long lastModified;
    private final List<Option> options = new ArrayList<>();

    public Module(String name, String description) {
        this.name = name;
        this.description = description;
        this.enabled = false;
    }

    /** Adds a customizable option and returns it (for keeping a reference). */
    public <T extends Option> T addOption(T option) {
        options.add(option);
        return option;
    }

    public List<Option> getOptions() {
        return options;
    }

    public Option getOption(String name) {
        for (Option o : options) {
            if (o.getName().equals(name)) {
                return o;
            }
        }
        return null;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            this.lastModified = System.currentTimeMillis();
            if (enabled) {
                onEnable();
            } else {
                onDisable();
            }
        }
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    public void toggle() {
        setEnabled(!this.enabled);
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    /** Mark this module as just changed (for "last modified" sorting). */
    public void touch() {
        this.lastModified = System.currentTimeMillis();
    }
}
