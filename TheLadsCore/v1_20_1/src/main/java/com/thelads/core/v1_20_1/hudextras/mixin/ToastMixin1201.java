package com.thelads.core.v1_20_1.hudextras.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.thelads.core.client.hud.HudExtras;
import com.thelads.core.modules.ToastControlModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.gui.components.toasts.TutorialToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Toast Control: hidden kinds never queue; the toasts that show are drawn moved. */
@Mixin(ToastComponent.class)
public class ToastMixin1201 {
    @Inject(method = "addToast(Lnet/minecraft/client/gui/components/toasts/Toast;)V", at = @At("HEAD"), cancellable = true)
    private void lads$hide(Toast toast, CallbackInfo ci) {
        int kind = toast instanceof RecipeToast ? ToastControlModule.RECIPE : toast instanceof TutorialToast ? ToastControlModule.TUTORIAL
            : toast instanceof AdvancementToast ? ToastControlModule.ADVANCEMENT : ToastControlModule.OTHER;
        if (HudExtras.TOASTS.hides(kind)) ci.cancel();
    }

    @WrapMethod(method = "render(Lnet/minecraft/client/gui/GuiGraphics;)V")
    private void lads$move(GuiGraphics graphics, Operation<Void> original) {
        ToastControlModule toasts = HudExtras.TOASTS;
        if (toasts.dx == 0 && toasts.dy == 0) {
            original.call(graphics);
            return;
        }
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(toasts.dx, toasts.dy, 0);
            original.call(graphics);
        } finally {
            graphics.pose().popPose();
        }
    }
}
