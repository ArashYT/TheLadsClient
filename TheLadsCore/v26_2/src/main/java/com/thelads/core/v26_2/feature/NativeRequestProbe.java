package com.thelads.core.v26_2.feature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.options.*;
import net.minecraft.client.gui.screens.options.controls.*;
import net.minecraft.client.gui.screens.packs.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.client.telemetry.*;
import com.thelads.core.v26_2.mixin.BossBarAccessor;
import java.util.*;
public final class NativeRequestProbe {
    public static int run() throws Exception {
        var mc=Minecraft.getInstance();var original=mc.gui.screen();int passed=0;
        try{
            var expectedRenderer=Boolean.getBoolean("thelads.verify134")?switch(System.getProperty("thelads.verifyRenderer","vulkan").toLowerCase(Locale.ROOT)){
                case "opengl" -> net.minecraft.client.PreferredGraphicsApi.OPENGL;
                case "default" -> net.minecraft.client.PreferredGraphicsApi.DEFAULT;
                default -> net.minecraft.client.PreferredGraphicsApi.VULKAN;
            }:GraphicsCompatibility.firstLaunchApi();
            require(mc.options.preferredGraphicsBackend().get()==expectedRenderer,"launch respects the requested renderer after option migrations");passed++;
            if(Boolean.getBoolean("thelads.verify134"))passed+=rendererMigration();
            require(mc.getTelemetryManager().getOutsideSessionSender()==TelemetryEventSender.DISABLED,"telemetry event sender disabled");passed++;
            var logs=ClientTelemetryManager.class.getDeclaredField("logManager");logs.setAccessible(true);
            require(((java.util.concurrent.CompletableFuture<java.util.Optional<?>>)logs.get(mc.getTelemetryManager())).join().isEmpty(),"telemetry log creation disabled");passed++;
            Screen pause=new PauseScreen(true);mc.setScreenAndShow(pause);
            for(String key:List.of("menu.sendFeedback","menu.reportBugs","menu.playerReporting")){
                require(pause.children().stream().noneMatch(c->c instanceof AbstractWidget w&&w.getMessage().getString().equals(Component.translatable(key).getString())),"pause removes "+key);passed++;
            }
            var multiplayer=pause.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().equals(Component.translatable("menu.multiplayer").getString())).map(c->(Button)c).findFirst().orElseThrow();
            require(multiplayer.active,"pause exposes Multiplayer action");passed++;
            if(mc.allowsMultiplayer()&&mc.level!=null) {
                var levelBefore=mc.level;multiplayer.onPress(null);
                require(mc.gui.screen() instanceof ConfirmScreen,"Multiplayer asks before leaving the world");passed++;
                require(mc.level==levelBefore,"opening Multiplayer confirmation preserves the loaded world");passed++;
                var stay=mc.gui.screen().children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().equals("Stay in game")).map(c->(Button)c).findFirst().orElseThrow();
                stay.onPress(null);require(mc.level==levelBefore&&mc.gui.screen()==pause,"cancel returns to pause without disconnecting");passed++;
            }
            passed += NativeImprovementsProbe.run();
            if(Boolean.getBoolean("thelads.verify133"))passed+=Version133Probe.run();
            passed+=swing();
            passed+=BorderlessProbe.run();
            Screen skin=new SkinCustomizationScreen(original,mc.options);mc.setScreenAndShow(skin);
            require(skin.children().stream().anyMatch(c->c instanceof PlayerSkinWidget),"custom skin contains real 3D skin widget");passed++;
            extract(skin);passed++;
            Screen keys=new KeyBindsScreen(original,mc.options);mc.setScreenAndShow(keys);
            // Exercise the screen the player actually sees with the complete mod pack loaded.
            keys=mc.gui.screen();
            require(keys instanceof com.thelads.core.v26_2.gui.LadsKeyBindsScreen,"active controls screen uses native filters");passed++;
            var search=keys.children().stream().filter(c->c instanceof EditBox).map(c->(EditBox)c).findFirst().orElseThrow();
            var list=keys.children().stream().filter(c->c instanceof KeyBindsList).map(c->(KeyBindsList)c).findFirst().orElseThrow();
            int total=list.children().size();
            keys.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(search.getX()+8,search.getY()+8,new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),false);
            for(char c:"Lads".toCharArray()) keys.charTyped(new net.minecraft.client.input.CharacterEvent(c));
            require(search.getValue().equals("Lads"),"mouse focus and typed characters reach controls search");passed++;
            require(!list.children().isEmpty()&&list.children().size()<total,"controls search filters real bindings");passed++;
            search.setValue("no_control_should_match_this_309127");require(list.children().isEmpty(),"controls search empty state");passed++;
            search.setValue("");require(list.children().size()==total,"clearing search restores bindings");passed++;extract(keys);
            var mode=keys.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().startsWith("Search:")).map(c->(Button)c).findFirst().orElseThrow();
            for(String label:List.of("Name","Keybind","Category","Mod","All")){
                mode.onPress(null);require(mode.getMessage().getString().equals("Search: "+label),"visible search mode "+label);passed++;
            }
            list.setScrollAmount(Math.min(260,list.maxScrollAmount()));double scrollBefore=list.scrollAmount();
            require(scrollBefore>0,"controls regression fixture is scrolled down");passed++;
            extract(keys);
            keys.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(1,1,new net.minecraft.client.input.MouseButtonInfo(0,0)),false);
            require(list.scrollAmount()==scrollBefore,"ordinary controls click keeps scroll position");passed++;
            var entry=list.children().stream().filter(c->c instanceof KeyBindsList.KeyEntry).findFirst().orElseThrow();
            var key=((com.thelads.core.v26_2.mixin.KeyEntryAccessor)entry).ladsKey();
            var keyField=net.minecraft.client.KeyMapping.class.getDeclaredField("key");keyField.setAccessible(true);
            var oldKey=(com.mojang.blaze3d.platform.InputConstants.Key)keyField.get(key);
            var changeField=KeyBindsList.KeyEntry.class.getDeclaredField("changeButton");changeField.setAccessible(true);
            try {
                ((Button)changeField.get(entry)).onPress(null);
                keys.keyPressed(new net.minecraft.client.input.KeyEvent(290,0,0));
                require(list.scrollAmount()==scrollBefore,"assigning a key keeps controls scroll position");passed++;
            }finally{key.setKey(oldKey);net.minecraft.client.KeyMapping.resetMapping();list.resetMappingAndUpdateButtons();mc.options.save();}
            keys.init(mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
            require(keys.children().stream().filter(c->c instanceof EditBox).count()==1,"resize leaves exactly one search field");passed++;

            Screen video=new VideoSettingsScreen(original,mc,mc.options);mc.setScreenAndShow(video);
            extract(mc.gui.screen());passed++;
            Screen packs=new PackSelectionScreen(mc.getResourcePackRepository(),repository->{},mc.getResourcePackDirectory(),Component.literal("Resource packs"));mc.setScreenAndShow(packs);
            require(packs.children().stream().anyMatch(c->c instanceof AbstractWidget w&&w.getMessage().getString().equals("Version: all")),"resource pack compatibility filter is installed");passed++;
            extract(packs);passed++;
            var global=GlobalScreenshots.directory();require(global.getName().equals("screenshots")&&!global.toPath().startsWith(mc.gameDirectory.toPath()),"screenshots use global location");passed++;
            require(com.thelads.core.v26_2.feature.screenshots.ScreenshotViewerUtils.getVanillaScreenshotsFolder().equals(global),"gallery shares global screenshot location");passed++;
            var overlay=(BossBarAccessor)mc.gui.hud.getBossOverlay();var graphics=new GuiGraphicsExtractor(mc,new GuiRenderState(),0,0);
            var adapter=new com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter(graphics);adapter.drawBossBars(10,10,3,true,true);passed++;
            var motion=new LegacyVerticalBob();float a=motion.sample(1,.4,false,1,true);float expected=(float)(Math.atan(-.4*.2)*15*.8);
            require(Math.abs(a-expected)<.00001,"legacy airborne pitch formula");passed++;
            require(motion.sample(1,.4,false,1,true)==a,"render frames do not advance tick smoothing");passed++;
            require(motion.sample(2,0,true,1,true)<0&&Math.abs(motion.sample(2,0,true,1,true))<Math.abs(a),"landing eases pitch to zero");passed++;
            require(motion.sample(3,.4,false,1,false)==0,"disabled bob resets immediately");passed++;
            org.slf4j.LoggerFactory.getLogger("TheLadsCore").info("Lads requested features probe END: {} passed, 0 failed",passed);
            return passed;
        }finally{mc.setScreenAndShow(original);}
    }
    private static int rendererMigration()throws Exception{
        var mc=Minecraft.getInstance();
        NativeWorldVerification.checkedGameDirectory(mc.gameDirectory.toPath());
        var options=mc.options;
        var fileField=net.minecraft.client.Options.class.getDeclaredField("optionsFile");fileField.setAccessible(true);
        var path=((java.io.File)fileField.get(options)).toPath();
        var startupField=net.minecraft.client.Options.class.getDeclaredField("preferredGraphicsBackendFromStartup");startupField.setAccessible(true);
        var preference=options.preferredGraphicsBackend().get();var startup=startupField.get(options);
        byte[] disk=java.nio.file.Files.exists(path)?java.nio.file.Files.readAllBytes(path):null;
        byte[] memory=null;int passed=0;
        try{
            // Save the live values before loading versionless fixtures; finally restores both
            // the live settings and exact original file bytes, including a saved crash fallback.
            options.save();memory=java.nio.file.Files.readAllBytes(path);
            String fixture=new String(memory,java.nio.charset.StandardCharsets.UTF_8).lines()
                .filter(line->!line.startsWith("version:")&&!line.startsWith("preferredGraphicsBackend:"))
                .collect(java.util.stream.Collectors.joining("\n","","\n"));
            for(String name:List.of("vulkan","opengl","default","missing")){
                var expected=switch(name){
                    case "vulkan" -> net.minecraft.client.PreferredGraphicsApi.VULKAN;
                    case "opengl" -> net.minecraft.client.PreferredGraphicsApi.OPENGL;
                    case "default" -> net.minecraft.client.PreferredGraphicsApi.DEFAULT;
                    default -> GraphicsCompatibility.firstLaunchApi();
                };
                java.nio.file.Files.writeString(path,fixture+(name.equals("missing")?"":"preferredGraphicsBackend:\""+name+"\"\n"));
                options.load();
                require(options.preferredGraphicsBackend().get()==expected,"versionless migration preserves "+name+" renderer preference");passed++;
                require(startupField.get(options)==expected,"versionless migration preserves "+name+" startup renderer");passed++;
            }
        }finally{
            try{if(memory!=null){java.nio.file.Files.write(path,memory);options.load();}}
            finally{
                options.preferredGraphicsBackend().set(preference);startupField.set(options,startup);
                if(disk==null)java.nio.file.Files.deleteIfExists(path);else java.nio.file.Files.write(path,disk);
            }
        }
        org.slf4j.LoggerFactory.getLogger("TheLadsCore").info("Lads 1.3.4 renderer options migration END: {} passed, 0 failed",passed);
        return passed;
    }
    private static int swing()throws Exception{
        var module=NativeQualityOfLife.module("LegacySwing");boolean enabled=module.isEnabled();long modified=module.getLastModified();
        var method=net.minecraft.client.renderer.ItemInHandRenderer.class.getDeclaredMethod("swingArm",float.class,com.mojang.blaze3d.vertex.PoseStack.class,int.class,net.minecraft.world.entity.HumanoidArm.class);method.setAccessible(true);
        Object renderer=Minecraft.getInstance().gameRenderer.itemInHandRenderer;
        try{
            module.setEnabled(false);var vanilla=new com.mojang.blaze3d.vertex.PoseStack();method.invoke(renderer,.5F,vanilla,1,net.minecraft.world.entity.HumanoidArm.RIGHT);
            module.setEnabled(true);var console=new com.mojang.blaze3d.vertex.PoseStack();method.invoke(renderer,.5F,console,1,net.minecraft.world.entity.HumanoidArm.RIGHT);
            require(!console.last().pose().equals(vanilla.last().pose()),"actual held-item renderer switches console animation");
            require(Math.abs(console.last().pose().m30()+Math.sin(Math.PI*.25)*.55)<.001,"reference swing translation at half progress");
            var left=new com.mojang.blaze3d.vertex.PoseStack();method.invoke(renderer,.5F,left,-1,net.minecraft.world.entity.HumanoidArm.LEFT);
            require(Math.abs(left.last().pose().m30()+console.last().pose().m30())<.001,"console swing mirrors left hand");
            module.setEnabled(false);var restored=new com.mojang.blaze3d.vertex.PoseStack();method.invoke(renderer,.5F,restored,1,net.minecraft.world.entity.HumanoidArm.RIGHT);
            require(restored.last().pose().equals(vanilla.last().pose()),"disabling module restores vanilla swing");return 4;
        }finally{module.setEnabled(enabled);module.setLastModified(modified);}
    }
    private static void extract(Screen screen){var mc=Minecraft.getInstance();screen.extractRenderState(new GuiGraphicsExtractor(mc,new GuiRenderState(),0,0),0,0,0);}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
