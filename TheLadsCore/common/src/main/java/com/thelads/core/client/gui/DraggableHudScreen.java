package com.thelads.core.client.gui;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.hud.HudElement;
import com.thelads.core.client.hud.HudGroupLayout;
import com.thelads.core.client.hud.HudGroupLayout.Rect;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.ModuleSupport;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The HUD editor: the live game in a framed, scaled-down preview, with a list of HUD elements beside it and a toolbar above.
 * Element bounds, drags, snapping, guides and groups are in game GUI coordinates; the preview shows that whole viewport at one
 * scale, so a HUD dropped in the preview is exactly where it draws in game. Mouse input arrives in screen coordinates.
 */
public class DraggableHudScreen {
    public record Control(String id,String label,Rect bounds,boolean enabled) {}
    /** A list row with its on/off switch (null when the module cannot be switched) and settings gear. */
    private record Row(HudElement element,Rect bounds,Rect toggle,Rect gear) {}
    private final Runnable saveConfig;
    private Runnable onClose=()->{};
    private java.util.function.Consumer<String> onSettings=name->{};
    private final List<Control> contextControls=new ArrayList<>();
    private int contextX,contextY;
    private boolean contextOpen;
    private final ColorPicker colorPicker = new ColorPicker();
    private final ConfirmDialog confirm = new ConfirmDialog();
    public void setOnSettings(java.util.function.Consumer<String> action){onSettings=action;}
    private final Set<HudElement> selected=new LinkedHashSet<>();
    private final Map<HudElement,Rect> measuredBounds=new IdentityHashMap<>();
    private final Map<HudElement,Rect> renderedBounds=new IdentityHashMap<>();
    private final Map<HudElement,Rect> dragStart=new LinkedHashMap<>();
    private final List<Control> controls=new ArrayList<>();
    private final List<Row> rows=new ArrayList<>();
    private boolean showGrid=true,showAll;
    private boolean marquee,marqueeAdditive;
    private boolean dragMoved,editingSearch;
    private double downX,downY,marqueeX,marqueeY;
    private int viewportWidth,viewportHeight,focusedControl=-1,listScroll,maxListScroll;
    /** The preview frame on screen and its scale: one game GUI pixel is {@code scale} screen pixels. */
    private Rect preview=new Rect(0,0,0,0),panel=new Rect(0,0,0,0),list=new Rect(0,0,0,0);
    private double scale=1;
    private Integer guideX,guideY;
    private HudElement reveal;
    private String notice="",search="";
    private static final int GRID=10,SNAP=4,TOP=26,STATUS=14,GAP=6,ROW=18;

    public DraggableHudScreen(){this(ConfigManager::save);}
    public DraggableHudScreen(Runnable saveConfig){this.saveConfig=Objects.requireNonNull(saveConfig);}
    public void setOnClose(Runnable action){onClose=action;}
    /** The reset confirmation (QA reads its bounds and clicks them). */
    public ConfirmDialog confirmDialog(){return confirm;}
    public Set<String> selectedNames(){var names=new LinkedHashSet<String>();for(var element:selected)if(element.getModuleName()!=null)names.add(element.getModuleName());return Set.copyOf(names);}
    /** Game GUI bounds of a HUD drawn in the last preview (the position it has in game), or null. */
    public Rect boundsFor(String name){for(var entry:renderedBounds.entrySet())if(Objects.equals(name,entry.getKey().getModuleName()))return entry.getValue();return null;}
    public List<Control> controls(){return List.copyOf(controls);}
    public List<Control> contextControls(){return List.copyOf(contextControls);}
    /** Screen bounds of the module switch in a HUD's list row, or null when that row is not shown or cannot switch. */
    public Rect toggleBoundsFor(String name){var row=row(name);return row==null?null:row.toggle();}
    /** Screen bounds of the settings gear in a HUD's list row, or null. */
    public Rect settingsBoundsFor(String name){var row=row(name);return row==null?null:row.gear();}
    /** Screen bounds of a HUD's list row, or null when it is not listed or scrolled out of view. */
    public Rect rowBoundsFor(String name){var row=row(name);return row==null?null:row.bounds();}
    /** Names in the element list, top to bottom (including rows scrolled out of view). */
    public List<String> listedNames(){return listed().stream().map(HudElement::getModuleName).toList();}
    public Rect previewBounds(){return preview;}
    public double previewScale(){return scale;}
    public boolean isShowingAll(){return showAll;}
    public boolean isDragging(){return !dragStart.isEmpty();}
    /** Screen position of a game GUI coordinate inside the preview. */
    public double screenX(double gameX){return preview.x()+gameX*scale;}
    public double screenY(double gameY){return preview.y()+gameY*scale;}
    /** Screen pixels covering game GUI bounds in the preview. */
    public Rect toScreen(Rect bounds){
        int x=(int)Math.floor(screenX(bounds.x())),y=(int)Math.floor(screenY(bounds.y()));
        return new Rect(x,y,Math.max(1,(int)Math.ceil(screenX(bounds.right()))-x),Math.max(1,(int)Math.ceil(screenY(bounds.bottom()))-y));
    }
    private double gameX(double x){return (x-preview.x())/scale;}
    private double gameY(double y){return (y-preview.y())/scale;}

