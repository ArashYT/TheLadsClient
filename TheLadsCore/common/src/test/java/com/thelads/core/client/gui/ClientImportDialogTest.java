package com.thelads.core.client.gui;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.client.bridge.LadsGraphics;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The dialog against a fake launcher command: what it asks, what it does with the answers. */
class ClientImportDialogTest {
    private static final String SCAN = """
        {"clients":[{"id":"lunar","name":"Lunar Client","basic":false,"note":"n","categories":[
          {"id":"Mods","changes":3,"skipped":1,"changeLines":["FPS: on"],"skippedLines":["X"]},
          {"id":"Servers","changes":0,"skipped":0,"changeLines":[],"skippedLines":[]},
          {"id":"Options","changes":2,"skipped":0,"problem":"damaged","changeLines":[],"skippedLines":[]}]}],"canUndo":false}""";

    private final List<String> calls = new ArrayList<>();
    private int reloads;

    private ClientImportDialog dialog(String apply) {
        return new ClientImportDialog(args -> {
            calls.add(String.join(" ", args));
            return JsonParser.parseString(args[0].equals("scan") ? SCAN : apply).getAsJsonObject();
        }, () -> reloads++);
    }

    private static void await(ClientImportDialog dialog, String settledFrom) throws Exception {
        for (int i = 0; i < 500 && dialog.status().equals(settledFrom); i++) {
            Thread.sleep(5);
            dialog.pump();
        }
    }

    @Test
    void importsOnlyTheUsableCategoriesAndReloadsTheConfig() throws Exception {
        ClientImportDialog dialog = dialog("""
            {"applied":{"Mods":3},"errors":[],"configChanged":true,"restartNeeded":true,"launcherPending":false,"backup":"b"}""");
        dialog.open();
        await(dialog, "Looking for other clients...");
        assertEquals("Pick the client to import from.", dialog.status());

        JsonObject lunar = JsonParser.parseString(SCAN).getAsJsonObject().getAsJsonArray("clients").get(0).getAsJsonObject();
        dialog.select(lunar);
        assertEquals(List.of("Mods"), List.copyOf(dialog.chosen()), "no changes and a problem are not choosable");

        dialog.apply();
        await(dialog, "Importing...");
        assertEquals("apply lunar Mods --in-game", calls.get(1));
        assertEquals(1, reloads, "the live modules take the new config");
        assertTrue(dialog.status().startsWith("Imported 3 mods."), dialog.status());
        assertTrue(dialog.status().contains("restart"), dialog.status());
        assertTrue(dialog.canUndo());

        dialog.undo(); // the warning
        dialog.undo();
        await(dialog, "Undoing the last import...");
        assertEquals("undo --in-game", calls.get(2));
        assertEquals(2, reloads);
    }

    /** LadsGraphics answering sizes and nothing else: enough to lay the dialog out and click it for real. */
    private static LadsGraphics graphics() {
        return (LadsGraphics) java.lang.reflect.Proxy.newProxyInstance(LadsGraphics.class.getClassLoader(), new Class<?>[] {LadsGraphics.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getScaledWidth" -> 800;
            case "getScaledHeight" -> 480;
            case "textWidth" -> ((String) args[0]).length() * 6;
            case "hashCode" -> 1;
            default -> {
                Class<?> type = method.getReturnType();
                yield type == int.class ? (Object) 0 : type == boolean.class ? (Object) false : type == float.class ? (Object) 0f : type == double.class ? (Object) 0d : null;
            }
        });
    }

    @Test
    void rendersAndClicksThroughEveryStep() throws Exception {
        ClientImportDialog dialog = dialog("""
            {"applied":{"Mods":3},"errors":[],"configChanged":true,"restartNeeded":false,"launcherPending":false,"backup":"b"}""");
        LadsGraphics g = graphics();
        dialog.open();
        dialog.render(g, 0, 0);
        await(dialog, "Looking for other clients...");
        dialog.render(g, 0, 0);

        // The dialog is 520 wide and centred; the first client row starts under the status line.
        assertTrue(dialog.click(400, 30 + 46 + 5, 0));
        assertNotNull(dialog.selected(), "the row was clicked");
        dialog.render(g, 0, 0);

        // "Import" sits at the dialog's bottom right.
        assertTrue(dialog.click(400 + 260 - 40, 240 + 210 - 12, 0));
        await(dialog, "Importing...");
        assertEquals("apply lunar Mods --in-game", calls.get(1));
        dialog.render(g, 0, 0);
        assertNull(dialog.selected());
        assertTrue(dialog.key(256));
        assertFalse(dialog.isOpen());
        dialog.render(g, 0, 0); // closed: draws nothing, does not throw
    }

    @Test
    void withoutTheLauncherTheCommandSaysWhereToImport() {
        IOException missing = assertThrows(IOException.class, () -> ClientImportDialog.launcherCommand(null).run("scan"));
        assertTrue(missing.getMessage().contains("The Lads Launcher"), missing.getMessage());
    }

    @Test
    void aFailingLauncherOnlyShowsItsMessage() throws Exception {
        ClientImportDialog dialog = new ClientImportDialog(args -> { throw new IOException("no launcher"); }, () -> reloads++);
        dialog.open();
        await(dialog, "Looking for other clients...");
        assertEquals("no launcher", dialog.status());
        assertEquals(0, reloads);
        assertTrue(dialog.isOpen());
    }

