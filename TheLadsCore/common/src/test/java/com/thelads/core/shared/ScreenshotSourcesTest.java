package com.thelads.core.shared;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScreenshotSourcesTest {
    @TempDir Path temp;
    private Path image(String path) throws Exception {Path file=temp.resolve(path);Files.createDirectories(file.getParent());Files.writeString(file,"screenshot fixture");return file.toRealPath();}
    @Test void discoversAllThreeLaunchersWithoutMovingOriginals() throws Exception {
        Path m=image("roaming/ModrinthApp/profiles/Pack/screenshots/same.png");
        Path p=image("roaming/PrismLauncher/instances/Pack/.minecraft/screenshots/same.png");
        Path c=image("home/curseforge/minecraft/Instances/Pack/screenshots/same.png");
        var roots=ScreenshotSources.discover(temp.resolve("shared"),temp.resolve("game"),Map.of("APPDATA",temp.resolve("roaming").toString()),temp.resolve("home"));
        var found=ScreenshotSources.scan(roots);assertEquals(Set.of(m,p,c),found.stream().map(ScreenshotSources.Image::path).collect(java.util.stream.Collectors.toSet()));
        assertTrue(found.stream().anyMatch(i->i.source().startsWith("Modrinth")));
        for(Path file:List.of(m,p,c))assertEquals("screenshot fixture",Files.readString(file));
        assertFalse(Files.exists(temp.resolve("shared/screenshots")));
    }
    @Test void readsCustomModrinthPrismAndCurseForgeLocations() throws Exception {
        Path m=image("moved/modrinth/profiles/M/screenshots/m.png"),p=image("moved/prism/P/minecraft/screenshots/p.jpg"),c=image("moved/curse/Instances/C/screenshots/c.JPEG");
        Path app=temp.resolve("roaming/ModrinthApp");Files.createDirectories(app);
        JsonObject modrinth=new JsonObject();modrinth.addProperty("custom_dir",temp.resolve("moved/modrinth").toString());Files.writeString(app.resolve("settings.json"),modrinth.toString());
        Path prism=temp.resolve("roaming/PrismLauncher");Files.createDirectories(prism);Files.writeString(prism.resolve("prismlauncher.cfg"),"[General]\nInstanceDir=../../moved/prism\n");
        Path curse=temp.resolve("local/CurseForge");Files.createDirectories(curse);JsonObject cf=new JsonObject();cf.addProperty("MinecraftFolder",temp.resolve("moved/curse").toString());Files.writeString(curse.resolve("settings.json"),cf.toString());
        var roots=ScreenshotSources.discover(temp.resolve("shared"),temp.resolve("game"),Map.of("APPDATA",temp.resolve("roaming").toString(),"LOCALAPPDATA",temp.resolve("local").toString()),temp.resolve("home"));
        assertEquals(Set.of(m,p,c),ScreenshotSources.scan(roots).stream().map(ScreenshotSources.Image::path).collect(java.util.stream.Collectors.toSet()));
    }
    @Test void customRootsRoundTripAndCanonicalDuplicatesCollapse() throws Exception {
        Path image=image("portable/instances/A/screenshots/a.png"),shared=temp.resolve("shared");
        ScreenshotSources.addCustomRoot(shared,temp.resolve("portable"));ScreenshotSources.addCustomRoot(shared,temp.resolve("portable/instances/.."));
        assertEquals(1,ScreenshotSources.customRoots(shared).size());
        var roots=new ArrayList<>(ScreenshotSources.customRoots(shared));roots.add(new ScreenshotSources.Root(image.getParent(),"duplicate"));
        var found=ScreenshotSources.scan(roots);assertEquals(1,found.size());assertEquals(image,found.getFirst().path());
        assertTrue(Files.readString(ScreenshotSources.configFile(shared)).contains("\"version\": 1"));
    }
    @Test void boundsTraversalAndSkipsWorldAndCacheTrees() throws Exception {
        Path good=image("instance/screenshots/good.png");
        image("instance/screenshots/thumbnails/old.png");image("instance/saves/world/screenshots/map.png");image("instance/mods/screenshots/icon.png");
        image("instance/a/b/c/d/e/f/screenshots/deep.png");image("instance/icon.png");
        var files=ScreenshotSources.scan(List.of(new ScreenshotSources.Root(temp.resolve("instance"),"Custom")));
        assertEquals(Set.of(good),files.stream().map(ScreenshotSources.Image::path).collect(java.util.stream.Collectors.toSet()));
    }
    @Test void malformedAndMissingConfigDoNotBlockOtherSources() throws Exception {
        Path image=image("game/screenshots/a.png"),shared=temp.resolve("shared");Files.createDirectories(shared.resolve("config"));
        Files.writeString(ScreenshotSources.configFile(shared),"not json");
        var roots=ScreenshotSources.discover(shared,temp.resolve("game"),Map.of(),temp.resolve("home"));
        assertEquals(List.of(image),ScreenshotSources.scan(roots).stream().map(ScreenshotSources.Image::path).toList());
    }
    @Test void launcherDiscoveredPortableInstanceFeedIsScanned() throws Exception {
        Path image=image("portable/pack/screenshots/a.png"),game=temp.resolve("game");Files.createDirectories(game);
        JsonArray json=new JsonArray();JsonObject instance=new JsonObject();instance.addProperty("GameDirectory",temp.resolve("portable/pack").toString());instance.addProperty("Name","Imported Prism");json.add(instance);Files.writeString(game.resolve("lads-world-sources.json"),json.toString());
        var result=ScreenshotSources.scan(ScreenshotSources.discover(temp.resolve("shared"),game,Map.of(),temp.resolve("home")));
        assertEquals(1,result.size());assertEquals(image,result.getFirst().path());assertTrue(result.getFirst().source().startsWith("Imported Prism"));
    }
    @Test void readsLauncherDiscoveredRootsForDatabaseBasedCustomLocations() throws Exception {
        Path image=image("elsewhere/Modrinth/profiles/Pack/screenshots/a.png"),game=temp.resolve("game");Files.createDirectories(game);
        JsonObject json=new JsonObject();json.addProperty("version",1);JsonArray roots=new JsonArray();JsonObject item=new JsonObject();
        item.addProperty("path",temp.resolve("elsewhere/Modrinth").toString());item.addProperty("label","Modrinth");roots.add(item);json.add("roots",roots);
        Files.writeString(game.resolve("lads-screenshot-discovered.json"),json.toString());
        var found=ScreenshotSources.scan(ScreenshotSources.discover(temp.resolve("shared"),game,Map.of(),temp.resolve("home")));
        assertEquals(List.of(image),found.stream().map(ScreenshotSources.Image::path).toList());
    }

    @Test void selectedCustomImageFolderDoesNotNeedAScreenshotsName() throws Exception {
        Path image=image("My captures/a.png"),shared=temp.resolve("shared");
        ScreenshotSources.addCustomRoot(shared,image.getParent());
        assertEquals(List.of(image),ScreenshotSources.scan(ScreenshotSources.customRoots(shared)).stream().map(ScreenshotSources.Image::path).toList());
    }

    @Test void specificCustomRootsCanExpandEarlierAutomaticTraversal() throws Exception {
        Path image=image("root/a/b/c/d/e/f/screenshots/a.png"),direct=image("root/icon.png");
        var found=ScreenshotSources.scan(List.of(new ScreenshotSources.Root(temp.resolve("root"),"Auto"),
            new ScreenshotSources.Root(temp.resolve("root/a/b/c/d"),"Custom"),new ScreenshotSources.Root(temp.resolve("root"),"Custom",true)));
        assertEquals(Set.of(image,direct),found.stream().map(ScreenshotSources.Image::path).collect(java.util.stream.Collectors.toSet()));
    }

    @Test void cancelledScansStopInsideDirectoryEnumeration() throws Exception {
        for(int i=0;i<100;i++)image("screenshots/"+i+".png");
        var roots=List.of(new ScreenshotSources.Root(temp.resolve("screenshots"),"Shared"));
        assertTrue(ScreenshotSources.scan(roots,()->true).isEmpty());
        var polls=new java.util.concurrent.atomic.AtomicInteger();
        var found=ScreenshotSources.scan(roots,()->polls.incrementAndGet()>5);
        assertTrue(found.size()<100,"cancel is checked between directory entries");
        assertTrue(polls.get()>5);
    }
    @Test void ladsProfileCopiesOfSharedScreenshotsAreListedOnce() throws Exception {
        Path shared=temp.resolve("shared"),game=temp.resolve("game");Files.createDirectories(game);
        Path original=image("shared/screenshots/a.png");
        image("profiles/old/screenshots/a.png");image("profiles/listed/screenshots/a.png");                  // synced copies: same name and size
        Path unsynced=image("profiles/old/screenshots/b.png"),edited=image("profiles/old/screenshots/c.png");
        image("shared/screenshots/c.png");Files.writeString(edited,"a different, longer screenshot");      // same name, other size
        Path modrinth=image("modrinth/profiles/Pack/screenshots/a.png"),custom=image("worlds/Imported/screenshots/a.png");
        JsonObject discovered=new JsonObject();JsonArray roots=new JsonArray();
        for(String[] root:new String[][]{{"profiles/listed","Lads · Listed"},{"modrinth","Modrinth"}}){JsonObject item=new JsonObject();item.addProperty("path",temp.resolve(root[0]).toString());item.addProperty("label",root[1]);roots.add(item);}
        discovered.add("roots",roots);Files.writeString(game.resolve("lads-screenshot-discovered.json"),discovered.toString());
        JsonArray worlds=new JsonArray();
        for(String[] world:new String[][]{{"profiles/old","Old","1.21.1"},{"worlds/Imported","Imported",""}}){JsonObject item=new JsonObject();item.addProperty("Name",world[1]);item.addProperty("GameDirectory",temp.resolve(world[0]).toString());item.addProperty("Version",world[2]);worlds.add(item);}
        Files.writeString(game.resolve("lads-world-sources.json"),worlds.toString());
        var sources=ScreenshotSources.discover(shared,game,Map.of(),temp.resolve("home"));
        var found=ScreenshotSources.scan(sources).stream().map(ScreenshotSources.Image::path).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(original,shared.resolve("screenshots/c.png").toRealPath(),unsynced,edited,modrinth,custom),found);
        // Deleting the shared a.png must also take exactly the profile copies it hid; never other launchers' or user folders' files.
        assertEquals(Set.of(temp.resolve("profiles/old/screenshots/a.png").toRealPath(),temp.resolve("profiles/listed/screenshots/a.png").toRealPath()),
            Set.copyOf(ScreenshotSources.syncedCopies(sources,original,Files.size(original))));
        Path sharedC=shared.resolve("screenshots/c.png");
        assertEquals(List.of(),ScreenshotSources.syncedCopies(sources,sharedC,Files.size(sharedC)),"a same-name file with another size is a different screenshot");
    }
    @Test void curseForgeMovedRootComesFromStorageJsonString() throws Exception {
        Path image=image("moved/Instances/Pack/screenshots/a.png"),storage=temp.resolve("roaming/CurseForge/storage.json");Files.createDirectories(storage.getParent());
        JsonObject settings=new JsonObject();settings.addProperty("minecraftRoot",temp.resolve("moved").toString());
        JsonObject json=new JsonObject();json.addProperty("session-tokens","not read");json.addProperty("minecraft-settings",settings.toString());Files.writeString(storage,json.toString());
        var env=Map.of("APPDATA",temp.resolve("roaming").toString());
        assertEquals(List.of(image),ScreenshotSources.scan(ScreenshotSources.discover(temp.resolve("shared"),temp.resolve("game"),env,temp.resolve("home"))).stream().map(ScreenshotSources.Image::path).toList());
        for(String unchanged:List.of("{\"minecraft-settings\":\"{\\\"minecraftRoot\\\":null}\"}","{\"minecraft-settings\":null}","{\"minecraft-settings\":\"not json\"}","{\"minecraft-settings\":\"{\\\"minecraftRoot\\\":\\\"relative\\\"}\"}","[]")) {
            Files.writeString(storage,unchanged);assertNull(ScreenshotSources.curseForgeInstances(storage),unchanged);
        }
    }
    @Test void everyRootGetsItsOwnFolderBudget() throws Exception {
        for(int i=0;i<10;i++)image("huge/f"+i+"/screenshots/"+i+".png");
        Path small=image("small/screenshots/a.png");
        var roots=List.of(new ScreenshotSources.Root(temp.resolve("huge"),"Huge"),new ScreenshotSources.Root(temp.resolve("small"),"Small"));
        assertEquals(List.of(small),ScreenshotSources.scan(roots,()->false,null,5).stream().map(ScreenshotSources.Image::path).toList());
    }
    @Test void progressReportsGrowingResultsAfterEachProductiveRoot() throws Exception {
        Path a=image("one/screenshots/a.png"),b=image("two/screenshots/b.png");Files.createDirectories(temp.resolve("empty/screenshots"));
        List<List<Path>> reports=new ArrayList<>();
        var roots=List.of(new ScreenshotSources.Root(temp.resolve("one"),"One"),new ScreenshotSources.Root(temp.resolve("empty"),"Empty"),new ScreenshotSources.Root(temp.resolve("two"),"Two"));
        ScreenshotSources.scan(roots,()->false,images->reports.add(images.stream().map(ScreenshotSources.Image::path).toList()));
        assertEquals(List.of(List.of(a),List.of(a,b)),reports);
    }
    @Test void discoveryNeverThrowsOnUnusableEnvironmentPaths() {
        var roots=assertDoesNotThrow(()->ScreenshotSources.discover(temp.resolve("shared"),temp.resolve("game"),Map.of("APPDATA","bad\0path","LOCALAPPDATA","also\0bad"),temp.resolve("home")));
        assertEquals(temp.resolve("shared/screenshots"),roots.getFirst().path());
    }
    @Test void addingFolderPreservesMalformedExistingConfig() throws Exception {
        Path shared=temp.resolve("shared"),folder=temp.resolve("captures");Files.createDirectories(folder);Files.createDirectories(shared.resolve("config"));
        Path config=ScreenshotSources.configFile(shared);
        for(String malformed:List.of("not json","{}","{\"roots\":[{\"path\":5}]}","{\"roots\":[{\"path\":\"relative\"}]}")) {
            Files.writeString(config,malformed);
            assertThrows(java.io.IOException.class,()->ScreenshotSources.addCustomRoot(shared,folder));
            assertEquals(malformed,Files.readString(config));
        }
    }

}
