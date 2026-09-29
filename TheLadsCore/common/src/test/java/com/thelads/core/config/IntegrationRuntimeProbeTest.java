package com.thelads.core.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class IntegrationRuntimeProbeTest {
    @TempDir Path temp;
    public static class Engine {
        public boolean enabled = true;
        public int distance = 10;
        public Mode mode = Mode.LAST;
        public float offset = .25f;
    }
    public enum Mode { FIRST, LAST }

    private IntegratedSettings.Page page(Engine engine, IntegratedSettings.Save save) {
        try { return new IntegratedSettings.Builder("Test", save).field(engine, "enabled").build(); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }

    private Path directory(String version) throws IOException {
        Path core = Files.createDirectories(temp.resolve("TheLadsCore"));
        Files.writeString(core.resolve("settings.gradle"), "// QA repository marker");
        Path directory = Files.createDirectories(temp.resolve("artifacts/verification/" + version + "-settings"));
        Files.createDirectories(directory.resolve("config"));
        return directory.toRealPath();
    }

    @Test void onlyTheTwoExplicitSettingsDirectoriesAreAccepted() throws Exception {
        for (String version : new String[] {"1.21.11", "26.2"}) {
            Path directory = directory(version);
            assertEquals(directory, IntegrationRuntimeProbe.validateDirectory(directory.toString(), directory, directory.resolve("config")));
        }
    }

    @Test void missingRelativeAndUserProfileDirectoriesAreRejected() throws Exception {
        Path qa = directory("1.21.11");
        assertThrows(IOException.class, () -> IntegrationRuntimeProbe.validateDirectory(null, qa, qa.resolve("config")));
        assertThrows(IOException.class, () -> IntegrationRuntimeProbe.validateDirectory("artifacts/verification/1.21.11-settings", qa, qa.resolve("config")));
        Path profile = Files.createDirectories(temp.resolve("profiles/1.21.11"));
        assertThrows(IOException.class, () -> IntegrationRuntimeProbe.validateDirectory(profile.toString(), profile, qa.resolve("config")));
        Path title = Files.createDirectories(temp.resolve("artifacts/verification/1.21.11-title"));
        assertThrows(IOException.class, () -> IntegrationRuntimeProbe.validateDirectory(title.toString(), title, qa.resolve("config")));
    }

    @Test void actualGameAndConfigMustBothBeInsideTheRequestedQaDirectory() throws Exception {
        Path qa = directory("1.21.11");
        Path user = Files.createDirectories(temp.resolve("profiles/user/config"));
        assertThrows(IOException.class, () -> IntegrationRuntimeProbe.validateDirectory(qa.toString(), user.getParent(), qa.resolve("config")));
        assertThrows(IOException.class, () -> IntegrationRuntimeProbe.validateDirectory(qa.toString(), qa, user));
    }

    @Test void repositoryMarkerIsRequired() throws Exception {
        Path qa = directory("1.21.11");
        Files.delete(temp.resolve("TheLadsCore/settings.gradle"));
        assertThrows(IOException.class, () -> IntegrationRuntimeProbe.validateDirectory(qa.toString(), qa, qa.resolve("config")));
    }

    @Test void toggleAndRestoreBothWriteAndReadBackTheSameEngine() throws Exception {
        Engine engine = new Engine(); AtomicInteger saves = new AtomicInteger();
        Path file = temp.resolve("engine.txt");
        Function<String, IntegratedSettings.Page> open = id -> page(engine, () -> {
            saves.incrementAndGet(); Files.writeString(file, Boolean.toString(engine.enabled));
        });
        assertEquals("Enabled", IntegrationRuntimeProbe.roundTrip("test", open));
        assertTrue(engine.enabled); assertEquals(2, saves.get());
        assertEquals("true", Files.readString(file));
    }

    @Test void unavailableOrImmutablePagesSkipWithoutWriting() throws Exception {
        assertNull(IntegrationRuntimeProbe.roundTrip("absent", ignored -> null));
        Engine engine = new Engine(); AtomicInteger saves = new AtomicInteger();
        var page = new IntegratedSettings.Builder("Fixed value", saves::incrementAndGet).number(engine, "distance", 10, 10, 1).build();
        assertNull(IntegrationRuntimeProbe.roundTrip("fixed", ignored -> page));
        assertEquals(0, saves.get()); assertEquals(10, engine.distance);
    }

    @Test void badToggleReadBackStillRestoresOriginalValue() {
        Engine engine = new Engine(); AtomicInteger opens = new AtomicInteger(), saves = new AtomicInteger();
        Function<String, IntegratedSettings.Page> open = id -> opens.incrementAndGet() == 2
            ? page(new Engine(), () -> fail("Read-back must not save"))
            : page(engine, saves::incrementAndGet);
        assertThrows(IllegalStateException.class, () -> IntegrationRuntimeProbe.roundTrip("test", open));
        assertTrue(engine.enabled); assertEquals(2, saves.get());
    }

    @Test void failedToggleIsNotReportedAsSuccessAndOriginalStateIsChecked() {
        Engine engine = new Engine(); AtomicInteger saves = new AtomicInteger();
        Function<String, IntegratedSettings.Page> open = id -> page(engine, () -> {
            saves.incrementAndGet(); throw new IOException("Toggle save failed");
        });
        assertThrows(IOException.class, () -> IntegrationRuntimeProbe.roundTrip("test", open));
        assertTrue(engine.enabled); assertEquals(1, saves.get());
    }

    @Test void failedRestorationIsNotReportedAsSuccess() {
        Engine engine = new Engine(); AtomicInteger saves = new AtomicInteger();
        Function<String, IntegratedSettings.Page> open = id -> page(engine, () -> {
            if (saves.incrementAndGet() == 2) throw new IOException("Restore save failed");
        });
        var failure = assertThrows(IOException.class, () -> IntegrationRuntimeProbe.roundTrip("test", open));
        assertEquals("Restore save failed", failure.getMessage());
        assertFalse(engine.enabled); // Shared apply rolls back the failed restoration; the probe must report ERROR.
    }

    @Test void enumFallbackCyclesAndRestoresBySerializedIndex() throws Exception {
        Engine engine = new Engine(); AtomicInteger saves = new AtomicInteger();
        Function<String, IntegratedSettings.Page> open = id -> {
            try { return new IntegratedSettings.Builder("Enum", saves::incrementAndGet).field(engine, "mode").build(); }
            catch (Exception error) { throw new IllegalStateException(error); }
        };
        assertEquals("Mode", IntegrationRuntimeProbe.roundTrip("enum", open));
        assertEquals(Mode.LAST, engine.mode); assertEquals(2, saves.get());
    }

    @Test void numericFallbackStepsDownAtMaximumAndRestoresSerializedFloat() throws Exception {
        Engine engine = new Engine(); AtomicInteger saves = new AtomicInteger();
        Function<String, IntegratedSettings.Page> open = id -> {
            try { return new IntegratedSettings.Builder("Numeric", () -> {
                if (saves.incrementAndGet() == 1) assertEquals(.125f, engine.offset);
            }).number(engine, "offset", 0, .25, .125).build(); }
            catch (Exception error) { throw new IllegalStateException(error); }
        };
        assertEquals("Offset", IntegrationRuntimeProbe.roundTrip("numeric", open));
        assertEquals(.25f, engine.offset); assertEquals(2, saves.get());
    }

    @Test void unrelatedChangesStagedOnOpenAreNeverWrittenByTheProbe() throws Exception {
        Engine engine = new Engine(); AtomicInteger saves = new AtomicInteger();
        var page = page(engine, saves::incrementAndGet);
        ((BoolOption) page.options().getFirst()).toggle();
        assertThrows(IllegalStateException.class, () -> IntegrationRuntimeProbe.roundTrip("pending", ignored -> page));
        assertTrue(engine.enabled); assertEquals(0, saves.get());
    }

    @Test void fullProbeChecksEveryMutableControlAndRestoresBeforeTheNext() throws Exception {
        Engine engine = new Engine(); AtomicInteger saves = new AtomicInteger();
        Function<String, IntegratedSettings.Page> open = id -> {
            try { return new IntegratedSettings.Builder("All controls", saves::incrementAndGet)
                .field(engine, "enabled").field(engine, "mode")
                .number(engine, "distance", 0, 10, 1).number(engine, "offset", .25, .25, .125).build(); }
            catch (Exception error) { throw new IllegalStateException(error); }
        };
        var controls = IntegrationRuntimeProbe.mutableOptions(open.apply("test"));
        assertEquals(3, controls.size());
        for (Option control : controls) {
            assertEquals(control.getName(), IntegrationRuntimeProbe.roundTrip("test", open, control));
            assertTrue(engine.enabled); assertEquals(Mode.LAST, engine.mode);
            assertEquals(10, engine.distance); assertEquals(.25f, engine.offset);
        }
        assertEquals(6, saves.get());
    }
}
