package com.thelads.core.v26_2.mixin;
import com.thelads.core.client.title.ButtonLift;
import com.thelads.core.client.title.TitleScreenTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
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
        TitleScreenTheme.renderButtonSurface(adapter,button.getX(),button.getY(),button.getWidth(),button.getHeight(),button.isHoveredOrFocused(),button.isFocused(),button.active,button.getAlpha(),ButtonLift.eased(button));
        // The pause menu's buttons: an icon before the label, the two centred together (ladsIconLabel).
        String icon=ButtonLift.icon(button);
        if(icon!=null)TitleScreenTheme.renderLabelIcon(adapter,icon,button.getX(),button.getY(),button.getWidth(),button.getHeight(),
            Minecraft.getInstance().font.width(button.getMessage()),button.active?0xFFFFFFFF:0xFFA0A0A0);
        ci.cancel();
    }

    @Inject(method="extractDefaultLabel",at=@At("HEAD"),cancellable=true,require=1)
    private void ladsIconLabel(ActiveTextCollector text,CallbackInfo ci){
        var button=(AbstractWidget)(Object)this;
        if(ButtonLift.icon(button)==null)return;
        int width=Minecraft.getInstance().font.width(button.getMessage()),left=TitleScreenTheme.iconLabelX(button.getX(),button.getWidth(),width);
        // Exactly its width: drawn where the icon expects it; a label too long for the button scrolls in the space left.
        text.acceptScrollingWithDefaultCenter(button.getMessage(),left,Math.min(left+width,button.getX()+button.getWidth()-4),button.getY(),button.getY()+button.getHeight());
        ci.cancel();
    }
}