    @Test
    void resolveLauncherExeChecksPropertyThenEnvThenLocalAppData(@TempDir Path temp) throws Exception {
        Path propExe = Files.writeString(temp.resolve("prop-launcher.exe"), "mz");
        Path envExe = Files.writeString(temp.resolve("env-launcher.exe"), "mz");
        Path localAppData = temp.resolve("LocalAppData");
        Path installedDir = Files.createDirectories(localAppData.resolve("TheLadsClient").resolve("current"));
        Path installedExe = Files.writeString(installedDir.resolve("TheLadsLauncher.exe"), "mz");

        assertEquals(propExe.toString(), ClientImportDialog.resolveLauncherExe(propExe.toString(), envExe.toString(), localAppData.toString()));
        assertEquals(envExe.toString(), ClientImportDialog.resolveLauncherExe(temp.resolve("missing.exe").toString(), envExe.toString(), localAppData.toString()));
        assertEquals(installedExe.toAbsolutePath().toString(), ClientImportDialog.resolveLauncherExe(null, "", localAppData.toString()));
        assertNull(ClientImportDialog.resolveLauncherExe(null, null, temp.resolve("EmptyLocalAppData").toString()));
    }

    @Test
    void customFolderPickerScansAndAppliesWithCustomDir(@TempDir Path temp) throws Exception {
        Path customFolder = Files.createDirectories(temp.resolve("MyCustomMc"));
        String customDir = customFolder.toAbsolutePath().toString();
        String customScan = """
            {"clients":[{"id":"custom","name":"MyCustomMc","basic":true,"note":"Basic import","categories":[
              {"id":"Options","changes":4,"skipped":0,"changeLines":["fov"],"skippedLines":[]},
              {"id":"Servers","changes":1,"skipped":0,"changeLines":["mc.example.net"],"skippedLines":[]}]}],"canUndo":false}""";
        String customApply = """
            {"applied":{"Options":4,"Servers":1},"errors":[],"configChanged":false,"restartNeeded":true,"launcherPending":false,"backup":"b"}""";

        ClientImportDialog dialog = new ClientImportDialog(args -> {
            calls.add(String.join(" ", args));
            if (args[0].equals("scan") && args.length >= 3 && args[1].equals("--custom-dir")) {
                return JsonParser.parseString(customScan).getAsJsonObject();
            }
            return JsonParser.parseString(args[0].equals("scan") ? SCAN : customApply).getAsJsonObject();
        }, () -> reloads++, () -> CompletableFuture.completedFuture(customFolder));

        LadsGraphics g = graphics();
        dialog.open();
        await(dialog, "Looking for other clients...");
        dialog.render(g, 0, 0);

        // Click "Choose folder..." at bottom right while on the client list.
        assertTrue(dialog.click(400 + 260 - 60, 240 + 210 - 12, 0));
        dialog.pump();
        await(dialog, "Reading MyCustomMc...");
        assertEquals("scan --custom-dir " + customDir, calls.get(1));
        assertNotNull(dialog.selected());
        assertEquals("custom", dialog.selected().get("id").getAsString());
        assertEquals(List.of("Options", "Servers"), List.copyOf(dialog.chosen()));

        dialog.render(g, 0, 0);
        assertTrue(dialog.click(400 + 260 - 40, 240 + 210 - 12, 0));
        await(dialog, "Importing...");
        assertEquals("apply custom Options,Servers --custom-dir " + customDir + " --in-game", calls.get(2));
        assertNull(dialog.selected());
    }

    @Test
    void profilesDialogImportClientButtonOpensAndDelegatesToClientImportDialog(@TempDir Path temp) throws Exception {
        com.thelads.core.config.ConfigManager.setTestConfigFile(new File(temp.toFile(), "thelads_config.json"));
        com.thelads.core.config.ProfileManager.get().setTestFile(new File(temp.toFile(), "thelads_profiles.json"));
        try {
            ClientImportDialog importDialog = dialog("""
                {"applied":{"Mods":3},"errors":[],"configChanged":true,"restartNeeded":false,"launcherPending":false,"backup":"b"}""");
            ProfilesDialog profiles = new ProfilesDialog(importDialog);
            LadsGraphics g = graphics();

            profiles.open();
            profiles.render(g, 0, 0);
            var btn = profiles.importClientBounds();
            assertTrue(btn.width() > 0 && btn.height() > 0, "Import Client... button rendered");
            assertFalse(profiles.clientImportDialog().isOpen());

            assertTrue(profiles.click(btn.x() + btn.width() / 2.0, btn.y() + btn.height() / 2.0, 0));
            assertTrue(profiles.clientImportDialog().isOpen());
            for (int i = 0; i < 500 && importDialog.status().equals("Looking for other clients..."); i++) {
                Thread.sleep(5);
                profiles.tick();
            }
            assertEquals("Pick the client to import from.", importDialog.status());
            profiles.render(g, 0, 0);

            // Esc closes the nested ClientImportDialog while keeping ProfilesDialog open in LIST mode.
            assertTrue(profiles.key(256, 0));
            assertFalse(profiles.clientImportDialog().isOpen());
            assertTrue(profiles.isOpen());
            assertEquals(ProfilesDialog.Mode.LIST, profiles.mode());

            // Re-open nested dialog and verify ProfilesDialog.close() closes both.
            assertTrue(profiles.click(btn.x() + btn.width() / 2.0, btn.y() + btn.height() / 2.0, 0));
            for (int i = 0; i < 500 && importDialog.status().equals("Looking for other clients..."); i++) {
                Thread.sleep(5);
                profiles.tick();
            }
            profiles.close();
            assertFalse(profiles.isOpen());
            assertFalse(profiles.clientImportDialog().isOpen());
        } finally {
            com.thelads.core.config.ProfileManager.get().setTestFile(null);
        }
    }
}
