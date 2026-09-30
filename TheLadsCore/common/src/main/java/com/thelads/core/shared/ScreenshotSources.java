package com.thelads.core.shared;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Read-only discovery of screenshot folders. Never traverses world/mod/cache trees or writes to an instance. */
public final class ScreenshotSources {
    /** syncedTo: set for Lads profiles, whose screenshots the launcher copies into that shared folder; such copies are listed there only. */
    public record Root(Path path, String label, boolean directImages, Path syncedTo) {
        public Root(Path path,String label){this(path,label,false,null);}
        public Root(Path path,String label,boolean directImages){this(path,label,directImages,null);}
    }
    public record Image(Path path, String source) {}
    private record Visit(Path path, int depth) {}
    private static final Set<String> SKIP = Set.of("saves","mods","libraries","assets","resourcepacks","shaderpacks","logs","cache","caches","thumbnails","lads-cache-v1",".git",".gradle","versions","natives");
    private ScreenshotSources() {}
    public static Path configFile(Path shared) { return shared.resolve("config/lads-screenshot-sources.json"); }

    /** Never throws: an unreadable or malformed source only drops that source. */
    public static List<Root> discover(Path shared, Path game, Map<String,String> env, Path home) {
        List<Root> roots=new ArrayList<>();Path synced=shared.resolve("screenshots");
        roots.add(new Root(synced,"Shared"));
        roots.add(new Root(game.resolve("screenshots"),"This instance",false,synced));
        // CurseForge (same list as the launcher): the default Instances folders, then a moved root from the app's storage.json.
        for(String folder:List.of("curseforge/minecraft/Instances","Documents/CurseForge/Minecraft/Instances","Documents/Curse/Minecraft/Instances"))
            roots.add(new Root(home.resolve(folder),"CurseForge"));
        for(String key:List.of("APPDATA","LOCALAPPDATA"))try {
            String folder=env.get(key);if(folder==null||folder.isBlank())continue;
            Path data=Path.of(folder);
            if(key.equals("APPDATA")){Path moved=curseForgeInstances(data.resolve("CurseForge/storage.json"));if(moved!=null)roots.add(new Root(moved,"CurseForge"));}
            for(String modrinth:List.of("ModrinthApp","Modrinth App","com.modrinth.theseus")) {
                Path app=data.resolve(modrinth);
                roots.add(new Root(app.resolve("profiles"),"Modrinth"));
                configRoots(app.resolve("settings.json"),Set.of("custom_dir","customDir"),"Modrinth",roots);
            }
            Path prism=data.resolve("PrismLauncher");
            roots.add(new Root(prism.resolve("instances"),"Prism"));
            prismRoots(prism.resolve("prismlauncher.cfg"),roots);
            for(String config:List.of("CurseForge/agent-settings.json","CurseForge/settings.json","Overwolf/CurseForge/settings.json")) // older guesses
                configRoots(data.resolve(config),Set.of("MinecraftFolder","minecraftFolder","minecraftFolderPath","MinecraftFolderPath"),"CurseForge",roots);
        }catch(RuntimeException ignored){}
        roots.add(new Root(home.resolve(".local/share/PrismLauncher/instances"),"Prism"));
        prismRoots(home.resolve(".local/share/PrismLauncher/prismlauncher.cfg"),roots);
        roots.add(new Root(home.resolve(".local/share/ModrinthApp/profiles"),"Modrinth"));
        roots.addAll(customRoots(shared));
        // The launcher labels its own profiles "Lads · <name>"; other launchers and custom folders are never matched against shared.
        for(Root root:readRoots(game.resolve("lads-screenshot-discovered.json"),false))
            roots.add(root.label().startsWith("Lads · ")?new Root(root.path(),root.label(),false,synced):root);
        JsonElement instances=readJson(game.resolve("lads-world-sources.json"));
        if(instances!=null&&instances.isJsonArray())for(JsonElement item:instances.getAsJsonArray())try {
            JsonObject obj=item.getAsJsonObject();
            // Only Lads profile entries carry a game version; the global folder and user-added world folders leave it empty.
            boolean profile=obj.get("Version") instanceof JsonPrimitive version&&!version.getAsString().isBlank();
            roots.add(new Root(Path.of(obj.get("GameDirectory").getAsString()),obj.has("Name")?obj.get("Name").getAsString():"Instance",false,profile?synced:null));
        }catch(RuntimeException ignored){}
        return roots;
    }
    /** CurseForge keeps a moved folder in storage.json: "minecraft-settings" holds a JSON document as a string, whose "minecraftRoot" (null when unchanged) contains Instances. */
    static Path curseForgeInstances(Path storage) {
        try {
            JsonElement settings=JsonParser.parseString(readJson(storage,4*1024*1024).getAsJsonObject().getAsJsonPrimitive("minecraft-settings").getAsString());
            JsonElement root=settings.getAsJsonObject().get("minecraftRoot");
            if(root==null||!root.isJsonPrimitive()||root.getAsString().isBlank())return null;
            Path path=Path.of(root.getAsString());return path.isAbsolute()?path.resolve("Instances"):null;
        }catch(RuntimeException missingOrMalformed){return null;}
    }
    public static List<Root> customRoots(Path shared) {
        return readRoots(configFile(shared),true);
    }
    private static List<Root> readRoots(Path file,boolean directImages) {
        List<Root> roots=new ArrayList<>();JsonElement json=readJson(file);
        if(json!=null&&json.isJsonObject()&&json.getAsJsonObject().has("roots"))try {
            for(JsonElement item:json.getAsJsonObject().getAsJsonArray("roots"))try {
                JsonObject obj=item.getAsJsonObject();Path path=Path.of(obj.get("path").getAsString());
                if(path.isAbsolute())roots.add(new Root(path,obj.has("label")?obj.get("label").getAsString():"Custom",directImages));
            }catch(RuntimeException ignored){}
        }catch(RuntimeException ignored){}
        return roots;
    }
    public static synchronized void addCustomRoot(Path shared,Path folder) throws IOException {
        Path canonical=folder.toRealPath();if(!Files.isDirectory(canonical))throw new IOException("Choose a folder");
        List<Root> roots=new ArrayList<>(customRootsForWrite(shared));
        if(roots.stream().noneMatch(root->canonical.equals(real(root.path()))))roots.add(new Root(canonical,"Custom"));
        JsonObject json=new JsonObject();json.addProperty("version",1);JsonArray array=new JsonArray();
        for(Root root:roots){JsonObject item=new JsonObject();item.addProperty("path",root.path().toString());item.addProperty("label",root.label());array.add(item);}
        json.add("roots",array);Path target=configFile(shared);Files.createDirectories(target.getParent());
        Path temp=Files.createTempFile(target.getParent(),"lads-screenshots-",".tmp");
        try {
            Files.writeString(temp,new GsonBuilder().setPrettyPrinting().create().toJson(json));
            try{Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
    }
    private static List<Root> customRootsForWrite(Path shared) throws IOException {
        Path file=configFile(shared);if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS))return List.of();
        if(Files.isSymbolicLink(file)||!Files.isRegularFile(file)||Files.size(file)>1024*1024)
            throw new IOException("Screenshot folder settings cannot be safely updated");
        try(var reader=Files.newBufferedReader(file)) {
            JsonObject json=JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray entries=json.getAsJsonArray("roots");if(entries==null)throw new IllegalArgumentException("Missing roots list");
            List<Root> roots=new ArrayList<>();
            for(JsonElement item:entries) {
                JsonObject entry=item.getAsJsonObject();
                if(!entry.has("path")||!entry.get("path").isJsonPrimitive()||!entry.getAsJsonPrimitive("path").isString())throw new IllegalArgumentException("Invalid folder path");
                Path path=Path.of(entry.get("path").getAsString());if(!path.isAbsolute())throw new IllegalArgumentException("Folder paths must be absolute");
                String label="Custom";
                if(entry.has("label")) {
                    if(!entry.get("label").isJsonPrimitive()||!entry.getAsJsonPrimitive("label").isString())throw new IllegalArgumentException("Invalid folder label");
                    label=entry.get("label").getAsString();
                }
                roots.add(new Root(path,label,true));
            }
            return roots;
        }catch(RuntimeException invalid){throw new IOException("Screenshot folder settings are invalid; the existing file was preserved",invalid);}
    }

