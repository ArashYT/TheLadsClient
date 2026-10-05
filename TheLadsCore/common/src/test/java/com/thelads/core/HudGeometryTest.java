package com.thelads.core;

import com.google.gson.JsonObject;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.client.hud.*;
import com.thelads.core.config.*;
import com.thelads.core.config.Module;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class HudGeometryTest {
    private JsonObject saved;
    private LadsGameBridge oldBridge;
    private List<HudElement> oldElements;
    private Game game;
    private Graphics graphics;
    private record Draw(String text, double x, double y, double width, double height) {}
    private static final class Graphics extends LadsGraphicsTest.MockGraphics {
        final List<Draw> draws = new ArrayList<>();
        final ArrayDeque<double[]> poses = new ArrayDeque<>();
        int glyph = 7, font = 11, widthCalls;
        double sx = 1, sy = 1, tx, ty;
        @Override public int textWidth(String value) { widthCalls++; return value.length() * glyph; }
        @Override public int fontHeight() { return font; }
        @Override public void pushPose() { poses.push(new double[]{sx,sy,tx,ty}); }
        @Override public void popPose() { var p=poses.pop();sx=p[0];sy=p[1];tx=p[2];ty=p[3]; }
        @Override public void translate(float x,float y) { tx += x*sx;ty += y*sy; }
        @Override public void scale(float x,float y) { sx*=x;sy*=y; }
        @Override public void fill(int x,int y,int right,int bottom,int color) { draws.add(new Draw(null,x*sx+tx,y*sy+ty,(right-x)*sx,(bottom-y)*sy)); }
        @Override public void drawText(String value,int x,int y,int color,boolean shadow) {
            draws.add(new Draw(value,x*sx+tx,y*sy+ty,(value.length()*glyph+(shadow?1:0))*sx,(font+(shadow?1:0))*sy));
        }
        @Override public void drawPlayerModel(int x,int y,int w,int h,boolean editor) { draws.add(new Draw("player",x*sx+tx,y*sy+ty,w*sx,h*sy)); }
        void clear() { draws.clear();widthCalls=0; }
    }
    private static final class Game extends DefaultGameBridge {
        String biome="Windswept Gravelly Hills";
        int biomeReads;
        boolean ingame;
        @Override public boolean isIngame(){return ingame;}
        List<String> effects=List.of("Strength II (120s)","Night Vision (360s)","Speed II (45s)");
        List<ArmorPiece> armor=List.of(new ArmorPiece("Helmet",120,165),new ArmorPiece("Chestplate",200,240));
        @Override public String getBiomeName(){biomeReads++;return biome;}
        @Override public List<String> getActivePotionEffects(){return effects;}
        @Override public List<ArmorPiece> getArmor(){return armor;}
        @Override public boolean hasPaperDollRenderer(){return true;}
        @Override public ScoreboardSnapshot getScoreboard(){return new ScoreboardSnapshot("Test Objective",List.of(new ScoreLine("Player name","123"),new ScoreLine("Team","4")));}
    }
    @BeforeEach void setup() {
        saved=ConfigManager.toJson();oldBridge=LadsGameBridge.get();oldElements=new ArrayList<>(HudManager.getInstance().getElements());
        HudSettings.getInstance().getPositions().clear();HudSettings.getInstance().getGroups().clear();HudSettings.getInstance().getLocked().clear();
        for(var module:ModuleManager.getInstance().getModules()){module.setEnabled(true);module.getOptions().forEach(Option::reset);}
        cycle("ArmorHUD","Style",1); // the List style; Hotbar Slots: ArmorSlotsHudTest
        game=new Game();graphics=new Graphics();LadsGameBridge.set(game);
    }
    @AfterEach void restore(){ConfigManager.applyJson(saved);LadsGameBridge.set(oldBridge);HudManager.getInstance().getElements().clear();HudManager.getInstance().getElements().addAll(oldElements);}
    private static Module module(String name){return ModuleManager.getInstance().getModule(name);}
    private static void bool(String name,String option,boolean value){((BoolOption)module(name).getOption(option)).set(value);}
    private static void cycle(String name,String option,int value){((DropdownOption)module(name).getOption(option)).setIndex(value);}
    private static HudElement element(String name){
        HudElement result=switch(name){
            case "FPS"->new FPSHudElement();case "CPS"->new CpsHudElement();case "Coordinates"->new CoordinatesHudElement();case "Biome"->new BiomeHudElement();
            case "PingHUD"->new PingHudElement();case "ArmorHUD"->new ArmorHudElement();case "Memory"->new MemoryHudElement();
            case "Direction"->new DirectionHudElement();case "Speed"->new SpeedHudElement();case "Day"->new DayHudElement();
            case "Time"->new TimeHudElement();case "Health"->new HealthHudElement();case "Hunger"->new HungerHudElement();
            case "XP"->new XpHudElement();case "Keystrokes"->new KeystrokesHudElement();case "TexturePacks"->new TexturePackHudElement();
            case "Potion Effects"->new PotionHudElement();case "Scoreboard"->new ScoreboardHudElement();case "Paperdoll"->new PaperdollHudElement();
            default->throw new IllegalArgumentException(name);
        };result.setModuleName(name);return result;
    }
    private HudGroupLayout.Rect paint(HudElement element,boolean editor){
        var measured=element.measureBounds(graphics,editor);
        assertTrue(graphics.draws.isEmpty(),"measurement must not draw");
        var placed=HudGroupLayout.translate(measured,HudGroupLayout.clampDelta(measured,0,0,graphics.width,graphics.height));
        element.renderAt(graphics,placed.x(),placed.y(),editor);
        for(var draw:graphics.draws){
            assertTrue(draw.x>=placed.x()-.0001 && draw.y>=placed.y()-.0001,"draw starts outside measured bounds: "+draw);
            assertTrue(draw.x+draw.width<=placed.right()+.0001 && draw.y+draw.height<=placed.bottom()+.0001,"draw exceeds measured bounds: "+draw+" / "+placed);
        }
        assertTrue(placed.x()>=0&&placed.y()>=0&&placed.right()<=graphics.width&&placed.bottom()<=graphics.height);
        assertTrue(graphics.poses.isEmpty());assertEquals(1,graphics.sx);assertEquals(0,graphics.tx);
        return placed;
    }
    @ParameterizedTest @ValueSource(strings={"FPS","CPS","Coordinates","Biome","PingHUD","ArmorHUD","Memory","Direction","Speed","Day","Time","Health","Hunger","XP","Keystrokes","TexturePacks","Potion Effects","Scoreboard","Paperdoll"})
    void firstFrameBoundsContainEveryDrawAcrossViewportAndHudScales(String name){
        var hud=element(name);
        for(int guiScale:List.of(1,2,3))for(int size:List.of(50,75,100,125,150,200)){
            graphics.width=1920/guiScale;graphics.height=1080/guiScale;
            if(module(name).getOption("Size") instanceof SliderOption slider)slider.setValue(size);
            hud.setPosition(graphics.width-2,graphics.height-2);graphics.clear();paint(hud,true);
            assertEquals(graphics.width-2,hud.getX(),"clamping must not rewrite saved layout");
        }
    }
    @Test void dynamicContentAndFontChangesAreMeasuredBeforeSameFrameClamping(){
        var hud=element("Biome");hud.setPosition(620,340);graphics.width=640;graphics.height=360;
        graphics.clear();var first=paint(hud,false);
        game.biome="Cherry Grove";graphics.glyph=11;graphics.font=17;graphics.clear();var second=paint(hud,false);
        assertNotEquals(first.width(),second.width());assertNotEquals(first.height(),second.height());
        assertEquals(2,game.biomeReads,"one live data snapshot per rendered frame");
        assertEquals(1,graphics.widthCalls,"measurement is reused by the same text draw");
    }
    @Test void multilineOptionAndListGrowthFitOnTheVeryNextFrame(){
        graphics.width=640;graphics.height=360;
        var coords=element("Coordinates");coords.setPosition(630,350);
        bool("Coordinates","Vertical",false);graphics.clear();var horizontal=paint(coords,false);bool("Coordinates","Vertical",true);
        graphics.clear();var vertical=paint(coords,false);assertTrue(vertical.height()>horizontal.height());
        var potion=element("Potion Effects");potion.setPosition(630,350);game.effects=List.of("Speed (2s)");
        graphics.clear();var one=paint(potion,false);game.effects=List.of("Speed II (60s)","Strength III (90s)","Resistance (30s)");
        graphics.clear();var three=paint(potion,false);assertTrue(three.height()>one.height());
    }
    @Test void attachedArmorPreviewFitsTheExactViewportOnItsFirstFrame(){
        var armor=element("ArmorHUD");game.armor=List.of();graphics.width=960;graphics.height=504;
        graphics.clear();var bounds=paint(armor,true);
        assertEquals(graphics.height-4,bounds.bottom());
        assertEquals(2,graphics.draws.stream().filter(d->d.text!=null).count());
        graphics.clear();armor.renderAt(graphics,bounds.x(),bounds.y(),false);
        assertTrue(graphics.draws.isEmpty(),"editor samples cannot leak into gameplay");
    }
    @Test void fractionalScaleUsesConservativeBoundsAndOffsetsRemainInvertible(){
        var board=element("Scoreboard");((SliderOption)module("Scoreboard").getOption("Size")).setValue(125);
        ((SliderOption)module("Scoreboard").getOption("X Offset")).setValue(17);
        ((SliderOption)module("Scoreboard").getOption("Y Offset")).setValue(-12);
        board.setDisplayPosition(120,100);var bounds=board.measureBounds(graphics,false);
        assertEquals(120,bounds.x());assertEquals(100,bounds.y());
        assertEquals((int)Math.ceil(board.getWidth()*1.25),bounds.width());
        assertEquals((int)Math.ceil(board.getHeight()*1.25),bounds.height());
    }
    @Test void hudFpsCapReplaysTheLastBuildInsteadOfBlinking(){
        var biome=element("Biome");HudManager.getInstance().getElements().clear();HudManager.getInstance().getElements().add(biome);
        HudSettings.getInstance().setHudFpsCapEnabled(true);HudSettings.getInstance().setHudFpsLimit(1);game.ingame=true;
        graphics.clear();HudManager.getInstance().render(graphics);
        var built=List.copyOf(graphics.draws);int reads=game.biomeReads;
        assertTrue(built.stream().anyMatch(d->d.text!=null&&d.text.contains("Windswept")));
        graphics.clear();HudManager.getInstance().render(graphics); // within the same second: replayed, not rebuilt, never empty
        assertEquals(built,graphics.draws);assertEquals(reads,game.biomeReads);
        graphics.width=640;graphics.clear();HudManager.getInstance().render(graphics); // a resize rebuilds at once
        assertTrue(game.biomeReads>reads);
        game.ingame=false;HudSettings.getInstance().setHudFpsCapEnabled(false);
    }
    @Test void hudFpsCapRecordsEveryGraphicsCall() throws Exception{
        // A LadsGraphics method left to its default under the cap drops its drawing from the replayed build (it shows on build
        // frames only) or measures the default instead of the game (1.8.9: the Armor HUD ignored Raised's lift with the cap on).
        var recording=Class.forName("com.thelads.core.client.hud.RecordingGraphics");
        for(var method:com.thelads.core.client.bridge.LadsGraphics.class.getMethods()){
            if(method.isDefault()&&(method.getName().equals("drawText")||method.getName().equals("drawCenteredText"))&&method.getParameterCount()==4)continue; // shadow default, then the recorded call
            assertDoesNotThrow(()->recording.getDeclaredMethod(method.getName(),method.getParameterTypes()),method.toString());
        }
    }
    @Test void disabledGroupMemberKeepsLiveAndEditorClampingIdentical(){
        var active=element("CPS");var hidden=element("Day");
        active.setPosition(580,300);hidden.setPosition(700,350);module("Day").setEnabled(false);
        HudSettings.getInstance().addGroup(Set.of("CPS","Day"));
        HudManager.getInstance().getElements().clear();HudManager.getInstance().getElements().addAll(List.of(active,hidden));
        graphics.width=640;graphics.height=360;graphics.clear();HudManager.getInstance().render(graphics);
        var live=graphics.draws.stream().filter(d->d.text!=null&&d.text.startsWith("CPS")).findFirst().orElseThrow();
        assertFalse(graphics.draws.stream().anyMatch(d->d.text!=null&&d.text.startsWith("Day")));
        graphics.clear();var screen=new DraggableHudScreen(()->{});screen.render(graphics,-1,-1);
        var editor=graphics.draws.stream().filter(d->d.text!=null&&d.text.startsWith("CPS")&&screen.previewBounds().contains(d.x,d.y)).findFirst().orElseThrow();
        assertEquals(screen.screenX(live.x),editor.x,0.01);assertEquals(screen.screenY(live.y),editor.y,0.01);
        assertEquals(580,active.getX());assertEquals(700,hidden.getX());
    }
}
