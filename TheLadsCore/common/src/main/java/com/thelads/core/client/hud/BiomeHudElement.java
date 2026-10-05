package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

public class BiomeHudElement extends TextHudElement {
    private String rawBiome, prettyBiome;

    public BiomeHudElement() {
        super(70);
        this.x = 5;
        this.y = 45;
        this.width = 90;
        this.height = 16;
    }

    @Override
    protected String updateText(LadsGraphics g) {
        boolean useId = optCycle("Format", 0) == 1;
        String biome = useId ? g.getGame().getBiomeId() : g.getGame().getBiomeName();
        if (biome == null || biome.isBlank()) {
            biome = "Unknown";
        } else if (!useId && !biome.contains(":")) {
            // Native fallback names may be registry paths rather than localized display names; capitalised once per name.
            if (!biome.equals(rawBiome)) {
                String[] words = biome.replace('_', ' ').split(" ");
                for (int i = 0; i < words.length; i++) {
                    if (!words[i].isEmpty()) words[i] = Character.toUpperCase(words[i].charAt(0)) + words[i].substring(1);
                }
                rawBiome = biome;
                prettyBiome = String.join(" ", words);
            }
            biome = prettyBiome;
        }
        boolean label = optBool("Show label", false);
        String text = (label ? "Biome: " : "") + biome;

        return text;
    }
}
