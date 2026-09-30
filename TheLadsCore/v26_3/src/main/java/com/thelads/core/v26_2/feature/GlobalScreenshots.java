package com.thelads.core.v26_2.feature;
import com.thelads.core.shared.SharedContentPaths;
import com.thelads.core.shared.ScreenshotSources;
import java.io.File;
import java.nio.file.*;
import java.util.*;
/** New captures remain shared. The gallery discovers external originals without importing them. */
public final class GlobalScreenshots {
    /** Resolved once per file on the scan thread, so gallery widgets never touch the filesystem on the render thread. */
    private record Provenance(String source,boolean readOnly) {}
    private static volatile Map<Path,Provenance> external=Map.of();
    /** Roots of the last scan: a shared delete also removes the Lads-profile copies that scan hid behind the file. */
    private static volatile List<ScreenshotSources.Root> lastRoots=List.of();
    private GlobalScreenshots() {}
    public static File gameDirectory(){return SharedContentPaths.root().toFile();}
    public static File directory(){return SharedContentPaths.screenshotsDir().toFile();}
    public static boolean sharedGallery(File folder){return same(folder.toPath(),directory().toPath());}
    /** Scan thread only. partial receives every file found so far after each source root that added some; provenance is published first. */
    public static List<File> scan(java.util.function.BooleanSupplier cancelled,java.util.function.Consumer<List<File>> partial) {
        Path game=net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath();
        Path shared=canonical(directory().toPath()),local=canonical(game.resolve("screenshots"));
        var roots=ScreenshotSources.discover(SharedContentPaths.root(),game,System.getenv(),Path.of(System.getProperty("user.home")));lastRoots=roots;
        Map<Path,Provenance> found=new HashMap<>();
        java.util.function.Function<List<ScreenshotSources.Image>,List<File>> publish=images->{
            List<File> files=new ArrayList<>(images.size());
            for(var image:images){ // scan results are real paths already
                Path path=image.path();files.add(path.toFile());
                found.computeIfAbsent(path,p->new Provenance(image.source(),!p.startsWith(shared)&&!p.startsWith(local)));
            }
            if(!cancelled.getAsBoolean())external=Map.copyOf(found);
            return files;
        };
        return publish.apply(ScreenshotSources.scan(roots,cancelled,images->partial.accept(publish.apply(images))));
    }
    public static boolean readOnly(File image){
        Provenance known=external.get(image.toPath());
        if(known!=null)return known.readOnly();
        // Not from a scan (configured folder, renamed file): resolve links before allowing any edit.
        Path file=canonical(image.toPath());
        Path shared=canonical(directory().toPath());
        Path local=canonical(net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots"));
        return !file.startsWith(shared)&&!file.startsWith(local);
    }
    public static String source(File image){
        Provenance known=external.get(image.toPath());
        return known!=null?known.source():readOnly(image)?"External folder / "+image.getParent():"Lads screenshots";
    }
    /** Call before deleting a shared screenshot; otherwise its hidden 1.21.x profile original reappears and is synced back on that game's exit. */
    public static List<Path> syncedCopies(File image){
        try{return sharedGallery(image.getParentFile())?ScreenshotSources.syncedCopies(lastRoots,image.toPath(),Files.size(image.toPath())):List.of();}
        catch(java.io.IOException|RuntimeException unreadable){return List.of();}
    }
    public static void deleteCopies(List<Path> copies){
        for(Path copy:copies)try{com.thelads.core.v26_2.feature.screenshots.ScreenshotFileIO.delete(copy);}
        catch(java.io.IOException|RuntimeException failure){org.slf4j.LoggerFactory.getLogger("TheLadsCore").warn("Could not delete synced screenshot copy {}",copy,failure);}
    }
    private static boolean same(Path a,Path b){return canonical(a).equals(canonical(b));}
    private static Path canonical(Path path){try{return path.toRealPath();}catch(java.io.IOException ignored){return path.toAbsolutePath().normalize();}}
}