    public void render(LadsGraphics graphics,int mouseX,int mouseY){
        finishHiddenDrag();
        int width=graphics.getScaledWidth(),height=graphics.getScaledHeight();
        if(viewportWidth>0&&(width!=viewportWidth||height!=viewportHeight)){finishDrag(false);marquee=false;}
        viewportWidth=width;viewportHeight=height;
        layout();
        measuredBounds.clear();renderedBounds.clear();controls.clear();rows.clear();
        graphics.fill(0,0,width,height,LadsPalette.BACKGROUND);
        graphics.fill(0,0,width,2,LadsPalette.ACCENT);
        List<HudElement> elements=HudManager.getInstance().getElements();
        // Hidden members are measured too: enabling a grouped HUD later must not reveal a split group.
        for(var element:elements)if(element.isAvailable())measuredBounds.put(element,element.measureBounds(graphics,true));
        HudGroupLayout.matchDockedWidths(measuredBounds);
        clampGroups(elements);
        drawToolbar(graphics,mouseX,mouseY);
        drawPanel(graphics,mouseX,mouseY);
        drawPreview(graphics,mouseX,mouseY);
        String status=notice.isEmpty()?selectionStatus():notice;
        graphics.drawText(MenuGraphics.fit(graphics,status,width-2*GAP),GAP,height-STATUS+3,LadsPalette.MUTED,false);
        if(contextOpen)drawContext(graphics,mouseX,mouseY);
        colorPicker.render(graphics,mouseX,mouseY);
        confirm.render(graphics,mouseX,mouseY);
    }

    /** Toolbar across the top, element list on the left, and the largest preview with the game's aspect ratio in the rest. */
    private void layout(){
        int side=viewportWidth>=560?150:viewportWidth>=400?132:112;
        panel=new Rect(GAP,TOP,side,Math.max(40,viewportHeight-TOP-STATUS));
        int ax=panel.right()+GAP+3,ay=TOP+3,aw=Math.max(16,viewportWidth-ax-GAP-3),ah=Math.max(16,viewportHeight-TOP-STATUS-6);
        scale=Math.min(aw/(double)Math.max(1,viewportWidth),ah/(double)Math.max(1,viewportHeight));
        int pw=(int)Math.round(viewportWidth*scale),ph=(int)Math.round(viewportHeight*scale);
        preview=new Rect(ax+(aw-pw)/2,ay+(ah-ph)/2,pw,ph);
    }

    private void drawPreview(LadsGraphics g,int mx,int my){
        Rect p=preview;
        MenuGraphics.round(g,p.x()-3,p.y()-3,p.width()+6,p.height()+6,isDragging()?LadsPalette.ACCENT:LadsPalette.BORDER);
        g.enableScissor(p.x(),p.y(),p.right(),p.bottom());
        try{
            g.fill(p.x(),p.y(),p.right(),p.bottom(),0xFF14101A);
            g.drawGameView(p.x(),p.y(),p.width(),p.height());
            if(showGrid&&isDragging()){
                for(int x=0;x<viewportWidth;x+=GRID){int sx=(int)Math.round(screenX(x));g.fill(sx,p.y(),sx+1,p.bottom(),0x15FFFFFF);}
                for(int y=0;y<viewportHeight;y+=GRID){int sy=(int)Math.round(screenY(y));g.fill(p.x(),sy,p.right(),sy+1,0x15FFFFFF);}
            }
            // Live HUD content, drawn in game coordinates under one translate and scale.
            g.pushPose();
            try{
                g.translate(p.x(),p.y());g.scale((float)scale,(float)scale);
                for(var element:paintOrder()){
                    if(!isVisible(element))continue;
                    Rect bounds=measuredBounds.get(element);if(bounds==null)continue;
                    element.renderAt(g,bounds.x(),bounds.y(),true);
                    renderedBounds.put(element,bounds);
                }
            }finally{g.popPose();}
            // Outlines in whole screen pixels, so they stay crisp at any preview scale.
            for(var element:paintOrder()){
                Rect bounds=renderedBounds.get(element);if(bounds==null)continue;
                Rect box=toScreen(bounds);
                boolean hover=box.contains(mx,my)&&p.contains(mx,my),chosen=selected.contains(element),locked=isLocked(element);
                if(!element.isEnabled())g.fill(box.x(),box.y(),box.right(),box.bottom(),0x88222222);
                border(g,box,chosen?LadsPalette.ACCENT:locked?LadsPalette.MUTED:hover?LadsPalette.PRIMARY_HOVER:0x66FFFFFF);
            }
            drawGroupOutlines(g);
            if(isDragging()&&showGrid){
                if(guideX!=null){int sx=(int)Math.round(screenX(guideX));g.fill(sx,p.y(),sx+1,p.bottom(),LadsPalette.ACCENT);}
                if(guideY!=null){int sy=(int)Math.round(screenY(guideY));g.fill(p.x(),sy,p.right(),sy+1,LadsPalette.ACCENT);}
            }
            if(marquee){Rect box=toScreen(marqueeBounds());g.fill(box.x(),box.y(),box.right(),box.bottom(),0x226F1624);border(g,box,LadsPalette.ACCENT);}
        }finally{g.disableScissor();}
        if(contextOpen||isDragging()||marquee||!p.contains(mx,my))return;
        var order=paintOrder();
        for(int i=order.size()-1;i>=0;i--){var element=order.get(i);Rect bounds=renderedBounds.get(element);
            if(bounds==null||!toScreen(bounds).contains(mx,my))continue;
            String label=element.getModuleName()+(!element.isEnabled()?" · disabled":"")+(isLocked(element)?" · locked":"");
            int w=g.textWidth(label)+6,lx=Math.max(0,Math.min(mx+12,viewportWidth-w)),ly=Math.max(0,Math.min(my+14,viewportHeight-g.fontHeight()-6));
            MenuGraphics.round(g,lx,ly,w,g.fontHeight()+6,LadsPalette.PANEL);
            g.drawText(label,lx+3,ly+3,LadsPalette.TEXT,false);
            return;
        }
    }

