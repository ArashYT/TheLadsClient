package com.thelads.core;

import com.google.gson.JsonParser;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.client.hud.HudElement;
import com.thelads.core.client.hud.HudGroupLayout;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.HudSettings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudGroupingTest {
    private static class Element extends HudElement {
        boolean active=true;float scale=1;
        Element(String name,int px,int py,int w,int h,float scale){setPosition(px,py);width=w;height=h;this.scale=scale;setModuleName(name);}
        @Override public float getScale(){return scale;}
        @Override public boolean isEnabled(){return active;}
        @Override public void render(LadsGraphics graphics){graphics.fill(x,y,x+width,y+height,0xffffffff);}
    }
    private List<HudElement> previousElements;
    private final List<Set<String>> previousGroups=new ArrayList<>();
    private final Set<String> previousLocks=new HashSet<>();
    private final HashMap<String,int[]> previousPositions=new HashMap<>();
    private final LadsGraphicsTest.MockGraphics graphics=new LadsGraphicsTest.MockGraphics();
    private final AtomicInteger saves=new AtomicInteger();
    private DraggableHudScreen editor;
    private HudSettings settings;
    @BeforeEach void setup(){settings=HudSettings.getInstance();previousElements=new ArrayList<>(HudManager.getInstance().getElements());HudManager.getInstance().getElements().clear();settings.getGroups().forEach(group->previousGroups.add(new HashSet<>(group)));previousLocks.addAll(settings.getLocked());settings.getPositions().forEach((name,xy)->previousPositions.put(name,xy.clone()));settings.getGroups().clear();settings.getLocked().clear();settings.getPositions().clear();editor=new DraggableHudScreen(saves::incrementAndGet);graphics.width=640;graphics.height=360;}
    @AfterEach void restore(){editor.close();HudManager.getInstance().getElements().clear();HudManager.getInstance().getElements().addAll(previousElements);settings.replaceGroups(previousGroups);settings.replaceLocked(previousLocks);settings.getPositions().clear();settings.getPositions().putAll(previousPositions);}
    private Element add(String name,int x,int y,int w,int h,float scale){var value=new Element(name,x,y,w,h,scale);HudManager.getInstance().getElements().add(value);return value;}
    private void render(){editor.render(graphics,-1,-1);}
    /** Pointer input at a game GUI position, through the preview's mapping to the screen. */
    private boolean click(double x,double y,int button,int modifiers){return editor.mouseClicked(editor.screenX(x),editor.screenY(y),button,modifiers);}
    private boolean move(double x,double y){return editor.mouseDragged(editor.screenX(x),editor.screenY(y),0);}
    private boolean release(double x,double y,int modifiers){return editor.mouseReleased(editor.screenX(x),editor.screenY(y),0,modifiers);}
    private void select(String name){var bounds=editor.boundsFor(name);assertNotNull(bounds);assertTrue(click(bounds.x()+1,bounds.y()+1,0,2));}
    private void action(String id){render();var button=editor.controls().stream().filter(control->control.id().equals(id)).findFirst().orElseThrow();assertTrue(button.enabled(),id+" is enabled");assertTrue(editor.mouseClicked(button.bounds().x()+2,button.bounds().y()+2,0));render();}
    private void drag(String name,int dx,int dy){drag(name,dx,dy,0);}
    private void contextClick(String id){var item=editor.contextControls().stream().filter(c->c.id().equals(id)).findFirst().orElseThrow();assertTrue(item.enabled(),id);editor.mouseClicked(item.bounds().x()+2,item.bounds().y()+2,0);render();}
    /** A drag in game pixels; modifiers 1 holds Shift at the drop. */
    private void drag(String name,int dx,int dy,int modifiers){render();var b=editor.boundsFor(name);assertTrue(click(b.x()+1,b.y()+1,0,0));move(b.x()+1+dx,b.y()+1+dy);release(b.x()+1+dx,b.y()+1+dy,modifiers);render();}

    @Test void visibleControlsCreateAndMoveARigidGroup(){Element a=add("A",10,30,40,15,1),b=add("B",80,60,30,20,1);render();select("A");select("B");action("group");assertEquals(Set.of("A","B"),settings.getGroupMembers("A"));editor.keyPressed(71);drag("B",35,27);assertEquals(75,a.getX());assertEquals(75,b.getX());assertEquals(57,a.getY());assertEquals(74,b.getY());assertArrayEquals(new int[]{75,57},settings.getPosition("A"));assertArrayEquals(new int[]{75,74},settings.getPosition("B"));}
    @Test void lockedHudRemainsSelectableAndUnlockWorks(){Element a=add("A",30,40,40,15,1);settings.setLocked("A",true);render();assertTrue(click(31,41,0,0));assertEquals(Set.of("A"),editor.selectedNames());assertFalse(move(100,100));assertEquals(30,a.getX());action("unlock");assertFalse(settings.isLocked("A"));editor.keyPressed(71);drag("A",20,10);assertEquals(50,a.getX());}
    @Test void oneLockedGroupMemberBlocksWholeGroupUntilUnlock(){Element a=add("A",10,30,40,15,1),b=add("B",70,30,40,15,1);settings.addGroup(Set.of("A","B"));settings.setLocked("B",true);render();assertTrue(click(11,31,0,0));assertEquals(Set.of("A","B"),editor.selectedNames());assertFalse(editor.isDragging());action("unlock");editor.keyPressed(71);drag("A",20,0);assertEquals(30,a.getX());assertEquals(90,b.getX());}
    @Test void scaledGroupClampsOnceAtRightAndBottomBorders(){graphics.width=200;graphics.height=260;Element a=add("A",10,20,40,20,2),b=add("B",100,75,20,20,1.5f);settings.addGroup(Set.of("A","B"));render();editor.keyPressed(71);drag("A",1000,1000);assertEquals(90,b.getX()-a.getX());assertEquals(55,b.getY()-a.getY());assertEquals(200,editor.boundsFor("B").right());assertEquals(260,editor.boundsFor("B").bottom());}
    @Test void groupedDisabledMemberMovesWithoutEnablingIt(){Element a=add("A",20,30,30,15,1),b=add("B",70,30,30,15,1);b.active=false;settings.addGroup(Set.of("A","B"));render();assertNull(editor.boundsFor("B"));editor.keyPressed(71);drag("A",20,0);assertEquals(90,b.getX());assertFalse(b.active);assertArrayEquals(new int[]{90,30},settings.getPosition("B"));}
    @Test void resizeKeepsGroupOffsetsAndDoesNotRewriteSavedLayout(){Element a=add("A",450,40,40,15,1),b=add("B",520,70,60,15,1);settings.addGroup(Set.of("A","B"));render();graphics.width=320;render();assertEquals(70,editor.boundsFor("B").x()-editor.boundsFor("A").x());assertEquals(30,editor.boundsFor("B").y()-editor.boundsFor("A").y());assertEquals(320,editor.boundsFor("B").right());assertEquals(450,a.getX());assertEquals(520,b.getX());assertEquals(0,saves.get());}
    @Test void oversizedGroupsStayRigidAndCanPanBetweenEdges(){var union=new HudGroupLayout.Rect(0,0,400,40);var first=HudGroupLayout.clampDelta(union,-500,0,200,100);var second=HudGroupLayout.clampDelta(union,500,0,200,100);assertEquals(-200,first.x());assertEquals(0,second.x());assertEquals(400,HudGroupLayout.translate(union,first).width());}
    @Test void snappingUsesWholeSelectionAndNearestGuide(){var bounds=new HudGroupLayout.Rect(17,18,70,20);var delta=HudGroupLayout.snapDelta(bounds,9,0,10,4,List.of(),640,360);assertEquals(30,HudGroupLayout.translate(bounds,delta).x());assertEquals(70,HudGroupLayout.translate(bounds,delta).width());var edge=HudGroupLayout.snapDelta(bounds,200,0,0,4,List.of(new HudGroupLayout.Rect(290,50,20,20)),640,360);assertEquals(290,HudGroupLayout.translate(bounds,edge).right());}
    @Test void groupMergeDoesNotDropUnselectedExistingMembers(){settings.addGroup(Set.of("A","B"));settings.addGroup(Set.of("C","D"));settings.addGroup(Set.of("B","C"));assertEquals(1,settings.getGroups().size());assertEquals(Set.of("A","B","C","D"),settings.getGroupMembers("A"));}
    @Test void ungroupControlRestoresIndividualDragging(){Element a=add("A",20,30,30,15,1),b=add("B",70,30,30,15,1);settings.addGroup(Set.of("A","B"));render();select("A");action("ungroup");assertNull(settings.getGroupMembers("A"));editor.keyPressed(71);drag("A",10,0);assertEquals(30,a.getX());assertEquals(70,b.getX());}
    @Test void groupAndLocksSurviveConfigSnapshotRoundTrip(){settings.addGroup(Set.of("A","B"));settings.setLocked("A",true);settings.setPosition("A",22,35);var snapshot=ConfigManager.toJson();settings.getGroups().clear();settings.getLocked().clear();settings.getPositions().clear();ConfigManager.applyJson(snapshot);assertEquals(Set.of("A","B"),settings.getGroupMembers("B"));assertTrue(settings.isLocked("A"));assertArrayEquals(new int[]{22,35},settings.getPosition("A"));}
    @Test void malformedAndOverlappingSavedGroupsNormalizeWithoutLosingGoodGroups(){ConfigManager.applyJson(JsonParser.parseString("{\"hud\":{\"groups\":[[\"A\",\"B\"],false,[\"B\",\"C\"],[\"alone\"],[null,42,\"\"]],\"locked\":[\"A\",null,false,\"\"]}}").getAsJsonObject());assertEquals(List.of(Set.of("A","B","C")),settings.getGroups());assertEquals(Set.of("A"),settings.getLocked());}
    @Test void marqueeSelectsAndGroupsWithoutKeyboardShortcuts(){add("A",20,30,30,15,1);add("B",70,30,30,15,1);render();click(10,20,0,0);assertTrue(move(110,60));assertTrue(release(110,60,0));assertEquals(Set.of("A","B"),editor.selectedNames());action("group");assertEquals(Set.of("A","B"),settings.getGroupMembers("A"));}
    @Test void closingMidDragPersistsAllMembersAndEndsEditing(){Element a=add("A",20,30,30,15,1),b=add("B",70,30,30,15,1);settings.addGroup(Set.of("A","B"));render();editor.keyPressed(71);click(21,31,0,0);move(41,51);editor.close();assertFalse(editor.isDragging());assertArrayEquals(new int[]{40,50},settings.getPosition("A"));assertArrayEquals(new int[]{90,50},settings.getPosition("B"));assertEquals(1,saves.get());}
    @Test void groupCreationDetachesAutomaticAnchorsBeforeAnyResize(){
        class Anchored extends Element {boolean attached=true;Anchored(){super("Anchored",0,30,40,15,1);}@Override public int getDisplayX(LadsGraphics g){return attached?g.getScaledWidth()-60:super.getDisplayX(g);}@Override public void beginPositionEdit(){super.beginPositionEdit();attached=false;}}
        var anchored=new Anchored();HudManager.getInstance().getElements().add(anchored);add("A",500,60,30,15,1);render();select("Anchored");select("A");action("group");assertFalse(anchored.attached);int gap=editor.boundsFor("Anchored").x()-editor.boundsFor("A").x();graphics.width=320;render();assertEquals(gap,editor.boundsFor("Anchored").x()-editor.boundsFor("A").x());assertArrayEquals(new int[]{540,30},settings.getPosition("Anchored"));
    }
    @Test void previewKeepsTheGameAspectAndFitsBesideTheChromeAtEveryGuiSize(){
        add("Armor",470,330,70,20,1);
        for(int[] size:new int[][]{{640,360},{427,240},{320,240},{480,270}}){
            graphics.width=size[0];graphics.height=size[1];render();var preview=editor.previewBounds();
            assertEquals(size[0]/(double)size[1],preview.width()/(double)preview.height(),0.02,"aspect at "+size[0]+"x"+size[1]);
            assertEquals(preview.width(),size[0]*editor.previewScale(),1);assertTrue(editor.previewScale()>0.5,"usable preview at "+size[0]+"x"+size[1]);
            assertTrue(preview.x()>=0&&preview.y()>=0&&preview.right()<=size[0]&&preview.bottom()<=size[1]);
            for(var control:editor.controls()){assertFalse(control.bounds().intersects(preview),control.id()+" covers the preview");
                assertTrue(control.bounds().x()>=0&&control.bounds().right()<=size[0]&&control.bounds().bottom()<=size[1],control.id()+" off screen at "+size[0]+"x"+size[1]);}
            var armor=editor.toScreen(editor.boundsFor("Armor"));assertTrue(preview.x()<=armor.x()&&armor.right()<=preview.right()+1&&armor.bottom()<=preview.bottom()+1);
        }
    }
    @Test void selectAllExpandsHiddenLockedGroupMembers(){add("A",20,30,30,15,1);var b=add("B",70,30,30,15,1);b.active=false;settings.addGroup(Set.of("A","B"));settings.setLocked("B",true);render();assertTrue(editor.keyPressed(65,2));assertEquals(Set.of("A","B"),editor.selectedNames());action("unlock");assertFalse(settings.isLocked("B"));}
    @Test void selectingAClampedGroupAfterResizeDoesNotRebaseSavedCoordinates(){
        Element a=add("A",450,30,30,15,1),b=add("B",510,30,30,15,1);settings.setPosition("A",450,30);settings.setPosition("B",510,30);settings.addGroup(Set.of("A","B"));render();graphics.width=320;render();var bounds=editor.boundsFor("A");click(bounds.x()+1,bounds.y()+1,0,0);release(bounds.x()+1,bounds.y()+1,0);editor.close();assertEquals(450,a.getX());assertEquals(510,b.getX());assertArrayEquals(new int[]{450,30},settings.getPosition("A"));assertArrayEquals(new int[]{510,30},settings.getPosition("B"));assertEquals(0,saves.get());
    }
    @Test void aZeroDistanceDragDoesNotDetachAnAnchorOrSave(){
        class Anchored extends Element {boolean edited;Anchored(){super("A",17,33,30,15,1);}@Override public void beginPositionEdit(){super.beginPositionEdit();edited=true;}}
        var a=new Anchored();HudManager.getInstance().getElements().add(a);render();click(18,34,0,0);move(18,34);release(18,34,1);assertFalse(a.edited);assertEquals(0,saves.get());
    }
    @Test void settingsGearOpensCorrectModuleWithoutDragging(){
        add("A",20,40,60,30,1);var opened=new ArrayList<String>();editor.setOnSettings(opened::add);render();
        var gear=editor.settingsBoundsFor("A");assertNotNull(gear,"every listed HUD has a settings gear in its row");
        assertTrue(editor.mouseClicked(gear.x()+5,gear.y()+5,0));assertEquals(List.of("A"),opened);assertFalse(editor.isDragging());assertEquals(0,saves.get());
    }
    @Test void contextCenterMovesWholeGroupAndHonorsLocks(){
        add("A",20,40,60,30,1);add("B",100,40,40,30,1);settings.addGroup(Set.of("A","B"));render();
        click(22,42,1,0);render();contextClick("centerBoth");
        assertEquals(260,editor.boundsFor("A").x());assertEquals(165,editor.boundsFor("A").y());assertEquals(80,editor.boundsFor("B").x()-editor.boundsFor("A").x());
        settings.setLocked("A",true);var before=editor.boundsFor("A");click(before.x()+2,before.y()+2,1,0);render();
        var center=editor.contextControls().stream().filter(c->c.id().equals("centerBoth")).findFirst().orElseThrow();assertFalse(center.enabled());
        editor.mouseClicked(center.bounds().x()+2,center.bounds().y()+2,0);render();assertEquals(before,editor.boundsFor("A"));
    }


    @Test void stackedGroupDoesNotDockToAnOverlappingClampedWidget(){
        add("A",12,42,62,14,1.5f);add("B",12,78,30,14,1.25f);add("Edge",10000,10,50,15,1);
        settings.setPosition("Edge",10000,10);render();select("A");select("B");action("group");
        drag("A",10000,-10000,1);
        assertEquals(Set.of("A","B"),settings.getGroupMembers("A"));
        assertEquals(graphics.width,Math.max(editor.boundsFor("A").right(),editor.boundsFor("B").right()));
        assertEquals(0,editor.boundsFor("A").y());assertArrayEquals(new int[]{10000,10},settings.getPosition("Edge"));
    }
    @Test void plainDropSnapsAgainstAHudButNeverGroups(){
        add("A",400,100,50,15,1);add("Edge",10000,10,50,15,1);settings.setPosition("Edge",10000,10);
        render();drag("A",190,-73);
        assertNull(settings.getGroupMembers("A"),"no Shift at the drop: no group");
        assertEquals(590,editor.boundsFor("A").x());assertEquals(editor.boundsFor("Edge").bottom(),editor.boundsFor("A").y(),"snapping still docks it");
        assertArrayEquals(new int[]{10000,10},settings.getPosition("Edge"));assertEquals(1,saves.get());
        drag("A",0,40);assertEquals(editor.boundsFor("Edge").bottom()+40,editor.boundsFor("A").y(),"and it moves away on its own");
    }
    @Test void shiftHeldFromPressToDropDragsAndGroups(){
        add("A",400,100,50,15,1);add("B",590,10,50,15,1);render();
        var a=editor.boundsFor("A");assertTrue(click(a.x()+1,a.y()+1,0,1));assertTrue(editor.isDragging(),"Shift on a HUD starts a drag, not a selection toggle");
        move(a.x()+1+190,a.y()+1-73);release(a.x()+1+190,a.y()+1-73,1);render();
        assertEquals(Set.of("A","B"),settings.getGroupMembers("A"));
    }
    @Test void dockingCapturesTheStationaryWidgetsClampedOrigin(){
        add("A",400,100,50,15,1);add("Edge",10000,10,50,15,1);settings.setPosition("Edge",10000,10);
        render();drag("A",190,-73,1);
        assertEquals(Set.of("A","Edge"),settings.getGroupMembers("A"));
        assertEquals(590,editor.boundsFor("A").x());assertEquals(590,editor.boundsFor("Edge").x());
        assertArrayEquals(new int[]{590,10},settings.getPosition("Edge"));assertEquals(graphics.width,editor.boundsFor("A").right());
    }
    @Test void dockingCapturesEveryMemberOfTheStationaryGroup(){
        add("A",400,100,50,15,1);add("B",10000,10,50,15,1);add("C",10000,27,50,15,1);
        settings.setPosition("B",10000,10);settings.setPosition("C",10000,27);settings.addGroup(Set.of("B","C"));
        render();drag("A",190,-56,1);
        assertEquals(Set.of("A","B","C"),settings.getGroupMembers("A"));
        assertArrayEquals(new int[]{590,10},settings.getPosition("B"));assertArrayEquals(new int[]{590,27},settings.getPosition("C"));
        assertEquals(590,editor.boundsFor("A").x());assertEquals(590,editor.boundsFor("B").x());assertEquals(590,editor.boundsFor("C").x());
    }

}
