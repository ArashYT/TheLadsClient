package com.thelads.core.v26_2.feature;
import com.thelads.core.shared.SharedContentPaths;
import java.io.File;
/** The launcher's shared Minecraft folder (LADS_GLOBAL_MINECRAFT_DIR), else the operating system's default .minecraft. */
public final class GlobalScreenshots {
    private GlobalScreenshots() {}
    public static File gameDirectory(){return SharedContentPaths.root().toFile();}
    public static File directory(){return SharedContentPaths.screenshotsDir().toFile();}
}