    private void drawToolbar(LadsGraphics g,int mx,int my){
        boolean locked=selected.stream().anyMatch(this::isLocked);
        String[][] buttons={{"group","Group"},{"ungroup","Ungroup"},{locked?"unlock":"lock",locked?"Unlock":"Lock"},{"snap",showGrid?"Snap: on":"Snap: off"},{"done","Done"}};
        var bounds=new Rect[buttons.length];
        int x=viewportWidth-GAP;
        for(int i=buttons.length-1;i>=0;i--){int w=g.textWidth(buttons[i][1])+14;x-=w;bounds[i]=new Rect(x,5,w,17);x-=4;}
        int titleRoom=x-GAP-4;
        if(titleRoom>=g.textWidth("THE LADS")){
            g.drawText("THE LADS",GAP+2,10,LadsPalette.ACCENT,false);
            if(titleRoom>=g.textWidth("THE LADS  HUD EDITOR"))g.drawText("HUD EDITOR",GAP+2+g.textWidth("THE LADS  "),10,LadsPalette.MUTED,false);
        }
        for(int i=0;i<buttons.length;i++){
            String id=buttons[i][0];
            boolean enabled=switch(id){case "group"->selectedNames().size()>=2;case "ungroup"->selected.stream().anyMatch(e->HudSettings.getInstance().getGroupIndex(e.getModuleName())>=0);case "lock"->!selected.isEmpty();default->true;};
            drawControl(g,new Control(id,buttons[i][1],bounds[i],enabled),mx,my,controls,id.equals("snap")&&showGrid,false);
        }
    }

    private void drawPanel(LadsGraphics g,int mx,int my){
        MenuGraphics.round(g,panel.x(),panel.y(),panel.width(),panel.height(),LadsPalette.PANEL);
        int x=panel.x()+5,w=panel.width()-10,y=panel.y()+6;
        var listed=listed();
        String count=String.valueOf(listed.size());
        g.drawText(MenuGraphics.fit(g,"HUD ELEMENTS",w-g.textWidth(count)-4),x,y,LadsPalette.MUTED,false);
        g.drawText(count,x+w-g.textWidth(count),y,LadsPalette.MUTED,false);
        y+=13;
        String query=editingSearch?search+"_":search.isEmpty()?"Search...":search;
        drawControl(g,new Control("search",query,new Rect(x,y,w,16),true),mx,my,controls,editingSearch,true);
        y+=20;
        drawControl(g,new Control("previews","Show disabled",new Rect(x,y,w,16),true),mx,my,controls,showAll,false);
        y+=21;
        int footer=panel.bottom()-21;
        list=new Rect(x,y,w,Math.max(ROW,footer-4-y));
        maxListScroll=Math.max(0,listed.size()*ROW-list.height());
        if(reveal!=null){int index=listed.indexOf(reveal);reveal=null;
            if(index>=0){int top=index*ROW;if(top<listScroll)listScroll=top;else if(top+ROW>listScroll+list.height())listScroll=top+ROW-list.height();}}
        listScroll=Math.max(0,Math.min(maxListScroll,listScroll));
        g.enableScissor(list.x(),list.y(),list.right(),list.bottom());
        try{
            for(int i=0;i<listed.size();i++){
                int ry=list.y()+i*ROW-listScroll;
                if(ry+ROW<=list.y()||ry>=list.bottom())continue;
                drawRow(g,listed.get(i),new Rect(x,ry,w-(maxListScroll>0?4:0),ROW-2),mx,my);
            }
            if(listed.isEmpty())g.drawText(MenuGraphics.fit(g,search.isEmpty()?"No HUD elements on":"No matches",w),x+2,list.y()+4,LadsPalette.MUTED,false);
        }finally{g.disableScissor();}
        if(maxListScroll>0){
            int thumb=Math.max(10,list.height()*list.height()/(list.height()+maxListScroll));
            int ty=list.y()+(list.height()-thumb)*listScroll/maxListScroll;
            g.fill(list.right()-2,list.y(),list.right(),list.bottom(),LadsPalette.CARD);
            g.fill(list.right()-2,ty,list.right(),ty+thumb,LadsPalette.BORDER);
        }
        int half=(w-4)/2;
        drawControl(g,new Control("colors","Colors",new Rect(x,footer,half,16),true),mx,my,controls,false,false);
        drawControl(g,new Control("reset","Reset",new Rect(x+half+4,footer,w-half-4,16),true),mx,my,controls,false,false);
    }

