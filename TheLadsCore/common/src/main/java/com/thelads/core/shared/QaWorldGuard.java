package com.thelads.core.shared;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * Where the 1.21.x auto-world QA may act (the 26.x adapters keep their own NativeWorldVerification copy): only in
 * {@code <repository>/artifacts/verification/<version>-title}, only on the save named for that version, in the profile's own
 * saves folder or the shared sandbox inside artifacts/verification, and never on a save written by a newer game.
 */
public final class QaWorldGuard {
    private QaWorldGuard() {}

    /** "Client QA 1_21_11": one save per game version, so no version opens another version's QA world. */
    public static String saveName(String version) {
        return "Client QA " + version.replace('.', '_');
    }

    /** The real game directory, when it is {@code <repository>/artifacts/verification/<version>-title} beside the repository marker. */
    public static Path gameDirectory(Path candidate, String version) throws IOException {
        Path game = candidate.toRealPath();
        Path verification = game.getParent();
        Path artifacts = verification == null ? null : verification.getParent();
        Path repository = artifacts == null ? null : artifacts.getParent();
        if (!game.getFileName().toString().equals(version + "-title") || verification == null
            || !verification.getFileName().toString().equals("verification") || artifacts == null
            || !artifacts.getFileName().toString().equals("artifacts") || repository == null
            || !Files.isRegularFile(repository.resolve("TheLadsCore/settings.gradle"))) {
            throw new IOException("Auto-world QA requires artifacts/verification/" + version + "-title with its repository marker");
        }
        if (!repository.resolve("artifacts/verification/" + version + "-title").toRealPath().equals(game))
            throw new IOException("QA game directory resolves outside its expected location");
        return game;
    }

    /** The real saves folder: inside the QA game directory, or the shared sandbox's saves when that root lies inside artifacts/verification. */
    public static Path saves(Path gameDirectory, Path saves) throws IOException {
        Path real = saves.toRealPath();
        boolean sandbox = SharedContentPaths.redirectedInside(gameDirectory.getParent()) && Files.isDirectory(SharedContentPaths.savesDir())
            && real.equals(SharedContentPaths.savesDir().toRealPath());
        if (!real.startsWith(gameDirectory) && !sandbox)
            throw new IOException("QA saves resolve outside the isolated game directory and the shared sandbox: " + real);
        return real;
    }

    /** The existing save {@code name} directly inside the checked {@code saves} folder, with its level.dat. */
    public static Path existingSave(Path saves, String name) throws IOException {
        Path world = saves.resolve(name).toRealPath();
        if (!saves.equals(world.getParent()) || !Files.isRegularFile(world.resolve("level.dat"), LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Existing isolated QA save is missing or resolves outside the QA saves directory");
        return world;
    }

    /** A newer save would open as a downgrade, which QA never accepts; 0 means its level.dat records no DataVersion. */
    public static void requireOpenable(int saveDataVersion, int gameDataVersion) throws IOException {
        if (saveDataVersion <= 0 || saveDataVersion > gameDataVersion)
            throw new IOException("QA save DataVersion " + saveDataVersion + " cannot be opened by this game (DataVersion " + gameDataVersion + ")");
    }
}
