package com.thelads.core.v26_2.feature;

import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.LoggerFactory;

/** Explicit isolated record/replay/export QA against the actual optional Flashback engine. */
public final class Version134ReplayProbe {
    private static boolean registered,done,quicksaveChanged;
    private static int stage,ticks,passed;
    private static long deadline;
    private static Path game,replayFolder,replay,output;
    private static Set<Path> previousReplays;
    private static Class<?> flashback;
    private static Object recordingControls;
    private static boolean quicksave;
    private static float originalYaw;
    private Version134ReplayProbe(){}
    public static void register(){
        if(registered||!Boolean.getBoolean("thelads.verify134"))return;
        registered=true;ClientTickEvents.END_CLIENT_TICK.register(mc->tick());
    }
    private static void tick(){
        if(done)return;
        var mc=Minecraft.getInstance();
        try{
            if(stage==0){
                Path request=mc.gameDirectory.toPath().resolve(".lads-qa-replay");
                if(!Files.isRegularFile(request,LinkOption.NOFOLLOW_LINKS)||!NativeWorldVerification.worldReady())return;
                game=NativeWorldVerification.checkedGameDirectory(mc.gameDirectory.toPath());
                Files.delete(request);
                check(FabricLoader.getInstance().isModLoaded("flashback"),"official Flashback engine loaded");
                flashback=Class.forName("com.moulberry.flashback.Flashback");
                check(flashback.getField("RECORDER").get(null)==null,"no existing recording is active");
                replayFolder=((Path)call("getReplayFolder")).toAbsolutePath().normalize();
                check(replayFolder.startsWith(game),"recordings stay inside isolated game directory");
                Files.createDirectories(replayFolder);check(replayFolder.toRealPath().startsWith(game),"recordings directory has no external link");
                previousReplays=zips();
                Object config=call("getConfig");recordingControls=config.getClass().getField("recordingControls").get(config);
                var quick=recordingControls.getClass().getField("quicksave");quicksave=quick.getBoolean(recordingControls);quick.setBoolean(recordingControls,true);quicksaveChanged=true;
                originalYaw=mc.player.getYRot();call("startRecordingReplay");
                check(flashback.getField("RECORDER").get(null)!=null,"production start action creates recorder");
                stage=1;deadline=System.nanoTime()+120_000_000_000L;
                log("Lads 1.3.4 replay probe BEGIN: recording 80 real world ticks in "+game);
                return;
            }
            if(System.nanoTime()>deadline)throw new IllegalStateException("Flashback QA timed out at stage "+stage);
            if(stage==1){
                if(!NativeWorldVerification.worldReady())return;
                mc.player.setYRot(originalYaw+Math.min(40,++ticks)*.5f);
                if(ticks<80)return;
                var recorder=flashback.getField("RECORDER").get(null);check(recorder!=null,"recorder survives actual client ticks");
                var written=recorder.getClass().getDeclaredField("writtenTicks");written.setAccessible(true);
                check(written.getInt(recorder)>=40,"recorded timeline contains real ticks");
                call("finishRecordingReplay");mc.player.setYRot(originalYaw);restoreQuicksave();
                check(flashback.getField("RECORDER").get(null)==null,"production stop action finishes recording");
                stage=2;return;
            }
            if(stage==2){
                var created=zips();created.removeAll(previousReplays);if(created.isEmpty())return;
                check(created.size()==1,"stop created one new saved replay");replay=created.iterator().next();
                check(Files.size(replay)>1024,"saved replay contains packet data");
                try(var zip=new ZipFile(replay.toFile())){
                    var entry=zip.getEntry("metadata.json");check(entry!=null,"saved replay has metadata");
                    try(var reader=new java.io.InputStreamReader(zip.getInputStream(entry),java.nio.charset.StandardCharsets.UTF_8)){
                        var metadata=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                        check(metadata.get("total_ticks").getAsInt()>=40,"saved metadata retains recorded duration");
                    }
                }
                stage=3;deadline=System.nanoTime()+120_000_000_000L;
                log("Lads 1.3.4 replay probe SAVED: "+replay);
                flashback.getMethod("openReplayWorld",Path.class).invoke(null,replay);return;
            }
            if(stage==3){
                var server=call("getReplayServer");if(server==null||mc.level==null||mc.player==null)return;
                if(!(boolean)server.getClass().getMethod("isReady").invoke(server))return;
                check((boolean)call("isInReplay"),"saved replay opened through the actual replay server");
                int total=(int)server.getClass().getMethod("getTotalReplayTicks").invoke(server);check(total>=40,"replay server loaded the recorded timeline");
                check(!server.getClass().getField("failedToLoadRegistryDataWarning").getBoolean(server),"replay registry data loaded");
                check(!server.getClass().getField("failedToSpawnPlayerWarning").getBoolean(server),"replay player spawned");
                output=game.resolve("flashback/qa-export-"+System.currentTimeMillis());Files.createDirectory(output);
                queueExport(server,mc);stage=4;deadline=System.nanoTime()+180_000_000_000L;
                log("Lads 1.3.4 replay probe OPENED: actual ReplayServer; exporting 320x180 PNG frames to "+output);return;
            }
            if(stage==4){
                if(flashback.getField("EXPORT_JOB").get(null)!=null)return;
                List<Path> frames;try(var files=Files.list(output)){frames=files.filter(p->p.getFileName().toString().endsWith(".png")).sorted().toList();}
                check(frames.size()>=10,"export produced a real short frame sequence");
                for(Path frame:frames){var image=javax.imageio.ImageIO.read(frame.toFile());check(image!=null&&image.getWidth()==320&&image.getHeight()==180,"export frame decodes at requested dimensions");}
                var first=javax.imageio.ImageIO.read(frames.getFirst().toFile());var colors=new HashSet<Integer>();
                for(int y=0;y<first.getHeight();y+=3)for(int x=0;x<first.getWidth();x+=3)colors.add(first.getRGB(x,y));
                check(colors.size()>32,"export contains rendered world detail rather than a flat frame");
                Files.writeString(game.resolve(".lads-qa-replay-done"),"replay="+replay+"\nframes="+output+"\ncount="+frames.size()+"\n");
                done=true;log("Lads 1.3.4 replay probe END: "+passed+" passed, 0 failed; recorded, reopened, exported and decoded "+frames.size()+" actual world frames");
            }
        }catch(Throwable failure){
            done=true;try{restoreQuicksave();if(stage==1&&flashback!=null){call("finishRecordingReplay");if(mc.player!=null)mc.player.setYRot(originalYaw);}}catch(Throwable suppressed){failure.addSuppressed(suppressed);}
            try{if(game!=null)Files.writeString(game.resolve(".lads-qa-replay-failed"),failure.toString());}catch(Exception ignored){}
            LoggerFactory.getLogger("TheLadsCore").error("Lads 1.3.4 replay probe FAILED at stage "+stage,failure);
        }
    }
    private static void queueExport(Object server,Minecraft mc)throws Exception{
        check(flashback.getField("EXPORT_JOB").get(null)==null,"no unrelated export is active");
        var values=new HashMap<String,Object>();
        values.put("name","Lads isolated replay QA");values.put("editorState",server.getClass().getMethod("getEditorState").invoke(server));
        values.put("initialCameraPosition",mc.player.position().add(0,mc.player.getEyeHeight(),0));values.put("initialCameraYaw",mc.player.getYRot());values.put("initialCameraPitch",mc.player.getXRot());
        values.put("resolutionX",320);values.put("resolutionY",180);values.put("startTick",5);values.put("endTick",25);
        values.put("projection",constant("ExportProjection","PERSPECTIVE"));values.put("orthographicZoom",1f);values.put("framerate",12d);
        values.put("resetRng",true);values.put("depthMap",false);values.put("container",constant("VideoContainer","PNG_SEQUENCE"));values.put("codec",constant("VideoCodec","PNG"));
        values.put("encoder","png");values.put("bitrate",1_000_000);values.put("transparent",false);values.put("ssaa",false);values.put("noGui",true);values.put("stereoAudio",false);
        values.put("audioCodec",null);values.put("output",output);values.put("pngSequenceFormat","frame-%04d");
        var type=Class.forName("com.moulberry.flashback.exporting.ExportSettings");var fields=type.getRecordComponents();
        Class<?>[] types=new Class<?>[fields.length];Object[] args=new Object[fields.length];
        for(int i=0;i<fields.length;i++){String name=fields[i].getName();if(!values.containsKey(name))throw new IllegalStateException("Unknown Flashback export setting: "+name);types[i]=fields[i].getType();args[i]=values.get(name);}
        Object settings=type.getConstructor(types).newInstance(args);
        var job=Class.forName("com.moulberry.flashback.exporting.ExportJob").getConstructor(type).newInstance(settings);
        flashback.getField("EXPORT_JOB").set(null,job);
    }
    private static Object constant(String type,String name)throws Exception{return Class.forName("com.moulberry.flashback.combo_options."+type).getField(name).get(null);}
    private static Object call(String name)throws Exception{return flashback.getMethod(name).invoke(null);}
    private static Set<Path> zips()throws Exception{try(var files=Files.list(replayFolder)){return new HashSet<>(files.filter(p->Files.isRegularFile(p)&&p.getFileName().toString().endsWith(".zip")).toList());}}
    private static void restoreQuicksave()throws Exception{if(quicksaveChanged){recordingControls.getClass().getField("quicksave").setBoolean(recordingControls,quicksave);quicksaveChanged=false;}}
    private static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);passed++;}
    private static void log(String message){LoggerFactory.getLogger("TheLadsCore").info(message);}
}
