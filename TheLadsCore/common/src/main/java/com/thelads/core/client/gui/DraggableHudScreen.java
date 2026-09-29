package com.thelads.core.client.gui;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.hud.HudElement;
import com.thelads.core.client.hud.HudGroupLayout;
import com.thelads.core.client.hud.HudGroupLayout.Rect;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.HudSettings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** One measured canvas for HUD selection, rigid groups, position locks and visible editor controls. */
public class DraggableHudScreen {
    public record Control(String id,String label,Rect bounds,boolean enabled) {}
    private final Runnable saveConfig;
    private Runnable onClose=()->{};
    private java.util.function.Consumer<String> onSettings=name->{};
    private final Map<HudElement,Rect> gears=new LinkedHashMap<>();
    private final List<Control> contextControls=new ArrayList<>();
    private int dock=0,contextX,contextY;
    private boolean collapsed,contextOpen;
    public void setOnSettings(java.util.function.Consumer<String> action){onSettings=action;}
    private final Set<HudElement> selected=new LinkedHashSet<>();
    private final Map<HudElement,Rect> measuredBounds=new IdentityHashMap<>();
    private final Map<HudElement,Rect> renderedBounds=new IdentityHashMap<>();
    private final Map<HudElement,Rect> dragStart=new LinkedHashMap<>();
    private final List<Control> controls=new ArrayList<>();
    private boolean showGrid=true,showAll,multiSelect,toolbarPinned;
    private boolean marquee,marqueeAdditive;
    private boolean dragMoved;
    private double downX,downY,marqueeX,marqueeY;
    private int viewportWidth,viewportHeight,focusedControl=-1;
    private Rect toolbarBounds=new Rect(0,0,0,0);
    private String notice="";
    private static final int GRID=10,SNAP=4;

    public DraggableHudScreen(){this(ConfigManager::save);}
    public DraggableHudScreen(Runnable saveConfig){this.saveConfig=Objects.requireNonNull(saveConfig);}
    public void setOnClose(Runnable action){onClose=action;}
    public Set<String> selectedNames(){var names=new LinkedHashSet<String>();for(var element:selected)if(element.getModuleName()!=null)names.add(element.getModuleName());return Set.copyOf(names);}
    public Rect boundsFor(String name){for(var entry:renderedBounds.entrySet())if(Objects.equals(name,entry.getKey().getModuleName()))return entry.getValue();return null;}
    public List<Control> controls(){return List.copyOf(controls);}
    public boolean isDragging(){return !dragStart.isEmpty();}

