package com.thelads.core.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;

public class BiomeHudElement extends HudElement {
    public BiomeHudElement() {
        this.x = 5;
        this.y = 45;
        this.width = 120;
        this.height = 16;
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        drawBackground(g);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        BlockPos pos = mc.player.blockPosition();
        Holder<Biome> biomeHolder = mc.level.getBiome(pos);

        boolean useId = optCycle("Format", 0) == 1;
        boolean showLabel = optBool("Show label", false);

        String biomeName;
        if (useId) {
            biomeName = biomeHolder.unwrapKey()
                .map(k -> k.identifier().toString())
                .orElse("unknown");
        } else {
            biomeName = biomeHolder.unwrapKey()
                .map(ResourceKey::identifier)
                .map(loc -> {
                    String[] words = loc.getPath().replace('_', ' ').split(" ");
                    StringBuilder sb = new StringBuilder();
                    for (String w : words) {
                        if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
                    }
                    return sb.toString().trim();
                })
                .orElse("Unknown");
        }

        String text = showLabel ? "Biome: " + biomeName : biomeName;
        drawCenteredText(g, text);
    }
}
