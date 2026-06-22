/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.Font
 *  net.minecraft.client.model.geom.ModelLayers
 *  net.minecraft.client.model.object.banner.BannerFlagModel
 *  net.minecraft.client.multiplayer.ClientLevel
 *  net.minecraft.core.HolderSet
 *  net.minecraft.core.component.DataComponents
 *  net.minecraft.core.registries.Registries
 *  net.minecraft.tags.TagKey
 *  net.minecraft.util.RandomSource
 *  net.minecraft.world.item.DyeColor
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.block.entity.BannerPattern
 *  net.minecraft.world.level.block.entity.BannerPatternLayers
 *  net.minecraft.world.level.block.entity.BannerPatternLayers$Layer
 *  org.jetbrains.annotations.NotNull
 */
package dev.ultimatchamp.enhancedtooltips.component;

import dev.ultimatchamp.enhancedtooltips.component.EnhancedTooltipsTooltipComponent;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.banner.BannerFlagModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.jetbrains.annotations.NotNull;

public record BannerPatternTooltipComponent(ItemStack stack) implements EnhancedTooltipsTooltipComponent
{
    private TagKey<@NotNull BannerPattern> getBannerPatternComponent() {
        return (TagKey)((HolderSet)this.stack.get(DataComponents.PROVIDES_BANNER_PATTERNS)).unwrapKey().get();
    }

    @Override
    public int height() {
        return 45;
    }

    public int getWidth(@NotNull Font textRenderer) {
        return 20;
    }

    @Override
    public void drawImage(@NotNull Font textRenderer, int x, int y, int width, int height, @NotNull GuiGraphicsExtractor context) {
        TagKey<BannerPattern> c = this.getBannerPatternComponent();
        ClientLevel world = Minecraft.getInstance().level;
        if (world == null) {
            return;
        }
        world.registryAccess().lookup(Registries.BANNER_PATTERN).flatMap(registry -> registry.getRandomElementOf(c, RandomSource.create())).ifPresent(entry -> {
            BannerPatternLayers patterns = new BannerPatternLayers(List.of(new BannerPatternLayers.Layer(entry, DyeColor.WHITE)));
            BannerFlagModel modelPart = new BannerFlagModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.STANDING_BANNER_FLAG));
            context.bannerPattern(modelPart, DyeColor.GRAY, patterns, x, y, x + 20, y + 40);
        });
    }
}

