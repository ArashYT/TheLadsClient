/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
 *  net.minecraft.util.FormattedCharSequence
 *  net.minecraft.world.inventory.tooltip.TooltipComponent
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package squeek.appleskin.mixin;

import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import squeek.appleskin.client.TooltipOverlayHandler;

@Mixin(value={ClientTooltipComponent.class})
public interface ClientTooltipComponentMixin
extends ClientTooltipComponent {
    @Inject(at={@At(value="HEAD")}, method={"create(Lnet/minecraft/util/FormattedCharSequence;)Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipComponent;"}, cancellable=true)
    private static void AppleSkin_of(FormattedCharSequence text, CallbackInfoReturnable<ClientTooltipComponent> info) {
        if (!com.thelads.core.config.ModuleManager.getInstance().getModule("AppleSkin").isEnabled()) {
            return;
        }
        if (text instanceof TooltipOverlayHandler.FoodOverlayTextComponent) {
            info.setReturnValue(((TooltipOverlayHandler.FoodOverlayTextComponent)text).foodOverlay);
        }
    }

    @Inject(at={@At(value="HEAD")}, method={"create(Lnet/minecraft/world/inventory/tooltip/TooltipComponent;)Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipComponent;"}, cancellable=true)
    private static void AppleSkin_ofData(TooltipComponent data, CallbackInfoReturnable<ClientTooltipComponent> info) {
        if (!com.thelads.core.config.ModuleManager.getInstance().getModule("AppleSkin").isEnabled()) {
            return;
        }
        if (data instanceof TooltipOverlayHandler.FoodOverlay) {
            info.setReturnValue((ClientTooltipComponent) ((TooltipOverlayHandler.FoodOverlay)data));
        }
    }
}