    public void render(LadsGraphics graphics,int mouseX,int mouseY){
        finishHiddenDrag();
        int width=graphics.getScaledWidth(),height=graphics.getScaledHeight();
        if(viewportWidth>0&&(width!=viewportWidth||height!=viewportHeight)){finishDrag();marquee=false;}
        viewportWidth=width;viewportHeight=height;
        measuredBounds.clear();renderedBounds.clear();gears.clear();
        graphics.fill(0,0,width,height,0x44000000);
        if(showGrid){
            for(int x=0;x<width;x+=GRID)graphics.fill(x,0,x+1,height,0x15FFFFFF);
            for(int y=0;y<height;y+=GRID)graphics.fill(0,y,width,y+1,0x15FFFFFF);
        }
        List<HudElement> elements=HudManager.getInstance().getElements();
        // Hidden members are measured too: enabling a grouped HUD later must not reveal a split group.
        for(var element:elements)if(element.isAvailable())measuredBounds.put(element,element.measureBounds(graphics,true));
        clampGroups(elements);
        for(var element:elements){
            if(!isVisible(element))continue;
            Rect bounds=measuredBounds.get(element);if(bounds==null)continue;
            element.renderAt(graphics,bounds.x(),bounds.y(),true);
            renderedBounds.put(element,bounds);
            boolean hover=bounds.contains(mouseX,mouseY),chosen=selected.contains(element),locked=isLocked(element);
            if(!element.isEnabled())graphics.fill(bounds.x(),bounds.y(),bounds.right(),bounds.bottom(),0x88222222);
            int color=chosen?LadsPalette.ACCENT:locked?LadsPalette.MUTED:hover?LadsPalette.PRIMARY_HOVER:0x66FFFFFF;
            border(graphics,bounds,color);
            // Only the hovered widget gets a label; disabled previews use a muted body.
            // Reserve an exterior hit target, keeping the icon off the widget's text.
            Rect gear=placeGear(bounds);
            gears.put(element,gear);
            graphics.fill(gear.x(),gear.y(),gear.right(),gear.bottom(),LadsPalette.CARD);
            int gx=gear.x()+5,gy=gear.y()+5;
            graphics.fill(gx-3,gy-3,gx+4,gy+4,LadsPalette.TEXT);
            graphics.fill(gx-1,gy-4,gx+2,gy+5,LadsPalette.TEXT);
            graphics.fill(gx-4,gy-1,gx+5,gy+2,LadsPalette.TEXT);
            graphics.fill(gx-1,gy-1,gx+2,gy+2,LadsPalette.CARD);
            if(hover&&!contextOpen){
                String label=element.getModuleName()+(!element.isEnabled()?" · disabled":"")+(locked?" · locked":"");
                int lx=Math.max(0,Math.min(mouseX+12,width-graphics.textWidth(label)-8));
                int ly=Math.max(0,Math.min(mouseY+14,height-graphics.fontHeight()-8));
                graphics.fill(lx,ly,lx+graphics.textWidth(label)+6,ly+graphics.fontHeight()+6,LadsPalette.PANEL);
                graphics.drawText(label,lx+3,ly+3,LadsPalette.TEXT,false);
            }
        }
        drawGroupOutlines(graphics);
        if(marquee){Rect box=marqueeBounds();graphics.fill(box.x(),box.y(),box.right(),box.bottom(),0x226F1624);border(graphics,box,LadsPalette.ACCENT);}
        if(!isDragging()&&!marquee)drawToolbar(graphics,mouseX,mouseY);else controls.clear();
        if(contextOpen)drawContext(graphics,mouseX,mouseY);
    }

