// Adapted from Hovering Hotbar 26.3.0 by Fuzs (MPL-2.0); modified by The Lads: a minimal reader of the same
// config/hoveringhotbar-client.toml replaces Puzzles Lib and Forge Config API Port; ported to Minecraft 1.20.1.
package com.thelads.core.v1_20_1.embedded.hoveringhotbar.config;

import com.thelads.core.v1_20_1.embedded.hoveringhotbar.HoveringHotbar;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

public class ClientConfig {
    private static final Pattern OFFSET = Pattern.compile("(?m)^\\s*hotbar_offset\\s*=\\s*(-?\\d+)\\s*$");
    private static final Pattern MOVE_EXPERIENCE = Pattern.compile("(?m)^\\s*move_experience_above_bar\\s*=\\s*(true|false)\\s*$");

    /** Move the experience level display above the experience bar. */
    public volatile boolean moveExperienceAboveBar = true;

    private volatile int hotbarOffset = 2;
    private int configSaveDelay;

    public int getHotbarOffset() {
        return this.hotbarOffset;
    }

    public void updateHotbarOffset(int screenHeight, boolean moveUp) {
        this.hotbarOffset = Mth.clamp(this.getHotbarOffset() + (moveUp ? 1 : -1), 0, screenHeight);
        this.configSaveDelay = 40;
    }

    public void onEndClientTick(Minecraft minecraft) {
        if (this.configSaveDelay > 0 && --this.configSaveDelay == 0) {
            this.save();
        }
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(HoveringHotbar.MOD_ID + "-client.toml");
    }

    /** Reads the two values; anything missing or invalid keeps its default, as the config spec would correct it. */
    public void load() {
        Path path = path();
        try {
            if (!Files.exists(path)) {
                Files.writeString(path, this.defaultFile());
                return;
            }
            String text = Files.readString(path);
            Matcher offset = OFFSET.matcher(text);
            if (offset.find()) {
                try {
                    this.hotbarOffset = Math.max(0, Integer.parseInt(offset.group(1)));
                } catch (NumberFormatException ignored) {
                }
            }
            Matcher moveExperience = MOVE_EXPERIENCE.matcher(text);
            if (moveExperience.find()) this.moveExperienceAboveBar = Boolean.parseBoolean(moveExperience.group(1));
        } catch (IOException exception) {
            HoveringHotbar.LOGGER.warn("Failed to read {}", path, exception);
        }
    }

    private void save() {
        Path path = path();
        try {
            if (Files.exists(path)) {
                String text = Files.readString(path);
                Matcher offset = OFFSET.matcher(text);
                Files.writeString(path, offset.find()
                        ? text.substring(0, offset.start(1)) + this.hotbarOffset + text.substring(offset.end(1))
                        : text + System.lineSeparator() + "hotbar_offset = " + this.hotbarOffset + System.lineSeparator());
            } else {
                Files.writeString(path, this.defaultFile());
            }
        } catch (IOException exception) {
            HoveringHotbar.LOGGER.warn("Failed to save {}", path, exception);
        }
    }

    // hotbar_gui_layers (26.3: gui layers of other mods that move with the hotbar) has no equivalent in 1.20.1, which has no gui
    // layer system; a file written by the original mod keeps its line untouched, because save() only rewrites hotbar_offset.
    private String defaultFile() {
        return String.join(System.lineSeparator(),
                "#Move the experience level display above the experience bar.",
                "#Default Value: true",
                "move_experience_above_bar = " + this.moveExperienceAboveBar,
                "#Height offset for the hotbar from the screen bottom.",
                "# Default: 2",
                "# Range: > 0",
                "hotbar_offset = " + this.hotbarOffset,
                "");
    }
}
