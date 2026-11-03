package com.thelads.core.client.gui;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.client.bridge.LadsGraphics;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

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
}