    /** Monogram tile, name, settings gear and on/off switch; the selected row is filled with the primary colour. */
    private void drawRow(LadsGraphics g,HudElement element,Rect r,int mx,int my){
        boolean hover=r.contains(mx,my)&&list.contains(mx,my),chosen=selected.contains(element),on=element.isEnabled();
        int fill=chosen?(hover?LadsPalette.PRIMARY_HOVER:LadsPalette.PRIMARY):hover?LadsPalette.HOVER:LadsPalette.CARD;
        MenuGraphics.round(g,r.x(),r.y(),r.width(),r.height(),fill);
        String name=element.getModuleName();
        // A monogram tile in the module's on/off colour, where the list is wide enough to keep the names whole.
        int text=r.x()+5;
        if(panel.width()>=130){int tile=r.y()+(r.height()-11)/2;text=r.x()+18;
            MenuGraphics.round(g,r.x()+3,tile,11,11,on?LadsPalette.CARD_ON:LadsPalette.CARD_OFF);
            g.drawCenteredText(name.substring(0,1).toUpperCase(Locale.ROOT),r.x()+9,tile+2,LadsPalette.TEXT,false);}
        Rect toggle=canToggle(element)?new Rect(r.right()-21,r.y()+(r.height()-10)/2,18,10):null;
        Rect gear=new Rect((toggle!=null?toggle.x():r.right())-14,r.y()+(r.height()-11)/2,11,11);
        if(toggle!=null){
            MenuGraphics.round(g,toggle.x(),toggle.y(),toggle.width(),toggle.height(),on?0xFF2E9E57:LadsPalette.BORDER);
            MenuGraphics.round(g,on?toggle.right()-8:toggle.x()+2,toggle.y()+2,6,6,on?LadsPalette.TEXT:LadsPalette.MUTED);
        }
        int gx=gear.x()+5,gy=gear.y()+5,gearColor=gear.contains(mx,my)?LadsPalette.TEXT:LadsPalette.MUTED;
        g.fill(gx-3,gy-3,gx+4,gy+4,gearColor);g.fill(gx-1,gy-4,gx+2,gy+5,gearColor);g.fill(gx-4,gy-1,gx+5,gy+2,gearColor);
        g.fill(gx-1,gy-1,gx+2,gy+2,fill);
        g.drawText(MenuGraphics.fit(g,name,gear.x()-text-4),text,r.y()+(r.height()-g.fontHeight())/2+1,on?LadsPalette.TEXT:LadsPalette.MUTED,false);
        rows.add(new Row(element,r,toggle,gear));
    }

    private void clampGroups(List<HudElement> elements){
        Set<HudElement> visited=new LinkedHashSet<>();
        for(var element:elements){
            if(!measuredBounds.containsKey(element)||!visited.add(element))continue;
            Set<HudElement> members=groupElements(element);members.retainAll(measuredBounds.keySet());visited.addAll(members);
            Rect union=HudGroupLayout.union(members.stream().map(measuredBounds::get).toList());
            var delta=HudGroupLayout.clampDelta(union,0,0,viewportWidth,viewportHeight);
            for(var member:members)measuredBounds.put(member,HudGroupLayout.translate(measuredBounds.get(member),delta));
        }
    }
    private void drawGroupOutlines(LadsGraphics graphics){
        Set<Integer> drawn=new LinkedHashSet<>();
        for(var element:selected){int group=HudSettings.getInstance().getGroupIndex(element.getModuleName());if(group<0||!drawn.add(group))continue;
            var boxes=groupElements(element).stream().filter(renderedBounds::containsKey).map(renderedBounds::get).toList();
            if(!boxes.isEmpty()){Rect union=toScreen(HudGroupLayout.union(boxes));border(graphics,new Rect(union.x()-2,union.y()-2,union.width()+4,union.height()+4),LadsPalette.ACCENT);}
        }
    }
    private static void border(LadsGraphics graphics,Rect bounds,int color){
        graphics.fill(bounds.x(),bounds.y(),bounds.right(),bounds.y()+1,color);graphics.fill(bounds.x(),bounds.bottom()-1,bounds.right(),bounds.bottom(),color);
        graphics.fill(bounds.x(),bounds.y(),bounds.x()+1,bounds.bottom(),color);graphics.fill(bounds.right()-1,bounds.y(),bounds.right(),bounds.bottom(),color);
    }

