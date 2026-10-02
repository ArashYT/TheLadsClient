package com.thelads.core.v26_2.mixin;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AbstractButton.class)
public class GlobalButtonMixin {
    @Inject(method="extractDefaultSprite",at=@At("HEAD"),cancellable=true,require=1)
    private void ladsButton(GuiGraphicsExtractor g,CallbackInfo ci){
        var button=(AbstractWidget)(Object)this;
        var adapter=new com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter(g);
        com.thelads.core.client.title.TitleScreenTheme.renderButtonSurface(adapter,button.getX(),button.getY(),button.getWidth(),button.getHeight(),button.isHoveredOrFocused(),button.isFocused(),button.active,button.getAlpha(),com.thelads.core.client.title.ButtonLift.eased(button));
        ci.cancel();
    }
}
