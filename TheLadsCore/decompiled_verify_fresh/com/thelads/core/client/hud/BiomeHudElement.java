/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Holder
 *  net.minecraft.resources.ResourceKey
 */
package com.thelads.core.client.hud;

import com.thelads.core.client.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;

public class BiomeHudElement
extends HudElement {
    public BiomeHudElement() {
        this.x = 5;
        this.y = 45;
        this.width = 120;
        this.height = 16;
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        this.drawBackground(g);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        BlockPos pos = mc.player.blockPosition();
        Holder biomeHolder = mc.level.getBiome(pos);
        boolean useId = this.optCycle("Format", 0) == 1;
        boolean showLabel = this.optBool("Show label", false);
        String biomeName = useId ? biomeHolder.unwrapKey().map(k -> k.identifier().toString()).orElse("unknown") : biomeHolder.unwrapKey().map(ResourceKey::identifier).map(loc -> {
            String[] words = loc.getPath().replace('_', ' ').split(" ");
            StringBuilder sb = new StringBuilder();
            for (String w : words) {
                if (w.isEmpty()) continue;
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
            }
            return sb.toString().trim();
        }).orElse("Unknown");
        Object text = showLabel ? "Biome: " + biomeName : biomeName;
        this.drawCenteredText(g, (String)text);
    }
}

