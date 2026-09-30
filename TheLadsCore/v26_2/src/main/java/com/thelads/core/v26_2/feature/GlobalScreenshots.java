package com.thelads.core.v26_2.feature;
import com.thelads.core.shared.SharedContentPaths;
import com.thelads.core.shared.ScreenshotSources;
import java.io.File;
import java.nio.file.*;
import java.util.*;
/** New captures remain shared. The gallery discovers external originals without importing them. */
public final class GlobalScreenshots {
    private static volatile Map<Path,String> external=Map.of();
    private GlobalScreenshots() {}
    public static File gameDirectory(){return SharedContentPaths.root().toFile();}
    public static File directory(){return SharedContentPaths.screenshotsDir().toFile();}
    public static boolean sharedGallery(File folder){return same(folder.toPath(),directory().toPath());}
    public static List<File> scan(java.util.function.BooleanSupplier cancelled) {
        Path game=net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath();
        var roots=ScreenshotSources.discover(SharedContentPaths.root(),game,System.getenv(),Path.of(System.getProperty("user.home")));
        var images=ScreenshotSources.scan(roots,cancelled);Map<Path,String> found=new HashMap<>();List<File> files=new ArrayList<>();
        for(var image:images){files.add(image.path().toFile());if(!same(image.path().getParent(),directory().toPath()))found.put(image.path(),image.source());}
        if(!cancelled.getAsBoolean())external=Map.copyOf(found);return files;
    }
    public static boolean readOnly(File image){
        Path file=canonical(image.toPath());
        Path shared=canonical(directory().toPath());
        Path local=canonical(net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots"));
        return !file.startsWith(shared)&&!file.startsWith(local);
    }
    public static String source(File image){return external.getOrDefault(image.toPath().toAbsolutePath().normalize(),
        readOnly(image)?"External folder / "+image.getParent():"Lads screenshots");}
    private static boolean same(Path a,Path b){return canonical(a).equals(canonical(b));}
    private static Path canonical(Path path){try{return path.toRealPath();}catch(java.io.IOException ignored){return path.toAbsolutePath().normalize();}}
}
