package com.thelads.core.v26_2.feature.food.mixin;

import com.mojang.datafixers.util.Either;
import com.thelads.core.v26_2.feature.food.client.TooltipOverlayHandler.FoodOverlayTextComponent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** JEI splits text before the vanilla tooltip factory, so preserve our typed food component first. */
@Pseudo
@Mixin(targets = "mezz.jei.fabric.platform.RenderHelper", remap = false)
public abstract class JEIRenderHelperMixin {
    // Both pinned 26.2 renderTooltip overloads use this helper. Copy only when needed, since
    // callers may supply List.of()/unmodifiable lists and retain ownership of their input.
    @ModifyVariable(method = "createClientTooltipComponents(Ljava/util/List;Lnet/minecraft/client/gui/Font;)Ljava/util/List;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0, remap = false)
    private List<Either<FormattedText, TooltipComponent>> ladsPreserveFoodTooltip(
            List<Either<FormattedText, TooltipComponent>> elements) {
        List<Either<FormattedText, TooltipComponent>> converted = null;
        for (int i = 0; i < elements.size(); i++) {
            if (elements.get(i).left().orElse(null) instanceof FoodOverlayTextComponent food) {
                if (converted == null) converted = new ArrayList<>(elements);
                converted.set(i, Either.right(food.foodOverlay));
            }
        }
        return converted == null ? elements : converted;
    }
}
