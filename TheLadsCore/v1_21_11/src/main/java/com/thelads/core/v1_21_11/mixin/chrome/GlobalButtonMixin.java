package com.thelads.core.v1_21_11.mixin.chrome;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Every vanilla and mod button draws the Lads title-screen surface instead of the vanilla sprite (26.x extractDefaultSprite). */
@Mixin(AbstractButton.class)
public class GlobalButtonMixin {
    @Inject(method="renderDefaultSprite",at=@At("HEAD"),cancellable=true,require=1)
    private void ladsButton(GuiGraphics g,CallbackInfo ci){
        var button=(AbstractWidget)(Object)this;
        var adapter=new com.thelads.core.v1_21_11.adapter.GuiGraphicsLadsAdapter(g);
        com.thelads.core.client.title.TitleScreenTheme.renderButtonSurface(adapter,button.getX(),button.getY(),button.getWidth(),button.getHeight(),button.isHoveredOrFocused(),button.isFocused(),button.active,button.getAlpha(),com.thelads.core.client.title.ButtonLift.eased(button));
        ci.cancel();
    }
}
