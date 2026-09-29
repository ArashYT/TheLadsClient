package com.thelads.core.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AdditionalIntegrationsTest {
    @TempDir Path temp;
    private static Option option(IntegratedSettings.Page p, String name) {
        return p.options().stream().filter(o -> o.getName().equals(name)).findFirst().orElseThrow();
    }
    private static void toggle(IntegratedSettings.Page p, String name) { ((BoolOption)option(p, name)).toggle(); }

    @Test void unsupportedEnginesRemainWithAdvanced() throws Exception {
        assertNull(AdditionalIntegrations.open("paperdoll"));
        assertNull(AdditionalIntegrations.open("durabilitytooltip"));
        assertNull(AdditionalIntegrations.open("unsupported"));
    }

    @Test void crosshairStagesActualPropertiesAndSavesTheWholeChangeOnce() throws Exception {
        var root = new CrosshairProperties();
        AtomicInteger saves = new AtomicInteger();
        var page = AdditionalIntegrations.crosshair(() -> root, () -> {
            assertFalse(root.enabled.value);
            assertEquals(25, root.crosshair.width.value);
            assertEquals(Style.CIRCLE, root.crosshair.style.value);
            saves.incrementAndGet();
        });
        assertEquals(31, page.options().size());
        assertFalse(page.hasChanges());
        toggle(page, "Enabled");
        ((SliderOption)option(page, "Width")).setValue(25);
        ((DropdownOption)option(page, "Style")).cycle();
        assertTrue(root.enabled.value);
        assertEquals(10, root.crosshair.width.value);
        assertEquals(3, page.apply());
        assertEquals(1, saves.get());
        assertFalse(page.hasChanges());
        assertEquals(0, page.apply());
        assertEquals(1, saves.get());
    }

    @Test void crosshairFailedSaveRestoresPropertiesWithoutLosingStagedChanges() throws Exception {
        var root = new CrosshairProperties();
        var p = AdditionalIntegrations.crosshair(() -> root, () -> { throw new IOException("Read only"); });
        toggle(p, "Enabled");
        ((SliderOption)option(p, "Offset x")).setValue(-100);
        assertThrows(IOException.class, p::apply);
        assertTrue(root.enabled.value);
        assertEquals(0, root.crosshair.offsetX.value);
        assertTrue(p.hasChanges());
        p.revert();
        assertFalse(p.hasChanges());
    }

    @Test void crosshairReplacedPropertiesAreReadAgainForConflictDetection() throws Exception {
        var root = new CrosshairProperties();
        var p = AdditionalIntegrations.crosshair(() -> root, () -> fail("Must not save"));
        ((SliderOption)option(p, "Width")).setValue(20);
        var old = root.crosshair;
        root.crosshair = new Crosshair();
        root.crosshair.width.set(30);
        assertThrows(IllegalStateException.class, p::apply);
        assertEquals(10, old.width.value);
        assertEquals(30, root.crosshair.width.value);
    }

    @Test void crosshairCustomBoundsAndUnrelatedComplexPropertiesStayIntact() throws Exception {
        var root = new CrosshairProperties(); root.crosshair.width.set(120);
        var p = AdditionalIntegrations.crosshair(() -> root, () -> {});
        assertEquals(30, p.options().size());
        assertFalse(p.options().stream().anyMatch(o -> o.getName().equals("Width")));
        toggle(p, "Enabled"); p.apply();
        assertEquals(120, root.crosshair.width.value);
        assertEquals("custom colors and drawn pixels", root.crosshair.complex);
    }

    @Test void raisedBatchesLayersPreservesLinksAndUsesPublicSetters() throws Exception {
        var root = new RaisedOptions(); AtomicInteger saves = new AtomicInteger();
        var p = AdditionalIntegrations.raised(() -> root, saves::incrementAndGet);
        assertEquals(8, p.options().size());
        ((SliderOption)option(p, "Minecraft:hotbar offset Y")).setValue(12);
        ((DropdownOption)option(p, "Minecraft:hotbar direction X")).cycle();
        assertEquals(2, root.layers.get("minecraft:hotbar").displacement.y);
        assertEquals(2, p.apply()); assertEquals(1, saves.get());
        assertEquals(12, root.layers.get("minecraft:hotbar").displacement.y);
        assertEquals(Direction.END, root.layers.get("minecraft:hotbar").direction.x);
        assertEquals("minecraft:hotbar", root.layers.get("minecraft:chat").sync);
    }

    @Test void raisedRemovedLayerCannotWriteIntoAnOrphanObject() throws Exception {
        var root = new RaisedOptions();
        var p = AdditionalIntegrations.raised(() -> root, () -> fail("Must not save"));
        ((SliderOption)option(p, "Minecraft:hotbar offset Y")).setValue(12);
        Layer removed = root.layers.remove("minecraft:hotbar");
        assertThrows(IllegalStateException.class, p::apply);
        assertEquals(2, removed.displacement.y);
    }

    @Test void raisedFailedSaveRestoresMutatedLayers() throws Exception {
        var root = new RaisedOptions();
        var p = AdditionalIntegrations.raised(() -> root, () -> { throw new IOException("Full"); });
        ((SliderOption)option(p, "Minecraft:hotbar offset Y")).setValue(12);
        assertThrows(IOException.class, p::apply);
        assertEquals(2, root.layers.get("minecraft:hotbar").displacement.y);
        assertTrue(p.hasChanges());
    }

    @Test void raisedLargeExistingOffsetsAreNotClampedOnOpen() throws Exception {
        var root = new RaisedOptions(); root.layers.get("minecraft:chat").displacement.x = 300;
        var p = AdditionalIntegrations.raised(() -> root, () -> {});
        assertEquals(7, p.options().size()); assertFalse(p.hasChanges());
        assertEquals(300, root.layers.get("minecraft:chat").displacement.x);
    }

    @Test void screenshotVersionLayoutsBothExposeRealSupportedValues() throws Exception {
        assertEquals(14, screenshotPage(new CatAccess(), new Screenshot121(), () -> {}).options().size());
        assertEquals(13, screenshotPage(new CatAccess(), new Screenshot26(), () -> {}).options().size());
    }

    @Test void screenshotSaveVerifiesTypedPersistedValuesAndPreservesComplexFields() throws Exception {
        var config = new CatAccess(); var options = new Screenshot121();
        Path file = temp.resolve("viewer.json"); AtomicInteger saves = new AtomicInteger();
        var p = screenshotPage(config, options, () -> verify(config, options, file, () -> {
            saves.incrementAndGet(); writeScreenshot(config, options, file);
        }));
        toggle(p, "Display hint tooltip");
        ((DropdownOption)option(p, "Default list order")).cycle();
        ((SliderOption)option(p, "Screen scroll speed")).setValue(20);
        assertEquals(3, p.apply()); assertEquals(1, saves.get());
        var reopened = screenshotPage(config, options, () -> {});
        assertFalse(((BoolOption)option(reopened, "Display hint tooltip")).get());
        assertEquals(20, ((SliderOption)option(reopened, "Screen scroll speed")).getIntValue());
        assertEquals("keep me", JsonParser.parseString(Files.readString(file)).getAsJsonObject().get("unknown").getAsString());
    }

    @Test void screenshotSwallowedSaveFailureIsDetectedAndLiveValuesRollback() throws Exception {
        var config = new CatAccess(); var options = new Screenshot121();
        Path file = temp.resolve("viewer.json"); writeScreenshot(config, options, file);
        var p = screenshotPage(config, options, () -> verify(config, options, file, () -> {}));
        toggle(p, "Display hint tooltip");
        assertThrows(IOException.class, p::apply);
        assertTrue((Boolean)config.getOrFallback(options.DISPLAY_HINT_TOOLTIP, true));
        assertTrue(p.hasChanges());
    }

    @Test void screenshotMissingOrMalformedSavedFileFailsApply() throws Exception {
        var config = new CatAccess(); var options = new Screenshot26(); Path file = temp.resolve("viewer.json");
        assertThrows(IOException.class, () -> verify(config, options, file, () -> {}));
        Files.writeString(file, "{broken");
        assertThrows(IOException.class, () -> verify(config, options, file, () -> {}));
    }

    @Test void screenshotRejectedSetterCannotReportSuccessfulApply() throws Exception {
        var config = new CatAccess(); var options = new Screenshot26();
        config.rejectWrites = true;
        var p = screenshotPage(config, options, () -> fail("Must not save")); toggle(p, "Display hint tooltip");
        assertThrows(IllegalStateException.class, p::apply); assertTrue(p.hasChanges());
    }

    @Test void xaeroAppliesOnlyLocalEditableOptionsUsingEngineCallbacksAndOneSave() throws Exception {
        var manager = new MapManager(); AtomicInteger saves = new AtomicInteger();
        manager.redirector.redirected.add(MapOptions.LINKED);
        manager.synced.values.put(MapOptions.SERVER, true);
        var p = mapPage(manager, saves::incrementAndGet);
        assertEquals(2, p.options().size());
        toggle(p, "Visible"); ((SliderOption)option(p, "Opacity")).setValue(70);
        assertEquals(0, manager.profile.callbacks);
        assertEquals(2, p.apply()); assertEquals(2, manager.profile.callbacks); assertEquals(1, saves.get());
        assertFalse((Boolean)manager.profile.values.get(MapOptions.VISIBLE));
        assertEquals(70, manager.profile.values.get(MapOptions.OPACITY));
    }

    @Test void xaeroProfileSwitchRejectsApplyEvenIfNewValuesMatch() throws Exception {
        var manager = new MapManager(); var p = mapPage(manager, () -> fail("Must not save"));
        toggle(p, "Visible"); var old = manager.profile; manager.profile = new MapProfile();
        assertThrows(IllegalStateException.class, p::apply);
        assertEquals(0, old.callbacks); assertEquals(0, manager.profile.callbacks);
    }

    @Test void xaeroNewServerEnforcementRejectsApplyBeforeAnyMutation() throws Exception {
        var manager = new MapManager(); var p = mapPage(manager, () -> fail("Must not save"));
        toggle(p, "Visible"); manager.synced.values.put(MapOptions.VISIBLE, true);
        assertThrows(IllegalStateException.class, p::apply); assertEquals(0, manager.profile.callbacks);
    }

    @Test void xaeroNewRedirectRejectsApplyBeforeAnyMutation() throws Exception {
        var manager = new MapManager(); var p = mapPage(manager, () -> fail("Must not save"));
        toggle(p, "Visible"); manager.redirector.redirected.add(MapOptions.VISIBLE);
        assertThrows(IllegalStateException.class, p::apply); assertEquals(0, manager.profile.callbacks);
    }

    @Test void xaeroSaveFailureRollsBackThroughEngineCallbacks() throws Exception {
        var manager = new MapManager(); var p = mapPage(manager, () -> { throw new IOException("Full"); });
        toggle(p, "Visible"); assertThrows(IOException.class, p::apply);
        assertTrue((Boolean)manager.profile.values.get(MapOptions.VISIBLE));
        assertEquals(2, manager.profile.callbacks); assertTrue(p.hasChanges());
    }

    @Test void mapRangesMustMatchEveryActualValidValueWithoutInventedChoices() {
        assertArrayEquals(new int[]{-2, 4, 2}, AdditionalIntegrations.uniformIntegerRange(List.of(-2, 0, 2, 4)));
        for (List<?> list : List.of(List.of(), List.of(1), List.of(0, 1, 4), List.of(2, 2), List.of(4, 2), List.of(1, "2"), List.of(Integer.MIN_VALUE, Integer.MAX_VALUE)))
            assertNull(AdditionalIntegrations.uniformIntegerRange(list));
    }

    private static IntegratedSettings.Page screenshotPage(CatAccess config, Object options, IntegratedSettings.Save save) throws Exception {
        return AdditionalIntegrations.screenshots(config, CatAccess.class, CatOption.class, options, save);
    }
    private static void verify(CatAccess config, Object options, Path file, IntegratedSettings.Save save) throws Exception {
        AdditionalIntegrations.verifyScreenshotSave(config, CatAccess.class, CatOption.class, options, CatValues.class, new CatValues(), file, save);
    }
    private static void writeScreenshot(CatAccess config, Object options, Path file) throws Exception {
        var root = new com.google.gson.JsonObject(); root.addProperty("unknown", "keep me");
        var category = new com.google.gson.JsonObject(); root.add("c$viewer", category);
        for (var field : options.getClass().getFields()) if (field.get(options) instanceof CatOption o) {
            Object value = config.getOrFallback(o, o.fallback);
            var node = new com.google.gson.JsonObject();
            node.add("value", new com.google.gson.Gson().toJsonTree(value)); node.addProperty("description", "Upstream description");
            category.add(o.name, node);
        }
        Files.writeString(file, root.toString());
    }
    private static IntegratedSettings.Page mapPage(MapManager manager, IntegratedSettings.Save save) throws Exception {
        return AdditionalIntegrations.xaero("Map", manager, manager.profile, MapOption.class, Indexed.class, MapOptions.class, save);
    }

    public enum Style { CROSS, CIRCLE }
    public static class Property<T> { T value; Property(T value) { this.value=value; } public T get(){return value;} public void set(T value){this.value=value;} }
    public static class CrosshairProperties {
        Property<Boolean> enabled = new Property<>(true); Crosshair crosshair = new Crosshair();
        public Property<Boolean> getIsModEnabled(){return enabled;} public Crosshair getCrosshair(){return crosshair;}
    }
    public static class Crosshair {
        public Property<Style> style = new Property<>(Style.CROSS);
        public Property<Boolean> isKeepDebugEnabled, isAdaptiveColourEnabled, isVisibleDefault, isVisibleHiddenGui, isVisibleDebug,
            isVisibleThirdPerson, isVisibleSpectator, isVisibleHoldingRangedWeapon, isVisibleHoldingThrowableItem, isVisibleUsingSpyglass,
            isOutlineEnabled, isDotEnabled, isDynamicAttackIndicatorEnabled, isDynamicBowEnabled, isHighlightHostilesEnabled,
            isHighlightPassivesEnabled, isHighlightPlayersEnabled, isItemCooldownEnabled, isRainbowEnabled, isToolDamageEnabled, isProjectileIndicatorEnabled;
        public Property<Integer> width=new Property<>(10), height=new Property<>(10), gap=new Property<>(3), thickness=new Property<>(1),
            rotation=new Property<>(0), scale=new Property<>(100), offsetX=new Property<>(0), offsetY=new Property<>(0);
        public String complex = "custom colors and drawn pixels";
        Crosshair() { try { for (var f:getClass().getFields()) if(f.getName().startsWith("is")) f.set(this,new Property<>(true)); } catch(Exception e){throw new AssertionError(e);} }
    }
    public enum Direction { START, END }
    public static class Displacement {
        int x=0,y=2; public int getX(){return x;} public int getY(){return y;} public void setX(int x){this.x=x;} public void setY(int y){this.y=y;}
    }
    public static class Directions {
        Direction x=Direction.START,y=Direction.START;
        public Direction getX(){return x;} public Direction getY(){return y;} public void setX(Direction x){this.x=x;} public void setY(Direction y){this.y=y;}
    }
    public static class Layer {
        Displacement displacement=new Displacement(); Directions direction=new Directions(); String sync="minecraft:hotbar";
        public Displacement getDisplacement(){return displacement;} public Directions getDirection(){return direction;}
    }
    public static class RaisedOptions {
        TreeMap<String,Layer> layers=new TreeMap<>(Map.of("minecraft:hotbar",new Layer(),"minecraft:chat",new Layer()));
        public TreeMap<String,Layer> getLayers(){return layers;}
    }
    public static class CatOption {
        final Object fallback; final String name;
        CatOption(String name,Object fallback){this.name=name;this.fallback=fallback;}
        public Class<?> type(){return fallback.getClass();} public Object defaultValue(){return fallback;} public String optionPath(){return "/viewer/"+name;}
    }
    public static class ScreenshotOptions {
        public final CatOption SHOW_BUTTON_IN_GAME_PAUSE_MENU=new CatOption("pause",true), SHOW_BUTTON_ON_TITLE_SCREEN=new CatOption("title",true),
            REDIRECT_SCREENSHOT_CHAT_LINKS=new CatOption("links",true), DEFAULT_LIST_ORDER=new CatOption("order",Style.CROSS),
            PROMPT_WHEN_DELETING_SCREENSHOT=new CatOption("prompt",true), ENABLE_SCREENSHOT_ENLARGEMENT_ANIMATION=new CatOption("animation",true),
            DISPLAY_HINT_TOOLTIP=new CatOption("hints",true), RENDER_WIDE_PROPERTIES_BUTTON=new CatOption("wide",true),
            INVERT_ZOOM_DIRECTION=new CatOption("zoom",true), SCREENSHOT_ELEMENT_TEXT_VISIBILITY=new CatOption("visibility",Style.CROSS),
            RENDER_SCREENSHOT_ELEMENT_FONT_SHADOW=new CatOption("shadow",true), INITIAL_SCREENSHOT_AMOUNT_PER_ROW=new CatOption("rows",4),
            SCREEN_SCROLL_SPEED=new CatOption("scroll",10);
    }
    public static class Screenshot121 extends ScreenshotOptions { public final CatOption SCREENSHOT_ELEMENT_BACKGROUND_OPACITY=new CatOption("opacity",100); }
    public static class Screenshot26 extends ScreenshotOptions { public final Object SCREENSHOT_ELEMENT_BACKGROUND_COLOR=new Object(); }
    public static class CatAccess {
        final Map<CatOption,Object> values=new HashMap<>(); boolean rejectWrites;
        public Object getOrFallback(CatOption option,Object fallback){return values.getOrDefault(option,fallback);}
        public void put(CatOption option,Object value){if(!rejectWrites)values.put(option,value);}
    }
    public static class CatValues {
        final Map<CatOption,Object> values=new HashMap<>(); public Object get(CatOption o){return values.get(o);}
        public void readAndPut(JsonReader reader, CatOption o) {
            JsonElement value=JsonParser.parseReader(reader).getAsJsonObject().get("value");
            values.put(o,new com.google.gson.Gson().fromJson(value,o.type()));
        }
    }
    public static class MapOption { public boolean isOverridable(){return true;} }
    public static class Indexed extends MapOption {
        final List<Integer> values; Indexed(Integer... values){this.values=List.of(values);} public List<Integer> getValidValues(){return values;}
    }
    public static class MapOptions {
        public static final MapOption VISIBLE=new MapOption(), LINKED=new MapOption(), SERVER=new MapOption(), COMPLEX=new MapOption();
        public static final Indexed OPACITY=new Indexed(0,10,20,30,40,50,60,70,80,90,100), NONUNIFORM=new Indexed(0,1,4);
    }
    public static class MapProfile {
        Map<MapOption,Object> values=new HashMap<>(Map.of(MapOptions.VISIBLE,true,MapOptions.LINKED,true,MapOptions.SERVER,true,
            MapOptions.COMPLEX,List.of("untouched"),MapOptions.OPACITY,50,MapOptions.NONUNIFORM,1)); int callbacks;
        public Object set(MapOption option,Object value){callbacks++;return values.put(option,value);}
    }
    public static class Redirector { Set<MapOption> redirected=new HashSet<>(); public boolean shouldRedirect(MapOption o){return redirected.contains(o);} }
    public static class Synced { Map<MapOption,Object> values=new HashMap<>(); public Object getEffective(MapOption o){return values.get(o);} }
    public static class MapManager {
        MapProfile profile=new MapProfile(); Redirector redirector=new Redirector(); Synced synced=new Synced();
        public MapProfile getCurrentProfile(){return profile;} public Redirector getRedirectorManager(){return redirector;}
        public Synced getServerSynced(){return synced;} public boolean shouldIgnoreServerEnforcement(MapOption o){return false;}
        public Object getRaw(MapOption o){return profile.values.get(o);} public Object getEffective(MapOption o){return synced.values.getOrDefault(o,getRaw(o));}
    }
}
