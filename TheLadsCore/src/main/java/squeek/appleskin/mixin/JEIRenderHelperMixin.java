/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.datafixers.util.Either
 *  mezz.jei.fabric.platform.RenderHelper
 *  net.minecraft.client.gui.Font
 *  net.minecraft.network.chat.FormattedText
 *  net.minecraft.world.inventory.tooltip.TooltipComponent
 *  net.minecraft.world.item.ItemStack
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package squeek.appleskin.mixin;

import com.mojang.datafixers.util.Either;
import java.util.List;
import java.util.Optional;
import mezz.jei.fabric.platform.RenderHelper;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import squeek.appleskin.client.TooltipOverlayHandler;

@Mixin(value={RenderHelper.class})
public class JEIRenderHelperMixin {
    @Inject(at={@At(value="HEAD")}, method={"renderTooltip"}, require=0)
    private void renderFoodPre(GuiGraphicsExtractor guiGraphics, List<Either<FormattedText, TooltipComponent>> elements, int x, int y, Font font, ItemStack stack, CallbackInfo info) {
        if (!com.thelads.core.config.ModuleManager.getInstance().getModule("AppleSkin").isEnabled()) {
            return;
        }
        for (int i = 0; i < elements.size(); ++i) {
            FormattedText left;
            Either<FormattedText, TooltipComponent> element = elements.get(i);
            Optional maybeLeft = element.left();
            if (maybeLeft.isEmpty() || !((left = (FormattedText)maybeLeft.get()) instanceof TooltipOverlayHandler.FoodOverlayTextComponent)) continue;
            TooltipOverlayHandler.FoodOverlay tooltipData = ((TooltipOverlayHandler.FoodOverlayTextComponent)left).foodOverlay;
            elements.set(i, Either.right(tooltipData));
        }
    }
}

