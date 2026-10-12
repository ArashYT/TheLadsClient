package com.thelads.core.v1_8_9.mixin;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderItem.class)
public abstract class RenderItemMixin {

    @Unique
    private static final String[] LADS_STACK_SIZES = new String[65];

    static {
        for (int i = 0; i <= 64; i++) {
            LADS_STACK_SIZES[i] = String.valueOf(i);
        }
    }

    /**
     * Item 9: Pre-allocate static strings 1..64 (and 0) for item stack sizes in GUI overlays.
     */
    @Redirect(
        method = "renderItemOverlayIntoGUI(Lnet/minecraft/client/gui/FontRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V",
        at = @At(
            value = "INVOKE",
            target = "Ljava/lang/String;valueOf(I)Ljava/lang/String;",
            remap = false
        )
    )
    private String ladsGetStackSizeString(int stackSize) {
        if (stackSize >= 0 && stackSize < LADS_STACK_SIZES.length) {
            return LADS_STACK_SIZES[stackSize];
        }
        return String.valueOf(stackSize);
    }

    /**
     * Item 10: Batch durability bar background and health quads into a single Tessellator draw.
     * In vanilla 1.8.9, renderItemOverlayIntoGUI issues 3 separate calls to draw(), each doing
     * WorldRenderer.begin(7, POSITION_COLOR) and Tessellator.getInstance().draw().
     * We batch all 3 quads into one begin/draw pair.
     */
    @Redirect(
        method = "renderItemOverlayIntoGUI(Lnet/minecraft/client/gui/FontRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RenderItem;draw(Lnet/minecraft/client/renderer/WorldRenderer;IIIIIIII)V",
            ordinal = 0
        )
    )
    private void ladsDurabilityQuad1(RenderItem instance, WorldRenderer renderer, int x, int y, int width, int height, int red, int green, int blue, int alpha) {
        renderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        ladsAddQuad(renderer, x, y, width, height, red, green, blue, alpha);
    }

    @Redirect(
        method = "renderItemOverlayIntoGUI(Lnet/minecraft/client/gui/FontRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RenderItem;draw(Lnet/minecraft/client/renderer/WorldRenderer;IIIIIIII)V",
            ordinal = 1
        )
    )
    private void ladsDurabilityQuad2(RenderItem instance, WorldRenderer renderer, int x, int y, int width, int height, int red, int green, int blue, int alpha) {
        ladsAddQuad(renderer, x, y, width, height, red, green, blue, alpha);
    }

    @Redirect(
        method = "renderItemOverlayIntoGUI(Lnet/minecraft/client/gui/FontRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RenderItem;draw(Lnet/minecraft/client/renderer/WorldRenderer;IIIIIIII)V",
            ordinal = 2
        )
    )
    private void ladsDurabilityQuad3(RenderItem instance, WorldRenderer renderer, int x, int y, int width, int height, int red, int green, int blue, int alpha) {
        ladsAddQuad(renderer, x, y, width, height, red, green, blue, alpha);
        Tessellator.getInstance().draw();
    }

    @Unique
    private static void ladsAddQuad(WorldRenderer renderer, int x, int y, int width, int height, int red, int green, int blue, int alpha) {
        renderer.pos((double) x, (double) y, 0.0D).color(red, green, blue, alpha).endVertex();
        renderer.pos((double) x, (double) (y + height), 0.0D).color(red, green, blue, alpha).endVertex();
        renderer.pos((double) (x + width), (double) (y + height), 0.0D).color(red, green, blue, alpha).endVertex();
        renderer.pos((double) (x + width), (double) y, 0.0D).color(red, green, blue, alpha).endVertex();
    }
}
