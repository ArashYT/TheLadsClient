package com.thelads.core.v1_21_1.mixin.chrome;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
/**
 * Every vanilla and mod button draws the Lads title-screen surface instead of the vanilla sprite (26.x extractDefaultSprite).
 * 1.21.1 has no sprite method to replace, so this swaps the sprite blit in renderWidget. The widget alpha is already the
 * shader colour there (setColor before the blit), so the surface is drawn opaque and faded by it once.
 */
@Mixin(AbstractButton.class)
public class GlobalButtonMixin {
    @Redirect(method="renderWidget",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"),require=1)
    private void ladsButton(GuiGraphics g,ResourceLocation sprite,int x,int y,int width,int height){
        var button=(AbstractWidget)(Object)this;
        var adapter=new com.thelads.core.v1_21_1.adapter.GuiGraphicsLadsAdapter(g);
        com.thelads.core.client.title.TitleScreenTheme.renderButtonSurface(adapter,x,y,width,height,button.isHoveredOrFocused(),button.isFocused(),button.active,1,com.thelads.core.client.title.ButtonLift.eased(button));
    }
}
