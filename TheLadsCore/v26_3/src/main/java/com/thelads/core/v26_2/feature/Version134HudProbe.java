package com.thelads.core.v26_2.feature;

import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.config.*;
import com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter;
import com.thelads.core.v26_2.feature.paperdoll.PaperDollRenderState;
import com.thelads.core.v26_2.gui.DraggableHudScreen26;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.*;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;

/** Render-bound checks run only after the isolated-world guard in Version133Probe. */
final class Version134HudProbe {
    private static int passed;
    static int run() throws Exception {
        passed=0;var mc=Minecraft.getInstance();var snapshot=ConfigManager.toJson();var screen=mc.gui.screen();
        var boots=mc.player.getItemBySlot(EquipmentSlot.FEET).copy();
        var bridge=com.thelads.core.client.bridge.LadsGameBridge.get();
        try{
            com.thelads.core.client.bridge.LadsGameBridge.set(new com.thelads.core.v26_2.adapter.VanillaGameBridge26());
            HudSettings.getInstance().replaceGroups(java.util.List.of());HudSettings.getInstance().clearPositions();
            for(var element:HudManager.getInstance().getElements())NativeQualityOfLife.module(element.getModuleName()).setEnabled(false);
            for(String name:java.util.List.of("FPS","ArmorHUD","Paperdoll","Autohide"))NativeQualityOfLife.module(name).setEnabled(true);
            HudSettings.getInstance().setBackgrounds(true);HudSettings.getInstance().setGlobalBackground(0xAA000000);
            ((ColorOption)NativeQualityOfLife.module("FPS").getOption("Background")).setUseGlobal(true);
            ((BoolOption)NativeQualityOfLife.module("Paperdoll").getOption("Always Display")).set(true);
            ((BoolOption)NativeQualityOfLife.module("Paperdoll").getOption("Show in First Person")).set(true);
            ((BoolOption)NativeQualityOfLife.module("Paperdoll").getOption("Show in Third Person")).set(true);
            ((SliderOption)NativeQualityOfLife.module("Paperdoll").getOption("Model Opacity")).setValue(100);
            ((BoolOption)NativeQualityOfLife.module("Autohide").getOption("Show when hurt or hungry")).set(false);
            ((BoolOption)NativeQualityOfLife.module("Autohide").getOption("Show while moving")).set(false);
            ((SliderOption)NativeQualityOfLife.module("Autohide").getOption("Fade milliseconds")).setValue(0);
            mc.player.setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.DIAMOND_BOOTS));mc.setScreenAndShow(null);
            NativeAutohide.update();set("activity",System.nanoTime()-60_000_000_000L);
            var state=new GuiRenderState();var graphics=new GuiGraphicsExtractor(mc,state,0,0);
            NativeAutohide.renderLadsHud(graphics);
            check(count(state)==0,"idle Lads text, backgrounds, armor and paper doll all disappear");
            check(NativeAutohide.scopeOpacity==1,"Lads fade restores the GUI scope");
            // Fade back in from fully hidden with calls microseconds apart (far above 143 FPS, where 1.3.4 snapped every step back to 0).
            ((SliderOption)NativeQualityOfLife.module("Autohide").getOption("Fade milliseconds")).setValue(350);
            var level=NativeAutohide.class.getDeclaredField("opacity");level.setAccessible(true);
            check(level.getFloat(null)==0,"idle Autohide reaches fully hidden");
            set("activity",System.nanoTime());set("frame",System.nanoTime());
            long until=System.nanoTime()+2_000_000_000L;int calls=0;float shown=0;
            while(shown<1&&System.nanoTime()<until){shown=NativeAutohide.update();calls++;}
            check(shown==1&&calls>1000,"fade-in from hidden completes with back-to-back frames ("+calls+" calls)");

