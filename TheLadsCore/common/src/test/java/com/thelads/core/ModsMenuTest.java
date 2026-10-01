package com.thelads.core;

import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.config.*;
import com.thelads.core.config.Module;
import java.lang.reflect.Field;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ModsMenuTest {
    @TempDir Path temporary;
    static class Graphics extends LadsGraphicsTest.MockGraphics {
        final Map<String,int[]> buttons = new LinkedHashMap<>();
        @Override public void drawCenteredText(String s,int x,int y,int color,boolean shadow) {
            super.drawCenteredText(s,x,y,color,shadow); buttons.putIfAbsent(s,new int[]{x,y+3});
        }
        void render(LadsSettingsScreen menu) { buttons.clear(); drawCalls.clear(); menu.render(this,-1,-1); }
        void click(LadsSettingsScreen menu,String label) {
            var p=buttons.get(label); assertNotNull(p,"Missing button: "+label);
            assertTrue(menu.mouseClicked(p[0],p[1],0));
        }
    }
    private OwnershipFixture ownership;
    private final List<ModuleState> originalModules = new ArrayList<>();
    private record ModuleState(Module module, boolean enabled, boolean favorite, long modified, long opened) {
        ModuleState(Module module) { this(module, module.isEnabled(), module.isFavorite(), module.getLastModified(), module.getLastOpenedTime()); }
        void restore() { module.setEnabled(enabled); module.setFavorite(favorite); module.setLastModified(modified); module.setLastOpenedTime(opened); }
    }
    /** Tests supply integrations explicitly, without leaking their ownership overrides to other suites. */
    static final class OwnershipFixture implements AutoCloseable {
        private final Map<Field,Object> values = new LinkedHashMap<>();
        OwnershipFixture() throws Exception {
            for (String name : List.of("STATUS", "EXTERNAL_IDS", "EXTERNAL_MOD_IDS", "BUILT_IN", "revision")) {
                Field field = ModuleSupport.class.getDeclaredField(name); field.setAccessible(true);
                Object value = field.get(null);
                values.put(field, value instanceof Map<?,?> map ? new HashMap<>(map) : value instanceof Set<?> set ? new HashSet<>(set) : value);
            }
        }
        @Override @SuppressWarnings({"rawtypes", "unchecked"}) public void close() throws Exception {
            for (var entry : values.entrySet()) {
                Object current = entry.getKey().get(null);
                if (current instanceof Map map) { map.clear(); map.putAll((Map)entry.getValue()); }
                else if (current instanceof Set set) { set.clear(); set.addAll((Set)entry.getValue()); }
                else entry.getKey().set(null, entry.getValue());
            }
        }
    }
    @BeforeEach void isolate() throws Exception {
        ConfigManager.setTestConfigFile(temporary.resolve("config.json").toFile());
        ownership = new OwnershipFixture();
        for (Module module : ModuleManager.getInstance().getModules()) {
            originalModules.add(new ModuleState(module));
            ModuleSupport.registerUnavailable(module.getName(), "Test fixture has no integration");
        }
        ModuleSupport.registerBuiltIn("FPS", "Coordinates", "CPS", "Keystrokes", "PingHUD", "Memory", "ArmorHUD", "Speed", "Day", "Time", "XP", "Potion Effects", "Direction", "Biome", "Health", "Hunger");
    }
    @AfterEach void restore() throws Exception {
        originalModules.forEach(ModuleState::restore); ownership.close(); ConfigManager.setTestConfigFile(null);
    }

    @Test void dropdownArrowsStepBothWays() {
        var menu=new LadsSettingsScreen(); menu.openModule("Coordinates"); var g=new Graphics(); g.render(menu);
        var format=(DropdownOption)ModuleManager.getInstance().getModule("Coordinates").getOptions().stream()
            .filter(o->o.getName().equals("Format")).findFirst().orElseThrow();
        format.setIndex(1); g.render(menu);
        var r=menu.controlBounds("option:Format"); assertNotNull(r); int y=r.y()+r.height()/2;
        assertTrue(menu.mouseClicked(r.x()+3,y,0)); assertEquals(0,format.getIndex());
        assertTrue(menu.mouseClicked(r.x()+r.width()-3,y,0)); assertEquals(1,format.getIndex());
        assertTrue(menu.mouseClicked(r.x()+r.width()-3,y,1)); assertEquals(0,format.getIndex());
    }
    @Test void searchHandlesUnicodeEditingAndActuallyFiltersModules() {
        var menu=new LadsSettingsScreen(); menu.keyPressed(70,2);
        "Coordinates".codePoints().forEach(menu::charTyped);
        assertEquals(1,menu.getFilteredModules().size());
        menu.charTyped(0x1F600); menu.keyPressed(263,0); menu.keyPressed(261,0);
        assertEquals("Coordinates",menu.getSearchQuery());
        menu.keyPressed(65,2); menu.charTyped('x'); assertEquals("x",menu.getSearchQuery());
    }
    @Test void pasteIntoSearchUsesNativeClipboardOnlyOnExplicitPaste() {
        var menu=new LadsSettingsScreen();int[] reads={0};menu.setClipboardReader(()->{reads[0]++;return "Coordinates";});
        menu.keyPressed(70,2);assertEquals(0,reads[0]);menu.keyPressed(86,2);
        assertEquals("Coordinates",menu.getSearchQuery());assertEquals(1,reads[0]);assertEquals(1,menu.getFilteredModules().size());
    }
    @Test void nativeCatalogHasNoRouteToExternalOrUnavailableFeatures() {
        ModuleSupport.registerExternal("AppleSkin", "AppleSkin", "appleskin", true);
        ModuleSupport.registerUnavailable("Exordium", "Renderer not supported");
        var menu = new LadsSettingsScreen(); var g = new Graphics(); g.render(menu);
        assertTrue(menu.visibleModuleNames().contains("FPS"));
        assertFalse(menu.visibleModuleNames().contains("AppleSkin"));
        assertFalse(menu.visibleModuleNames().contains("Exordium"));
        assertFalse(g.buttons.containsKey("Engines")); assertFalse(g.buttons.containsKey("Show all"));
        assertFalse(g.buttons.containsKey("Working")); assertFalse(g.buttons.containsKey("MOD"));
        assertEquals("appleskin", ModuleSupport.getExternalId("AppleSkin"));
        assertFalse(ModuleSupport.isBuiltIn("AppleSkin"));
    }
    @Test void changingRuntimeOwnershipInvalidatesSearchAndStaleCardActions() {
        var menu = new LadsSettingsScreen(); menu.setSearchQuery("Show your current FPS");
        var g = new Graphics(); g.render(menu); assertEquals(List.of("FPS"), menu.visibleModuleNames());
        var module = ModuleManager.getInstance().getModule("FPS"); var state = new ModuleState(module);
        int[] settings = g.buttons.get("Settings");
        ModuleSupport.registerExternal("FPS", "External FPS", "externalfps", true);
        menu.mouseClicked(settings[0], settings[1], 0); g.render(menu);
        assertTrue(menu.visibleModuleNames().isEmpty()); assertFalse(g.buttons.containsKey("Settings"));
        assertEquals(state.enabled(), module.isEnabled()); assertEquals(state.opened(), module.getLastOpenedTime());
        ModuleSupport.registerBuiltIn("FPS");
        assertEquals(List.of("FPS"), menu.visibleModuleNames()); assertNull(ModuleSupport.getExternalId("FPS"));
    }
    @Test void openDetailsDismissesIfOwnershipChangesButUnrelatedRegistrationKeepsControlsWorking() {
        var menu = new LadsSettingsScreen(); menu.setSearchQuery("Show your current FPS");
        var g = new Graphics(); g.render(menu); g.click(menu,"Settings"); g.render(menu);
        ModuleSupport.registerExternal("AppleSkin", "AppleSkin", "appleskin", true);
        g.render(menu); boolean before = ModuleManager.getInstance().getModule("FPS").isEnabled();
        g.click(menu,before ? "ON" : "OFF");
        assertEquals(!before,ModuleManager.getInstance().getModule("FPS").isEnabled());
        ModuleSupport.registerUnavailable("FPS", "Native hook is no longer active"); g.render(menu);
        assertTrue(menu.visibleModuleNames().isEmpty()); assertFalse(g.buttons.containsKey("< Modules"));
    }
    @Test void tinyWindowsKeepSettingsAndOptionsUsable() {
        ModuleSupport.registerBuiltIn("FPS");
        for (int[] size:new int[][]{{320,180},{427,240},{640,360},{960,540}}) {
            var menu=new LadsSettingsScreen(); menu.setSearchQuery("Show your current FPS");
            var g=new Graphics(); g.width=size[0]; g.height=size[1]; g.render(menu);
            g.click(menu,"Settings"); g.render(menu);
            assertTrue(g.drawCalls.stream().anyMatch(s->s.contains("FPS")));
            assertTrue(g.buttons.containsKey("ON")||g.buttons.containsKey("OFF"));
            assertTrue(g.buttons.containsKey("100"),"Size slider is reachable at "+Arrays.toString(size));
            var slider=menu.controlBounds("option:Size"); int[] label=g.buttons.get("100");
            assertTrue(slider!=null&&slider.contains(label[0],label[1]),"QA finds the drawn Size slider by id at "+Arrays.toString(size));
            assertNull(menu.controlBounds("option:Missing"));
        }
    }
    @Test void favoritesEnabledCategoriesAndSearchCannotExposeExternalModsOrAlterTheirPreferences() {
        ModuleSupport.registerExternal("AppleSkin", "AppleSkin", "appleskin", true);
        ModuleSupport.registerExternal("XaeroMinimap", "Xaero Minimap", "xaerominimap", true);
        ModuleSupport.registerExternal("Lithium", "Lithium", "lithium", true);
        ModuleSupport.registerExternal("XaeroWorldmap", "Xaero World Map", "xaeroworldmap", true);
        var names = List.of("AppleSkin", "XaeroMinimap", "Lithium", "XaeroWorldmap");
        var snapshots = new HashMap<String,ModuleState>();
        for (String name : names) {
            var module = ModuleManager.getInstance().getModule(name); assertNotNull(module, name);
            module.setFavorite(true); module.setEnabled(true); snapshots.put(name,new ModuleState(module));
        }
        var menu = new LadsSettingsScreen(); var g = new Graphics(); g.render(menu);
        g.click(menu,"Favorites"); g.render(menu); g.click(menu,"Enabled");
        for (String category : List.of("All", "HUD", "Gameplay", "Performance", "Server")) {
            g.render(menu); g.click(menu,category);
            for (String name : names) {
                menu.setSearchQuery(name); g.render(menu);
                assertTrue(menu.visibleModuleNames().isEmpty(),category+" / "+name);
                assertFalse(g.buttons.containsKey("Settings"));
            }
            menu.setSearchQuery("");
            assertTrue(menu.getFilteredModules().stream().allMatch(m -> ModuleSupport.isBuiltIn(m.getName())));
        }
        menu.close();
        assertFalse(Files.exists(temporary.resolve("config.json")),"Browsing must not rewrite hidden preferences");
        for (String name : names) {
            var module = ModuleManager.getInstance().getModule(name); var before = snapshots.get(name);
            assertEquals(before.enabled(),module.isEnabled()); assertEquals(before.favorite(),module.isFavorite());
            assertEquals(before.modified(),module.getLastModified()); assertEquals(before.opened(),module.getLastOpenedTime());
        }
    }
    @Test void scrollingStaysBoundedAndCategoryChangeResetsIt() {
        var menu=new LadsSettingsScreen(); var g=new Graphics();g.height=240;g.render(menu);
        for(int i=0;i<100;i++)menu.keyPressed(267,0);
        int end=menu.getScrollOffset(); assertTrue(end>0);menu.keyPressed(267,0);assertEquals(end,menu.getScrollOffset());
        g.render(menu);g.click(menu,"HUD");assertEquals(0,menu.getScrollOffset());
        assertTrue(menu.getFilteredModules().stream().allMatch(m->m.getCategory()==com.thelads.core.config.Module.Category.HUD));
    }
    @Test void numericConfigurationCannotInjectNaNOrOutOfBounds() {
        var d=new DoubleOption("Size",1,0,2); d.set(Double.NaN); assertEquals(1,d.get());
        var value=new com.google.gson.JsonObject();value.addProperty("value",500);d.load(value);assertEquals(2,d.get());
        var s=new SliderOption("Scale",100,50,200,25);s.setValue(Double.POSITIVE_INFINITY);assertTrue(Double.isFinite(s.getValue()));
        for(var m:ModuleManager.getInstance().getModules()) for(var option:m.getOptions()) if(option instanceof SliderOption slider)
            assertTrue(slider.getMin()<=slider.getValue()&&slider.getValue()<=slider.getMax(),m.getName()+" "+option.getName());
    }
    @Test void savingUsesCompleteJsonAndLeavesNoTemporaryFiles() throws Exception {
        ConfigManager.save(); var file=temporary.resolve("config.json");
        assertTrue(com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject().has("modules"));
        ConfigManager.save(); try(var files=Files.list(temporary)){assertEquals(1,files.count());}
    }
}
