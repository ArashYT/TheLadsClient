package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thelads.core.LadsVersion;
import com.thelads.core.client.WindowTitle.Menu;
import com.thelads.core.client.ladsapi.LadsApiClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WindowTitleTest {
    @AfterEach
    void resetApi() {
        LadsApiClient.reset();
        LadsApiClient.install(null, "");
    }

    @Test
    void formatsExactTitleWithDynamicClientVersionAcrossMenusAndLocalWorld() {
        String v = LadsVersion.VERSION;
        assertEquals("The Lads Client - 26.3 - " + v + " - Title Screen",
            WindowTitle.compute("26.3", false, false, null, Menu.TITLE));
        assertEquals("The Lads Client - 1.20.1 - " + v + " - Singleplayer",
            WindowTitle.compute("1.20.1", false, false, null, Menu.WORLD_SELECT));
        assertEquals("The Lads Client - 1.8.9 - " + v + " - Multiplayer",
            WindowTitle.compute("1.8.9", false, false, null, Menu.SERVER_LIST));
        assertEquals("The Lads Client - 1.21.11 - " + v + " - Menus",
            WindowTitle.compute("1.21.11", false, false, null, Menu.OTHER));
        assertEquals("The Lads Client - 26.2 - " + v + " - Menus",
            WindowTitle.compute("26.2", false, false, null, Menu.NONE));
        assertEquals("The Lads Client - 26.3 - " + v + " - Singleplayer",
            WindowTitle.compute("26.3", true, true, null, Menu.NONE));
    }

    @Test
    void menusOpenedInWorldOrOnServerKeepTheWorldOrServerActivity() {
        String v = LadsVersion.VERSION;
        assertEquals("The Lads Client - 26.3 - " + v + " - Singleplayer",
            WindowTitle.compute("26.3", true, true, null, Menu.OTHER));
        assertEquals("The Lads Client - 1.8.9 - " + v + " - Hypixel",
            WindowTitle.compute("1.8.9", true, false, "mc.hypixel.net", Menu.OTHER));
        assertEquals("The Lads Client - 1.20.1 - " + v + " - Multiplayer",
            WindowTitle.compute("1.20.1", true, false, "play.private-smp.example", Menu.SERVER_LIST));
    }

    @Test
    void namesKnownServersAndLadsNetworkWhileHidingUnknownHostsAndIps() {
        String v = LadsVersion.VERSION;
        assertEquals("The Lads Client - 1.8.9 - " + v + " - Hypixel",
            WindowTitle.compute("1.8.9", true, false, "MC.Hypixel.NET:25565", Menu.NONE));
        assertEquals("The Lads Client - 26.3 - " + v + " - The Lads Network",
            WindowTitle.compute("26.3", true, false, "mc.arashyt.ca", Menu.NONE));

        LadsApiClient.install(bytes -> {}, "26.3");
        LadsApiClient.receive("{\"t\":\"hello\",\"v\":1,\"server\":\"The Lads Network\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals("The Lads Client - 26.3 - " + v + " - The Lads Network",
            WindowTitle.compute("26.3", true, false, "lobby.arashyt.ca:25565", Menu.NONE));

        for (String unknown : new String[] {
            "play.funnyservername.com",
            "funny.minehut.gg",
            "coolsmp.aternos.me",
            "notarashyt.ca",
            "192.168.1.42:25565",
            "127.0.0.1",
            "[::1]:25565",
            "localhost",
            "",
            null
        }) {
            assertEquals("The Lads Client - 26.3 - " + v + " - Multiplayer",
                WindowTitle.compute("26.3", true, false, unknown, Menu.NONE), String.valueOf(unknown));
        }
    }

    @Test
    void updaterCallsSinkOnlyWhenStringChanges() {
        List<String> applied = new ArrayList<String>();
        WindowTitle.Updater updater = new WindowTitle.Updater("26.3", applied::add);

        assertTrue(updater.update(false, false, null, Menu.TITLE));
        assertFalse(updater.update(false, false, null, Menu.TITLE));
        assertFalse(updater.update(false, false, null, Menu.TITLE));
        assertEquals(1, applied.size());

        assertTrue(updater.update(false, false, null, Menu.SERVER_LIST));
        assertFalse(updater.update(false, false, null, Menu.SERVER_LIST));
        assertEquals(2, applied.size());

        assertTrue(updater.update(true, true, null, Menu.NONE));
        assertFalse(updater.update(true, true, null, Menu.OTHER)); // pause menu in local world keeps Singleplayer
        assertEquals(3, applied.size());
    }
}
