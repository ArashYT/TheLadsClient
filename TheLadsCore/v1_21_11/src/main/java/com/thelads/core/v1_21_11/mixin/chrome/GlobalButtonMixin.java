package com.thelads.core.v1_21_11.mixin.chrome;
import com.thelads.core.client.title.ButtonIcons;
import com.thelads.core.client.title.ButtonLift;
import com.thelads.core.client.title.TitleScreenTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Every vanilla and mod button draws the Lads title-screen surface instead of the vanilla sprite (26.x extractDefaultSprite), and an icon before its label where its text maps to one. */
@Mixin(AbstractButton.class)
public class GlobalButtonMixin {
    /**
     * The icon drawn before the button's label: the one its screen gave it (the pause menu's), else the one its text maps to
     * (ButtonIcons: vanilla's keys, the Lads' own labels); null for none, or for a button too narrow for both.
     */
    @Unique
    private static String ladsIcon(AbstractWidget button){
        String icon=ButtonLift.icon(button);
        if(icon==null){
            Component message=button.getMessage();
            if(message==null||!message.getSiblings().isEmpty())return null;
            icon=ButtonIcons.of(message.getContents() instanceof TranslatableContents t?t.getKey():null,message.getContents() instanceof PlainTextContents p?p.text():null);
        }
        return icon!=null&&TitleScreenTheme.iconFits(button.getWidth(),Minecraft.getInstance().font.width(button.getMessage()))?icon:null;
    }

    @Inject(method="renderDefaultSprite",at=@At("HEAD"),cancellable=true,require=1)
    private void ladsButton(GuiGraphics g,CallbackInfo ci){
        var button=(AbstractWidget)(Object)this;
        var adapter=new com.thelads.core.v1_21_11.adapter.GuiGraphicsLadsAdapter(g);
        TitleScreenTheme.renderButtonSurface(adapter,button.getX(),button.getY(),button.getWidth(),button.getHeight(),button.isHoveredOrFocused(),button.isFocused(),button.active,button.getAlpha(),ButtonLift.eased(button));
        // An icon before the label, the two centred together (ladsIconLabel).
        String icon=ladsIcon(button);
        if(icon!=null)TitleScreenTheme.renderLabelIcon(adapter,icon,button.getX(),button.getY(),button.getWidth(),button.getHeight(),
            Minecraft.getInstance().font.width(button.getMessage()),button.active?0xFFFFFFFF:0xFFA0A0A0);
        ci.cancel();
    }

    @Inject(method="renderDefaultLabel",at=@At("HEAD"),cancellable=true,require=1)
    private void ladsIconLabel(ActiveTextCollector text,CallbackInfo ci){
        var button=(AbstractWidget)(Object)this;
        if(ladsIcon(button)==null)return;
        int width=Minecraft.getInstance().font.width(button.getMessage()),left=TitleScreenTheme.iconLabelX(button.getX(),button.getWidth(),width);
        // Exactly its width: drawn where the icon expects it; a label too long for the button scrolls in the space left.
        text.acceptScrollingWithDefaultCenter(button.getMessage(),left,Math.min(left+width,button.getX()+button.getWidth()-4),button.getY(),button.getY()+button.getHeight());
        ci.cancel();
    }
}
