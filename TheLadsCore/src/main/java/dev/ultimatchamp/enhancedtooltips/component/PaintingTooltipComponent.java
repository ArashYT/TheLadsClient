/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.Font
 *  net.minecraft.client.renderer.RenderPipelines
 *  net.minecraft.client.renderer.texture.TextureAtlas
 *  net.minecraft.client.renderer.texture.TextureAtlasSprite
 *  net.minecraft.core.Holder
 *  net.minecraft.core.component.DataComponents
 *  net.minecraft.world.entity.decoration.painting.PaintingVariant
 *  net.minecraft.world.item.ItemStack
 *  org.jetbrains.annotations.NotNull
 */
package dev.ultimatchamp.enhancedtooltips.component;

import dev.ultimatchamp.enhancedtooltips.component.EnhancedTooltipsTooltipComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class PaintingTooltipComponent
implements EnhancedTooltipsTooltipComponent {
    private final PaintingVariant variant;
    private final int width;
    private final int height;

    /*
     * Issues handling annotations - annotations may be inaccurate
     */
    public PaintingTooltipComponent(ItemStack stack) {
        @NotNull Holder variant = (Holder)stack.get(DataComponents.PAINTING_VARIANT);
        if (variant != null) {
            this.variant = (PaintingVariant)variant.value();
            this.width = ((PaintingVariant)variant.value()).width() * 25;
            this.height = ((PaintingVariant)variant.value()).height() * 25;
        } else {
            this.variant = null;
            this.width = 0;
            this.height = 0;
        }
    }

    @Override
    public int height() {
        return this.height;
    }

    public int getWidth(@NotNull Font textRenderer) {
        return this.width;
    }

    @Override
    public void drawImage(@NotNull Font textRenderer, int x, int y, int width, int height, @NotNull GuiGraphicsExtractor context) {
        if (this.variant == null) {
            return;
        }
        TextureAtlas paintingAtlas = (TextureAtlas)Minecraft.getInstance().getTextureManager().getTexture(Identifier.withDefaultNamespace("textures/atlas/paintings.png"));
        TextureAtlasSprite sprite = paintingAtlas.getSprite(this.variant.assetId());
        context.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, this.width, this.height);
    }
}

