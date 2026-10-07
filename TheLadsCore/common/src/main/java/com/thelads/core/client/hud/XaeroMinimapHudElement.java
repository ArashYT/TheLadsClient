package com.thelads.core.client.hud;
import com.thelads.core.client.bridge.*;
/** Editor control surface for the retained Xaero renderer, never a fabricated map. */
public class XaeroMinimapHudElement extends HudElement {
    @Override public boolean isAvailable(){return LadsGameBridge.get().hasMinimap() && !com.thelads.core.client.HypixelSafetyManager.isHypixel();}
    @Override public float getScale(){return 1;}
    @Override public void prepareRender(LadsGraphics g,boolean editor){int[] size=g.getGame().minimapSize();width=size[0];height=size[1];}
    @Override public void render(LadsGraphics g){}
    @Override public void renderEditor(LadsGraphics g){
        if(!isAvailable())return;
        // Xaero owns its terrain render pass; editor uses its exact measured bounds.
        g.drawCenteredText("Minimap",x+width/2,y+height/2,0xFFFFFFFF,true);
    }
    @Override public void setDisplayPosition(int dx,int dy){super.setDisplayPosition(dx,dy);LadsGameBridge.get().positionMinimap(dx,dy);}
}
