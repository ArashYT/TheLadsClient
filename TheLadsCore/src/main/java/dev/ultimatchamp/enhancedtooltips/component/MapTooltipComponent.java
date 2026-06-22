/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.Font
 *  net.minecraft.client.renderer.MapRenderer
 *  net.minecraft.client.renderer.state.MapRenderState
 *  net.minecraft.core.component.DataComponents
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.MapItem
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.saveddata.maps.MapId
 *  net.minecraft.world.level.saveddata.maps.MapItemSavedData
 *  org.jetbrains.annotations.NotNull
 */
package dev.ultimatchamp.enhancedtooltips.component;

import dev.ultimatchamp.enhancedtooltips.component.EnhancedTooltipsTooltipComponent;
import dev.ultimatchamp.enhancedtooltips.util.MatricesUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.NotNull;

public record MapTooltipComponent(ItemStack stack) implements EnhancedTooltipsTooltipComponent
{
    @Override
    public int getHeight(@NotNull Font textRenderer) {
        return 130;
    }

    public int getWidth(@NotNull Font textRenderer) {
        return 128;
    }

    @Override
    public void drawImage(@NotNull Font textRenderer, int x, int y, int width, int height, @NotNull GuiGraphicsExtractor context) {
        MapRenderer mapRenderer = Minecraft.getInstance().getMapRenderer();
        MapRenderState renderState = new MapRenderState();
        MapId mapId = (MapId)this.stack.get(DataComponents.MAP_ID);
        MapItemSavedData mapState = MapItem.getSavedData((ItemStack)this.stack, (Level)Minecraft.getInstance().level);
        if (mapState == null) {
            return;
        }
        MatricesUtil matrices = new MatricesUtil((Object)context.pose());
        matrices.pushMatrix();
        matrices.trans(x, y, 0.0f);
        matrices.scal(1.0f, 1.0f, 0.0f);
        mapRenderer.extractRenderState(mapId, mapState, renderState);
        context.map(renderState);
        matrices.popMatrix();
    }
}