    /** Breadth-first scan with hard work limits; links resolve once, so aliases never duplicate images or loop. */
    public static List<Image> scan(List<Root> roots) { return scan(roots,()->false,null); }
    public static List<Image> scan(List<Root> roots,java.util.function.BooleanSupplier cancelled) { return scan(roots,cancelled,null); }
    /** Roots are scanned in order; progress gets every image found so far after each root that added some. */
    public static List<Image> scan(List<Root> roots,java.util.function.BooleanSupplier cancelled,java.util.function.Consumer<List<Image>> progress) {
        return scan(roots,cancelled,progress,20_000); // same per-root folder limit as the launcher's gallery
    }
    /** foldersPerRoot is a budget per root, so one huge launcher cannot starve the roots after it. */
    static List<Image> scan(List<Root> roots,java.util.function.BooleanSupplier cancelled,java.util.function.Consumer<List<Image>> progress,int foldersPerRoot) {
        Map<Path,Image> images=new LinkedHashMap<>();Map<Path,Integer> visited=new HashMap<>();
        for(Root root:roots) {
            if(stop(images,cancelled))break;
            int before=images.size();
            try{scanRoot(root,images,visited,cancelled,foldersPerRoot);}catch(RuntimeException ignored){}
            if(progress!=null&&images.size()>before&&!cancelled.getAsBoolean())progress.accept(List.copyOf(images.values()));
        }
        return new ArrayList<>(images.values());
    }
    private static void scanRoot(Root root,Map<Path,Image> images,Map<Path,Integer> visited,java.util.function.BooleanSupplier cancelled,int budget) {
        if(root.path()==null)return;
        Path synced=root.syncedTo()==null?null:real(root.syncedTo());
        ArrayDeque<Visit> queue=new ArrayDeque<>();queue.add(new Visit(root.path(),0));
        int folders=0;
        while(!queue.isEmpty()&&folders<budget&&!stop(images,cancelled)) {
            Visit visit=queue.removeFirst();Path dir=real(visit.path());
            if(dir==null||!Files.isDirectory(dir))continue;
            boolean direct=visit.depth()==0&&root.directImages();
            int allowance=(5-visit.depth())*2+(direct?1:0);
            if(visited.getOrDefault(dir,-1)>=allowance)continue;
            visited.put(dir,allowance);folders++;
            boolean screenshotDir=dir.getFileName()!=null&&dir.getFileName().toString().equalsIgnoreCase("screenshots");
            // A directly selected image folder can have any name; descendant folders must be named screenshots.
            boolean accept=screenshotDir||direct,copies=synced!=null&&!dir.equals(synced);
            try(var entries=Files.newDirectoryStream(dir)) {
                for(Path entry:entries) {
                    if(stop(images,cancelled))break;
                    if(accept&&isImage(entry)) {
                        Path real=real(entry);
                        if(real!=null&&!(copies&&syncedCopy(real,synced)))images.putIfAbsent(real,new Image(real,root.label()+" / "+dir));
                    }else if(!screenshotDir&&visit.depth()<5&&queue.size()<budget&&Files.isDirectory(entry)
                        &&!SKIP.contains(entry.getFileName().toString().toLowerCase(Locale.ROOT)))
                        queue.addLast(new Visit(entry,visit.depth()+1));
                }
            }catch(IOException|RuntimeException ignored){} // includes DirectoryIteratorException: skip the rest of this folder only
        }
    }
    private static boolean stop(Map<Path,Image> images,java.util.function.BooleanSupplier cancelled) {
        return images.size()>=50_000||cancelled.getAsBoolean()||Thread.currentThread().isInterrupted();
    }
    /** The launcher copies (not moves) 1.21.x profile screenshots into the shared folder on exit: same name and size there is that copy. */
    private static boolean syncedCopy(Path image,Path shared) {
        try{Path copy=shared.resolve(image.getFileName().toString());return Files.isRegularFile(copy)&&Files.size(copy)==Files.size(image);}
        catch(IOException|RuntimeException unreadable){return false;}
    }
    /**
     * The Lads-profile originals a scan hides behind this shared screenshot (same name and size in a synced profile's screenshots folder).
     * Deleting the shared copy alone would show them again and the next 1.21.x exit would copy them back. Call before deleting.
     */
    public static List<Path> syncedCopies(List<Root> roots,Path sharedImage,long size) {
        List<Path> copies=new ArrayList<>();Path folder=real(sharedImage.toAbsolutePath().getParent());
        if(folder==null)return copies;
        for(Root root:roots) {
            if(root.syncedTo()==null||root.path()==null||!folder.equals(real(root.syncedTo())))continue;
            Path copy=real(root.path().resolve("screenshots").resolve(sharedImage.getFileName().toString()));
            try{if(copy!=null&&!folder.equals(copy.getParent())&&Files.isRegularFile(copy)&&Files.size(copy)==size&&!copies.contains(copy))copies.add(copy);}
            catch(IOException|RuntimeException unreadable){}
        }
        return copies;
    }
    public static boolean isImage(Path path) {
        String name=path.getFileName().toString().toLowerCase(Locale.ROOT);
        return (name.endsWith(".png")||name.endsWith(".jpg")||name.endsWith(".jpeg"))&&Files.isRegularFile(path);
    }
    private static Path real(Path path){try{return path.toRealPath();}catch(IOException|SecurityException e){return null;}}
    private static JsonElement readJson(Path file) { return readJson(file,1024*1024); }
    private static JsonElement readJson(Path file,long maxBytes) {
        try{if(!Files.isRegularFile(file)||Files.size(file)>maxBytes)return null;
            try(var reader=Files.newBufferedReader(file)){return JsonParser.parseReader(reader);}
        }catch(IOException|RuntimeException ignored){return null;}
    }
    private static void configRoots(Path file,Set<String> keys,String label,List<Root> roots){
        JsonElement json=readJson(file);if(json!=null)jsonRoots(json,keys,label,roots,0);
    }
    private static void jsonRoots(JsonElement json,Set<String> keys,String label,List<Root> roots,int depth){
        if(depth>8)return;
        if(json.isJsonObject())for(var entry:json.getAsJsonObject().entrySet()) {
            if(keys.contains(entry.getKey())&&entry.getValue().isJsonPrimitive())try {
                Path path=Path.of(entry.getValue().getAsString());if(path.isAbsolute())roots.add(new Root(path,label));
            }catch(RuntimeException ignored){}
            else jsonRoots(entry.getValue(),keys,label,roots,depth+1);
        }
        else if(json.isJsonArray())for(JsonElement item:json.getAsJsonArray())jsonRoots(item,keys,label,roots,depth+1);
    }
    private static void prismRoots(Path config,List<Root> roots){
        try{if(!Files.isRegularFile(config)||Files.size(config)>1024*1024)return;
            for(String line:Files.readAllLines(config))if(line.strip().startsWith("InstanceDir=")) {
                String value=line.strip().substring("InstanceDir=".length()).strip();
                if(value.isBlank())continue;Path path=Path.of(value);if(!path.isAbsolute())path=config.getParent().resolve(path);
                roots.add(new Root(path,"Prism"));
            }
        }catch(IOException|RuntimeException ignored){}
    }
}