    private Rect placeGear(Rect bounds){
        Rect best=new Rect(Math.max(0,Math.min(bounds.right()+2,viewportWidth-11)),Math.max(0,Math.min(bounds.y(),viewportHeight-11)),11,11);
        long bestOverlap=Long.MAX_VALUE;
        for(Rect candidate:List.of(new Rect(bounds.right()+2,bounds.y(),11,11),new Rect(bounds.x()-13,bounds.y(),11,11),
                new Rect(bounds.right()-11,bounds.y()-12,11,11),new Rect(bounds.right()-11,bounds.bottom()+2,11,11))){
            if(candidate.x()<0||candidate.y()<0||candidate.right()>viewportWidth||candidate.bottom()>viewportHeight)continue;
            long overlap=0;
            for(var entry:measuredBounds.entrySet())if(isVisible(entry.getKey()))overlap+=intersectionArea(candidate,entry.getValue());
            for(Rect gear:gears.values())overlap+=intersectionArea(candidate,gear);
            if(overlap<bestOverlap){best=candidate;bestOverlap=overlap;if(overlap==0)break;}
        }
        return best;
    }
    private static long intersectionArea(Rect a,Rect b){
        return (long)Math.max(0,Math.min(a.right(),b.right())-Math.max(a.x(),b.x()))*Math.max(0,Math.min(a.bottom(),b.bottom())-Math.max(a.y(),b.y()));
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
            if(!boxes.isEmpty()){Rect union=HudGroupLayout.union(boxes);border(graphics,new Rect(union.x()-2,union.y()-2,union.width()+4,union.height()+4),LadsPalette.ACCENT);}
        }
    }
    private static void border(LadsGraphics graphics,Rect bounds,int color){
        graphics.fill(bounds.x(),bounds.y(),bounds.right(),bounds.y()+1,color);graphics.fill(bounds.x(),bounds.bottom()-1,bounds.right(),bounds.bottom(),color);
        graphics.fill(bounds.x(),bounds.y(),bounds.x()+1,bounds.bottom(),color);graphics.fill(bounds.right()-1,bounds.y(),bounds.right(),bounds.bottom(),color);
    }

    private void drawToolbar(LadsGraphics g,int mx,int my){
        controls.clear();
        if(collapsed){
            toolbarBounds=new Rect(viewportWidth/2-28,viewportHeight/2-10,56,20);
            drawControl(g,new Control("collapse","Controls",toolbarBounds,true),mx,my,controls);return;
        }
        boolean locked=selected.stream().anyMatch(this::isLocked);
        String[] docks={"bottom","left","right","top"};
        String[][] buttons={{"select",multiSelect?"Multi-select: on":"Select multiple"},{"group","Group"},{"ungroup","Ungroup"},{"lock","Lock position"},{"unlock","Unlock"},{"snap",showGrid?"Snap: on":"Snap: off"},{"previews",showAll?"Enabled only":"All previews"},{"toolbar","Dock: "+docks[dock]},{"collapse","Hide controls"},{"reset","Reset layout"},{"done","Done"}};
        boolean vertical=dock==1||dock==2;
        int cols=vertical?1:Math.max(1,Math.min(5,(viewportWidth-24)/90));
        int bw=vertical?Math.min(116,viewportWidth-16):Math.min(100,(viewportWidth-24)/cols-4);
        int bh=Math.max(12,Math.min(21,(viewportHeight-38)/(vertical?buttons.length:3)-3));
        int rows=(buttons.length+cols-1)/cols,pw=cols*(bw+4)+12,ph=rows*(bh+3)+30;
        int px=dock==1?4:dock==2?viewportWidth-pw-4:(viewportWidth-pw)/2;
        if(!toolbarPinned&&!vertical){
            long top=overlapArea(new Rect(px,4,pw,ph)),bottom=overlapArea(new Rect(px,Math.max(4,viewportHeight-ph-4),pw,ph));
            dock=top<bottom?3:0;
        }
        int py=dock==3?4:vertical?Math.max(4,(viewportHeight-ph)/2):Math.max(4,viewportHeight-ph-4);
        toolbarBounds=new Rect(px,py,pw,ph);
        g.fill(px,py,px+pw,py+ph,LadsPalette.PANEL);border(g,toolbarBounds,LadsPalette.BORDER);
        g.drawCenteredText(clip(g,selectionStatus(),pw-12),px+pw/2,py+6,LadsPalette.TEXT,false);
        for(int i=0;i<buttons.length;i++){
            String id=buttons[i][0];boolean enabled=switch(id){case "group"->selectedNames().size()>=2;case "ungroup"->selected.stream().anyMatch(e->HudSettings.getInstance().getGroupIndex(e.getModuleName())>=0);case "lock"->!selected.isEmpty()&&!locked;case "unlock"->locked;default->true;};
            drawControl(g,new Control(id,buttons[i][1],new Rect(px+8+(i%cols)*(bw+4),py+23+(i/cols)*(bh+3),bw,bh),enabled),mx,my,controls);
        }
    }
    private void drawControl(LadsGraphics g,Control c,int mx,int my,List<Control> target){
        target.add(c);Rect b=c.bounds();
        g.fill(b.x(),b.y(),b.right(),b.bottom(),b.contains(mx,my)?LadsPalette.HOVER:LadsPalette.CARD);
        border(g,b,LadsPalette.BORDER);
        g.drawCenteredText(clip(g,c.label(),b.width()-6),b.x()+b.width()/2,b.y()+(b.height()-g.fontHeight())/2,c.enabled()?LadsPalette.TEXT:LadsPalette.DISABLED,false);
    }
    private void drawContext(LadsGraphics g,int mx,int my){
        contextControls.clear();
        String[][] items={{"settings","Module settings"},{selected.stream().anyMatch(this::isLocked)?"unlock":"lock",selected.stream().anyMatch(this::isLocked)?"Unlock":"Lock"},{"centerX","Center horizontally"},{"centerY","Center vertically"},{"centerBoth","Center both"}};
        int x=Math.max(0,Math.min(contextX,viewportWidth-132)),y=Math.max(0,Math.min(contextY,viewportHeight-105));
        for(int i=0;i<items.length;i++)drawControl(g,new Control(items[i][0],items[i][1],new Rect(x,y+i*21,132,21),true),mx,my,contextControls);
    }
    private void center(boolean horizontal,boolean vertical){
        if(selected.isEmpty()||selected.stream().anyMatch(this::isLocked))return;
        Rect union=HudGroupLayout.union(selected.stream().filter(measuredBounds::containsKey).map(measuredBounds::get).toList());
        int dx=horizontal?(viewportWidth-union.width())/2-union.x():0;
        int dy=vertical?(viewportHeight-union.height())/2-union.y():0;
        startDrag(0,0);boolean snap=showGrid;showGrid=false;mouseDragged(dx,dy,0);showGrid=snap;finishDrag();
    }
    private long overlapArea(Rect panel){long area=0;for(var bounds:renderedBounds.values())area+=(long)Math.max(0,Math.min(panel.right(),bounds.right())-Math.max(panel.x(),bounds.x()))*Math.max(0,Math.min(panel.bottom(),bounds.bottom())-Math.max(panel.y(),bounds.y()));return area;}
    private static String clip(LadsGraphics graphics,String text,int width){if(graphics.textWidth(text)<=width)return text;int end=text.length();while(end>0&&graphics.textWidth(text.substring(0,end)+"…")>width)end--;return text.substring(0,end)+"…";}
    private String selectionStatus(){
        if(selected.isEmpty())return showAll?"All HUDs · disabled previews stay disabled":"HUD editor · select HUDs to group or lock";
        boolean locked=selected.stream().anyMatch(this::isLocked);String name=selected.size()==1?selected.iterator().next().getModuleName():selected.size()+" HUDs selected";
        return name+(locked?" · Position locked: use Unlock":" · Drag to move together");
    }

    public boolean mouseClicked(double x,double y,int button){return mouseClicked(x,y,button,0);}
    public boolean mouseClicked(double x,double y,int button,int modifiers){
        finishHiddenDrag();
        if(contextOpen){
            if(button==0)for(var c:contextControls)if(c.bounds().contains(x,y)){contextOpen=false;perform(c.id());return true;}
            contextOpen=false;return true;
        }
        if(button==1){
            for(var entry:renderedBounds.entrySet())if(entry.getValue().contains(x,y)){
                selected.clear();selected.addAll(groupElements(entry.getKey()));contextOpen=true;contextX=(int)x;contextY=(int)y;return true;
            }
            return false;
        }
        if(button!=0)return false;marquee=false;
        for(var control:controls)if(control.bounds.contains(x,y)){if(control.enabled)perform(control.id);return true;}
        if(!controls.isEmpty()&&toolbarBounds.contains(x,y))return true;
        for(var entry:gears.entrySet())if(entry.getValue().contains(x,y)){finishDrag();onSettings.accept(entry.getKey().getModuleName());return true;}
        notice="";focusedControl=-1;boolean additive=multiSelect||(modifiers&3)!=0;
        var elements=HudManager.getInstance().getElements();
        for(int i=elements.size()-1;i>=0;i--){var element=elements.get(i);Rect bounds=renderedBounds.get(element);
            if(!isVisible(element)||bounds==null||!bounds.contains(x,y))continue;
            Set<HudElement> group=groupElements(element);
            if(additive){if(selected.containsAll(group))selected.removeAll(group);else selected.addAll(group);return true;}
            if(!selected.contains(element)){selected.clear();selected.addAll(group);}
            if(selected.stream().anyMatch(this::isLocked)){notice="Position locked. Select Unlock to move this selection.";return true;}
            startDrag(x,y);return true;
        }
        if(!additive)selected.clear();
        marquee=true;marqueeAdditive=additive;downX=x;downY=y;marqueeX=x;marqueeY=y;
        return false;
    }
    private void startDrag(double x,double y){
        finishDrag();downX=x;downY=y;
        Set<HudElement> members=new LinkedHashSet<>();for(var element:selected)members.addAll(groupElements(element));selected.addAll(members);
        for(var element:members){Rect bounds=measuredBounds.get(element);if(bounds!=null)dragStart.put(element,bounds);}
    }
    public boolean mouseDragged(double x,double y,int button){
        finishHiddenDrag();if(button!=0)return false;
        if(marquee){marqueeX=x;marqueeY=y;return true;}
        if(!isDragging())return false;
        Rect union=HudGroupLayout.union(dragStart.values());int dx=(int)Math.round(x-downX),dy=(int)Math.round(y-downY);
        if(!dragMoved&&dx==0&&dy==0)return true;
        var targets=renderedBounds.entrySet().stream().filter(entry->!dragStart.containsKey(entry.getKey())).map(Map.Entry::getValue).toList();
        var delta=showGrid?HudGroupLayout.snapDelta(union,dx,dy,GRID,SNAP,targets,viewportWidth,viewportHeight):HudGroupLayout.clampDelta(union,dx,dy,viewportWidth,viewportHeight);
        if(!dragMoved){
            if(delta.x()==0&&delta.y()==0)return true;
            dragMoved=true;for(var element:dragStart.keySet())element.beginPositionEdit();
        }
        for(var entry:dragStart.entrySet()){Rect moved=HudGroupLayout.translate(entry.getValue(),delta);entry.getKey().setDisplayPosition(moved.x(),moved.y());measuredBounds.put(entry.getKey(),moved);if(renderedBounds.containsKey(entry.getKey()))renderedBounds.put(entry.getKey(),moved);}
        return true;
    }
    public boolean mouseReleased(double x,double y,int button){
        finishHiddenDrag();if(button!=0)return false;
        if(marquee){marqueeX=x;marqueeY=y;Rect box=marqueeBounds();marquee=false;if(box.width()>3||box.height()>3){if(!marqueeAdditive)selected.clear();for(var entry:renderedBounds.entrySet())if(box.intersects(entry.getValue()))selected.addAll(groupElements(entry.getKey()));}return true;}
        if(isDragging()){finishDrag();return true;}return false;
    }
    private Rect marqueeBounds(){return new Rect((int)Math.floor(Math.min(downX,marqueeX)),(int)Math.floor(Math.min(downY,marqueeY)),(int)Math.ceil(Math.abs(marqueeX-downX)),(int)Math.ceil(Math.abs(marqueeY-downY)));}

    public boolean keyPressed(int key){return keyPressed(key,0);}
    public boolean keyPressed(int key,int modifiers){
        if(key==256&&contextOpen){contextOpen=false;return true;}
        if(key==256){close();if(onClose!=null)onClose.run();return true;}
        if(key==65&&(modifiers&2)==0){perform("previews");return true;}
        if(key==65&&(modifiers&2)!=0){for(var element:renderedBounds.keySet())selected.addAll(groupElements(element));return true;}
        if(key==71&&(modifiers&2)!=0){perform((modifiers&1)!=0?"ungroup":"group");return true;}
        if(key==71){perform("snap");return true;}
        if(key==258&&!controls.isEmpty()){focusedControl=Math.floorMod(focusedControl+((modifiers&1)!=0?-1:1),controls.size());return true;}
        if((key==257||key==32)&&focusedControl>=0&&focusedControl<controls.size()){var control=controls.get(focusedControl);if(control.enabled)perform(control.id);return true;}
        if(key==263||key==262||key==265||key==264){
            if(selected.isEmpty()||selected.stream().anyMatch(this::isLocked))return false;
            startDrag(0,0);boolean snap=showGrid;showGrid=false;int step=(modifiers&1)!=0?10:1;
            mouseDragged(key==263?-step:key==262?step:0,key==265?-step:key==264?step:0,0);showGrid=snap;finishDrag();return true;
        }
        return false;
    }
    private void perform(String id){
        finishDrag();marquee=false;notice="";
        switch(id){
            case "select"->multiSelect=!multiSelect;
            case "group"->{if(selectedNames().size()>=2){
                HudSettings.getInstance().addGroup(selectedNames());multiSelect=false;
                // Capture displayed origins now, including auto-anchored HUDs, so resizing before the first drag cannot change the group offsets.
                for(var element:selected){Rect bounds=measuredBounds.get(element);if(bounds==null)continue;element.beginPositionEdit();try{element.setDisplayPosition(bounds.x(),bounds.y());HudSettings.getInstance().setPosition(element.getModuleName(),element.getX(),element.getY());}finally{element.endPositionEdit();}}
                saveConfig.run();notice=selected.stream().anyMatch(this::isLocked)?"Group created. Unlock its position to move together.":"Group created. Drag any member to move together.";
            }}
            case "ungroup"->{HudSettings.getInstance().ungroup(selectedNames());saveConfig.run();notice="Ungrouped. Each HUD can now be selected separately.";selected.clear();}
            case "lock","unlock"->{for(String name:selectedNames())HudSettings.getInstance().setLocked(name,id.equals("lock"));saveConfig.run();notice=id.equals("lock")?"Position locked. Select this HUD and use Unlock to move it.":"Position unlocked. Drag to move.";}
            case "snap"->showGrid=!showGrid;
            case "previews"->{showAll=!showAll;finishHiddenDrag();selected.removeIf(element->!isVisible(element)&&HudSettings.getInstance().getGroupIndex(element.getModuleName())<0);renderedBounds.clear();}
            case "toolbar"->{dock=switch(dock){case 3->0;case 0->1;case 1->2;default->3;};toolbarPinned=true;}
            case "collapse"->collapsed=!collapsed;
            case "settings"->{if(!selected.isEmpty())onSettings.accept(selected.iterator().next().getModuleName());}
            case "centerX"->center(true,false);
            case "centerY"->center(false,true);
            case "centerBoth"->center(true,true);
            case "reset"->{HudSettings.getInstance().clearPositions();selected.clear();saveConfig.run();}
            case "done"->{close();if(onClose!=null)onClose.run();}
            default->{}
        }
    }
    private boolean isVisible(HudElement element){return element.isAvailable()&&(showAll||element.isEnabled());}
    private boolean isLocked(HudElement element){return HudSettings.getInstance().isLocked(element.getModuleName());}
    private Set<HudElement> groupElements(HudElement element){
        Set<HudElement> members=new LinkedHashSet<>();members.add(element);Set<String> names=HudSettings.getInstance().getGroupMembers(element.getModuleName());
        if(names!=null)for(var other:HudManager.getInstance().getElements())if(other.isAvailable()&&names.contains(other.getModuleName()))members.add(other);return members;
    }
    private void finishHiddenDrag(){
        var elements=HudManager.getInstance().getElements();
        if(isDragging()&&dragStart.keySet().stream().anyMatch(element->!elements.contains(element)||!element.isAvailable()||isLocked(element))){finishDrag();}
        // A fully hidden selection ends its drag, but a disabled member of a visible group moves with that group.
        if(isDragging()&&dragStart.keySet().stream().noneMatch(this::isVisible))finishDrag();
        selected.removeIf(element->!elements.contains(element)||!element.isAvailable());
    }
    public void close(){finishDrag();marquee=false;}
    private void finishDrag(){
        if(!isDragging())return;var members=new ArrayList<>(dragStart.keySet());dragStart.clear();boolean moved=dragMoved;dragMoved=false;if(!moved)return;
        try{for(var element:members)if(element.getModuleName()!=null)HudSettings.getInstance().setPosition(element.getModuleName(),element.getX(),element.getY());saveConfig.run();}
        finally{for(var element:members)element.endPositionEdit();}
    }
}
