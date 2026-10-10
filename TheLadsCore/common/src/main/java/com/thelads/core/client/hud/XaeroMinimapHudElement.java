package com.thelads.core.client.hud;
import com.thelads.core.client.bridge.*;
/** Editor control surface for the retained Xaero renderer, never a fabricated map. Xaero draws it at vanilla's GUI scale. */
public class XaeroMinimapHudElement extends HudElement {
    /** One vanilla GUI unit in the Lads HUD's units (HudScale), as last measured. */
    private double unit = 1;
    @Override public boolean isAvailable(){return LadsGameBridge.get().hasMinimap() && !com.thelads.core.client.HypixelSafetyManager.isHypixel();}
    @Override public float getScale(){return 1;}
    @Override public void prepareRender(LadsGraphics g,boolean editor){unit=g.hudUnit();int[] size=g.getGame().minimapSize();width=(int)Math.round(size[0]*unit);height=(int)Math.round(size[1]*unit);}
    @Override public void render(LadsGraphics g){}
    @Override public void renderEditor(LadsGraphics g){
        if(!isAvailable())return;
        // Xaero owns its terrain render pass; editor uses its exact measured bounds.
        g.drawCenteredText("Minimap",x+width/2,y+height/2,0xFFFFFFFF,true);
    }
    /** Xaero's map goes where the Lads layout puts it (anchored, docked, clamped), every layout. */
    @Override public void placed(LadsGraphics g,HudGroupLayout.Rect bounds){position(bounds.x(),bounds.y());}
    @Override public void setDisplayPosition(int dx,int dy){super.setDisplayPosition(dx,dy);position(dx,dy);}
    private void position(int x,int y){LadsGameBridge.get().positionMinimap((int)Math.round(x/unit),(int)Math.round(y/unit));}
}
