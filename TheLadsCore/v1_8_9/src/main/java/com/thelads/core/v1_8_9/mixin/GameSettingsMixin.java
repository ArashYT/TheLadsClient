package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.Zoom189;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lads Zoom is the only zoom: OptiFine's getFOVModifier asks GameSettings.isKeyDown(ofKeyBindZoom) each frame, so that key reads
 * as up while Lads Zoom is on. OptiFine then never divides the FOV or forces smooth camera; its binding and options are untouched.
 * OptiFine's loadOfOptions (the end of every loadOptions) also unbinds every other key on its zoom key's code, Lads Zoom's C
 * included (KeyUtils.fixKeyConflicts): Lads Zoom is left out of that list. Without OptiFine the method is absent (require = 0).
 *
 * <p>saveOptions (every key bind change, every options change) writes options.txt.tmp and moves it over options.txt once it is
 * complete. Vanilla empties options.txt first, so a crash or kill mid-save left it cut off: every key bind after the cut came
 * back at its default. Key binds in options.txt that no loaded mod has (Essential failed to download, a mod turned off) are
 * kept: vanilla dropped them, so the mod's binds were back at their defaults when it loaded again.
 */
@Mixin(GameSettings.class)
public abstract class GameSettingsMixin {
    @Shadow private File optionsFile;

    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true, require = 1)
    private static void ladsOnlyZoom(KeyBinding key, CallbackInfoReturnable<Boolean> cir) {
        if (Zoom189.blocksOptiFine(key)) cir.setReturnValue(false);
    }

    @ModifyArg(method = "loadOfOptions", at = @At(value = "INVOKE", target = "Lnet/optifine/util/KeyUtils;fixKeyConflicts("
        + "[Lnet/minecraft/client/settings/KeyBinding;[Lnet/minecraft/client/settings/KeyBinding;)V"), index = 0, remap = false, require = 0)
    private KeyBinding[] ladsKeepZoomKey(KeyBinding[] keys) {
        return ArrayUtils.removeElement(keys, Zoom189.ZOOM);
    }

    @ModifyArg(method = "saveOptions", at = @At(value = "INVOKE", target = "Ljava/io/FileWriter;<init>(Ljava/io/File;)V", remap = false),
        require = 1, allow = 1)
    private File ladsSaveToTemp(File options) {
        return new File(options.getPath() + ".tmp");
    }

    @Redirect(method = "saveOptions", at = @At(value = "INVOKE", target = "Ljava/io/PrintWriter;close()V", remap = false), require = 1, allow = 1)
    private void ladsReplaceWhenWhole(PrintWriter writer) {
        writer.close();
        File temp = new File(optionsFile.getPath() + ".tmp");
        try {
            if (writer.checkError()) throw new IOException("writing " + temp + " failed");
            ladsKeepUnloadedKeys(temp);
            Files.move(temp.toPath(), optionsFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LogManager.getLogger("TheLadsCore-1.8.9").error("Failed to save options; options.txt keeps the last saved ones", e);
        }
    }

    /** The key binds of a mod that did not load this time (Essential is downloaded at launch) stay for when it loads again. */
    private void ladsKeepUnloadedKeys(File temp) {
        if (!optionsFile.isFile()) return;
        try {
            Charset charset = Charset.defaultCharset(); // vanilla's FileReader and FileWriter
            Set<String> written = new HashSet<>();
            for (String line : Files.readAllLines(temp.toPath(), charset)) written.add(line.substring(0, Math.max(0, line.indexOf(':'))));
            List<String> kept = new ArrayList<>();
            for (String line : Files.readAllLines(optionsFile.toPath(), charset))
                if (line.startsWith("key_") && line.indexOf(':') > 0 && !written.contains(line.substring(0, line.indexOf(':')))) kept.add(line);
            if (!kept.isEmpty()) Files.write(temp.toPath(), kept, charset, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LogManager.getLogger("TheLadsCore-1.8.9").warn("Key binds of mods not loaded now were not kept: " + e);
        }
    }
}
