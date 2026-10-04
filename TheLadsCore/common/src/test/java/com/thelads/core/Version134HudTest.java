package com.thelads.core;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.client.hud.*;
import com.thelads.core.config.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class Version134HudTest {
    private com.google.gson.JsonObject snapshot;
    private List<HudElement> original;
    private ModsMenuTest.OwnershipFixture ownership;
    private final Canvas graphics=new Canvas();
    private final AtomicInteger saves=new AtomicInteger();
    private DraggableHudScreen editor;
    /** Content size is recomputed each frame, like real HUDs whose text or rows change. */
    private static class Box extends HudElement {
        int w,h;
        Box(String name,int x,int y,int width,int height){setModuleName(name);setPosition(x,y);w=width;h=height;this.width=width;this.height=height;}
        @Override public float getScale(){return 1;}
        @Override public void prepareRender(LadsGraphics g,boolean editor){width=w;height=h;}
        @Override public void render(LadsGraphics g){g.fill(x,y,x+width,y+height,moduleName.hashCode());}
    }
    /** Records where each Box body lands on screen, after renderAt's translations. */
    private static final class Canvas extends LadsGraphicsTest.MockGraphics {
        final Map<Integer,HudGroupLayout.Rect> drawn=new HashMap<>();float tx,ty;final ArrayDeque<float[]> poses=new ArrayDeque<>();
        @Override public void pushPose(){poses.push(new float[]{tx,ty});}
        @Override public void popPose(){var pose=poses.pop();tx=pose[0];ty=pose[1];}
        @Override public void translate(float x,float y){tx+=x;ty+=y;}
        @Override public void fill(int left,int top,int right,int bottom,int color){drawn.put(color,new HudGroupLayout.Rect(Math.round(left+tx),Math.round(top+ty),right-left,bottom-top));}
    }
    @BeforeEach void setup() throws Exception {
        snapshot=ConfigManager.toJson();original=new ArrayList<>(HudManager.getInstance().getElements());HudManager.getInstance().getElements().clear();
        HudSettings.getInstance().clearPositions();HudSettings.getInstance().replaceGroups(List.of());HudSettings.getInstance().replaceLocked(Set.of());
        ownership=new ModsMenuTest.OwnershipFixture();
        ModuleSupport.registerBuiltIn("FPS","PingHUD","Coordinates","Memory","XP","Hunger","ArmorHUD","TexturePacks","Day","Time");
        graphics.width=640;graphics.height=360;editor=new DraggableHudScreen(saves::incrementAndGet);
    }
    @AfterEach void restore() throws Exception {editor.close();HudManager.getInstance().getElements().clear();HudManager.getInstance().getElements().addAll(original);ConfigManager.applyJson(snapshot);ownership.close();}
    private Box add(String name,int x,int y,int width,int height,boolean enabled){var box=new Box(name,x,y,width,height);HudManager.getInstance().getElements().add(box);ModuleManager.getInstance().getModule(name).setEnabled(enabled);return box;}
    private void render(){editor.render(graphics,-1,-1);}
    /** Pointer input at a game GUI position, through the preview's mapping to the screen. */
    private boolean click(double x,double y,int button,int modifiers){return editor.mouseClicked(editor.screenX(x),editor.screenY(y),button,modifiers);}
    private void select(String name){var b=editor.boundsFor(name);assertTrue(click(b.x()+1,b.y()+1,0,2));}
    private void click(DraggableHudScreen.Control c){assertTrue(c.enabled());assertTrue(editor.mouseClicked(c.bounds().x()+2,c.bounds().y()+2,0));render();}
    private void drag(double x,double y,int modifiers){editor.mouseDragged(editor.screenX(x),editor.screenY(y),0);editor.mouseReleased(editor.screenX(x),editor.screenY(y),0,modifiers);}
    private DraggableHudScreen.Control contextControl(String id){return editor.contextControls().stream().filter(c->c.id().equals(id)).findFirst().orElseThrow();}
    private void context(String id){render();click(contextControl(id));}
    private HudGroupLayout.Rect inGame(String name){graphics.drawn.clear();HudManager.getInstance().render(graphics);return graphics.drawn.get(name.hashCode());}
    @Test void rightClickKeepsMultipleSelectionAndGroupsCenteredRows(){
        add("FPS",20,30,40,15,true);add("PingHUD",100,60,80,20,true);add("Coordinates",45,110,60,35,true);
        render();select("FPS");select("PingHUD");select("Coordinates");click(22,32,1,0);render();
        assertFalse(contextControl("stack").enabled(),"Center stack re-stacks an existing group; Group joins");
        assertEquals(Set.of("FPS","PingHUD","Coordinates"),editor.selectedNames());context("group");
        var a=editor.boundsFor("FPS");var b=editor.boundsFor("PingHUD");var c=editor.boundsFor("Coordinates");
        assertEquals(a.x(),b.x());assertEquals(a.width(),b.width());assertEquals(b.x(),c.x());assertEquals(b.width(),c.width());
        assertEquals(a.bottom()+2,b.y());assertEquals(b.bottom()+2,c.y());assertEquals(1,saves.get());
        assertEquals(Set.of("FPS","PingHUD","Coordinates"),HudSettings.getInstance().getGroupMembers("FPS"));
        click(a.x()+1,a.y()+1,1,0);context("ungroup");assertNull(HudSettings.getInstance().getGroupMembers("FPS"));
    }
    @Test void groupStackClampsOnceAndDoesNotOverwriteLockedCoordinates(){
        add("FPS",590,330,40,15,true);add("PingHUD",570,350,70,20,true);render();select("FPS");select("PingHUD");editor.keyPressed(71,2);render();
        var a=editor.boundsFor("FPS");var b=editor.boundsFor("PingHUD");assertEquals(640,b.right());assertEquals(360,b.bottom());assertEquals(a.bottom()+2,b.y());
        HudSettings.getInstance().setLocked("FPS",true);click(a.x()+1,a.y()+1,1,0);render();
        assertFalse(contextControl("stack").enabled());
        var before=HudSettings.getInstance().getPosition("FPS").clone();editor.keyPressed(71,2);assertArrayEquals(before,HudSettings.getInstance().getPosition("FPS"));
    }
    @Test void rowSwitchTurnsAModuleOffAndShowDisabledBringsItBack(){
        add("FPS",50,60,60,20,true);render();var toggle=editor.toggleBoundsFor("FPS");
        assertNotNull(toggle);assertFalse(toggle.intersects(editor.previewBounds()));editor.mouseClicked(toggle.x()+2,toggle.y()+2,0);render();
        assertFalse(ModuleManager.getInstance().getModule("FPS").isEnabled());assertEquals(1,saves.get());
        assertNull(editor.boundsFor("FPS"),"switched off: gone from the default preview");assertNull(editor.toggleBoundsFor("FPS"),"and from the list");
        click(editor.controls().stream().filter(c->c.id().equals("previews")).findFirst().orElseThrow());
        assertNotNull(editor.boundsFor("FPS"),"Show disabled previews it dimmed");toggle=editor.toggleBoundsFor("FPS");assertNotNull(toggle);
        editor.mouseClicked(toggle.x()+2,toggle.y()+2,0);render();assertTrue(ModuleManager.getInstance().getModule("FPS").isEnabled());assertEquals(2,saves.get());
    }
    @Test void switchedOffModulesNeedShowDisabledAndUnsupportedOnesNeverGetASwitch(){
        add("FPS",50,60,60,20,false);add("Scoreboard",200,60,60,20,false);ModuleSupport.registerUnavailable("Scoreboard","Not connected on this version");
        render();assertNull(editor.toggleBoundsFor("FPS"));assertNull(editor.boundsFor("FPS"));assertEquals(List.of(),editor.listedNames());
        // Like the settings card, a module this version cannot run is listed but never gets a switch.
        click(editor.controls().stream().filter(c->c.id().equals("previews")).findFirst().orElseThrow());
        assertEquals(List.of("FPS","Scoreboard"),editor.listedNames());
        assertNotNull(editor.boundsFor("Scoreboard"));assertNull(editor.toggleBoundsFor("Scoreboard"));assertNotNull(editor.settingsBoundsFor("Scoreboard"));
        var toggle=editor.toggleBoundsFor("FPS");editor.mouseClicked(toggle.x()+2,toggle.y()+2,0);assertTrue(ModuleManager.getInstance().getModule("FPS").isEnabled());
    }
    @Test void groupedColumnStaysCenteredInGameWhenMemberWidthAndHeightChange(){
        // Joined by dragging with the center snap: FPS and Ping keep their own centered x under the wider row.
        var coords=add("Coordinates",100,50,80,20,true);var fps=add("FPS",120,72,40,15,true);add("PingHUD",125,89,30,10,true);
        HudSettings.getInstance().addGroup(Set.of("Coordinates","FPS","PingHUD"));
        for(String name:List.of("Coordinates","FPS","PingHUD")){var r=inGame(name);assertEquals(100,r.x(),name);assertEquals(80,r.width(),name);}
        coords.w=120;fps.h=40; // Wider by 40 and taller than the 4px chain: both used to split the column.
        for(String name:List.of("Coordinates","FPS","PingHUD")){var r=inGame(name);assertEquals(100,r.x(),name);assertEquals(120,r.width(),name);}
        render();for(String name:List.of("Coordinates","FPS","PingHUD")){assertEquals(100,editor.boundsFor(name).x(),name);assertEquals(120,editor.boundsFor(name).width(),name);}
        // Center stack repacks the taller rows without changing who is in the group.
        var f=editor.boundsFor("FPS");click(f.x()+1,f.y()+1,1,0);context("stack");
        assertEquals(editor.boundsFor("FPS").bottom()+2,editor.boundsFor("PingHUD").y());assertEquals(100,editor.boundsFor("PingHUD").x());
        assertEquals(Set.of("Coordinates","FPS","PingHUD"),HudSettings.getInstance().getGroupMembers("FPS"));
    }
    @Test void switchedOffMemberNeitherReservesARowNorSetsTheSharedWidth(){
        add("FPS",20,30,40,15,true);add("Memory",20,60,200,15,false);add("PingHUD",20,100,50,15,true);
        editor.keyPressed(65);render();select("FPS");select("Memory");select("PingHUD");click(22,32,1,0);context("group");
        var fps=editor.boundsFor("FPS");var ping=editor.boundsFor("PingHUD");
        assertEquals(fps.bottom()+2,ping.y());assertEquals(fps.x(),ping.x());assertEquals(50,fps.width());assertEquals(50,ping.width());
        assertEquals(Set.of("FPS","Memory","PingHUD"),HudSettings.getInstance().getGroupMembers("Memory"));
        assertEquals(50,inGame("FPS").width());assertEquals(ping.x(),inGame("PingHUD").x());assertNull(inGame("Memory"));
    }
    @Test void dimmedPreviewSitsBehindLiveHudsAndRightClickKeepsTheSelection(){
        // Memory is switched off; with Show disabled its dimmed preview overlaps CPS's corner (the in-game editor QA failure).
        add("CPS",100,100,60,20,true);add("Day",100,140,60,20,true);add("Memory",90,95,40,20,false);
        editor.keyPressed(65);render();select("CPS");select("Day");assertEquals(Set.of("CPS","Day"),editor.selectedNames(),"a click on the overlap picks the live HUD");
        click(101,101,1,0);assertEquals(Set.of("CPS","Day"),editor.selectedNames(),"right-click on the selection keeps it");
        render();assertTrue(contextControl("group").enabled());
    }
    @Test void switchedOffPreviewNeitherPullsNorJoinsADraggedHud(){
        var fps=add("FPS",300,200,60,15,true);add("Coordinates",100,117,60,15,false);
        // Shift-drop FPS 1px above the switched-off Coordinates: it must not dock into a group with a HUD that is not drawn in game.
        render();assertTrue(click(301,201,0,0));drag(101,102,1);
        assertNotEquals(300,fps.getX());assertNull(HudSettings.getInstance().getGroupMembers("FPS"));
        // Once Coordinates is switched on, the same Shift-drop docks and groups.
        ModuleManager.getInstance().getModule("Coordinates").setEnabled(true);HudSettings.getInstance().clearPositions();fps.setPosition(300,200);
        render();assertTrue(click(301,201,0,0));drag(101,102,1);
        assertEquals(Set.of("FPS","Coordinates"),HudSettings.getInstance().getGroupMembers("FPS"));
    }
    @Test void centerGuideWinsOverNearerGridLine(){
        var moving=new HudGroupLayout.Rect(31,30,36,15);var target=new HudGroupLayout.Rect(20,80,60,20);
        var snapped=HudGroupLayout.translate(moving,HudGroupLayout.snapDelta(moving,0,0,10,4,List.of(target),640,360));
        assertEquals(50,snapped.x()+snapped.width()/2);
    }
    @Test void exactEdgeDockBeatsANearbyUnrelatedCenterGuide(){
        var moving=new HudGroupLayout.Rect(100,70,60,15);var above=new HudGroupLayout.Rect(100,50,60,20);var unrelated=new HudGroupLayout.Rect(300,66,40,19);
        var snapped=HudGroupLayout.translate(moving,HudGroupLayout.snapDelta(moving,0,0,10,4,List.of(above,unrelated),640,360));
        assertEquals(above.bottom(),snapped.y());assertEquals(100,snapped.x());
    }
    @Test void centerDockedRowsMatchWidthsAndRemainStableWhenContentChanges(){
        var a=add("FPS",30,20,40,20,true);var b=add("PingHUD",10,42,80,20,true);HudSettings.getInstance().addGroup(Set.of("FPS","PingHUD"));
        render();assertEquals(editor.boundsFor("FPS").x(),editor.boundsFor("PingHUD").x());assertEquals(80,editor.boundsFor("FPS").width());
        render();assertEquals(10,editor.boundsFor("FPS").x());assertEquals(80,editor.boundsFor("FPS").width());assertEquals(30,a.getX());assertEquals(10,b.getX());
    }
}
