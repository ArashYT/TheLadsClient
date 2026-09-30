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
    private final LadsGraphicsTest.MockGraphics graphics=new LadsGraphicsTest.MockGraphics();
    private final AtomicInteger saves=new AtomicInteger();
    private DraggableHudScreen editor;
    private static class Box extends HudElement {
        Box(String name,int x,int y,int width,int height){setModuleName(name);setPosition(x,y);this.width=width;this.height=height;}
        @Override public float getScale(){return 1;}
        @Override public void render(LadsGraphics g){g.fill(x,y,x+width,y+height,-1);}
    }
    @BeforeEach void setup(){
        snapshot=ConfigManager.toJson();original=new ArrayList<>(HudManager.getInstance().getElements());HudManager.getInstance().getElements().clear();
        HudSettings.getInstance().clearPositions();HudSettings.getInstance().replaceGroups(List.of());HudSettings.getInstance().replaceLocked(Set.of());
        graphics.width=640;graphics.height=360;editor=new DraggableHudScreen(saves::incrementAndGet);
    }
    @AfterEach void restore(){editor.close();HudManager.getInstance().getElements().clear();HudManager.getInstance().getElements().addAll(original);ConfigManager.applyJson(snapshot);}
    private Box add(String name,int x,int y,int width,int height,boolean enabled){var box=new Box(name,x,y,width,height);HudManager.getInstance().getElements().add(box);ModuleManager.getInstance().getModule(name).setEnabled(enabled);return box;}
    private void render(){editor.render(graphics,-1,-1);}
    private void select(String name){var b=editor.boundsFor(name);assertTrue(editor.mouseClicked(b.x()+1,b.y()+1,0,2));}
    private void click(DraggableHudScreen.Control c){assertTrue(c.enabled());assertTrue(editor.mouseClicked(c.bounds().x()+2,c.bounds().y()+2,0));render();}
    private void context(String id){render();click(editor.contextControls().stream().filter(c->c.id().equals(id)).findFirst().orElseThrow());}
    @Test void rightClickKeepsMultipleSelectionAndGroupsCenteredRows(){
        add("FPS",20,30,40,15,true);add("PingHUD",100,60,80,20,true);add("Coordinates",45,110,60,35,true);
        render();select("FPS");select("PingHUD");select("Coordinates");editor.mouseClicked(22,32,1);render();
        assertEquals(Set.of("FPS","PingHUD","Coordinates"),editor.selectedNames());context("group");
        var a=editor.boundsFor("FPS");var b=editor.boundsFor("PingHUD");var c=editor.boundsFor("Coordinates");
        assertEquals(a.x(),b.x());assertEquals(a.width(),b.width());assertEquals(b.x(),c.x());assertEquals(b.width(),c.width());
        assertEquals(a.bottom()+2,b.y());assertEquals(b.bottom()+2,c.y());assertEquals(1,saves.get());
        assertEquals(Set.of("FPS","PingHUD","Coordinates"),HudSettings.getInstance().getGroupMembers("FPS"));
        editor.mouseClicked(a.x()+1,a.y()+1,1);context("ungroup");assertNull(HudSettings.getInstance().getGroupMembers("FPS"));
    }
    @Test void groupStackClampsOnceAndDoesNotOverwriteLockedCoordinates(){
        add("FPS",590,330,40,15,true);add("PingHUD",570,350,70,20,true);render();select("FPS");select("PingHUD");editor.keyPressed(71,2);render();
        var a=editor.boundsFor("FPS");var b=editor.boundsFor("PingHUD");assertEquals(640,b.right());assertEquals(360,b.bottom());assertEquals(a.bottom()+2,b.y());
        HudSettings.getInstance().setLocked("FPS",true);editor.mouseClicked(a.x()+1,a.y()+1,1);render();
        assertFalse(editor.contextControls().stream().filter(c->c.id().equals("stack")).findFirst().orElseThrow().enabled());
        var before=HudSettings.getInstance().getPosition("FPS").clone();editor.keyPressed(71,2);assertArrayEquals(before,HudSettings.getInstance().getPosition("FPS"));
    }
    @Test void moduleTogglePersistsAndKeepsDisabledPreviewAvailableToTurnBackOn(){
        add("FPS",50,60,60,20,true);render();var original=editor.boundsFor("FPS");var toggle=editor.toggleBoundsFor("FPS");
        assertNotNull(toggle);assertFalse(toggle.intersects(original));editor.mouseClicked(toggle.x()+2,toggle.y()+2,0);render();
        assertFalse(ModuleManager.getInstance().getModule("FPS").isEnabled());assertEquals(original,editor.boundsFor("FPS"));assertEquals(1,saves.get());
        toggle=editor.toggleBoundsFor("FPS");editor.mouseClicked(toggle.x()+2,toggle.y()+2,0);render();assertTrue(ModuleManager.getInstance().getModule("FPS").isEnabled());assertEquals(2,saves.get());
    }
    @Test void allPreviewsExposesPreviouslyDisabledModuleOnButton(){
        add("FPS",50,60,60,20,false);render();assertNull(editor.toggleBoundsFor("FPS"));
        click(editor.controls().stream().filter(c->c.id().equals("previews")).findFirst().orElseThrow());
        var toggle=editor.toggleBoundsFor("FPS");assertNotNull(toggle);editor.mouseClicked(toggle.x()+2,toggle.y()+2,0);assertTrue(ModuleManager.getInstance().getModule("FPS").isEnabled());
    }
    @Test void centerGuideWinsOverNearerGridLine(){
        var moving=new HudGroupLayout.Rect(31,30,36,15);var target=new HudGroupLayout.Rect(20,80,60,20);
        var snapped=HudGroupLayout.translate(moving,HudGroupLayout.snapDelta(moving,0,0,10,4,List.of(target),640,360));
        assertEquals(50,snapped.x()+snapped.width()/2);
    }
    @Test void centerDockedRowsMatchWidthsAndRemainStableWhenContentChanges(){
        var a=add("FPS",30,20,40,20,true);var b=add("PingHUD",10,42,80,20,true);HudSettings.getInstance().addGroup(Set.of("FPS","PingHUD"));
        render();assertEquals(editor.boundsFor("FPS").x(),editor.boundsFor("PingHUD").x());assertEquals(80,editor.boundsFor("FPS").width());
        render();assertEquals(10,editor.boundsFor("FPS").x());assertEquals(80,editor.boundsFor("FPS").width());assertEquals(30,a.getX());assertEquals(10,b.getX());
    }
}
