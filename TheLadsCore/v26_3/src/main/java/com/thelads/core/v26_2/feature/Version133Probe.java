package com.thelads.core.v26_2.feature;
import com.thelads.core.config.*;
import com.thelads.core.v26_2.gui.SmoothScrollTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.renderer.state.gui.*;
import java.util.*;
/** Exercises transformed production APIs only inside the explicitly isolated game harness. */
final class Version133Probe {
    private static int passed;
    static int run() throws Exception {
        var mc=Minecraft.getInstance();NativeWorldVerification.checkedGameDirectory(mc.gameDirectory.toPath());
        var snapshot=ConfigManager.toJson();var screen=mc.gui.screen();
        try {
            check(ModuleSupport.isBuiltIn("Autohide")&&ModuleSupport.isBuiltIn("Jade"),"native modules registered");
            var loader=net.fabricmc.loader.api.FabricLoader.getInstance();
            check(loader.getModContainer("jade").orElseThrow().getContainingMod().orElseThrow().getMetadata().getId().equals("theladscore"),"Jade identity belongs to Core");
            check(snownee.jade.Jade.class.getProtectionDomain().getCodeSource().getLocation().equals(Version133Probe.class.getProtectionDomain().getCodeSource().getLocation()),"Jade implementation is in Core, not an external engine");
            var addon=Class.forName("qa.JadeAddon");check(addon.getField("common").getInt(null)==1&&addon.getField("client").getInt(null)==1,"external addon registered exactly once through jade entrypoint");
            var registration=snownee.jade.impl.WailaClientRegistration.instance();
            var pos=mc.player.blockPosition().below();var block=mc.level.getBlockState(pos);
            var accessor=registration.blockAccessor().level(mc.level).player(mc.player).blockState(block).serverConnected(true)
                .hit(new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),net.minecraft.core.Direction.UP,pos,false)).build();
            var providers=registration.getBlockProviders(block.getBlock(),p->p.getUid().getNamespace().equals("lads_jade_qa"));
            check(providers.size()==1,"addon component discoverable through production provider registry");
            var tooltip=new snownee.jade.impl.Tooltip();providers.getFirst().appendTooltip(tooltip,accessor,snownee.jade.api.config.IWailaConfig.get().plugin());
            check(addon.getField("tooltips").getInt(null)>0,"external addon tooltip invoked with a real world accessor");
            var serverProviders=snownee.jade.impl.WailaCommonRegistration.instance().blockDataProvidersOf(block,null,false).stream().filter(p->p.getUid().getNamespace().equals("lads_jade_qa")).toList();
            check(serverProviders.size()==1,"server provider registration retained");
            var tag=new net.minecraft.nbt.CompoundTag();serverProviders.getFirst().appendServerData(tag,accessor);
            check(tag.getBooleanOr("lads_jade_qa",false),"server provider payload");
            check(NativeQualityOfLife.module("Paperdoll").getOptions().stream().filter(o->o instanceof PlayerActionOption).count()>35,"available player states discovered");
            var legacy=NativeQualityOfLife.module("LegacySwing");legacy.setEnabled(true);
            var equip=mc.player.firstPersonHandsAndItems();
            var saved=new LinkedHashMap<java.lang.reflect.Field,Object>();
            for(var field:equip.getClass().getDeclaredFields())if(!java.lang.reflect.Modifier.isStatic(field.getModifiers())&&(field.getType()==float.class||field.getType()==net.minecraft.world.item.ItemStack.class)){field.setAccessible(true);saved.put(field,field.get(equip));}
            try {
                var ticker=net.minecraft.world.entity.LivingEntity.class.getDeclaredField("attackStrengthTicker");ticker.setAccessible(true);int ticks=ticker.getInt(mc.player);
                var item=equip.getClass().getDeclaredField("mainHandItem");item.setAccessible(true);
                var height=equip.getClass().getDeclaredField("mainHandHeight");height.setAccessible(true);
                try {
                    item.set(equip,mc.player.getMainHandItem());height.setFloat(equip,1);
                    mc.player.resetAttackStrengthTicker();
                    float cooldown=mc.player.getAttackStrengthScale(0);
                    equip.tick(mc.player);
                    equip.itemUsed(net.minecraft.world.InteractionHand.MAIN_HAND);
                    check(height.getFloat(equip)==1,"legacy hand stays up through the attack cooldown and after using an item");
                    check(cooldown<1&&mc.player.getAttackStrengthScale(0)==cooldown,"visual equip suppression preserves gameplay/crosshair cooldown");
                    item.set(equip,new net.minecraft.world.item.ItemStack(mc.player.getMainHandItem().is(net.minecraft.world.item.Items.STONE)?net.minecraft.world.item.Items.DIRT:net.minecraft.world.item.Items.STONE));
                    equip.tick(mc.player);
                    check(height.getFloat(equip)<1,"legacy: switching items still lowers the hand to pull the next one out");
                }finally{ticker.setInt(mc.player,ticks);}
            }finally{for(var entry:saved.entrySet())entry.getKey().set(equip,entry.getValue());}
            var state=new GuiRenderState();var graphics=new GuiGraphicsExtractor(mc,state,0,0);
            NativeAutohide.scopeOpacity=0;
            graphics.fill(1,1,30,30,-1);graphics.text(mc.font,"hidden",2,2,-1,false);graphics.item(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE),3,3);
            check(count(state)==0,"fully faded sprites/text/items are not submitted");
            NativeAutohide.scopeOpacity=.5f;graphics.fill(1,1,30,30,-1);
            final boolean[] faded={false};state.forEachElement(e->faded[0]|=e instanceof FadedElement,GuiRenderState.TraverseRange.ALL);
            check(faded[0],"partial opacity is carried into real GUI vertex state");
            NativeAutohide.scopeOpacity=1;state.reset();
            mc.setScreenAndShow(null);NativeQualityOfLife.module("Autohide").setEnabled(true);
            ((BoolOption)NativeQualityOfLife.module("Autohide").getOption("Show when hurt or hungry")).set(false);
            ((BoolOption)NativeQualityOfLife.module("Autohide").getOption("Show while moving")).set(false);
            ((SliderOption)NativeQualityOfLife.module("Autohide").getOption("Fade milliseconds")).setValue(0);
            NativeAutohide.update();var activity=NativeAutohide.class.getDeclaredField("activity");activity.setAccessible(true);activity.setLong(null,System.nanoTime()-60_000_000_000L);
            var hotbar=mc.gui.hud.getClass().getDeclaredMethod("extractHotbarAndDecorations",GuiGraphicsExtractor.class,net.minecraft.client.DeltaTracker.class);hotbar.setAccessible(true);hotbar.invoke(mc.gui.hud,graphics,mc.getDeltaTracker());
            check(count(state)==0,"fully hidden native hotbar includes its selected-slot highlight");
            check(NativeAutohide.scopeOpacity==1,"opacity scope restored for unrelated HUD elements");
            graphics.fill(1,1,3,3,-1);check(count(state)>0,"later GUI content remains visible");
            passed+=Version134HudProbe.run();
            var pause=new PauseScreen(true);mc.setScreenAndShow(pause);pause.extractRenderState(graphics,0,0,0);
            var buttons=pause.children().stream().filter(c->c instanceof Button b&&b.visible).map(c->(Button)c).toList();
            check(buttons.stream().anyMatch(b->b.getMessage().getString().equals("Lads Client")),"pause retains Lads settings action");
            for(int i=0;i<buttons.size();i++)for(int j=i+1;j<buttons.size();j++){var a=buttons.get(i);var b=buttons.get(j);check(!(a.getX()<b.getRight()&&a.getRight()>b.getX()&&a.getY()<b.getBottom()&&a.getBottom()>b.getY()),"pause buttons do not overlap");}
            var keys=new KeyBindsScreen(screen,mc.options);mc.setScreenAndShow(keys);var current=mc.gui.screen();
            var list=current.children().stream().filter(c->c instanceof AbstractScrollArea).map(c->(AbstractScrollArea)c).findFirst().orElseThrow();
            list.setScrollAmount(0);list.mouseScrolled(list.getX()+10,list.getY()+10,0,-3);
            check(list.scrollAmount()==0,"wheel stores a target without jumping");
            var frame=AbstractScrollArea.class.getDeclaredField("ladsFrame");frame.setAccessible(true);frame.setLong(list,System.nanoTime()-25_000_000L);
            ((SmoothScrollTarget)list).ladsAdvanceScroll();check(list.scrollAmount()>0&&list.scrollAmount()<list.maxScrollAmount(),"wheel target advances on a render frame");
            list.setScrollAmount(0);check(list.scrollAmount()==0,"direct scrollbar/navigation positioning remains immediate");
            check(LocalSkins.current()!=null,"file/HTTP skin reached the world");
            check(mc.player.getSkin().body().texturePath().equals(LocalSkins.current().body().texturePath()),"local player renders the selected skin");
            var selectedModel=LocalSkins.current().model();
            LocalSkins.setModel(false);check(LocalSkins.current().model()==net.minecraft.world.entity.player.PlayerModelType.WIDE,"model toggle updates the active skin immediately");
            LocalSkins.setModel(selectedModel==net.minecraft.world.entity.player.PlayerModelType.SLIM);
            var skinWidget=new PlayerSkinWidget(150,220,mc.getEntityModels(),LocalSkins::current);
            var modelField=PlayerSkinWidget.class.getDeclaredField("slimModel");modelField.setAccessible(true);
            var model=(net.minecraft.client.model.Model.Simple)modelField.get(skinWidget);
            PreviewSkinLayers.apply(model,LocalSkins.current());
            var jacket=model.root().getChild("body").getChild("jacket");
            check(((com.thelads.core.v26_2.mixin.ModelPartAccessor)(Object)jacket).ladsCubes().size()>10,"3D preview extrudes actual outer-layer pixels");
            try{LocalSkins.validate(new byte[24]);throw new IllegalStateException("invalid PNG accepted");}catch(java.io.IOException expected){passed++;}
            org.slf4j.LoggerFactory.getLogger("TheLadsCore").info("Lads 1.3.3 probe END: {} passed, 0 failed; transformed HUD, menus, cooldown and independent Jade addon",passed);
            return passed;
        }finally{NativeAutohide.scopeOpacity=1;ConfigManager.applyJson(snapshot);mc.setScreenAndShow(screen);}
    }
    private static int count(GuiRenderState s){int[] n={0};s.forEachElement(e->n[0]++,GuiRenderState.TraverseRange.ALL);s.forEachItem(e->n[0]++);s.forEachText(e->n[0]++);s.forEachPictureInPicture(e->n[0]++);return n[0];}
    private static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);passed++;}
}
