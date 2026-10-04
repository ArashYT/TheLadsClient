package com.thelads.core.v26_2.feature.flashback;

import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.FlashbackModule;
import com.thelads.core.v26_2.gui.NativeFileDialogs;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Flashback Settings on 26.x: the replay folder Flashback uses and the export switches its mixins read. No Flashback class is
 * referenced here, so this loads without Flashback; the mixins (flashback/mixin) only apply with it.
 */
public final class NativeFlashback {
    public static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    /** QA only (FlashbackExportProbe): exports run exactly as stock Flashback runs them. */
    public static volatile boolean qaStock;
    private static String warnedFolder;
    private NativeFlashback() {}

    public static FlashbackModule module() { return (FlashbackModule) ModuleManager.getInstance().getModule(FlashbackModule.NAME); }

    public static void initialize() {
        if (!FabricLoader.getInstance().isModLoaded("flashback")) {
            ModuleSupport.registerUnavailable(FlashbackModule.NAME, "Flashback is not installed or is turned off for this profile.");
            return;
        }
        ModuleSupport.registerSettingsOnly(FlashbackModule.NAME);
        FlashbackModule module = module();
        module.browse.setAction(() -> NativeFileDialogs.choose(true, "Flashback replay folder").whenComplete((folder, error) ->
            Minecraft.getInstance().execute(() -> { if (folder != null) { module.replayFolder.setValue(folder.toString()); ConfigManager.save(); } })));
        module.defaultFolder.setAction(() -> { module.replayFolder.setValue(""); ConfigManager.save(); });
    }

    /** The chosen replay folder, created if missing; null keeps Flashback's own (none chosen, or the chosen one is unusable). */
    public static Path replayFolder() {
        String text = module().replayFolder.getValue();
        if (text.isBlank()) return null;
        Path folder = FlashbackModule.replayFolder(text, FabricLoader.getInstance().getGameDir());
        try {
            if (folder != null) return Files.createDirectories(folder);
        } catch (Exception e) {
            folder = null;
        }
        // A missing drive must not lose a recording: Flashback's own folder takes it.
        if (!text.equals(warnedFolder)) LOGGER.warn("Flashback Settings: replay folder '{}' is not usable; Flashback's own folder is used", text);
        warnedFolder = text;
        return null;
    }

    /** Lads changes the export (always, except in the QA stock comparison). */
    public static boolean exportChanges() { return !qaStock; }
    public static boolean gpuEncoder() { return !qaStock && module().gpuEncoder.get(); }
}
