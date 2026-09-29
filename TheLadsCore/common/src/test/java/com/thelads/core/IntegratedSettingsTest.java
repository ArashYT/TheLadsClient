package com.thelads.core;

import com.thelads.core.config.*;
import com.thelads.core.client.gui.LadsSettingsScreen;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class IntegratedSettingsTest {
    public enum Mode { FAST, BALANCED, FANCY }
    public static class Engine {
        public boolean enabled = true;
        public boolean shadow = false;
        public Mode mode = Mode.BALANCED;
        public int distance = 14;
        public float opacity = .8f;
        public final boolean readOnly = true;
        public String untouched = "preserve";
    }
    @TempDir Path temp;
    private IntegratedSettings.Page page(Engine e, IntegratedSettings.Save save) throws Exception {
        return new IntegratedSettings.Builder("Test engine", save).field(e,"enabled").field(e,"shadow")
            .field(e,"mode").number(e,"distance",5,40,1).number(e,"opacity",0,1,.05).build();
    }
    @Test void openingAndRevertingNeverMutateEngineOrSave() throws Exception {
        Engine e = new Engine(); AtomicInteger saves = new AtomicInteger(); var p = page(e,saves::incrementAndGet);
        assertFalse(p.hasChanges()); ((BoolOption)p.options().get(0)).toggle(); assertTrue(p.hasChanges());
        assertTrue(e.enabled); assertEquals(0,saves.get()); p.revert(); assertFalse(p.hasChanges());
        assertEquals(0,p.apply()); assertEquals(0,saves.get());
    }
    @Test void applyUpdatesRealTypedFieldsAndWritesEngineFileOnlyOnce() throws Exception {
        Engine e = new Engine(); AtomicInteger saves = new AtomicInteger(); Path saved = temp.resolve("engine.txt");
        var p = page(e, () -> { saves.incrementAndGet(); Files.writeString(saved,e.enabled+":"+e.mode+":"+e.distance); });
        ((BoolOption)p.options().get(0)).toggle(); ((DropdownOption)p.options().get(2)).cycle();
        ((SliderOption)p.options().get(3)).setValue(100);
        assertEquals(3,p.apply()); assertFalse(e.enabled); assertEquals(Mode.FANCY,e.mode); assertEquals(40,e.distance);
        assertEquals("false:FANCY:40",Files.readString(saved)); assertEquals("preserve",e.untouched);
        assertFalse(p.hasChanges()); assertEquals(0,p.apply()); assertEquals(1,saves.get());
    }
    @Test void conflictIsDetectedBeforeAnyWrites() throws Exception {
        Engine e = new Engine(); AtomicInteger saves = new AtomicInteger(); var p = page(e,saves::incrementAndGet);
        ((BoolOption)p.options().get(0)).toggle(); ((DropdownOption)p.options().get(2)).cycle(); e.mode = Mode.FAST;
        assertThrows(IllegalStateException.class,p::apply); assertTrue(e.enabled); assertEquals(Mode.FAST,e.mode); assertEquals(0,saves.get());
    }
    @Test void unrelatedExternalChangesArePreserved() throws Exception {
        Engine e = new Engine(); var p = page(e, () -> {}); e.shadow = true;
        ((BoolOption)p.options().get(0)).toggle(); assertEquals(1,p.apply()); assertTrue(e.shadow);
    }
    @Test void failedSaveRestoresLiveValuesAndRetainsStagedChangesForRetry() throws Exception {
        Engine e = new Engine(); var p = page(e, () -> { throw new java.io.IOException("Disk full"); });
        ((BoolOption)p.options().get(0)).toggle(); ((DropdownOption)p.options().get(2)).cycle();
        assertThrows(java.io.IOException.class,p::apply); assertTrue(e.enabled); assertEquals(Mode.BALANCED,e.mode);
        assertTrue(p.hasChanges()); p.revert(); assertFalse(p.hasChanges());
    }
    @Test void failingSetterRestoresEarlierFields() throws Exception {
        Engine e = new Engine(); AtomicInteger saves = new AtomicInteger();
        var p = new IntegratedSettings.Builder("Test",saves::incrementAndGet).field(e,"enabled")
            .property("Shadow",boolean.class,()->e.shadow,v->{if ((Boolean)v) throw new IllegalStateException("No");e.shadow=(Boolean)v;}).build();
        p.options().forEach(o->((BoolOption)o).toggle()); assertThrows(IllegalStateException.class,p::apply);
        assertTrue(e.enabled); assertFalse(e.shadow); assertEquals(0,saves.get());
    }
    @Test void numericControlsPreserveFloatTypeAndRejectNonfiniteInjection() throws Exception {
        Engine e = new Engine(); var p = page(e,()->{}); var opacity=(SliderOption)p.options().get(4);
        opacity.setValue(.35); p.apply(); assertEquals(.35f,e.opacity);
        opacity.setValue(Double.NaN); p.apply(); assertTrue(Float.isFinite(e.opacity));
    }
    @Test void customValuesOutsideSupportedSliderRangeAreNotClampedOnOpen() throws Exception {
        Engine e = new Engine(); e.distance=200; var p=page(e,()->{});
        assertEquals(4,p.options().size()); assertEquals(200,e.distance); assertFalse(p.hasChanges());
    }
    @Test void readOnlyAndUnboundedFieldsCannotBecomeFakeControls() {
        Engine e=new Engine(); var b=new IntegratedSettings.Builder("Test",()->{});
        assertThrows(IllegalArgumentException.class,()->b.field(e,"readOnly"));
        assertThrows(IllegalArgumentException.class,()->b.field(e,"distance"));
        assertThrows(IllegalArgumentException.class,()->b.field(e,"untouched"));
    }
    @Test void settersThatNormalizeValuesAreReadBack() throws Exception {
        Engine e=new Engine();var p=new IntegratedSettings.Builder("Test",()->{})
            .number("Distance",int.class,()->e.distance,v->e.distance=Math.min(20,(Integer)v),5,40,1).build();
        ((SliderOption)p.options().getFirst()).setValue(30);p.apply();assertEquals(20,e.distance);
        assertEquals(20,((SliderOption)p.options().getFirst()).getIntValue());assertFalse(p.hasChanges());
    }
    @Test void engineSettingsStayUsableWithoutAnEntryInTheLadsCatalog() throws Exception {
        try (var ownership = new ModsMenuTest.OwnershipFixture()) {
            Engine engine = new Engine(); AtomicInteger saves = new AtomicInteger(); var p = page(engine,saves::incrementAndGet);
            ModuleSupport.registerExternal("AppleSkin", "AppleSkin", "appleskin", true);
            var menu = new LadsSettingsScreen(); menu.setSearchQuery("AppleSkin");
            assertTrue(menu.visibleModuleNames().isEmpty());
            ((BoolOption)p.options().getFirst()).toggle(); assertEquals(1,p.apply());
            assertFalse(engine.enabled); assertEquals(1,saves.get());
            assertEquals("appleskin",ModuleSupport.getExternalId("AppleSkin"));
        }
    }
    @Test void closingTheLadsMenuCannotApplyStagedExternalSettings() throws Exception {
        try (var ownership = new ModsMenuTest.OwnershipFixture()) {
            Engine engine = new Engine(); AtomicInteger saves = new AtomicInteger(); var p = page(engine,saves::incrementAndGet);
            ModuleSupport.registerExternal("AppleSkin", "AppleSkin", "appleskin", true);
            ((BoolOption)p.options().getFirst()).toggle();
            var menu = new LadsSettingsScreen(); menu.setSearchQuery("AppleSkin");
            AtomicInteger closed = new AtomicInteger(); menu.setOnClose(closed::incrementAndGet); menu.close();
            assertEquals(1,closed.get()); assertTrue(engine.enabled); assertEquals(0,saves.get()); assertTrue(p.hasChanges());
        }
    }
}
