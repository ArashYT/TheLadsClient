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
            require(mc.options.preferredGraphicsBackend().get()==GraphicsCompatibility.firstLaunchApi(),"first launch respects renderer compatibility after option migrations");passed++;
            require(mc.getTelemetryManager().getOutsideSessionSender()==TelemetryEventSender.DISABLED,"telemetry event sender disabled");passed++;
            var logs=ClientTelemetryManager.class.getDeclaredField("logManager");logs.setAccessible(true);
            require(((java.util.concurrent.CompletableFuture<java.util.Optional<?>>)logs.get(mc.getTelemetryManager())).join().isEmpty(),"telemetry log creation disabled");passed++;
            Screen pause=new PauseScreen(true);mc.setScreenAndShow(pause);
            for(String key:List.of("menu.sendFeedback","menu.reportBugs","menu.playerReporting")){
                require(pause.children().stream().noneMatch(c->c instanceof AbstractWidget w&&w.getMessage().getString().equals(Component.translatable(key).getString())),"pause removes "+key);passed++;
            }
            passed+=swing();
            Screen skin=new SkinCustomizationScreen(original,mc.options);mc.setScreenAndShow(skin);
            require(skin.children().stream().anyMatch(c->c instanceof PlayerSkinWidget),"custom skin contains real 3D skin widget");passed++;
            extract(skin);passed++;
            Screen keys=new KeyBindsScreen(original,mc.options);mc.setScreenAndShow(keys);
            // Controlling may replace the active screen; initialize the native fallback explicitly as well.
            if(mc.gui.screen()!=keys)keys.init(mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
            var search=keys.children().stream().filter(c->c instanceof EditBox).map(c->(EditBox)c).findFirst().orElseThrow();
            var list=keys.children().stream().filter(c->c instanceof KeyBindsList).map(c->(KeyBindsList)c).findFirst().orElseThrow();
            int total=list.children().size();search.setValue("Lads");require(!list.children().isEmpty()&&list.children().size()<total,"controls search filters real bindings");passed++;
            search.setValue("no_control_should_match_this_309127");require(list.children().isEmpty(),"controls search empty state");passed++;
            search.setValue("");require(list.children().size()==total,"clearing search restores bindings");passed++;extract(keys);
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
    private static int swing()throws Exception{
        var module=NativeQualityOfLife.module("LegacySwing");boolean enabled=module.isEnabled();long modified=module.getLastModified();
        var method=net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer.class.getDeclaredMethod("swingArm",float.class,com.mojang.blaze3d.vertex.PoseStack.class,int.class,net.minecraft.world.entity.HumanoidArm.class);method.setAccessible(true);
        Object renderer=Minecraft.getInstance().gameRenderer.firstPersonHandsAndItemsRenderer;
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
