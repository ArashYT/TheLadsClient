package com.thelads.core.v1_20_1.hud.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.client.FrameAnimation;
import com.thelads.core.v1_20_1.hud.Hud1201;
import com.thelads.core.v1_20_1.hud.Raised1201;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Smooth Hotbar and Raised's frame fix on vanilla's hotbar. Gui.renderHotbar is called from Gui.render on plain Fabric and from
 * Forge's hotbar overlay under Forge, so both are covered here.
 */
@Mixin(Gui.class)
public abstract class GuiHotbarMixin {
    @Unique private final FrameAnimation lads$selection = new FrameAnimation();

    /** The selection frame (the second widgets.png blit of renderHotbar) glides to its slot, and gets its two bottom rows once the hotbar is lifted. */
    @WrapOperation(method = "renderHotbar(FLnet/minecraft/client/gui/GuiGraphics;)V", at = @At(value = "INVOKE", ordinal = 1,
        target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"), require = 1)
    private void lads$selection(GuiGraphics graphics, ResourceLocation texture, int x, int y, int u, int v, int width, int height, Operation<Void> original) {
        double speed = switch (Hud1201.choice("SmoothHotbar", "Speed", 1)) {
            case 0 -> 10;
            case 2 -> 28;
            default -> 18;
        };
        int moved = (int) Math.round(lads$selection.update(x, speed, System.nanoTime(), Hud1201.enabled("SmoothHotbar")));
        original.call(graphics, texture, moved, y, u, v, width, height);
        // The frame in widgets.png is 24 rows tall; vanilla draws 22 (the rest is off screen), which shows once the hotbar leaves the edge.
        if (Raised1201.hotbar() > 0) original.call(graphics, texture, moved, y + height, u, v + height, width, 2);
    }
}
