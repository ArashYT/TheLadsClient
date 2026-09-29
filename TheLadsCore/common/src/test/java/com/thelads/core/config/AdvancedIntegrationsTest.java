package com.thelads.core.config;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Tests the adapter's actual save wiring; public API presence in upstream jars is checked separately. */
public class AdvancedIntegrationsTest {
    @TempDir Path temp;

    public static final class Performance {
        public boolean useEntityCulling = true;
        public boolean unrelated = true;
    }

    public static final class Options {
        public final Performance performance = new Performance();
        Path file;
        boolean readOnly, failSave;
        int saves;
        public boolean isReadOnly() { return readOnly; }
        public static void writeToDisk(Options options) throws IOException {
            options.saves++;
            if (options.failSave) throw new IOException("Cannot save");
            Files.writeString(options.file, options.performance.useEntityCulling + ":" + options.performance.unrelated);
        }
    }

    public static final class Model {
        boolean pending;
        int resets;
        public boolean anyOptionChanged() { return pending; }
        public void resetAllOptionsFromBindings() {
            resets++;
            throw new IllegalStateException("Another mod's getter failed while resetting the model");
        }
    }

    private Options options() throws IOException {
        Options options = new Options();
        options.file = temp.resolve("sodium-options.json");
        Files.writeString(options.file, "true:true");
        return options;
    }

    @Test void successfulSaveDoesNotRunFallibleGlobalResetAfterPersisting() throws Exception {
        Options options = options(); Model model = new Model();
        var page = AdvancedIntegrations.sodiumPage(options, model);
        ((BoolOption) page.options().getFirst()).toggle();
        assertEquals(1, page.apply());
        assertFalse(options.performance.useEntityCulling);
        assertEquals("false:true", Files.readString(options.file));
        assertEquals(1, options.saves);
        assertEquals(0, model.resets);
        assertFalse(page.hasChanges());
    }

    @Test void saveFailureRollsBackLiveFieldAndKeepsChangeForRetry() throws Exception {
        Options options = options(); Model model = new Model(); options.failSave = true;
        var page = AdvancedIntegrations.sodiumPage(options, model);
        ((BoolOption) page.options().getFirst()).toggle();
        var failure = assertThrows(InvocationTargetException.class, page::apply);
        assertInstanceOf(IOException.class, failure.getCause());
        assertTrue(options.performance.useEntityCulling);
        assertEquals("true:true", Files.readString(options.file));
        assertTrue(page.hasChanges()); assertEquals(0, model.resets);
        options.failSave = false;
        assertEquals(1, page.apply());
        assertEquals("false:true", Files.readString(options.file));
    }

    @Test void pendingUpstreamChangesBlockSaveWithoutResettingThem() throws Exception {
        Options options = options(); Model model = new Model();
        var page = AdvancedIntegrations.sodiumPage(options, model);
        ((BoolOption) page.options().getFirst()).toggle(); model.pending = true;
        assertThrows(IllegalStateException.class, page::apply);
        assertTrue(options.performance.useEntityCulling); assertTrue(model.pending);
        assertEquals(0, options.saves); assertEquals(0, model.resets);
        assertEquals("true:true", Files.readString(options.file));
    }

    @Test void unavailableOrReadOnlyModelsRetainTheUpstreamEditor() throws Exception {
        Options options = options(); Model model = new Model();
        assertNull(AdvancedIntegrations.sodiumPage(options, null));
        options.readOnly = true; assertNull(AdvancedIntegrations.sodiumPage(options, model));
        options.readOnly = false; model.pending = true;
        assertNull(AdvancedIntegrations.sodiumPage(options, model));
        assertEquals(0, options.saves); assertEquals(0, model.resets);
    }

    @Test void openingRevertingAndApplyingNoChangesNeverSave() throws Exception {
        Options options = options(); Model model = new Model();
        var page = AdvancedIntegrations.sodiumPage(options, model);
        ((BoolOption) page.options().getFirst()).toggle(); page.revert();
        assertEquals(0, page.apply()); assertEquals(0, options.saves);
        assertEquals("true:true", Files.readString(options.file));
    }

    @Test void independentLiveChangesAreSavedWithoutBeingOverwritten() throws Exception {
        Options options = options(); Model model = new Model();
        var page = AdvancedIntegrations.sodiumPage(options, model);
        options.performance.unrelated = false;
        ((BoolOption) page.options().getFirst()).toggle();
        page.apply(); assertEquals("false:false", Files.readString(options.file));
    }

    public interface PublicValue { Object getValue(); }
    private static final class HiddenValue implements PublicValue {
        @Override public Object getValue() { return Boolean.TRUE; }
    }

    @Test void publicInterfaceInvocationWorksWithHiddenImplementations() throws Exception {
        var method = AdvancedIntegrations.publicMethod(PublicValue.class, "getValue");
        assertEquals(Boolean.TRUE, method.invoke(new HiddenValue()));
    }

    @Test void inaccessibleDeclaringClassesAreRejectedDuringPageConstruction() {
        assertThrows(IllegalAccessException.class,
            () -> AdvancedIntegrations.publicMethod(HiddenValue.class, "getValue"));
    }
}