            ((SliderOption)NativeQualityOfLife.module("Autohide").getOption("Fade milliseconds")).setValue(1000);
            var opacity=NativeAutohide.class.getDeclaredField("opacity");opacity.setAccessible(true);opacity.setFloat(null,.5f);set("frame",System.nanoTime());
            NativeAutohide.renderLadsHud(graphics);
            boolean[] faded={false,false,false,false};
            state.forEachElement(element->faded[0]|=element instanceof FadedElement,GuiRenderState.TraverseRange.ALL);
            var color=GuiTextRenderState.class.getDeclaredField("color");color.setAccessible(true);
            state.forEachText(text->{try{int alpha=color.getInt(text)>>>24;faded[1]|=alpha>0&&alpha<255;}catch(IllegalAccessException failure){throw new IllegalStateException(failure);}});
            state.forEachItem(item->faded[2]|=((FadedItem)(Object)item).ladsOpacity()>0&&((FadedItem)(Object)item).ladsOpacity()<1);
            state.forEachPictureInPicture(picture->{if(picture instanceof GuiEntityRenderState entity){int alpha=((PaperDollRenderState)entity.renderState()).ladsPaperDollAlpha();faded[3]|=alpha>0&&alpha<255;}});
            check(faded[0],"Lads plate carries partial alpha into GUI render state");
            check(faded[1],"Lads text carries partial alpha into deferred text state");
            check(faded[2],"Lads armor carries partial alpha into deferred item state");
            check(faded[3],"Lads paper doll carries partial alpha into its entity state");
            // 1.4.0: Xaero's minimap fades and hides with the HUD; its HUD element runs in the Autohide scope and its picture blits at that opacity.
            if(MinimapIntegration.available()){
                // Fabric resolves HUD element replacements while the HUD renders; this probe can run before the first in-world HUD frame.
                if(MinimapIntegration.faded==null){mc.gui.hud.extractRenderState(graphics,mc.getDeltaTracker());state.reset();}
                check(MinimapIntegration.faded!=null,"Xaero's HUD element runs inside the Autohide scope");
                state.reset();set("activity",System.nanoTime()-60_000_000_000L);opacity.setFloat(null,0);set("frame",System.nanoTime());
                MinimapIntegration.faded.extractRenderState(graphics,mc.getDeltaTracker());
                check(count(state)==0,"a hidden HUD hides Xaero's minimap");
                opacity.setFloat(null,.5f);set("frame",System.nanoTime());NativeAutohide.PICTURES.clear();
                MinimapIntegration.faded.extractRenderState(graphics,mc.getDeltaTracker());
                net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState[] map={null};
                state.forEachPictureInPicture(picture->{if(picture.getClass().getName().startsWith("xaero."))map[0]=picture;});
                check(map[0]!=null,"Xaero submits its minimap picture");
                Float alpha=NativeAutohide.PICTURES.get(map[0]);
                check(alpha!=null&&alpha>0&&alpha<1,"the minimap picture keeps the partial HUD opacity for its blit ("+alpha+")");
                state.reset();
                var renderer=new net.minecraft.client.gui.render.pip.PictureInPictureRenderer<net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState>(){
                    public Class<net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState> getRenderStateClass(){return net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState.class;}
                    protected void renderToTexture(net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState picture,com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.SubmitNodeCollector nodes){}
                    protected String getTextureLabel(){return "lads qa";}
                    void blit(net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState picture,GuiRenderState target){blitTexture(picture,target);}
                };
                try{renderer.blit(map[0],state);}finally{renderer.close();}
                int premultiplied=com.thelads.core.client.hud.AutohideFade.tintPremultiplied(-1,alpha);
                boolean[] blit={false};state.forEachElement(element->blit[0]|=element instanceof BlitRenderState b&&b.color()==premultiplied,GuiRenderState.TraverseRange.ALL);
                check(blit[0],"the minimap blit fades every premultiplied channel");
                state.reset();
            }

            var controller=new DraggableHudScreen(()->{});mc.setScreenAndShow(new DraggableHudScreen26(null,controller));state.reset();
            NativeAutohide.renderLadsHud(graphics);check(count(state)==0,"editor suppresses duplicate live HUD pass");
            controller.render(new GuiGraphicsExtractorLadsAdapter(graphics),-1,-1);
            check(count(state)>0,"editor controls and previews remain visible while Autohide is enabled");
            boolean[] opaqueDoll={false};state.forEachPictureInPicture(picture->{if(picture instanceof GuiEntityRenderState entity)opaqueDoll[0]|=((PaperDollRenderState)entity.renderState()).ladsPaperDollAlpha()==255;});
            check(opaqueDoll[0],"editor paper doll preview retains full opacity");controller.close();
            org.slf4j.LoggerFactory.getLogger("TheLadsCore").info("Lads 1.3.4 HUD probe END: {} passed, 0 failed; live fade and opaque editor render states",passed);
            return passed;
        }finally{com.thelads.core.client.bridge.LadsGameBridge.set(bridge);mc.player.setItemSlot(EquipmentSlot.FEET,boots);ConfigManager.applyJson(snapshot);NativeAutohide.scopeOpacity=1;mc.setScreenAndShow(screen);}
    }
    private static void set(String name,long value)throws Exception{var field=NativeAutohide.class.getDeclaredField(name);field.setAccessible(true);field.setLong(null,value);}
    private static int count(GuiRenderState state){int[] n={0};state.forEachElement(e->n[0]++,GuiRenderState.TraverseRange.ALL);state.forEachText(e->n[0]++);state.forEachItem(e->n[0]++);state.forEachPictureInPicture(e->n[0]++);return n[0];}
    private static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);passed++;}
}