    private void drawControl(LadsGraphics g,Control c,int mx,int my,List<Control> target,boolean on,boolean leftAligned){
        target.add(c);Rect b=c.bounds();
        boolean hover=c.enabled()&&b.contains(mx,my),focused=target==controls&&focusedControl==controls.size()-1;
        if(focused)MenuGraphics.round(g,b.x()-1,b.y()-1,b.width()+2,b.height()+2,LadsPalette.ACCENT);
        MenuGraphics.round(g,b.x(),b.y(),b.width(),b.height(),!c.enabled()?LadsPalette.PANEL:on?(hover?LadsPalette.PRIMARY_HOVER:LadsPalette.PRIMARY):hover?LadsPalette.HOVER:LadsPalette.CARD);
        int ty=b.y()+(b.height()-g.fontHeight())/2+1,color=c.enabled()?LadsPalette.TEXT:LadsPalette.DISABLED;
        if(leftAligned)g.drawText(MenuGraphics.fit(g,c.label(),b.width()-10),b.x()+5,ty,on||!search.isEmpty()?color:LadsPalette.MUTED,false);
        else g.drawCenteredText(MenuGraphics.fit(g,c.label(),b.width()-6),b.x()+b.width()/2,ty,color,false);
    }
    private void drawContext(LadsGraphics g,int mx,int my){
        contextControls.clear();
        boolean locked=selected.stream().anyMatch(this::isLocked);
        String[][] items={{"settings","Module settings"},{locked?"unlock":"lock",locked?"Unlock":"Lock"},{"centerX","Center horizontally"},{"centerY","Center vertically"},{"centerBoth","Center both"},{"group","Group"},{"ungroup","Ungroup"},{"stack","Center stack"},{"toggle",selected.stream().filter(DraggableHudScreen::canToggle).allMatch(HudElement::isEnabled)?"Turn off":"Turn on"}};
        int rowHeight=Math.max(12,Math.min(21,viewportHeight/items.length));
        int x=Math.max(0,Math.min(contextX,viewportWidth-132)),y=Math.max(0,Math.min(contextY,viewportHeight-rowHeight*items.length));
        for(int i=0;i<items.length;i++){
            String id=items[i][0];boolean enabled=switch(id){case "group"->selectedNames().size()>=2&&!locked;case "stack"->selectedNames().size()>=2&&!locked&&Objects.requireNonNullElse(HudSettings.getInstance().getGroupMembers(selectedNames().iterator().next()),Set.of()).containsAll(selectedNames());case "ungroup"->selected.stream().anyMatch(e->HudSettings.getInstance().getGroupIndex(e.getModuleName())>=0);case "centerX","centerY","centerBoth"->!locked;case "toggle"->selected.stream().anyMatch(DraggableHudScreen::canToggle);default->!selected.isEmpty();};
            drawControl(g,new Control(id,items[i][1],new Rect(x,y+i*rowHeight,132,rowHeight),enabled),mx,my,contextControls,false,false);
        }
    }
    private void center(boolean horizontal,boolean vertical){
        if(selected.isEmpty()||selected.stream().anyMatch(this::isLocked))return;
        Rect union=HudGroupLayout.union(selected.stream().filter(measuredBounds::containsKey).map(measuredBounds::get).toList());
        int dx=horizontal?(viewportWidth-union.width())/2-union.x():0;
        int dy=vertical?(viewportHeight-union.height())/2-union.y():0;
        startDrag(0,0);boolean snap=showGrid;showGrid=false;dragTo(dx,dy);showGrid=snap;finishDrag(false);
    }
    private String selectionStatus(){
        if(isDragging())return "Release to place · hold Shift while releasing to group with the HUD it touches";
        if(selected.isEmpty())return "Drag HUDs in the preview · Ctrl-click or drag a box to select several · right-click for more";
        boolean locked=selected.stream().anyMatch(this::isLocked);String name=selected.size()==1?selected.iterator().next().getModuleName():selected.size()+" HUDs selected";
        return name+(locked?" · Position locked: use Unlock":" · Drag to move · arrows nudge");
    }

