package com.thelads.core.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.slf4j.LoggerFactory;

/**
 * Cheats stay as the host set them, on every version.
 *
 * <p>Minecraft keeps joined players' cheats (Open to LAN's Allow Cheats, 26.2's Other Players commands, 26.3's guest
 * command access) only while the world is open; the host's last choice is kept in the world folder (lads-cheats.properties).
 */
public final class WorldCheats {
    static final String FILE = "lads-cheats.properties";

    private WorldCheats() {}

    /** The host's last choice for joined players' cheats in this world folder, or null when the host never chose. */
    public static Boolean guests(Path world) {
        Path file = world.resolve(FILE);
        if (!Files.isRegularFile(file)) return null;
        Properties saved = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            saved.load(in);
        } catch (IOException failure) {
            LoggerFactory.getLogger("TheLadsCore").warn("Lads cheats: could not read {}", file, failure);
            return null;
        }
        String value = saved.getProperty("guests");
        return value == null ? null : Boolean.valueOf(value.trim());
    }

    /** Keeps the host's choice for joined players' cheats in this world folder. */
    public static void guests(Path world, boolean allowed) {
        if (Boolean.valueOf(allowed).equals(guests(world))) return;
        Properties saved = new Properties();
        saved.setProperty("guests", Boolean.toString(allowed));
        try (OutputStream out = Files.newOutputStream(world.resolve(FILE))) {
            saved.store(out, "Lads Client: whether players who join this world may use cheats, as the host last chose");
        } catch (IOException failure) {
            LoggerFactory.getLogger("TheLadsCore").warn("Lads cheats: could not save the joined players' choice in {}", world, failure);
        }
    }
}
