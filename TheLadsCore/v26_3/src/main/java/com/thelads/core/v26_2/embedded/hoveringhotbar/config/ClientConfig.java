// Adapted from Hovering Hotbar 26.3.0 by Fuzs (MPL-2.0); modified by The Lads: a minimal reader of the same
// config/hoveringhotbar-client.toml replaces Puzzles Lib and Forge Config API Port.
package com.thelads.core.v26_2.embedded.hoveringhotbar.config;

import com.google.common.collect.ImmutableSet;
import com.thelads.core.v26_2.embedded.hoveringhotbar.HoveringHotbar;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public class ClientConfig {
    private static final List<Identifier> DEFAULT_HOTBAR_GUI_LAYERS = List.of(Identifier.parse(
                    "hotbarslotcycling:cycling_slots"),
            Identifier.parse("enchantmentswitch:slot_overlay"),
            Identifier.parse("lockedinslots:slot_overlay"));
    private static final Pattern OFFSET = Pattern.compile("(?m)^\\s*hotbar_offset\\s*=\\s*(-?\\d+)\\s*$");
    private static final Pattern MOVE_EXPERIENCE = Pattern.compile("(?m)^\\s*move_experience_above_bar\\s*=\\s*(true|false)\\s*$");
    private static final Pattern LAYERS = Pattern.compile("(?ms)^\\s*hotbar_gui_layers\\s*=\\s*\\[(.*?)]");
    private static final Pattern STRING = Pattern.compile("\"([^\"]*)\"|'([^']*)'");

    /** Move the experience level display above the experience bar. Requires a game restart. */
    public boolean moveExperienceAboveBar = true;

    private int hotbarOffset = 2;
    private int configSaveDelay;
    public Set<Identifier> hotbarGuiLayers = ImmutableSet.copyOf(DEFAULT_HOTBAR_GUI_LAYERS);

    public int getHotbarOffset() {
        return this.hotbarOffset;
    }

    public void updateHotbarOffset(int screenHeight, boolean moveUp) {
        this.hotbarOffset = Math.clamp(this.getHotbarOffset() + (moveUp ? 1 : -1), 0, screenHeight);
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

    /** Reads the three values; anything missing or invalid keeps its default, as the config spec would correct it. */
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
            Matcher layers = LAYERS.matcher(text);
            if (layers.find()) {
                ImmutableSet.Builder<Identifier> builder = ImmutableSet.builder();
                Matcher entry = STRING.matcher(layers.group(1));
                while (entry.find()) {
                    Identifier identifier = Identifier.tryParse(Objects.requireNonNullElse(entry.group(1), entry.group(2)));
                    if (identifier != null) builder.add(identifier);
                }
                this.hotbarGuiLayers = builder.build();
            }
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

    private String defaultFile() {
        return String.join(System.lineSeparator(),
                "#Move the experience level display above the experience bar.",
                "#Default Value: true",
                "#Requires Restart: Game",
                "move_experience_above_bar = " + this.moveExperienceAboveBar,
                "#Height offset for the hotbar from the screen bottom.",
                "# Default: 2",
                "# Range: > 0",
                "hotbar_offset = " + this.hotbarOffset,
                "#Defines a set of gui layers that should be shifted together with the hotbar.",
                "hotbar_gui_layers = [\"hotbarslotcycling:cycling_slots\", \"enchantmentswitch:slot_overlay\", \"lockedinslots:slot_overlay\"]",
                "");
    }
}
