package com.thelads.core.modules;

import com.thelads.core.config.ActionOption;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.TextOption;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * Lads additions to Flashback (26.x): the folder replays are saved to, and faster exports. Flashback itself records, plays
 * back and exports; a settings entry with no on/off switch.
 */
public final class FlashbackModule extends Module {
    public static final String NAME = "Flashback Settings";
    /** GPU H.264 encoders an OpenH264 export may move to, best first. */
    private static final List<String> GPU_H264 = List.of("h264_nvenc", "h264_qsv", "h264_amf");

    public final TextOption replayFolder = addOption(new TextOption("Replay folder", ""));
    public final ActionOption browse = addOption(new ActionOption("Choose replay folder", "Browse..."));
    public final ActionOption defaultFolder = addOption(new ActionOption("Default folder", "Reset"));
    public final SliderOption pngCompression = addOption(new SliderOption("PNG compression", 6, 0, 9, 1));
    // Off by default: on the Lads test PC (24 threads, RTX 4060 Ti) NVENC exports were slower than OpenH264. For CPU-bound exports.
    public final BoolOption gpuEncoder = addOption(new BoolOption("OpenH264 to GPU", false));

    public FlashbackModule() {
        super(NAME, "Where Flashback saves replays (blank: its own folder) and how fast it exports.");
        setEnabled(true);
    }

    /**
     * The folder typed in Replay folder, or null for Flashback's own: blank, not a path, or an existing file. Relative paths
     * start in the game folder; quotes from Explorer's "Copy as path" are ignored.
     */
    public static Path replayFolder(String text, Path gameDirectory) {
        String folder = text == null ? "" : text.trim();
        if (folder.length() >= 2 && folder.startsWith("\"") && folder.endsWith("\"")) folder = folder.substring(1, folder.length() - 1).trim();
        if (folder.isEmpty()) return null;
        try {
            Path path = gameDirectory.resolve(folder).toAbsolutePath().normalize();
            return Files.exists(path) && !Files.isDirectory(path) ? null : path;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /** The GPU encoder an OpenH264 export moves to, or null to keep it; {@code working}: H.264 encoders that open on this machine. */
    public static String promotedEncoder(String encoder, Collection<String> working) {
        if (!"libopenh264".equals(encoder)) return null;
        for (String candidate : GPU_H264) if (working.contains(candidate)) return candidate;
        return null;
    }
}
