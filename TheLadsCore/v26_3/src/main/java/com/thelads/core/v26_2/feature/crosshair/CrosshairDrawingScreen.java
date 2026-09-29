package com.thelads.core.v26_2.feature.crosshair;

import com.thelads.core.client.CrosshairDesign;
import com.thelads.core.client.CrosshairDrawing;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.DropdownOption;
import java.util.ArrayDeque;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

/** Transactional drawing editor: edits remain local until Save; Escape and Cancel discard them. */
public final class CrosshairDrawingScreen extends Screen {
    private final Screen parent;
    private CrosshairDrawing draft=NativeCrosshair.drawing();
    private final ArrayDeque<CrosshairDrawing> history=new ArrayDeque<>();
    private int gridX,gridY,cell,lastX,lastY;
    private boolean painting,paintValue;
    private String error="";
    public CrosshairDrawingScreen(Screen parent){super(Component.literal("Crosshair drawing"));this.parent=parent;}
    @Override protected void init(){
        layout(); int width=Math.max(30,(this.width-56)/3),bottom=this.height-58;
        addRenderableWidget(Button.builder(Component.literal("Undo"),b->undo()).bounds(20,bottom,width,20).build());
        addRenderableWidget(Button.builder(Component.literal("Clear"),b->{checkpoint();draft=new CrosshairDrawing(draft.width(),draft.height());}).bounds(28+width,bottom,width,20).build());
        addRenderableWidget(Button.builder(Component.literal("Size: "+draft.width()),b->{resizeDrawing();b.setMessage(Component.literal("Size: "+draft.width()));}).bounds(36+width*2,bottom,width,20).build());
        addRenderableWidget(Button.builder(Component.literal("Mirror"),b->{checkpoint();var next=new CrosshairDrawing(draft.width(),draft.height());for(int y=0;y<draft.height();y++)for(int x=0;x<draft.width();x++)next.set(draft.width()-1-x,y,draft.get(x,y));draft=next;}).bounds(20,bottom+26,width,20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"),b->onClose()).bounds(28+width,bottom+26,width,20).build());
        addRenderableWidget(Button.builder(Component.literal("Save drawing"),b->saveDrawing()).bounds(36+width*2,bottom+26,width,20).build());
    }
    boolean hasPreview(){return width>=600&&height>=330;}
    private void layout(){int availableWidth=hasPreview()?this.width-210:this.width-40;cell=Math.max(1,Math.min(availableWidth/draft.width(),Math.max(1,(height-144)/draft.height())));gridX=(availableWidth-draft.width()*cell)/2+20;gridY=70+Math.max(0,(height-144-draft.height()*cell)/2);}
    private void checkpoint(){history.push(draft.copy());while(history.size()>32)history.removeLast();}
    void undo(){if(!history.isEmpty()){draft=history.pop();layout();}}
    private void resizeDrawing(){checkpoint();int[] sizes={9,17,25,33,49,64};int index=0;while(index<sizes.length&&sizes[index]<=draft.width())index++;draft=draft.resized(sizes[index%sizes.length],sizes[index%sizes.length]);layout();}
    private void saveDrawing(){
        try{NativeCrosshair.commitDrawing(draft);((DropdownOption)NativeCrosshair.module().getOption("Shape")).setIndex(8);ConfigManager.save();onClose();}
        catch(java.io.IOException failure){error="Could not save drawing. Your edits are still here.";org.slf4j.LoggerFactory.getLogger("TheLadsCore").warn("Crosshair drawing save failed",failure);}
    }
    @Override public void extractBackground(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partial){}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partial){
        graphics.fill(0,0,width,height,0xf510131a);graphics.fill(0,0,width,2,0xffa98aff);
        graphics.text(font,"CROSSHAIR / DRAWING",20,18,0xffb99bff);graphics.text(font,"Left drag paints · Right drag erases · Ctrl+Z undoes",20,38,0xffa9afbd);
        for(int y=0;y<draft.height();y++)for(int x=0;x<draft.width();x++){
            int left=gridX+x*cell,top=gridY+y*cell;graphics.fill(left,top,left+cell,top+cell,((x+y)&1)==0?0xff202632:0xff29313e);
            if(draft.get(x,y))graphics.fill(left,top,left+cell,top+cell,0xffdccfff);
            if(cell>=5){graphics.fill(left,top,left+1,top+cell,0x77000000);graphics.fill(left,top,left+cell,top+1,0x77000000);}
        }
        if(hasPreview()){int x=width-160;graphics.text(font,"LIVE PREVIEW",x,82,0xffa9afbd);graphics.fill(x,102,x+120,222,0xff697986);NativeCrosshair.draw(graphics,x+60,162,NativeCrosshair.number("Gap"),1,false,draft);graphics.text(font,"Uses your color,",x,236,0xffa9afbd);graphics.text(font,"scale and rotation.",x,248,0xffa9afbd);}
        if(!error.isEmpty())graphics.centeredText(font,error,width/2,height-72,0xffff7777);
        super.extractRenderState(graphics,mouseX,mouseY,partial);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){
        int x=(int)Math.floor((event.x()-gridX)/cell),y=(int)Math.floor((event.y()-gridY)/cell);
        if((event.button()==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT||event.button()==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT)&&x>=0&&y>=0&&x<draft.width()&&y<draft.height()){checkpoint();painting=true;paintValue=event.button()==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT;lastX=x;lastY=y;draft.set(x,y,paintValue);return true;}
        return super.mouseClicked(event,doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double deltaX,double deltaY){
        if(painting){int x=Math.max(0,Math.min(draft.width()-1,(int)Math.floor((event.x()-gridX)/cell))),y=Math.max(0,Math.min(draft.height()-1,(int)Math.floor((event.y()-gridY)/cell)));var line=new java.util.ArrayList<CrosshairDesign.Rect>();CrosshairDesign.line(line,lastX,lastY,x,y,1);for(var pixel:line)draft.set(pixel.left(),pixel.top(),paintValue);lastX=x;lastY=y;return true;}
        return super.mouseDragged(event,deltaX,deltaY);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event){if(painting){painting=false;return true;}return super.mouseReleased(event);}
    @Override public boolean keyPressed(KeyEvent event){if(event.key()==InputConstants.KEY_Z&&(event.modifiers()&InputConstants.MOD_CONTROL)!=0){undo();return true;}return super.keyPressed(event);}
    @Override public void onClose(){minecraft.setScreenAndShow(parent);}
    CrosshairDrawing draftForProbe(){return draft.copy();}
    int gridXForProbe(){return gridX;}int gridYForProbe(){return gridY;}int cellForProbe(){return cell;}
}