    public boolean mouseClicked(double x,double y,int button){return mouseClicked(x,y,button,0);}
    public boolean mouseClicked(double x,double y,int button,int modifiers){
        if(confirm.isOpen())return confirm.click(x,y,button);
        if(colorPicker.click(x,y,button))return true;
        finishHiddenDrag();
        if(contextOpen){
            if(button==0)for(var c:contextControls)if(c.bounds().contains(x,y)){contextOpen=false;if(c.enabled())perform(c.id());return true;}
            contextOpen=false;return true;
        }
        boolean onSearch=controls.stream().anyMatch(c->c.id().equals("search")&&c.bounds().contains(x,y));
        if(editingSearch&&!onSearch)editingSearch=false;
        marquee=false;
        if(button==0)for(var control:controls)if(control.bounds().contains(x,y)){if(control.enabled())perform(control.id());return true;}
        Row row=rowAt(x,y);
        if(row!=null)return clickRow(row,x,y,button,modifiers);
        if(!preview.contains(x,y))return button==0&&(panel.contains(x,y)||y<TOP);
        double gx=gameX(x),gy=gameY(y);
        HudElement hit=hitAt(gx,gy);
        if(button==1){
            // Right-clicking any part of the selection keeps it for Group; otherwise the topmost HUD becomes the selection.
            boolean onSelection=selected.stream().map(renderedBounds::get).anyMatch(bounds->bounds!=null&&bounds.contains(gx,gy));
            if(!onSelection&&hit==null&&selected.isEmpty())return false;
            finishDrag(false);if(!onSelection&&hit!=null){selected.clear();selected.addAll(groupElements(hit));reveal=hit;}
            openContext(x,y);return true;
        }
        if(button!=0)return false;
        notice="";focusedControl=-1;
        if(hit!=null){
            Set<HudElement> group=groupElements(hit);reveal=hit;
            // Ctrl toggles a HUD in the selection; Shift alone still drags, so Shift can be held through a drop that groups.
            if((modifiers&2)!=0){if(selected.containsAll(group))selected.removeAll(group);else selected.addAll(group);return true;}
            if(!selected.contains(hit)){selected.clear();selected.addAll(group);}
            if(selected.stream().anyMatch(this::isLocked)){notice="Position locked. Select Unlock to move this selection.";return true;}
            startDrag(gx,gy);return true;
        }
        boolean additive=(modifiers&3)!=0;
        if(!additive)selected.clear();
        marquee=true;marqueeAdditive=additive;downX=gx;downY=gy;marqueeX=gx;marqueeY=gy;
        return false;
    }
    /** Topmost HUD under a game coordinate, as render() paints them. */
    private HudElement hitAt(double gx,double gy){
        var order=paintOrder();
        for(int i=order.size()-1;i>=0;i--){var element=order.get(i);Rect bounds=renderedBounds.get(element);if(isVisible(element)&&bounds!=null&&bounds.contains(gx,gy))return element;}
        return null;
    }
    private boolean clickRow(Row row,double x,double y,int button,int modifiers){
        var element=row.element();finishDrag(false);notice="";
        if(button==0&&row.toggle()!=null&&row.toggle().contains(x,y)){
            boolean on=!element.isEnabled();setEnabled(element,on);saveConfig.run();
            if(!on&&!showAll)notice=element.getModuleName()+" is off. Turn on Show disabled to list it again.";
            return true;
        }
        if(button==0&&row.gear().contains(x,y)){onSettings.accept(element.getModuleName());return true;}
        Set<HudElement> group=groupElements(element);
        if(button==1){if(!selected.contains(element)){selected.clear();selected.addAll(group);}openContext(x,y);return true;}
        if(button!=0)return false;
        if((modifiers&2)!=0){if(selected.containsAll(group))selected.removeAll(group);else selected.addAll(group);}
        else{selected.clear();selected.addAll(group);}
        return true;
    }
    private void openContext(double x,double y){contextOpen=true;contextX=(int)x;contextY=(int)y;}
    private Row row(String name){for(var row:rows)if(Objects.equals(name,row.element().getModuleName()))return row;return null;}
    private Row rowAt(double x,double y){if(!list.contains(x,y))return null;for(var row:rows)if(row.bounds().contains(x,y))return row;return null;}
    private void startDrag(double x,double y){
        finishDrag(false);downX=x;downY=y;
        Set<HudElement> members=new LinkedHashSet<>();for(var element:selected)members.addAll(groupElements(element));selected.addAll(members);
        for(var element:members){Rect bounds=measuredBounds.get(element);if(bounds!=null)dragStart.put(element,bounds);}
    }
    public boolean mouseDragged(double x,double y,int button){
        if(confirm.isOpen())return true;
        if(colorPicker.move(x,y))return true;
        finishHiddenDrag();if(button!=0)return false;
        if(marquee){marqueeX=gameX(x);marqueeY=gameY(y);return true;}
        if(!isDragging())return false;
        dragTo(gameX(x),gameY(y));return true;
    }
    /** Moves the dragged selection to a pointer position in game coordinates, with snapping and viewport clamping. */
    private void dragTo(double x,double y){
        Rect union=HudGroupLayout.union(dragStart.values());int dx=(int)Math.round(x-downX),dy=(int)Math.round(y-downY);
        if(!dragMoved&&dx==0&&dy==0)return;
        var targets=renderedBounds.entrySet().stream().filter(entry->!dragStart.containsKey(entry.getKey())&&attracts(entry.getKey())).map(Map.Entry::getValue).toList();
        var delta=showGrid?HudGroupLayout.snapDelta(union,dx,dy,GRID,SNAP,targets,viewportWidth,viewportHeight):HudGroupLayout.clampDelta(union,dx,dy,viewportWidth,viewportHeight);
        Rect movedSelection=HudGroupLayout.translate(union,delta);int cx=movedSelection.x()+movedSelection.width()/2,cy=movedSelection.y()+movedSelection.height()/2;
        guideX=cx==viewportWidth/2||targets.stream().anyMatch(target->target.x()+target.width()/2==cx)?cx:null;
        guideY=cy==viewportHeight/2||targets.stream().anyMatch(target->target.y()+target.height()/2==cy)?cy:null;
        if(!dragMoved){
            if(delta.x()==0&&delta.y()==0)return;
            dragMoved=true;for(var element:dragStart.keySet())element.beginPositionEdit();
        }
        for(var entry:dragStart.entrySet()){Rect moved=HudGroupLayout.translate(entry.getValue(),delta);entry.getKey().setDisplayPosition(moved.x(),moved.y());measuredBounds.put(entry.getKey(),moved);if(renderedBounds.containsKey(entry.getKey()))renderedBounds.put(entry.getKey(),moved);}
    }
    public boolean mouseReleased(double x,double y,int button){return mouseReleased(x,y,button,0);}
    /** Shift held at the drop (modifier bit 1) groups the dropped HUDs with the HUDs they now touch; a plain drop never groups. */
    public boolean mouseReleased(double x,double y,int button,int modifiers){
        if(confirm.isOpen())return true;
        if(colorPicker.release())return true;
        finishHiddenDrag();if(button!=0)return false;
        if(marquee){marqueeX=gameX(x);marqueeY=gameY(y);Rect box=marqueeBounds();marquee=false;if(box.width()>3||box.height()>3){if(!marqueeAdditive)selected.clear();for(var entry:renderedBounds.entrySet())if(box.intersects(entry.getValue()))selected.addAll(groupElements(entry.getKey()));}return true;}
        if(isDragging()){finishDrag((modifiers&1)!=0);return true;}return false;
    }
    public boolean mouseScrolled(double x,double y,double amount){
        if(confirm.isOpen())return true;
        if(colorPicker.isOpen())return true;
        if(!list.contains(x,y))return false;
        listScroll=Math.max(0,Math.min(maxListScroll,listScroll-(int)Math.round(amount*ROW)));return true;
    }
    private Rect marqueeBounds(){return new Rect((int)Math.floor(Math.min(downX,marqueeX)),(int)Math.floor(Math.min(downY,marqueeY)),(int)Math.ceil(Math.abs(marqueeX-downX)),(int)Math.ceil(Math.abs(marqueeY-downY)));}

