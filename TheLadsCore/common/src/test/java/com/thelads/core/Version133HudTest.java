package com.thelads.core;

import com.thelads.core.client.bridge.*;
import com.thelads.core.client.gui.*;
import com.thelads.core.client.hud.*;
import com.thelads.core.config.*;
import com.thelads.core.modules.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class Version133HudTest {
    @TempDir Path temp;
    private com.google.gson.JsonObject before;
    private final LadsGraphicsTest.MockGraphics g=new LadsGraphicsTest.MockGraphics();
    @BeforeEach void setup(){before=ConfigManager.toJson();ConfigManager.setTestConfigFile(temp.resolve("settings.json").toFile());LadsGameBridge.set(new DefaultGameBridge());g.width=640;g.height=360;}
    @AfterEach void restore(){ConfigManager.applyJson(before);ConfigManager.setTestConfigFile(null);}
    @Test void paperDollDefaultTriggersAndPitchMatchConsoleBehavior(){
        var module=new PaperdollModule();
        assertEquals(Set.of("Crouching","Sprinting","Swimming","Riding"),module.getOptions().stream().filter(o->o instanceof PlayerActionOption a&&a.get()).map(Option::getName).collect(java.util.stream.Collectors.toSet()));
        assertFalse(((BoolOption)module.getOption("Always Display")).get());
        assertEquals("Yaw Only",((DropdownOption)module.getOption("Head Movement")).getValue());
    }
    @Test void colorInheritanceAndOverridesSurviveConfigRoundTrip(){
        var module=(HudModule)ModuleManager.getInstance().getModule("FPS");
        module.setUseGlobalColor(false);module.setCustomColor(0xFFAABBCC);
        var hud=HudSettings.getInstance();hud.setGlobalColor(0xFF001122);hud.setBackgrounds(false);hud.setTextShadow(false);hud.getFavoriteColors().clear();hud.getFavoriteColors().add(0x12345678);
        var json=ConfigManager.toJson();module.setUseGlobalColor(true);hud.setBackgrounds(true);hud.getFavoriteColors().clear();ConfigManager.applyJson(json);
        assertFalse(module.isUseGlobalColor());assertEquals(0xFFAABBCC,module.getCustomColor());assertEquals(0xFF001122,hud.getGlobalColor());assertFalse(hud.isBackgrounds());assertFalse(hud.isTextShadow());assertEquals(List.of(0x12345678),hud.getFavoriteColors());
    }
    @Test void oldNametagSettingsMigrateWithoutLosingExplicitBackgroundChoice(){
        var old=com.google.gson.JsonParser.parseString("{\"modules\":{\"ToggleNametags\":{\"enabled\":true,\"options\":{\"Render Background\":false}}}}").getAsJsonObject();ConfigManager.applyJson(old);
        var module=ModuleManager.getInstance().getModule("Nametags");assertTrue(module.isEnabled());assertFalse(((BoolOption)module.getOption("Render Background")).get());
    }
    private static final class Box extends HudElement {
        Box(String name,int y,int width){setModuleName(name);setPosition(10,y);this.width=width;height=20;}
        public void render(LadsGraphics g){drawBackground(g);}
    }
    @Test void connectedWidthsMatchAndUngroupingRestoresNaturalGeometry(){
        var a=new Box("a",10,40);var b=new Box("b",30,95);var hud=HudSettings.getInstance();hud.addGroup(Set.of("a","b"));
        var bounds=new LinkedHashMap<HudElement,HudGroupLayout.Rect>();bounds.put(a,a.measureBounds(g,true));bounds.put(b,b.measureBounds(g,true));HudGroupLayout.matchDockedWidths(bounds);
        assertEquals(95,a.getRenderWidth());assertEquals(a.getRenderWidth(),b.getRenderWidth());hud.ungroup(Set.of("a"));assertEquals(40,a.measureBounds(g,true).width());
    }
    @Test void globallyDisabledBackgroundDoesNotSubmitModulePlate(){
        var box=new Box("a",10,40);HudSettings.getInstance().setBackgrounds(false);box.render(g);assertTrue(g.drawCalls.isEmpty());
    }
    @Test void pickerCancelDoesNotApplyButEnterCommitsChosenColor(){
        var result=new AtomicInteger(0);var picker=new ColorPicker();picker.open("Test",0xFF123456,result::set);picker.render(g,0,0);picker.key(256);assertEquals(0,result.get());assertFalse(picker.isOpen());
        picker.open("Test",0xFF123456,result::set);picker.key(257);assertEquals(0xFF123456,result.get());assertFalse(picker.isOpen());
    }
    @Test void cpsUsesSixtyFivePercentTextScale(){
        var keys=new KeystrokesHudElement();keys.setModuleName("Keystrokes");keys.prepareRender(g,true);keys.renderEditor(g);assertEquals(2,g.drawCalls.stream().filter(s->s.equals("scale:0.65,0.65")).count());
    }
    @Test void collapsedDockOpensOnHoverAndClosesAfterExitDeadline()throws Exception{
        var editor=new DraggableHudScreen(()->{});editor.render(g,-1,-1);
        var hide=editor.controls().stream().filter(c->c.id().equals("collapse")).findFirst().orElseThrow();editor.mouseClicked(hide.bounds().x()+2,hide.bounds().y()+2,0);editor.render(g,-1,-1);
        assertEquals(1,editor.controls().size());var tab=editor.controls().getFirst().bounds();assertTrue(tab.x()==0||tab.y()==0||tab.right()==g.width||tab.bottom()==g.height);
        editor.render(g,tab.x()+2,tab.y()+2);assertTrue(editor.controls().size()>1);
        var deadline=DraggableHudScreen.class.getDeclaredField("dockExitNanos");deadline.setAccessible(true);deadline.setLong(editor,System.nanoTime()-1_600_000_000L);editor.render(g,-1,-1);assertEquals(1,editor.controls().size());editor.close();
    }
    @Test void searchableActionDropdownFiltersAndTogglesOnlyMatchingState()throws Exception{
        try(var ownership=new ModsMenuTest.OwnershipFixture()){
            ModuleSupport.registerBuiltIn("Paperdoll");var module=ModuleManager.getInstance().getModule("Paperdoll");var trigger=(BoolOption)module.getOption("Sleeping");trigger.set(false);
            var menu=new LadsSettingsScreen();menu.openModule("Paperdoll");var graphics=new ModsMenuTest.Graphics();graphics.render(menu);graphics.click(menu,"Display actions...");
            "Sleeping".codePoints().forEach(menu::charTyped);graphics.render(menu);assertTrue(menu.isEditingText());menu.keyPressed(257,0);assertTrue(trigger.get());menu.keyPressed(256,0);assertFalse(menu.isEditingText());
        }
    }
}
