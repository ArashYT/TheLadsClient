package com.thelads.core.v26_2.feature.crosshair;

import com.google.gson.JsonElement;
import com.thelads.core.client.CrosshairDrawing;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import java.util.LinkedHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.LoggerFactory;

/** Opt-in world checks exercise transformed extraction and editor handlers without desktop input. */
public final class NativeCrosshairProbe {
    private static boolean initialized,done;
    private static int passed;
    private NativeCrosshairProbe(){}
    public static void initialize(){if(initialized||!Boolean.getBoolean("thelads.verifyCrosshair"))return;initialized=true;ClientTickEvents.END_CLIENT_TICK.register(mc->{if(done||mc.player==null||mc.level==null||mc.gui.overlay()!=null||mc.gui.screen()!=null)return;done=true;try{run();}catch(Throwable failure){LoggerFactory.getLogger("TheLadsCore").error("Lads native crosshair probe FAILED after {} checks",passed,failure);}});}
    private static void run()throws Exception{
        passed=0;var mc=Minecraft.getInstance();var module=NativeCrosshair.module();boolean enabled=module.isEnabled();long modified=module.getLastModified();
        var settings=new LinkedHashMap<Option,JsonElement>();module.getOptions().forEach(option->settings.put(option,option.save().deepCopy()));
        var camera=mc.options.getCameraType();var attack=mc.options.attackIndicator().get();var target=mc.crosshairPickEntity;var hit=mc.hitResult;var drawing=NativeCrosshair.drawing();
        var inventory=mc.player.getInventory();var inventoryCopy=new java.util.ArrayList<ItemStack>();for(int i=0;i<inventory.getContainerSize();i++)inventoryCopy.add(inventory.getItem(i).copy());
        boolean creative=mc.player.getAbilities().instabuild;
        var hidden=Hud.class.getDeclaredField("isHidden");hidden.setAccessible(true);boolean wasHidden=hidden.getBoolean(mc.gui.hud);
        var strength=net.minecraft.world.entity.LivingEntity.class.getDeclaredField("attackStrengthTicker");strength.setAccessible(true);int attackTicks=strength.getInt(mc.player);
        // A world once played with GoodMC keeps its attack_speed base (32767) in the player data; check vanilla combat, restore after.
        var attackSpeed=mc.player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);double attackSpeedBase=attackSpeed.getBaseValue();
        var statusesField=mc.debugEntries.getClass().getDeclaredField("allStatuses");statusesField.setAccessible(true);
        @SuppressWarnings("unchecked") var statuses=(java.util.Map<net.minecraft.resources.Identifier,net.minecraft.client.gui.components.debug.DebugScreenEntryStatus>)statusesField.get(mc.debugEntries);
        var debugId=net.minecraft.client.gui.components.debug.DebugScreenEntries.THREE_DIMENSIONAL_CROSSHAIR;var debugStatus=statuses.get(debugId);boolean hadStatus=statuses.containsKey(debugId);
        var rebuildDebug=mc.debugEntries.getClass().getDeclaredMethod("rebuildCurrentList");rebuildDebug.setAccessible(true);
        var cooldowns=mc.player.getCooldowns();var cooldownMapField=net.minecraft.world.item.ItemCooldowns.class.getDeclaredField("cooldowns");cooldownMapField.setAccessible(true);
        @SuppressWarnings("unchecked") var cooldownMap=(java.util.Map<Object,Object>)cooldownMapField.get(cooldowns);var savedCooldowns=new java.util.HashMap<>(cooldownMap);
        var cooldownTickField=net.minecraft.world.item.ItemCooldowns.class.getDeclaredField("tickCount");cooldownTickField.setAccessible(true);int cooldownTick=cooldownTickField.getInt(cooldowns);
        try{
            check(!NativeCrosshair.externalPresent(),"isolated QA has no external crosshair renderer");
            module.getOptions().forEach(Option::reset);module.setEnabled(true);mc.options.setCameraType(CameraType.FIRST_PERSON);mc.options.attackIndicator().set(AttackIndicatorStatus.OFF);hidden.setBoolean(mc.gui.hud,false);
            mc.crosshairPickEntity=null;mc.hitResult=null;flag("Keep Vanilla Debug",false);flag("Visible with Debug",true);flag("Visible Using Spyglass",true);flag("Show in Spectator",true);
            for(int shape=0;shape<9;shape++){
                ((DropdownOption)module.getOption("Shape")).setIndex(shape);var state=extractHook();check(elements(state)>0,"shape "+shape+" reaches transformed native extraction");
            }
            ((DropdownOption)module.getOption("Shape")).setIndex(0);
            flag("Disable Crosshair",true);check(elements(extractHook())==0,"crosshair visibility switch suppresses extraction");flag("Disable Crosshair",false);
            slider("Opacity First Person",0);check(elements(extractHook())==0,"zero first-person opacity emits no shape");slider("Opacity First Person",100);
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);check(elements(extractHook())==0,"vanilla third-person exclusion preserved");flag("Show in Third Person",true);check(elements(extractHook())>0,"explicit third-person visibility enabled");slider("Opacity Third Person",0);check(elements(extractHook())==0,"third-person opacity independent");slider("Opacity Third Person",100);
            mc.options.setCameraType(CameraType.FIRST_PERSON);flag("Outline",false);flag("Adaptive Color",true);
            check(pipelineCount(extractHook(),RenderPipelines.GUI_INVERT)>0,"adaptive geometry uses actual inverse blending pipeline");flag("Remove Blend First Person",true);check(pipelineCount(extractHook(),RenderPipelines.GUI_INVERT)==0,"first-person blend removal overrides adaptive");
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);check(pipelineCount(extractHook(),RenderPipelines.GUI_INVERT)>0,"third-person blend policy independent");flag("Remove Blend Third Person",true);check(pipelineCount(extractHook(),RenderPipelines.GUI_INVERT)==0,"third-person blend removal consumed");mc.options.setCameraType(CameraType.FIRST_PERSON);
            flag("Adaptive Color",false);flag("Highlight Players",true);mc.crosshairPickEntity=mc.player;check(NativeCrosshair.targetColor()==NativeCrosshair.color("Player Color"),"actual player target color selected");mc.crosshairPickEntity=null;check(NativeCrosshair.targetColor()==NativeCrosshair.color("Color"),"target color clears after moving off entity");
            flag("Highlight Hostiles",true);mc.crosshairPickEntity=net.minecraft.world.entity.EntityTypes.ZOMBIE.create(mc.level,net.minecraft.world.entity.EntitySpawnReason.LOAD);check(NativeCrosshair.targetColor()==NativeCrosshair.color("Hostile Color"),"hostile fixture receives hostile highlight");
            flag("Highlight Passives",true);mc.crosshairPickEntity=net.minecraft.world.entity.EntityTypes.COW.create(mc.level,net.minecraft.world.entity.EntitySpawnReason.LOAD);check(NativeCrosshair.targetColor()==NativeCrosshair.color("Passive Color"),"passive fixture receives passive highlight");mc.crosshairPickEntity=null;
            slider("Rotation",37);slider("Offset X",12);slider("Offset Y",-8);module.scale.set(1.7);var state=new GuiRenderState();var graphics=new GuiGraphicsExtractor(mc,state,0,0);var pose=new org.joml.Matrix3x2f(graphics.pose());NativeCrosshair.extract(graphics,DeltaTracker.ZERO);check(graphics.pose().equals(pose)&&elements(state)>0,"rotated scaled offset crosshair preserves caller pose");
            hidden.setBoolean(mc.gui.hud,true);check(elements(extractHook())==0,"hidden HUD excludes by default");flag("Visible with Hidden HUD",true);check(elements(extractHook())>0,"hidden HUD explicit visibility consumed");hidden.setBoolean(mc.gui.hud,false);
            module.scale.set(1);slider("Rotation",0);slider("Offset X",0);slider("Offset Y",0);
            attackSpeed.setBaseValue(attackSpeed.getAttribute().value().getDefaultValue());
            inventory.setSelectedItem(new ItemStack(Items.DIAMOND_SWORD));strength.setInt(mc.player,0);flag("Disable Crosshair",true);mc.options.attackIndicator().set(AttackIndicatorStatus.CROSSHAIR);
            boolean hasAttackCooldown = mc.player.getCurrentItemAttackStrengthDelay() > 1;
            if (hasAttackCooldown) {
            check(pipelineCount(extractHook(),RenderPipelines.CROSSHAIR)>0,"actual attack recovery indicator uses vanilla inverse sprites; charge=" + mc.player.getAttackStrengthScale(0) + " delay=" + mc.player.getCurrentItemAttackStrengthDelay());slider("Attack Opacity First Person",0);check(elements(extractHook())==0,"first-person attack opacity zero suppresses sprites");slider("Attack Opacity First Person",100);flag("Remove Attack Blend First Person",true);check(pipelineCount(extractHook(),RenderPipelines.CROSSHAIR)==0&&elements(extractHook())>0,"attack blend removal preserves actual sprite");
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);check(pipelineCount(extractHook(),RenderPipelines.CROSSHAIR)>0,"third-person attack blending independent");flag("Remove Attack Blend Third Person",true);check(pipelineCount(extractHook(),RenderPipelines.CROSSHAIR)==0&&elements(extractHook())>0,"third-person attack blend removal consumed");slider("Attack Opacity Third Person",0);check(elements(extractHook())==0,"third-person attack opacity independent");slider("Attack Opacity Third Person",100);mc.options.setCameraType(CameraType.FIRST_PERSON);
            } else {
                check(elements(extractHook()) == 0, "legacy combat correctly has no attack cooldown indicator");
                LoggerFactory.getLogger("TheLadsCore").info("Lads crosshair QA: cooldown geometry checks skipped because the installed combat engine removes attack cooldowns");
            }
            statuses.put(debugId,net.minecraft.client.gui.components.debug.DebugScreenEntryStatus.ALWAYS_ON);rebuildDebug.invoke(mc.debugEntries);flag("Keep Vanilla Debug",true);flag("Disable Crosshair",false);
            check(elements(extractHook())==0,"vanilla debug axes retained without duplicate 2D crosshair");flag("Debug Attack Indicator",true);check(!hasAttackCooldown || elements(extractHook())>0,"debug attack override extracts cooldown sprites when combat has cooldowns");flag("Disable Attack Indicator",true);check(elements(extractHook())==0,"attack disable wins over debug override");flag("Disable Attack Indicator",false);flag("Keep Vanilla Debug",false);mc.options.attackIndicator().set(AttackIndicatorStatus.OFF);
            check(NativeCrosshair.suppressDebugAxes()&&elements(extractHook())>0,"custom debug preference suppresses vanilla axes and draws native shape");
            statuses.put(debugId,net.minecraft.client.gui.components.debug.DebugScreenEntryStatus.NEVER);rebuildDebug.invoke(mc.debugEntries);
            check(NativeCrosshair.chargeProgress(new ItemStack(Items.BOW),10,0,true)==.5,"bow gap reads native use duration");check(NativeCrosshair.chargeProgress(new ItemStack(Items.TRIDENT),5,0,true)==.5,"trident gap reads native charge duration");check(NativeCrosshair.chargeProgress(new ItemStack(Items.CROSSBOW),0,0,true)==0,"crossbow charge supported");check(NativeCrosshair.chargeProgress(new ItemStack(Items.BOW),10,0,false)==-1,"inactive bow has no charge gap");
            cooldowns.addCooldown(new ItemStack(Items.ENDER_PEARL),20);cooldowns.tick();int withoutCooldown=elements(extractHook());flag("Item Cooldown",true);check(elements(extractHook())>withoutCooldown,"cooldown ring renders for a pearl even after switching to sword");flag("Item Cooldown",false);
            flag("Tool Durability Indicator",true);check(texts(extractHook())==1,"tool indicator extracts actual remaining durability text");flag("Tool Durability Indicator",false);
            for(int i=0;i<inventory.getContainerSize();i++)inventory.setItem(i,ItemStack.EMPTY);mc.player.getAbilities().instabuild=false;
            inventory.setItem(9,new ItemStack(Items.ARROW,11));inventory.setItem(10,new ItemStack(Items.ARROW,7));
            var ammo=NativeCrosshair.projectiles(new ItemStack(Items.BOW));check(ammo!=null&&ammo.count()==18&&!ammo.infinite(),"projectile indicator counts actual inventory stacks");mc.player.getAbilities().instabuild=true;check(NativeCrosshair.projectiles(new ItemStack(Items.BOW)).infinite(),"creative ammunition explicitly infinite");mc.player.getAbilities().instabuild=false;
            inventory.setItem(11,new ItemStack(Items.ENDER_PEARL,5));check(NativeCrosshair.projectiles(new ItemStack(Items.ENDER_PEARL)).count()==5,"throwable indicator counts matching items");check(NativeCrosshair.projectiles(new ItemStack(Items.STONE))==null,"ordinary blocks have no projectile indicator");
            var loaded=new ItemStack(Items.CROSSBOW);loaded.set(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES,net.minecraft.world.item.component.ChargedProjectiles.ofNonEmpty(java.util.List.of(new ItemStack(Items.ARROW))));check(NativeCrosshair.projectiles(loaded).count()==19,"loaded crossbow projectile included");
            var editor=new CrosshairDrawingScreen(mc.gui.screen());editor.init(640,400);var before=editor.draftForProbe();int x=editor.gridXForProbe()+editor.cellForProbe()/2,y=editor.gridYForProbe()+editor.cellForProbe()/2;
            editor.mouseClicked(new MouseButtonEvent(x,y,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),false);editor.mouseDragged(new MouseButtonEvent(x+editor.cellForProbe()*3,y,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),editor.cellForProbe()*3,0);editor.mouseReleased(new MouseButtonEvent(x,y,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)));
            check(editor.draftForProbe().get(0,0)&&editor.draftForProbe().get(3,0),"editor paints contiguous drag stroke");check(NativeCrosshair.drawing().rows().equals(drawing.rows()),"unsaved editor stroke leaves active drawing intact");editor.undo();check(editor.draftForProbe().rows().equals(before.rows()),"undo restores whole stroke");
            editor.resize(360,270);boolean compactFits=editor.gridXForProbe()>=0&&editor.gridYForProbe()+editor.draftForProbe().height()*editor.cellForProbe()<270-58;
            editor.resize(900,270);check(compactFits&&!editor.hasPreview()&&editor.gridYForProbe()+editor.draftForProbe().height()*editor.cellForProbe()<270-58,"compact and wide-short editor layouts keep previews clear of controls");
            var editorState=new GuiRenderState();var editorGraphics=new GuiGraphicsExtractor(mc,editorState,0,0);editor.extractRenderState(editorGraphics,0,0,0);check(elements(editorState)>0,"editor extracts actual grid and widgets");
            var marker=new CrosshairDrawing(9,9);marker.set(4,4,true);NativeCrosshair.setDrawingForProbe(marker);((DropdownOption)module.getOption("Shape")).setIndex(8);check(elements(extractHook())>0,"saved drawing shape consumes bitmap data");
        }finally{
            settings.forEach(Option::load);module.setEnabled(enabled);module.setLastModified(modified);mc.options.setCameraType(camera);mc.options.attackIndicator().set(attack);mc.crosshairPickEntity=target;mc.hitResult=hit;NativeCrosshair.setDrawingForProbe(drawing);hidden.setBoolean(mc.gui.hud,wasHidden);mc.player.getAbilities().instabuild=creative;for(int i=0;i<inventoryCopy.size();i++)inventory.setItem(i,inventoryCopy.get(i));strength.setInt(mc.player,attackTicks);attackSpeed.setBaseValue(attackSpeedBase);cooldownMap.clear();cooldownMap.putAll(savedCooldowns);cooldownTickField.setInt(cooldowns,cooldownTick);if(hadStatus)statuses.put(debugId,debugStatus);else statuses.remove(debugId);rebuildDebug.invoke(mc.debugEntries);
        }
        LoggerFactory.getLogger("TheLadsCore").info("Lads native crosshair probe END: {} passed, 0 failed (transformed extraction/editor handlers; preferences and inventory restored)",passed);
    }
    private static GuiRenderState extractHook()throws Exception{var mc=Minecraft.getInstance();var state=new GuiRenderState();var graphics=new GuiGraphicsExtractor(mc,state,0,0);var method=Hud.class.getDeclaredMethod("extractCrosshair",GuiGraphicsExtractor.class,DeltaTracker.class);method.setAccessible(true);long previous=NativeCrosshair.verificationExtractions;method.invoke(mc.gui.hud,graphics,DeltaTracker.ZERO);if(NativeCrosshair.verificationExtractions!=previous+1)throw new IllegalStateException("Crosshair mixin did not invoke the native renderer exactly once");return state;}
    private static int elements(GuiRenderState state){int[] count={0};state.forEachElement(element->count[0]++,GuiRenderState.TraverseRange.ALL);return count[0];}
    private static int texts(GuiRenderState state){int[] count={0};state.forEachText(text->{if(text.ensurePrepared()!=null)count[0]++;});return count[0];}
    private static int pipelineCount(GuiRenderState state,com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline){int[] count={0};state.forEachElement(element->{if(element.pipeline()==pipeline)count[0]++;},GuiRenderState.TraverseRange.ALL);return count[0];}
    private static void flag(String name,boolean value){((BoolOption)NativeCrosshair.module().getOption(name)).set(value);}
    private static void slider(String name,double value){((SliderOption)NativeCrosshair.module().getOption(name)).setValue(value);}
    private static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);passed++;}
}