    public boolean keyPressed(int key){return keyPressed(key,0);}
    public boolean keyPressed(int key,int modifiers){
        if(confirm.isOpen())return confirm.key(key);
        if(colorPicker.key(key))return true;
        if(editingSearch){
            if(key==256||key==257)editingSearch=false;
            else if(key==259&&!search.isEmpty()){search=search.substring(0,search.offsetByCodePoints(search.length(),-1));listScroll=0;}
            return true; // letters arrive through charTyped, so A and G stay text while typing
        }
        if(key==256&&contextOpen){contextOpen=false;return true;}
        if(key==256){close();if(onClose!=null)onClose.run();return true;}
        if(key==70&&(modifiers&2)!=0){perform("search");return true;}
        if(key==65&&(modifiers&2)==0){perform("previews");return true;}
        if(key==65&&(modifiers&2)!=0){for(var element:renderedBounds.keySet())selected.addAll(groupElements(element));return true;}
        if(key==71&&(modifiers&2)!=0){perform((modifiers&1)!=0?"ungroup":"group");return true;}
        if(key==71){perform("snap");return true;}
        if(key==258&&!controls.isEmpty()){focusedControl=Math.floorMod(focusedControl+((modifiers&1)!=0?-1:1),controls.size());return true;}
        if((key==257||key==32)&&focusedControl>=0&&focusedControl<controls.size()){var control=controls.get(focusedControl);if(control.enabled())perform(control.id());return true;}
        if(key==263||key==262||key==265||key==264){
            if(selected.isEmpty()||selected.stream().anyMatch(this::isLocked))return false;
            startDrag(0,0);boolean snap=showGrid;showGrid=false;int step=(modifiers&1)!=0?10:1;
            dragTo(key==263?-step:key==262?step:0,key==265?-step:key==264?step:0);showGrid=snap;finishDrag(false);return true;
        }
        return false;
    }
    private void perform(String id){
        finishDrag(false);marquee=false;notice="";
        switch(id){
            case "group"->{if(selectedNames().size()>=2){
                HudSettings.getInstance().addGroup(selectedNames());
                if(selected.stream().noneMatch(this::isLocked))stackSelection();
                // Capture displayed origins now, including auto-anchored HUDs, so resizing before the first drag cannot change the group offsets.
                for(var element:selected){Rect bounds=measuredBounds.get(element);if(bounds==null)continue;element.beginPositionEdit();try{element.setDisplayPosition(bounds.x(),bounds.y());HudSettings.getInstance().setPosition(element.getModuleName(),element.getX(),element.getY());}finally{element.endPositionEdit();}}
                saveConfig.run();notice=selected.stream().anyMatch(this::isLocked)?"Group created. Unlock its position to move together.":"Group created. Drag any member to move together.";
            }}
            // Re-stacks one existing group (e.g. after a member grew taller) without changing membership; Group joins HUDs.
            case "stack"->{if(selected.size()>=2&&selected.stream().noneMatch(this::isLocked)){stackSelection();savePositions(selected);saveConfig.run();}}
            case "toggle"->{var modules=selected.stream().filter(DraggableHudScreen::canToggle).toList();boolean enabled=modules.stream().anyMatch(element->!element.isEnabled());for(var element:modules)setEnabled(element,enabled);saveConfig.run();}
            case "ungroup"->{HudSettings.getInstance().ungroup(selectedNames());saveConfig.run();notice="Ungrouped. Each HUD can now be selected separately.";selected.clear();}
            case "lock","unlock"->{for(String name:selectedNames())HudSettings.getInstance().setLocked(name,id.equals("lock"));saveConfig.run();notice=id.equals("lock")?"Position locked. Select this HUD and use Unlock to move it.":"Position unlocked. Drag to move.";}
            case "snap"->showGrid=!showGrid;
            case "previews"->{showAll=!showAll;listScroll=0;finishHiddenDrag();selected.removeIf(element->!isVisible(element)&&HudSettings.getInstance().getGroupIndex(element.getModuleName())<0);renderedBounds.clear();rows.clear();}
            case "search"->editingSearch=true;
            case "colors"->colorPicker.openGlobal();
            case "settings"->{if(!selected.isEmpty())onSettings.accept(selected.iterator().next().getModuleName());}
            case "centerX"->center(true,false);
            case "centerY"->center(false,true);
            case "centerBoth"->center(true,true);
            case "reset"->confirm.open("Reset every HUD element to its default position? Groups and locks are cleared too.",()->{HudSettings.getInstance().clearPositions();selected.clear();saveConfig.run();});
            case "done"->{close();if(onClose!=null)onClose.run();}
            default->{}
        }
    }
    private void setEnabled(HudElement element,boolean enabled){
        var module=com.thelads.core.config.ModuleManager.getInstance().getModule(element.getModuleName());
        if(module!=null)module.setEnabled(enabled);else element.setEnabled(enabled);
    }
    private void stackSelection(){
        // Switched-off members are not drawn in game, so they neither reserve a row nor set the shared width.
        var bounds=new LinkedHashMap<HudElement,Rect>();for(var element:selected)if(element.isEnabled()&&measuredBounds.containsKey(element))bounds.put(element,measuredBounds.get(element));
        var layout=HudGroupLayout.centeredStack(bounds,viewportWidth,viewportHeight);
        for(var entry:layout.entrySet()){
            var element=entry.getKey();Rect position=entry.getValue();element.beginPositionEdit();
            try{element.setDisplayPosition(position.x(),position.y());}finally{element.endPositionEdit();}
            measuredBounds.put(element,position);if(renderedBounds.containsKey(element))renderedBounds.put(element,position);
        }
    }
    private void savePositions(Collection<HudElement> elements){for(var element:elements)if(element.getModuleName()!=null)HudSettings.getInstance().setPosition(element.getModuleName(),element.getX(),element.getY());}
    /** By default only switched-on HUDs are listed and previewed; Show disabled adds the rest, dimmed, so they can be switched on. */
    private boolean isVisible(HudElement element){return element.isAvailable()&&(showAll||element.isEnabled());}
    private static boolean canToggle(HudElement element){return ModuleSupport.isToggleable(element.getModuleName());}
    /** Dimmed switched-off previews are not drawn in game, so they only snap or join while Show disabled is on. */
    private boolean attracts(HudElement element){return showAll||element.isEnabled();}
    /** Draw and hit-test order: dimmed switched-off previews first, so live HUDs always sit on top of them and win the click. */
    private List<HudElement> paintOrder(){
        var elements=HudManager.getInstance().getElements();var order=new ArrayList<HudElement>(elements.size());
        for(var element:elements)if(!element.isEnabled())order.add(element);
        for(var element:elements)if(element.isEnabled())order.add(element);
        return order;
    }
    /** The list: shown HUDs matching the search, by name, so a row never jumps when it is switched on or off. */
    private List<HudElement> listed(){
        String query=search.trim().toLowerCase(Locale.ROOT);
        return HudManager.getInstance().getElements().stream().filter(e->e.getModuleName()!=null&&isVisible(e)&&(query.isEmpty()||e.getModuleName().toLowerCase(Locale.ROOT).contains(query)))
            .sorted(Comparator.comparing(e->e.getModuleName().toLowerCase(Locale.ROOT))).toList();
    }
    private boolean isLocked(HudElement element){return HudSettings.getInstance().isLocked(element.getModuleName());}
    private Set<HudElement> groupElements(HudElement element){
        Set<HudElement> members=new LinkedHashSet<>();members.add(element);Set<String> names=HudSettings.getInstance().getGroupMembers(element.getModuleName());
        if(names!=null)for(var other:HudManager.getInstance().getElements())if(other.isAvailable()&&names.contains(other.getModuleName()))members.add(other);return members;
    }
    private void finishHiddenDrag(){
        var elements=HudManager.getInstance().getElements();
        if(isDragging()&&dragStart.keySet().stream().anyMatch(element->!elements.contains(element)||!element.isAvailable()||isLocked(element))){finishDrag(false);}
        // A fully hidden selection ends its drag, but a disabled member of a visible group moves with that group.
        if(isDragging()&&dragStart.keySet().stream().noneMatch(this::isVisible))finishDrag(false);
        selected.removeIf(element->!elements.contains(element)||!element.isAvailable());
    }
    public boolean charTyped(int codePoint){
        if(confirm.isOpen())return true;
        if(editingSearch){if(codePoint>=32&&codePoint!=127&&search.length()<32){search+=new String(Character.toChars(codePoint));listScroll=0;}return true;}
        return colorPicker.type(codePoint);
    }
    public void close(){finishDrag(false);marquee=false;editingSearch=false;confirm.close();}
    /**
     * Ends a drag and saves where every member landed. Only a drop with {@code join} (Shift held) groups: the dropped HUDs join
     * each HUD they now dock against (edge to edge, aligned), together with that HUD's own group.
     */
    private void finishDrag(boolean join){
        if(!isDragging())return;var members=new ArrayList<>(dragStart.keySet());dragStart.clear();boolean moved=dragMoved;dragMoved=false;if(!moved)return;
        if(join){
            var names=new LinkedHashSet<String>();for(var member:members)names.add(member.getModuleName());
            var joined=new LinkedHashSet<HudElement>();
            var moving=members.stream().map(measuredBounds::get).filter(Objects::nonNull).toList();
            for(var entry:renderedBounds.entrySet())if(!members.contains(entry.getKey())&&!joined.contains(entry.getKey())&&attracts(entry.getKey())){
                var candidate=groupElements(entry.getKey());
                if(candidate.stream().anyMatch(this::isLocked))continue;
                // A near edge inside a stack is an overlap, not a dock. Consider the whole candidate group.
                boolean overlaps=candidate.stream().map(measuredBounds::get).filter(Objects::nonNull)
                    .anyMatch(box->moving.stream().anyMatch(box::intersects));
                if(overlaps||moving.stream().noneMatch(box->HudGroupLayout.docked(box,entry.getValue())))continue;
                joined.addAll(candidate);
            }
            // Stationary widgets may be auto-anchored or viewport-clamped. Capture the displayed
            // origins before merging, otherwise their old stored coordinates can stretch the new group.
            for(var element:joined){
                Rect bounds=measuredBounds.get(element);if(bounds==null)continue;
                element.beginPositionEdit();
                try{element.setDisplayPosition(bounds.x(),bounds.y());HudSettings.getInstance().setPosition(element.getModuleName(),element.getX(),element.getY());}
                finally{element.endPositionEdit();}
                names.add(element.getModuleName());
            }
            if(names.size()>members.size()){HudSettings.getInstance().addGroup(names);notice="Grouped: "+String.join(", ",names);}
            else notice="Nothing touching to group with. Drop against another HUD's edge while holding Shift.";
        }
        try{for(var element:members)if(element.getModuleName()!=null)HudSettings.getInstance().setPosition(element.getModuleName(),element.getX(),element.getY());saveConfig.run();}
        finally{for(var element:members)element.endPositionEdit();}
    }
}
