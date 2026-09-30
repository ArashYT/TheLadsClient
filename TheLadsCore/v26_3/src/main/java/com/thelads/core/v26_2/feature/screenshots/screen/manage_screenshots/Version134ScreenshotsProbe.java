package com.thelads.core.v26_2.feature.screenshots.screen.manage_screenshots;

import com.google.gson.*;
import com.thelads.core.shared.*;
import com.thelads.core.v26_2.feature.GlobalScreenshots;
import com.thelads.core.v26_2.feature.NativeWorldVerification;
import com.thelads.core.v26_2.feature.screenshots.ScreenshotViewer;
import com.thelads.core.v26_2.feature.screenshots.config.*;
import io.github.lgatodu47.catconfig.ConfigOption;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.LoggerFactory;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import javax.imageio.ImageIO;

/** Explicit isolated request only. Exercises real async discovery, gallery textures, and completed GPU readback. */
public final class Version134ScreenshotsProbe {
    private static int state,passed,frames;
    private static long deadline;
    private static Screen previous;
    private static ManageScreenshotsScreen gallery;
    private static Path fixture,capture;
    private static final List<Path> images=new ArrayList<>();
    private static final Map<Path,byte[]> backups=new LinkedHashMap<>();
    private static final Map<ConfigOption<?>,Object> options=new LinkedHashMap<>();
    private static Map<?,?> originalProvenance;
    private static volatile boolean captured;
    private static volatile Throwable captureFailure;
    private Version134ScreenshotsProbe() {}
    public static void tick() {
        if(state<0||!NativeWorldVerification.active()||!ScreenshotViewer.available())return;
        Minecraft mc=Minecraft.getInstance();
        try {
            if(state==0) {
                Path request=mc.gameDirectory.toPath().resolve(".lads-qa-screenshots134");
                if(!Files.isRegularFile(request,LinkOption.NOFOLLOW_LINKS)||mc.level==null||mc.gui.screen()!=null)return;
                Path game=mc.gameDirectory.toPath().toRealPath(),verification=game.getParent();
                if(!verification.getFileName().toString().equals("verification")||!verification.getParent().getFileName().toString().equals("artifacts")
                    ||!SharedContentPaths.redirectedInside(verification))throw new IllegalStateException("Screenshot probe requires isolated game and shared roots");
                Files.delete(request);begin(mc,game,verification);deadline=System.nanoTime()+90_000_000_000L;state=1;
                return;
            }
            if(System.nanoTime()>deadline)throw new IllegalStateException("Async screenshot discovery/GPU capture timed out");
            if(state==1) {
                ScreenshotList list=field(gallery,"list");if(list.scanning())return;
                List<ScreenshotWidget> widgets=new ArrayList<>();
                for(Path image:images){var match=list.findByFileName(image.toFile());if(match.isEmpty())throw new IllegalStateException("Missing external fixture "+image);widgets.add((ScreenshotWidget)match.get());}
                if(widgets.stream().anyMatch(widget->widget.image()==null||widget.textureId()==null))return;
                check(widgets.stream().map(ScreenshotWidget::getScreenshotFile).distinct().count()==4,"identical filenames from four folders retain separate entries");
                for(int i=0;i<widgets.size();i++){
                    var widget=widgets.get(i);
                    check(widget.image().getWidth()==64&&widget.image().getHeight()==36,"external PNG decoded at full resolution "+i);
                    check(widget.image().getPixel(3,3)==0xff305070+i,"external original pixels survive GPU image loading "+i);
                    check(mc.getTextureManager().getTexture(widget.textureId())!=null,"external GPU texture registered "+i);
                    check(GlobalScreenshots.readOnly(widget.getScreenshotFile()),"external provenance is read-only "+i);
                }
                var provenance=GlobalScreenshots.class.getDeclaredField("external");provenance.setAccessible(true);
                originalProvenance=(Map<?,?>)provenance.get(null);provenance.set(null,Map.of());
                check(widgets.stream().allMatch(widget->GlobalScreenshots.readOnly(widget.getScreenshotFile())),"stale or empty scan metadata cannot enable destructive actions");
                var first=widgets.getFirst();check(gallery.isShowing(first),"chat image opens after asynchronous scan completes");
                // F11/resize re-inits the screen: it must re-lay out the same widgets, not rescan and orphan the enlarged one (1.3.4 audit).
                Integer revision=field(list,"scanRevision");
                gallery.resize(mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
                ScreenshotList resized=field(gallery,"list");Integer after=field(resized,"scanRevision");
                check(resized==list&&!resized.scanning()&&after.equals(revision),"window resize re-lays out the gallery without rescanning");
                check(gallery.isShowing(first)&&resized.findByFileName(images.getFirst().toFile()).orElse(null)==first&&first.textureId()!=null,"enlarged screenshot survives a window resize");
                first.requestFileDeletion();first.renameFile();first.deleteScreenshot();
                check(field(gallery,"dialogScreen")==null&&Files.isRegularFile(images.getFirst()),"external delete/rename cannot mutate originals");
                gallery.showScreenshotProperties(20,40,first);ScreenshotPropertiesMenu properties=field(gallery,"screenshotProperties");
                List<AbstractWidget> buttons=field(properties,"buttons");check(!buttons.get(2).active&&!buttons.get(3).active,"external destructive controls disabled");properties.hide();
                check(gallery.children().stream().anyMatch(c->c instanceof AbstractWidget w&&w.getMessage().getString().equals("Add folder")),"native Add folder entry visible");
                gallery.enlargeScreenshot(null);state=2;frames=gallery.extractedFrames();return;
            }
            if(state==2&&gallery.extractedFrames()-frames>=3) {
                state=3;
                net.minecraft.client.Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(),image->{
                    try{image.writeToFile(capture);}catch(Throwable failure){captureFailure=failure;}
                    finally{image.close();captured=true;}
                });
            }else if(state==3&&captured) {
                if(captureFailure!=null)throw new IllegalStateException("Gallery framebuffer capture failed",captureFailure);
                check(Files.size(capture)>1000,"completed gallery framebuffer saved");
                ScreenshotViewer.getInstance().getConfig().put(ScreenshotViewerOptions.SCREENSHOTS_FOLDER,images.getFirst().getParent().toFile());
                gallery=new ManageScreenshotsScreen(previous);mc.setScreenAndShow(gallery);state=4;return;
            }else if(state==4) {
                ScreenshotList configured=field(gallery,"list");
                check(configured.size()==1,"explicit external folder uses its configured inventory");
                ScreenshotWidget widget=(ScreenshotWidget)configured.getScreenshot(0);
                if(widget.image()==null||widget.textureId()==null){passed--;return;}
                check(GlobalScreenshots.readOnly(widget.getScreenshotFile()),"configured external folder stays read-only without scan metadata");
                widget.requestFileDeletion();widget.renameFile();widget.deleteScreenshot();
                check(field(gallery,"dialogScreen")==null&&Files.isRegularFile(images.getFirst()),"configured folder delete and rename preserve external originals");
                restore(mc);state=-1;
                LoggerFactory.getLogger("TheLadsCore").info("Lads 1.3.4 screenshots probe END: {} passed, 0 failed; real async gallery/GPU at {}",passed,capture);
            }
        }catch(Throwable failure){
            LoggerFactory.getLogger("TheLadsCore").error("Lads 1.3.4 screenshots probe FAILED after {} checks",passed,failure);
            try{restore(mc);}catch(Throwable restore){failure.addSuppressed(restore);}state=-1;
        }
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void begin(Minecraft mc,Path game,Path verification) throws Exception {
        previous=mc.gui.screen();fixture=Files.createTempDirectory(verification,"screenshots134-");
        Path discovered=game.resolve("lads-screenshot-discovered.json"),custom=ScreenshotSources.configFile(SharedContentPaths.root());
        backups.put(discovered,Files.exists(discovered)?Files.readAllBytes(discovered):null);
        backups.put(custom,Files.exists(custom)?Files.readAllBytes(custom):null);
        String[] names={"Modrinth/profiles/Pack/screenshots","CurseForge/Instances/Pack/screenshots","Prism/instances/Pack/.minecraft/screenshots","My captures"};
        JsonObject json=new JsonObject();json.addProperty("version",1);JsonArray roots=new JsonArray();
        for(int i=0;i<names.length;i++) {
            Path file=fixture.resolve(names[i]).resolve("same.png");Files.createDirectories(file.getParent());
            BufferedImage image=new BufferedImage(64,36,BufferedImage.TYPE_INT_RGB);
            for(int y=0;y<36;y++)for(int x=0;x<64;x++)image.setRGB(x,y,0xff305070+i);
            ImageIO.write(image,"png",file.toFile());image.flush();Files.setLastModifiedTime(file,FileTime.fromMillis(4_102_444_800_000L+i));images.add(file.toRealPath());
            if(i<3){JsonObject root=new JsonObject();root.addProperty("path",fixture.resolve(names[i].substring(0,names[i].indexOf('/'))).toString());root.addProperty("label",names[i].substring(0,names[i].indexOf('/')));roots.add(root);}
        }
        json.add("roots",roots);Files.writeString(discovered,json.toString());
        Files.createDirectories(custom.getParent());Files.writeString(custom,"{\"version\":1,\"roots\":[]}");
        ScreenshotSources.addCustomRoot(SharedContentPaths.root(),images.get(3).getParent());
        var config=ScreenshotViewer.getInstance().getConfig();
        for(var option:List.of(ScreenshotViewerOptions.SCREENSHOTS_FOLDER,ScreenshotViewerOptions.DEFAULT_LIST_ORDER,ScreenshotViewerOptions.INITIAL_SCREENSHOT_AMOUNT_PER_ROW))
            options.put(option,config.get((ConfigOption)option).orElse(null));
        config.put(ScreenshotViewerOptions.SCREENSHOTS_FOLDER,GlobalScreenshots.directory());config.put(ScreenshotViewerOptions.DEFAULT_LIST_ORDER,ScreenshotListOrder.DESCENDING);
        config.put(ScreenshotViewerOptions.INITIAL_SCREENSHOT_AMOUNT_PER_ROW,4);
        Path output=game.resolve("screenshots");Files.createDirectories(output);capture=output.resolve("native-screenshots134-"+System.currentTimeMillis()+".png");
        gallery=new ManageScreenshotsScreen(previous,images.getFirst().toFile());mc.setScreenAndShow(gallery);
        LoggerFactory.getLogger("TheLadsCore").info("Lads 1.3.4 screenshots probe BEGIN: isolated external fixtures at {}",fixture);
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void restore(Minecraft mc) throws Exception {
        if(gallery!=null&&mc.gui.screen()==gallery)mc.setScreenAndShow(previous);
        if(ScreenshotViewer.available())options.forEach((option,value)->ScreenshotViewer.getInstance().getConfig().put((ConfigOption)option,value));
        for(var backup:backups.entrySet())if(backup.getValue()==null)Files.deleteIfExists(backup.getKey());else Files.write(backup.getKey(),backup.getValue());
        if(originalProvenance!=null){var field=GlobalScreenshots.class.getDeclaredField("external");field.setAccessible(true);field.set(null,originalProvenance);originalProvenance=null;}
        backups.clear();options.clear();
    }
    @SuppressWarnings("unchecked") private static <T>T field(Object instance,String name)throws Exception{var f=instance.getClass().getDeclaredField(name);f.setAccessible(true);return (T)f.get(instance);}
    private static void check(boolean valid,String description){if(!valid)throw new IllegalStateException(description);passed++;}
}
