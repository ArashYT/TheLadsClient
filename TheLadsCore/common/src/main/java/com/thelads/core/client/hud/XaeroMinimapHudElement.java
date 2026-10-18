package com.thelads.core.client.hud;
import com.thelads.core.client.bridge.*;
/** Editor control surface for the retained Xaero renderer, never a fabricated map. Xaero draws it at vanilla's GUI scale. */
public class XaeroMinimapHudElement extends HudElement {
    /** One vanilla GUI unit in the Lads HUD's units (HudScale), as last measured. */
    private double unit = 1;
    @Override public boolean isAvailable(){return LadsGameBridge.get().hasMinimap() && !com.thelads.core.client.HypixelSafetyManager.isMinimapBanned();}
    @Override public float getScale(){return 1;}
    /** Xaero's map is a fixed number of pixels: in GUI units it shrinks at a larger GUI scale, and what is docked under it follows. */
    @Override public boolean keepsRoom(){return false;}
    @Override public void prepareRender(LadsGraphics g,boolean editor){unit=g.hudUnit();int[] size=g.getGame().minimapSize();width=(int)Math.round(size[0]*unit);height=(int)Math.round(size[1]*unit);}
    @Override public void render(LadsGraphics g){}
    @Override public void renderEditor(LadsGraphics g){
        if(!isAvailable())return;
        // Xaero owns its terrain render pass; editor uses its exact measured bounds.
        g.drawCenteredText("Minimap",x+width/2,y+height/2,0xFFFFFFFF);
    }
    /** Xaero's map goes where the Lads layout puts it (anchored, docked, clamped), every layout. */
    @Override public void placed(LadsGraphics g,HudGroupLayout.Rect bounds){position(bounds.x(),bounds.y());}
    @Override public void setDisplayPosition(int dx,int dy){super.setDisplayPosition(dx,dy);position(dx,dy);}
    private void position(int x,int y){LadsGameBridge.get().positionMinimap((int)Math.round(x/unit),(int)Math.round(y/unit));}

    private static long linesAt;
    private static int lines;
    /** Xaero's info displays that draw a line whenever they are switched on (the others only in some states, e.g. in the Nether). */
    private static final String[] LINED={"COORDINATES","CHUNK_COORDINATES","ANGLES","DIMENSION","BIOME","WEATHER","LIGHT_LEVEL","TIME","REAL_TIME"};
    /**
     * Xaero's info lines under the map (coordinates by default, 10 px each), part of the map's box so a HUD docked under it sits
     * under them. Read from Xaero's public objects (the xaero.common.HudMod instance) once a second; 0 if that fails.
     */
    public static int infoLinesHeight(Object hudMod){
        long now=System.nanoTime();
        if(now-linesAt<1_000_000_000L)return lines*10;
        linesAt=now;lines=0;
        try{
            Class<?> builtIn=Class.forName("xaero.hud.minimap.info.BuiltInInfoDisplays",false,hudMod.getClass().getClassLoader());
            var lined=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object,Boolean>());
            for(String name:LINED)lined.add(builtIn.getField(name).get(null));
            Object minimap=hudMod.getClass().getMethod("getMinimap").invoke(hudMod);
            Object displays=minimap.getClass().getMethod("getInfoDisplays").invoke(minimap);
            Object manager=displays.getClass().getMethod("getManager").invoke(displays);
            var shown=((java.util.stream.Stream<?>)manager.getClass().getMethod("getOrderedStream").invoke(manager)).iterator();
            while(shown.hasNext()){Object display=shown.next();if(!lined.contains(display))continue;
                Object state=display.getClass().getMethod("getEffectiveState").invoke(display);
                if(Boolean.TRUE.equals(state)||state instanceof Number number&&number.intValue()!=0)lines++;}
        }catch(ReflectiveOperationException|RuntimeException|LinkageError failure){lines=0;}
        return lines*10;
    }
}
