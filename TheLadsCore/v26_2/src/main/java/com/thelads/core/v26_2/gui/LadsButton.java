package com.thelads.core.v26_2.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Native keyboard/narration behavior with the Lads palette. */
public final class LadsButton extends Button {
    public LadsButton(int x,int y,int width,int height,Component label,OnPress action){
        super(x,y,width,height,label,action,DEFAULT_NARRATION);
    }
    @Override protected void extractContents(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta){
        boolean hover=isHoveredOrFocused();
        int border=hover?0xFFD42649:0xFF59303D;
        g.fill(getX(),getY(),getRight(),getBottom(),border);
        g.fill(getX()+1,getY()+1,getRight()-1,getBottom()-1,hover?0xFF572132:0xFF29141E);
        g.fill(getX()+1,getY()+1,getX()+3,getBottom()-1,0xFFCD1538);
        g.centeredText(Minecraft.getInstance().font,getMessage(),getX()+getWidth()/2,getY()+(getHeight()-9)/2,active?0xFFFCECF3:0xFF86717B);
    }
}
