package com.thelads.core.v26_2.gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
public final class LadsButton extends Button {
    public LadsButton(int x,int y,int width,int height,Component label,OnPress action){super(x,y,width,height,label,action,DEFAULT_NARRATION);}
    @Override protected void extractContents(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta){
        extractDefaultSprite(g); g.centeredText(net.minecraft.client.Minecraft.getInstance().font,getMessage(),getX()+getWidth()/2,getY()+(getHeight()-9)/2,active?0xFFFCECF3:0xFF86717B);
    }
}
