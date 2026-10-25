package com.thelads.core.client.social;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * "Backup the world before opening" on every version: the world folder zipped to &lt;game dir&gt;/lads-backups/&lt;world&gt;-&lt;yyyyMMdd-HHmm&gt;.zip
 * (session.lock and lads-host.json left out), the newest 5 per world kept. Runs on the caller's thread: SocialState uses a worker.
 */
public final class WorldBackup {
    static final int KEEP = 5;

    private WorldBackup() {}

    /** Zips worldDir (saves/&lt;world&gt;, saved first); progress gets 0..100 in steps of 10 or more. Returns the zip. */
    public static Path zip(Path worldDir, IntConsumer progress) throws IOException {
        Path world = worldDir.toAbsolutePath().normalize();
        Path game = world.getParent().getParent() != null ? world.getParent().getParent() : world.getParent();
        Path backups = Files.createDirectories(game.resolve("lads-backups"));
        String name = world.getFileName().toString();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(world)) {
            files = walk.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).filter(p -> { // a symlinked file is not read
                String n = p.getFileName().toString();
                return !n.equals("session.lock") && !n.equals(HostOptions.FILE);
            }).collect(Collectors.toList());
        }
        long total = 0;
        for (Path f : files) total += Files.size(f);
        if (Files.getFileStore(backups).getUsableSpace() < total) throw new IOException("Not enough disk space for the world backup");

        Path zip = backups.resolve(name + "-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(new Date()) + ".zip");
        Path part = zip.resolveSibling(zip.getFileName() + ".part");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(part))) {
            long done = 0;
            int shown = -10;
            for (Path f : files) {
                out.putNextEntry(new ZipEntry(world.relativize(f).toString().replace('\\', '/')));
                Files.copy(f, out);
                out.closeEntry();
                done += Files.size(f);
                int pct = total == 0 ? 100 : (int) (done * 100 / total);
                if (pct >= shown + 10) progress.accept(shown = pct);
            }
        } catch (IOException e) {
            Files.deleteIfExists(part);
            throw e;
        }
        Files.move(part, zip, StandardCopyOption.REPLACE_EXISTING);
        prune(backups, name);
        return zip;
    }

    /** Keeps the newest KEEP of this world's backups (names sort by time; another world whose name starts the same is not touched). */
    static void prune(Path backups, String world) throws IOException {
        Pattern mine = Pattern.compile(Pattern.quote(world) + "-\\d{8}-\\d{4}\\.zip");
        List<Path> zips;
        try (Stream<Path> list = Files.list(backups)) {
            zips = list.filter(p -> mine.matcher(p.getFileName().toString()).matches())
                .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed()).collect(Collectors.toList());
        }
        for (int i = KEEP; i < zips.size(); i++) Files.deleteIfExists(zips.get(i));
        // a .part left by a crash mid-zip, more than a day old
        long dayAgo = System.currentTimeMillis() - 86_400_000L;
        try (Stream<Path> list = Files.list(backups)) {
            for (Path p : list.filter(p -> p.getFileName().toString().startsWith(world + "-") && p.getFileName().toString().endsWith(".zip.part"))
                    .collect(Collectors.toList())) {
                if (Files.getLastModifiedTime(p).toMillis() < dayAgo) Files.deleteIfExists(p);
            }
        }
    }
}
