package com.thelads.core.config;

import java.util.ArrayList;
import java.util.List;

public class Module {
    public enum Category { ALL, NEW, HUD, SERVER, MECHANIC }
    
    private final String name;
    private final String description;
    private boolean enabled;
    private boolean favorite;
    private long lastModified;
    private long lastOpenedTime;
    private final List<Option> options = new ArrayList<>();
    private Category category = Category.ALL;

    public Module(String name, String description) {
        this.name = name;
        this.description = description;
        this.enabled = false;
    }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

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

    public long getLastOpenedTime() {
        return lastOpenedTime;
    }

    public void setLastOpenedTime(long lastOpenedTime) {
        this.lastOpenedTime = lastOpenedTime;
    }

    /** Mark this module as just changed (for "last modified" sorting). */
    public void touch() {
        this.lastModified = System.currentTimeMillis();
    }
}
