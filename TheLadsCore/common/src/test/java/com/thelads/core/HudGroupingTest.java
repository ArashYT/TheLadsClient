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
    private void select(String name){var bounds=editor.boundsFor(name);assertNotNull(bounds);assertTrue(editor.mouseClicked(bounds.x()+1,bounds.y()+1,0,2));}
    private void action(String id){render();var button=editor.controls().stream().filter(control->control.id().equals(id)).findFirst().orElseThrow();assertTrue(button.enabled(),id+" is enabled");assertTrue(editor.mouseClicked(button.bounds().x()+2,button.bounds().y()+2,0));render();}
    private void drag(String name,int dx,int dy){render();var b=editor.boundsFor(name);assertTrue(editor.mouseClicked(b.x()+1,b.y()+1,0));editor.mouseDragged(b.x()+1+dx,b.y()+1+dy,0);editor.mouseReleased(b.x()+1+dx,b.y()+1+dy,0);render();}

    @Test void visibleControlsCreateAndMoveARigidGroup(){Element a=add("A",10,30,40,15,1),b=add("B",80,60,30,20,1);render();select("A");select("B");action("group");assertEquals(Set.of("A","B"),settings.getGroupMembers("A"));editor.keyPressed(71);drag("B",35,27);assertEquals(45,a.getX());assertEquals(115,b.getX());assertEquals(57,a.getY());assertEquals(87,b.getY());assertArrayEquals(new int[]{45,57},settings.getPosition("A"));assertArrayEquals(new int[]{115,87},settings.getPosition("B"));}
    @Test void lockedHudRemainsSelectableAndUnlockWorks(){Element a=add("A",30,40,40,15,1);settings.setLocked("A",true);render();assertTrue(editor.mouseClicked(31,41,0));assertEquals(Set.of("A"),editor.selectedNames());assertFalse(editor.mouseDragged(100,100,0));assertEquals(30,a.getX());action("unlock");assertFalse(settings.isLocked("A"));editor.keyPressed(71);drag("A",20,10);assertEquals(50,a.getX());}
    @Test void oneLockedGroupMemberBlocksWholeGroupUntilUnlock(){Element a=add("A",10,30,40,15,1),b=add("B",70,30,40,15,1);settings.addGroup(Set.of("A","B"));settings.setLocked("B",true);render();assertTrue(editor.mouseClicked(11,31,0));assertEquals(Set.of("A","B"),editor.selectedNames());assertFalse(editor.isDragging());action("unlock");editor.keyPressed(71);drag("A",20,0);assertEquals(30,a.getX());assertEquals(90,b.getX());}
    @Test void scaledGroupClampsOnceAtRightAndBottomBorders(){graphics.width=200;graphics.height=260;Element a=add("A",10,20,40,20,2),b=add("B",100,75,20,20,1.5f);settings.addGroup(Set.of("A","B"));render();editor.keyPressed(71);drag("A",1000,1000);assertEquals(90,b.getX()-a.getX());assertEquals(55,b.getY()-a.getY());assertEquals(200,editor.boundsFor("B").right());assertEquals(260,editor.boundsFor("B").bottom());}
    @Test void groupedDisabledMemberMovesWithoutEnablingIt(){Element a=add("A",20,30,30,15,1),b=add("B",70,30,30,15,1);b.active=false;settings.addGroup(Set.of("A","B"));render();assertNull(editor.boundsFor("B"));editor.keyPressed(71);drag("A",20,0);assertEquals(90,b.getX());assertFalse(b.active);assertArrayEquals(new int[]{90,30},settings.getPosition("B"));}
    @Test void resizeKeepsGroupOffsetsAndDoesNotRewriteSavedLayout(){Element a=add("A",450,40,40,15,1),b=add("B",520,70,60,15,1);settings.addGroup(Set.of("A","B"));render();graphics.width=320;render();assertEquals(70,editor.boundsFor("B").x()-editor.boundsFor("A").x());assertEquals(30,editor.boundsFor("B").y()-editor.boundsFor("A").y());assertEquals(320,editor.boundsFor("B").right());assertEquals(450,a.getX());assertEquals(520,b.getX());assertEquals(0,saves.get());}
    @Test void oversizedGroupsStayRigidAndCanPanBetweenEdges(){var union=new HudGroupLayout.Rect(0,0,400,40);var first=HudGroupLayout.clampDelta(union,-500,0,200,100);var second=HudGroupLayout.clampDelta(union,500,0,200,100);assertEquals(-200,first.x());assertEquals(0,second.x());assertEquals(400,HudGroupLayout.translate(union,first).width());}
    @Test void snappingUsesWholeSelectionAndNearestGuide(){var bounds=new HudGroupLayout.Rect(17,18,70,20);var delta=HudGroupLayout.snapDelta(bounds,9,0,10,4,List.of(),640,360);assertEquals(30,HudGroupLayout.translate(bounds,delta).x());assertEquals(70,HudGroupLayout.translate(bounds,delta).width());var edge=HudGroupLayout.snapDelta(bounds,200,0,0,4,List.of(new HudGroupLayout.Rect(290,50,20,20)),640,360);assertEquals(290,HudGroupLayout.translate(bounds,edge).right());}
    @Test void groupMergeDoesNotDropUnselectedExistingMembers(){settings.addGroup(Set.of("A","B"));settings.addGroup(Set.of("C","D"));settings.addGroup(Set.of("B","C"));assertEquals(1,settings.getGroups().size());assertEquals(Set.of("A","B","C","D"),settings.getGroupMembers("A"));}
    @Test void ungroupControlRestoresIndividualDragging(){Element a=add("A",20,30,30,15,1),b=add("B",70,30,30,15,1);settings.addGroup(Set.of("A","B"));render();select("A");action("ungroup");assertNull(settings.getGroupMembers("A"));editor.keyPressed(71);drag("A",10,0);assertEquals(30,a.getX());assertEquals(70,b.getX());}
    @Test void groupAndLocksSurviveConfigSnapshotRoundTrip(){settings.addGroup(Set.of("A","B"));settings.setLocked("A",true);settings.setPosition("A",22,35);var snapshot=ConfigManager.toJson();settings.getGroups().clear();settings.getLocked().clear();settings.getPositions().clear();ConfigManager.applyJson(snapshot);assertEquals(Set.of("A","B"),settings.getGroupMembers("B"));assertTrue(settings.isLocked("A"));assertArrayEquals(new int[]{22,35},settings.getPosition("A"));}
    @Test void malformedAndOverlappingSavedGroupsNormalizeWithoutLosingGoodGroups(){ConfigManager.applyJson(JsonParser.parseString("{\"hud\":{\"groups\":[[\"A\",\"B\"],false,[\"B\",\"C\"],[\"alone\"],[null,42,\"\"]],\"locked\":[\"A\",null,false,\"\"]}}").getAsJsonObject());assertEquals(List.of(Set.of("A","B","C")),settings.getGroups());assertEquals(Set.of("A"),settings.getLocked());}
    @Test void marqueeSelectsAndGroupsWithoutKeyboardShortcuts(){add("A",20,30,30,15,1);add("B",70,30,30,15,1);render();editor.mouseClicked(10,20,0);assertTrue(editor.mouseDragged(110,60,0));assertTrue(editor.mouseReleased(110,60,0));assertEquals(Set.of("A","B"),editor.selectedNames());action("group");assertEquals(Set.of("A","B"),settings.getGroupMembers("A"));}
    @Test void closingMidDragPersistsAllMembersAndEndsEditing(){Element a=add("A",20,30,30,15,1),b=add("B",70,30,30,15,1);settings.addGroup(Set.of("A","B"));render();editor.keyPressed(71);editor.mouseClicked(21,31,0);editor.mouseDragged(41,51,0);editor.close();assertFalse(editor.isDragging());assertArrayEquals(new int[]{40,50},settings.getPosition("A"));assertArrayEquals(new int[]{90,50},settings.getPosition("B"));assertEquals(1,saves.get());}
    @Test void groupCreationDetachesAutomaticAnchorsBeforeAnyResize(){
        class Anchored extends Element {boolean attached=true;Anchored(){super("Anchored",0,30,40,15,1);}@Override public int getDisplayX(LadsGraphics g){return attached?g.getScaledWidth()-60:super.getDisplayX(g);}@Override public void beginPositionEdit(){super.beginPositionEdit();attached=false;}}
        var anchored=new Anchored();HudManager.getInstance().getElements().add(anchored);add("A",500,60,30,15,1);render();select("Anchored");select("A");action("group");assertFalse(anchored.attached);int gap=editor.boundsFor("Anchored").x()-editor.boundsFor("A").x();graphics.width=320;render();assertEquals(gap,editor.boundsFor("Anchored").x()-editor.boundsFor("A").x());assertArrayEquals(new int[]{580,30},settings.getPosition("Anchored"));
    }
    @Test void toolbarAvoidsBottomHudAndCanBeMovedExplicitly(){add("Armor",470,330,70,20,1);render();var bottomHud=editor.boundsFor("Armor");assertTrue(editor.controls().stream().noneMatch(control->control.bounds().intersects(bottomHud)));var before=editor.controls().getFirst().bounds().y();action("toolbar");assertTrue(editor.controls().getFirst().bounds().y()>before);}
    @Test void selectAllExpandsHiddenLockedGroupMembers(){add("A",20,30,30,15,1);var b=add("B",70,30,30,15,1);b.active=false;settings.addGroup(Set.of("A","B"));settings.setLocked("B",true);render();assertTrue(editor.keyPressed(65,2));assertEquals(Set.of("A","B"),editor.selectedNames());action("unlock");assertFalse(settings.isLocked("B"));}
    @Test void selectingAClampedGroupAfterResizeDoesNotRebaseSavedCoordinates(){
        Element a=add("A",450,30,30,15,1),b=add("B",510,30,30,15,1);settings.setPosition("A",450,30);settings.setPosition("B",510,30);settings.addGroup(Set.of("A","B"));render();graphics.width=320;render();var bounds=editor.boundsFor("A");editor.mouseClicked(bounds.x()+1,bounds.y()+1,0);editor.mouseReleased(bounds.x()+1,bounds.y()+1,0);editor.close();assertEquals(450,a.getX());assertEquals(510,b.getX());assertArrayEquals(new int[]{450,30},settings.getPosition("A"));assertArrayEquals(new int[]{510,30},settings.getPosition("B"));assertEquals(0,saves.get());
    }
    @Test void aZeroDistanceDragDoesNotDetachAnAnchorOrSave(){
        class Anchored extends Element {boolean edited;Anchored(){super("A",17,33,30,15,1);}@Override public void beginPositionEdit(){super.beginPositionEdit();edited=true;}}
        var a=new Anchored();HudManager.getInstance().getElements().add(a);render();editor.mouseClicked(18,34,0);editor.mouseDragged(18,34,0);editor.mouseReleased(18,34,0);assertFalse(a.edited);assertEquals(0,saves.get());
    }
    @Test void settingsGearOpensCorrectModuleWithoutDragging(){
        add("A",20,40,60,30,1);var opened=new ArrayList<String>();editor.setOnSettings(opened::add);render();
        assertTrue(editor.mouseClicked(87,45,0));assertEquals(List.of("A"),opened);assertFalse(editor.isDragging());assertEquals(0,saves.get());
    }
    @Test void contextCenterMovesWholeGroupAndHonorsLocks(){
        add("A",20,40,60,30,1);add("B",100,40,40,30,1);settings.addGroup(Set.of("A","B"));render();
        editor.mouseClicked(22,42,1);render();editor.mouseClicked(25,42+4*21+5,0);render();
        assertEquals(260,editor.boundsFor("A").x());assertEquals(165,editor.boundsFor("A").y());assertEquals(80,editor.boundsFor("B").x()-editor.boundsFor("A").x());
        settings.setLocked("A",true);var before=editor.boundsFor("A");editor.mouseClicked(before.x()+2,before.y()+2,1);render();editor.mouseClicked(before.x()+5,before.y()+2+4*21+5,0);render();assertEquals(before,editor.boundsFor("A"));
    }
    @Test void toolbarCyclesBothVerticalSidesAndCanCollapseAndRestore(){
        render();action("toolbar");assertTrue(editor.controls().stream().allMatch(c->c.bounds().x()<graphics.width/2));
        action("toolbar");assertTrue(editor.controls().stream().allMatch(c->c.bounds().x()>graphics.width/2));
        action("collapse");assertEquals(1,editor.controls().size());var button=editor.controls().getFirst().bounds();assertEquals(graphics.width/2,button.x()+button.width()/2);
        action("collapse");assertTrue(editor.controls().size()>1);
    }

    @Test void rightEdgeStackPlacesSettingsBesideText(){
        add("A",580,40,55,15,1);add("B",580,60,55,15,1);var opened=new ArrayList<String>();editor.setOnSettings(opened::add);render();
        assertTrue(editor.mouseClicked(572,45,0));assertTrue(editor.mouseClicked(572,65,0));assertEquals(List.of("A","B"),opened);
    }
    @Test void verticalControlsFitSmallGui(){
        graphics.width=320;graphics.height=240;render();action("toolbar");
        assertTrue(editor.controls().stream().allMatch(c->c.bounds().y()>=0&&c.bounds().bottom()<=240));
    }

}
