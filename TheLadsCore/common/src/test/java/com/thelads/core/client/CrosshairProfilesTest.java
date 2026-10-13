package com.thelads.core.client;

import com.google.gson.JsonObject;
import com.thelads.core.client.util.ClientPaths;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.DoubleOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import com.thelads.core.modules.CrosshairModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class CrosshairProfilesTest {

    @TempDir
    Path tempDir;

    private CrosshairModule module;

    @BeforeEach
    void setUp() {
        ClientPaths.setBaseDir(tempDir);
        module = new CrosshairModule();
        CrosshairProfiles.setModuleForTest(module);
        CrosshairProfiles.reload();
    }

    @AfterEach
    void tearDown() {
        CrosshairProfiles.setModuleForTest(null);
        CrosshairDrawingStore.setDrawingChangedListener(null);
    }

    @Test
    void saveModifyApplyRoundTripRestoresEveryOptionAndDrawing() throws IOException {
        // 1. Set specific options on CrosshairModule
        module.color.setColor(0xFF112233);
        module.scale.set(2.5);
        module.thickness.set(3.0);
        module.background.setIndex(1); // SOLID
        module.solidColor.setColor(0xFF445566);

        ((DropdownOption) module.getOption("Shape")).setIndex(1); // Dot
        ((SliderOption) module.getOption("Gap")).setValue(7);
        ((BoolOption) module.getOption("Outline")).set(false);
        ((BoolOption) module.getOption("Center Dot")).set(true);
        ((SliderOption) module.getOption("Width")).setValue(15);
        ((SliderOption) module.getOption("Height")).setValue(12);
        ((SliderOption) module.getOption("Rotation")).setValue(45);
        ((ColorOption) module.getOption("Outline Color")).setColor(0xFF8899AA);
        ((ColorOption) module.getOption("Dot Color")).setColor(0xFFBBCCDD);
        ((BoolOption) module.getOption("Adaptive Color")).set(true);
        ((BoolOption) module.getOption("Rainbow")).set(true);
        ((SliderOption) module.getOption("Rainbow Speed")).setValue(4.5);
        ((SliderOption) module.getOption("Rainbow Phase")).setValue(180);

        // 2. Set specific custom drawing
        CrosshairDrawing originalDrawing = new CrosshairDrawing(15, 15);
        originalDrawing.set(2, 2, true);
        originalDrawing.set(7, 7, true);
        originalDrawing.set(10, 5, true);
        CrosshairDrawingStore.save(originalDrawing);

        // 3. Save profile
        assertTrue(CrosshairProfiles.save("MyProfile"));
        assertTrue(CrosshairProfiles.has("myprofile"));
        assertEquals(List.of("MyProfile"), CrosshairProfiles.list());

        // 4. Modify options and drawing to completely different values
        module.color.setColor(0xFFAAAAAA);
        module.scale.set(1.0);
        module.thickness.set(1.0);
        module.background.setIndex(0);
        ((DropdownOption) module.getOption("Shape")).setIndex(0);
        ((SliderOption) module.getOption("Gap")).setValue(2);
        ((BoolOption) module.getOption("Outline")).set(true);
        ((BoolOption) module.getOption("Center Dot")).set(false);
        ((SliderOption) module.getOption("Width")).setValue(5);
        ((SliderOption) module.getOption("Height")).setValue(5);
        ((SliderOption) module.getOption("Rotation")).setValue(0);
        ((BoolOption) module.getOption("Adaptive Color")).set(false);
        ((BoolOption) module.getOption("Rainbow")).set(false);

        CrosshairDrawing modifiedDrawing = new CrosshairDrawing(9, 9);
        CrosshairDrawingStore.save(modifiedDrawing);

        // Verify that modifications took effect
        assertNotEquals(0xFF112233, module.color.getColor());
        assertNotEquals(2.5, module.scale.get());
        assertNotEquals(originalDrawing.rows(), CrosshairDrawingStore.load().rows());

        // Track that listener was notified
        boolean[] refreshed = {false};
        CrosshairDrawingStore.setDrawingChangedListener(() -> refreshed[0] = true);

        // 5. Apply the saved profile
        assertTrue(CrosshairProfiles.apply("myprofile"));
        assertTrue(refreshed[0]);

        // 6. Assert options are completely restored
        assertEquals(0xFF112233, module.color.getColor());
        assertEquals(2.5, module.scale.get(), 0.001);
        assertEquals(3.0, module.thickness.get(), 0.001);
        assertEquals(1, module.background.getIndex());
        assertEquals(0xFF445566, module.solidColor.getColor());

        assertEquals(1, ((DropdownOption) module.getOption("Shape")).getIndex());
        assertEquals(7.0, ((SliderOption) module.getOption("Gap")).getValue(), 0.001);
        assertFalse(((BoolOption) module.getOption("Outline")).get());
        assertTrue(((BoolOption) module.getOption("Center Dot")).get());
        assertEquals(15.0, ((SliderOption) module.getOption("Width")).getValue(), 0.001);
        assertEquals(12.0, ((SliderOption) module.getOption("Height")).getValue(), 0.001);
        assertEquals(45.0, ((SliderOption) module.getOption("Rotation")).getValue(), 0.001);
        assertEquals(0xFF8899AA, ((ColorOption) module.getOption("Outline Color")).getColor());
        assertEquals(0xFFBBCCDD, ((ColorOption) module.getOption("Dot Color")).getColor());
        assertTrue(((BoolOption) module.getOption("Adaptive Color")).get());
        assertTrue(((BoolOption) module.getOption("Rainbow")).get());
        assertEquals(4.5, ((SliderOption) module.getOption("Rainbow Speed")).getValue(), 0.001);
        assertEquals(180.0, ((SliderOption) module.getOption("Rainbow Phase")).getValue(), 0.001);

        // 7. Assert drawing is completely restored
        CrosshairDrawing restoredDrawing = CrosshairDrawingStore.load();
        assertEquals(15, restoredDrawing.width());
        assertEquals(15, restoredDrawing.height());
        assertTrue(restoredDrawing.get(2, 2));
        assertTrue(restoredDrawing.get(7, 7));
        assertTrue(restoredDrawing.get(10, 5));
        assertFalse(restoredDrawing.get(0, 0));
        assertEquals(originalDrawing.rows(), restoredDrawing.rows());
    }

    @Test
    void deleteAndRenameProfiles() {
        assertTrue(CrosshairProfiles.save("Profile Alpha"));
        assertTrue(CrosshairProfiles.save("Profile Beta"));
        assertEquals(List.of("Profile Alpha", "Profile Beta"), CrosshairProfiles.list());

        // Rename Alpha -> Renamed Alpha
        assertTrue(CrosshairProfiles.rename("profile alpha", "Renamed Alpha"));
        assertFalse(CrosshairProfiles.has("Profile Alpha"));
        assertTrue(CrosshairProfiles.has("renamed alpha"));
        assertEquals(List.of("Renamed Alpha", "Profile Beta"), CrosshairProfiles.list());

        // Rename to an already taken name (case-insensitively) -> fails
        assertFalse(CrosshairProfiles.rename("Renamed Alpha", "profile beta"));

        // Delete Beta
        assertTrue(CrosshairProfiles.delete("profile beta"));
        assertFalse(CrosshairProfiles.has("Profile Beta"));
        assertEquals(List.of("Renamed Alpha"), CrosshairProfiles.list());

        // Delete non-existent
        assertFalse(CrosshairProfiles.delete("NonExistent"));
    }

    @Test
    void badJsonFileReturnsEmptyListWithoutException() throws IOException {
        Path file = ClientPaths.getCrosshairProfilesPath();
        Files.createDirectories(file.getParent());

        // Write corrupt JSON
        Files.writeString(file, "{ this is not valid json : [");
        CrosshairProfiles.reload();

        List<String> list = CrosshairProfiles.list();
        assertNotNull(list);
        assertTrue(list.isEmpty());

        // Write array JSON instead of object
        Files.writeString(file, "[\"one\", \"two\"]");
        CrosshairProfiles.reload();

        list = CrosshairProfiles.list();
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    void nameValidationAndCaseInsensitiveUniqueness() {
        assertFalse(CrosshairProfiles.save(null));
        assertFalse(CrosshairProfiles.save(""));
        assertFalse(CrosshairProfiles.save("   "));
        assertFalse(CrosshairProfiles.save("a".repeat(33)));

        assertTrue(CrosshairProfiles.save("a".repeat(32)));
        assertTrue(CrosshairProfiles.has("a".repeat(32)));

        // Trimming
        assertTrue(CrosshairProfiles.save("  Trimmed  "));
        assertTrue(CrosshairProfiles.has("Trimmed"));
        assertTrue(CrosshairProfiles.list().contains("Trimmed"));

        // Case-insensitive update
        assertTrue(CrosshairProfiles.save("unique"));
        assertTrue(CrosshairProfiles.save("UNIQUE"));
        assertEquals(3, CrosshairProfiles.list().size()); // 32-char, Trimmed, UNIQUE
    }

    @Test
    void corruptOrUnknownOptionKeysIgnoredWithoutCrash() throws IOException {
        assertTrue(CrosshairProfiles.save("TestCorrupt"));

        Path file = ClientPaths.getCrosshairProfilesPath();
        String json = Files.readString(file);
        // Inject unknown key and corrupt type key into options
        String modified = json.replace("\"options\": {", "\"options\": {\"UnknownKey\": 999, \"Color\": \"not an int\", ");
        Files.writeString(file, modified);

        CrosshairProfiles.reload();
        assertTrue(CrosshairProfiles.has("TestCorrupt"));

        // Applying corrupt options must not throw an exception
        assertDoesNotThrow(() -> CrosshairProfiles.apply("TestCorrupt"));
    }

    @Test
    void crosshairDrawingStoreLoadSaveFallback() throws IOException {
        CrosshairDrawing def = CrosshairDrawingStore.defaultDrawing();
        assertNotNull(def);
        assertEquals(17, def.width());
        assertEquals(17, def.height());

        // Non-existent file loads default
        CrosshairDrawing loaded = CrosshairDrawingStore.load();
        assertEquals(def.rows(), loaded.rows());

        // Save custom drawing
        CrosshairDrawing custom = new CrosshairDrawing(5, 5);
        custom.set(1, 1, true);
        CrosshairDrawingStore.save(custom);

        CrosshairDrawing read = CrosshairDrawingStore.load();
        assertEquals(5, read.width());
        assertEquals(5, read.height());
        assertTrue(read.get(1, 1));
        assertFalse(read.get(0, 0));
    }

    @Test
    void saveASaveBApplyAThenCycleToB() throws IOException {
        module.scale.set(1.5);
        assertTrue(CrosshairProfiles.save("A"));

        module.scale.set(3.0);
        assertTrue(CrosshairProfiles.save("B"));

        // Verify ProfilesOption is excluded from the saved json options
        Path file = ClientPaths.getCrosshairProfilesPath();
        String json = Files.readString(file);
        assertFalse(json.contains("\"Profiles\":"), "ProfilesOption should not be included in options snapshot");

        // Apply A
        assertTrue(CrosshairProfiles.apply("A"));
        assertEquals("A", module.profiles.getSelected());
        assertEquals(1.5, module.scale.get());

        // Cycle from current profile
        List<String> list = CrosshairProfiles.list();
        String current = module.profiles.getSelected();
        if ((current.isEmpty() || !CrosshairProfiles.has(current)) && !list.isEmpty()) {
            current = list.get(0);
        }
        int idx = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).equalsIgnoreCase(current)) {
                idx = i;
                break;
            }
        }
        int nextIdx = (idx + 1) % list.size();
        String nextName = list.get(nextIdx);
        assertEquals("B", nextName);

        // Apply B
        assertTrue(CrosshairProfiles.apply(nextName));
        assertEquals("B", module.profiles.getSelected());
        assertEquals(3.0, module.scale.get());

        // Cycle back to A
        int cycleBackIdx = (nextIdx + 1) % list.size();
        String backToA = list.get(cycleBackIdx);
        assertEquals("A", backToA);
        assertTrue(CrosshairProfiles.apply(backToA));
        assertEquals("A", module.profiles.getSelected());
        assertEquals(1.5, module.scale.get());
    }
}
