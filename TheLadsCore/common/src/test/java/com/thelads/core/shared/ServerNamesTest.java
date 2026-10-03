package com.thelads.core.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The launcher's ServerNameResolverTests use the same cases. */
class ServerNamesTest {
    @Test
    void knownServersMatchEveryAddressOfTheirDomain() {
        for (String address : new String[] {"mc.hypixel.net", "hypixel.net", "play.hypixel.net", "stuck.hypixel.net", "MC.Hypixel.NET:25565",
            "  mc.hypixel.net.  "}) assertEquals("Hypixel", ServerNames.resolve(address), address);
        assertEquals("CubeCraft", ServerNames.resolve("play.cubecraft.net"));
        assertEquals("2b2t", ServerNames.resolve("2b2t.org"));
        assertEquals("Complex Gaming", ServerNames.resolve("hub.mc-complex.com"));
        assertEquals("CraftRise", ServerNames.resolve("play.craftrise.com.tr"));
        assertEquals("Minehut", ServerNames.resolve("minehut.com"));
    }

    @Test
    void unknownDomainsAreNamedAfterTheirRegistrableLabel() {
        assertEquals("Funnyservername", ServerNames.resolve("play.funnyservername.com"));
        assertEquals("My Cool Server", ServerNames.resolve("mc.my-cool_server.co.uk:25566"));
        assertEquals("Example", ServerNames.resolve("eu.play.example.gg"));
        assertEquals("Coolsmp", ServerNames.resolve("coolsmp.aternos.me"));
        assertEquals("Funny", ServerNames.resolve("funny.minehut.gg"));
        // A domain matches on whole labels only.
        assertEquals("Notahypixel", ServerNames.resolve("notahypixel.net"));
        assertEquals("Evil", ServerNames.resolve("hypixel.net.evil.com"));
        // Being typed: "mc.hypixel.n" already reads as Hypixel, "mc.hypixel" (no suffix yet) does not.
        assertEquals("Hypixel", ServerNames.resolve("mc.hypixel.n"));
        assertNull(ServerNames.resolve("mc.hypixel"));
    }

    @Test
    void ipsAndIncompleteAddressesGetNoName() {
        for (String address : new String[] {null, "", "   ", "127.0.0.1", "192.168.1.20:25565", "192.168", "[::1]:25565", "::1", "localhost",
            "localhost:25565", "play.com", "mc.", "bad host.com", "a..b", "play.mc.net"}) assertNull(ServerNames.resolve(address), address);
    }

    @Test
    void everyKnownServerResolvesToItselfAndNamesAreUnique() throws Exception {
        JsonObject root;
        try (InputStreamReader in = new InputStreamReader(ServerNames.class.getResourceAsStream("/thelads/known_servers.json"), StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(in).getAsJsonObject();
        }
        Set<String> names = new HashSet<>(), domains = new HashSet<>();
        for (JsonElement element : root.getAsJsonArray("servers")) {
            JsonObject server = element.getAsJsonObject();
            String name = server.get("name").getAsString();
            assertTrue(names.add(name), "duplicate name " + name);
            assertEquals(name, ServerNames.resolve(server.get("address").getAsString()), name);
            assertTrue(!server.get("category").getAsString().isEmpty() && !server.get("description").getAsString().isEmpty(), name);
            for (JsonElement domain : server.getAsJsonArray("domains")) assertTrue(domains.add(domain.getAsString()), "duplicate " + domain);
        }
        assertTrue(names.size() >= 80, "known servers: " + names.size());
    }

    @Test
    void defaultNameFollowsTheAddressAndRevertsWhenItHasNone() {
        ServerNames.AutoName auto = new ServerNames.AutoName("Minecraft Server");
        String name = "Minecraft Server";
        assertNull(auto.update(name, ""));
        assertNull(auto.update(name, "mc.hypixel"));
        name = auto.update(name, "mc.hypixel.n");
        assertEquals("Hypixel", name);
        assertNull(auto.update(name, "mc.hypixel.n"), "the name field's own change is no address change");
        assertNull(auto.update(name, "mc.hypixel.net"));
        name = auto.update(name, "play.cubecraft.net");
        assertEquals("CubeCraft", name);
        assertEquals("Minecraft Server", auto.update(name, ""));
    }

    @Test
    void aTypedNameIsNeverReplaced() {
        ServerNames.AutoName auto = new ServerNames.AutoName("Minecraft Server");
        assertNull(auto.update("Minecraft Server", ""));
        assertNull(auto.update("Lads SMP", ""));
        assertNull(auto.update("Lads SMP", "mc.hypixel.net"));
        // Editing an existing server: its saved name stays when the address changes.
        ServerNames.AutoName edit = new ServerNames.AutoName("Minecraft Server");
        assertNull(edit.update("Hypixel", "mc.hypixel.net"));
        assertNull(edit.update("Hypixel", "play.cubecraft.net"));
        // A name the user typed over a filled-in one is theirs from then on.
        ServerNames.AutoName over = new ServerNames.AutoName("Minecraft Server");
        assertEquals("Hypixel", over.update("Minecraft Server", "hypixel.net"));
        assertNull(over.update("Hypixel!", "hypixel.net"));
        assertNull(over.update("Hypixel!", "play.cubecraft.net"));
    }

    @Test
    void anEmptyOrTranslatedDefaultNameIsFilledAndPutBack() {
        ServerNames.AutoName empty = new ServerNames.AutoName("Minecraft-Server");
        assertEquals("Funnyservername", empty.update("", "play.funnyservername.com"));
        assertEquals("", empty.update("Funnyservername", "192.168.0.2"));
        assertEquals("Hypixel", new ServerNames.AutoName("Minecraft-Server").update("Minecraft-Server", "mc.hypixel.net"));
        assertEquals("Hypixel", new ServerNames.AutoName("Minecraft-Server").update(ServerNames.VANILLA_DEFAULT, "mc.hypixel.net"));
    }
}
