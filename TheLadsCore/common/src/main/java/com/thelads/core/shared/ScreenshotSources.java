package com.thelads.core.shared;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Read-only discovery of screenshot folders. Never traverses world/mod/cache trees or writes to an instance. */
public final class ScreenshotSources {
    public record Root(Path path, String label, boolean directImages) {
        public Root(Path path,String label){this(path,label,false);}
    }
    public record Image(Path path, String source) {}
    private record Visit(Path path, String label, int depth, boolean directImages) {}
    private static final Set<String> SKIP = Set.of("saves","mods","libraries","assets","resourcepacks","shaderpacks","logs","cache","caches","thumbnails","lads-cache-v1",".git",".gradle","versions","natives");
    private ScreenshotSources() {}
    public static Path configFile(Path shared) { return shared.resolve("config/lads-screenshot-sources.json"); }

    public static List<Root> discover(Path shared, Path game, Map<String,String> env, Path home) {
        List<Root> roots=new ArrayList<>();
        roots.add(new Root(shared.resolve("screenshots"),"Shared"));
        roots.add(new Root(game.resolve("screenshots"),"This instance"));
        roots.add(new Root(home.resolve("curseforge/minecraft/Instances"),"CurseForge"));
        roots.add(new Root(home.resolve("Documents/CurseForge/Minecraft/Instances"),"CurseForge"));
        roots.add(new Root(home.resolve("Documents/Curse/Minecraft/Instances"),"CurseForge"));
        for(String key:List.of("APPDATA","LOCALAPPDATA")) {
            String folder=env.get(key);if(folder==null||folder.isBlank())continue;
            Path data=Path.of(folder);
            for(String modrinth:List.of("ModrinthApp","Modrinth App","com.modrinth.theseus")) {
                Path app=data.resolve(modrinth);
                roots.add(new Root(app.resolve("profiles"),"Modrinth"));
                configRoots(app.resolve("settings.json"),Set.of("custom_dir","customDir"),"Modrinth",roots);
            }
            Path prism=data.resolve("PrismLauncher");
            roots.add(new Root(prism.resolve("instances"),"Prism"));
            prismRoots(prism.resolve("prismlauncher.cfg"),roots);
            roots.add(new Root(data.resolve("CurseForge/minecraft/Instances"),"CurseForge"));
            for(String config:List.of("CurseForge/agent-settings.json","CurseForge/settings.json","Overwolf/CurseForge/settings.json"))
                configRoots(data.resolve(config),Set.of("MinecraftFolder","minecraftFolder","minecraftFolderPath","MinecraftFolderPath"),"CurseForge",roots);
        }
        roots.add(new Root(home.resolve(".local/share/PrismLauncher/instances"),"Prism"));
        prismRoots(home.resolve(".local/share/PrismLauncher/prismlauncher.cfg"),roots);
        roots.add(new Root(home.resolve(".local/share/ModrinthApp/profiles"),"Modrinth"));
        roots.addAll(customRoots(shared));
        roots.addAll(readRoots(game.resolve("lads-screenshot-discovered.json"),false));
        JsonElement instances=readJson(game.resolve("lads-world-sources.json"));
        if(instances!=null&&instances.isJsonArray())for(JsonElement item:instances.getAsJsonArray())try {
            JsonObject obj=item.getAsJsonObject();
            roots.add(new Root(Path.of(obj.get("GameDirectory").getAsString()),obj.has("Name")?obj.get("Name").getAsString():"Instance"));
        }catch(RuntimeException ignored){}
        return roots;
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
    public static List<Image> scan(List<Root> roots) { return scan(roots,()->false); }
    public static List<Image> scan(List<Root> roots,java.util.function.BooleanSupplier cancelled) {
        Map<Path,Image> images=new LinkedHashMap<>();Map<Path,Integer> visited=new HashMap<>();ArrayDeque<Visit> queue=new ArrayDeque<>();
        for(Root root:roots)if(root.path()!=null)queue.add(new Visit(root.path(),root.label(),0,root.directImages()));
        int folders=0;
        while(!queue.isEmpty()&&folders<20_000&&images.size()<50_000&&!cancelled.getAsBoolean()&&!Thread.currentThread().isInterrupted()) {
            Visit visit=queue.removeFirst();Path dir=real(visit.path());
            if(dir==null||!Files.isDirectory(dir))continue;
            int allowance=(5-visit.depth())*2+(visit.directImages()?1:0);
            if(visited.getOrDefault(dir,-1)>=allowance)continue;
            visited.put(dir,allowance);folders++;
            boolean screenshotDir=dir.getFileName()!=null&&dir.getFileName().toString().equalsIgnoreCase("screenshots");
            // A directly selected image folder can have any name; descendant folders must be named screenshots.
            boolean accept=screenshotDir||(visit.depth()==0&&visit.directImages());
            try(var entries=Files.newDirectoryStream(dir)) {
                for(Path entry:entries) {
                    if(images.size()>=50_000||cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())break;
                    if(accept&&isImage(entry)) {
                        Path real=real(entry);if(real!=null)images.putIfAbsent(real,new Image(real,visit.label()+" / "+dir));
                    }else if(!screenshotDir&&visit.depth()<5&&queue.size()<20_000&&Files.isDirectory(entry)
                        &&!SKIP.contains(entry.getFileName().toString().toLowerCase(Locale.ROOT)))
                        queue.addLast(new Visit(entry,visit.label(),visit.depth()+1,false));
                }
            }catch(IOException|SecurityException ignored){}
        }
        return new ArrayList<>(images.values());
    }
    public static boolean isImage(Path path) {
        String name=path.getFileName().toString().toLowerCase(Locale.ROOT);
        return (name.endsWith(".png")||name.endsWith(".jpg")||name.endsWith(".jpeg"))&&Files.isRegularFile(path);
    }
    private static Path real(Path path){try{return path.toRealPath();}catch(IOException|SecurityException e){return null;}}
    private static JsonElement readJson(Path file) {
        try{if(!Files.isRegularFile(file)||Files.size(file)>1024*1024)return null;
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
