package com.thelads.core.v26_2.feature.crosshair;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.thelads.core.client.CrosshairDesign;
import com.thelads.core.client.CrosshairDrawing;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.HudSettings;
import com.thelads.core.modules.CrosshairModule;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/** Original renderer. It reads local aiming state and never changes attacks, inventories or hit results. */
public final class NativeCrosshair {
    private static final boolean EXTERNAL = FabricLoader.getInstance().isModLoaded("custom-crosshair-mod") || FabricLoader.getInstance().isModLoaded("crosshairtweaks");
    private static final Identifier VANILLA = Identifier.withDefaultNamespace("hud/crosshair");
    private static final Identifier ATTACK_FULL = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_full");
    private static final Identifier ATTACK_BG = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_background");
    private static final Identifier ATTACK_PROGRESS = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_progress");
    private static CrosshairDrawing drawing = defaultDrawing();
    private static String geometryKey = "";
    private static List<CrosshairDesign.Rect> geometry = List.of(), outline = List.of();
    private static final java.util.Map<Integer,List<CrosshairDesign.Rect>> rings=new java.util.HashMap<>();
    static long verificationExtractions;
    private NativeCrosshair() {}
    public static CrosshairModule module() { return NativeQualityOfLife.module("Crosshair Tweaks") instanceof CrosshairModule module ? module : null; }
    public static boolean active() { var module=module(); return !EXTERNAL && module!=null && module.isEnabled(); }
    public static boolean externalPresent() { return EXTERNAL; }
    public static boolean flag(String name) { return NativeQualityOfLife.bool("Crosshair Tweaks",name,false); }
    public static double number(String name) { return NativeQualityOfLife.number("Crosshair Tweaks",name,0); }
    public static int color(String name) { var value=(ColorOption)module().getOption(name); return value.isUseGlobal()?HudSettings.getInstance().getGlobalColor():value.getColor(); }
    public static void register() {
        var module=module(); if(module==null)return;
        drawing=CrosshairDrawingStore.load(defaultDrawing());
        module.drawingEditor.setAction(()->Minecraft.getInstance().setScreenAndShow(new CrosshairDrawingScreen(Minecraft.getInstance().gui.screen())));
        NativeCrosshairProbe.initialize();
    }
    public static CrosshairDrawing drawing() { return drawing.copy(); }
    public static void commitDrawing(CrosshairDrawing next) throws java.io.IOException { CrosshairDrawingStore.save(next); drawing=next.copy(); geometryKey=""; }
    static void setDrawingForProbe(CrosshairDrawing value) { drawing=value.copy(); geometryKey=""; }
    private static CrosshairDrawing defaultDrawing() { var value=new CrosshairDrawing(17,17); for(var r:CrosshairDesign.geometry(0,5,5,2,1))for(int y=r.top();y<r.bottom();y++)for(int x=r.left();x<r.right();x++)value.set(x+8,y+8,true); return value; }
    public static boolean debugAxes() { var mc=Minecraft.getInstance(); return mc.debugEntries.isCurrentlyEnabled(DebugScreenEntries.THREE_DIMENSIONAL_CROSSHAIR); }
    public static CrosshairDesign.Visibility context() {
        var mc=Minecraft.getInstance(); var player=mc.player; var held=player.getMainHandItem(); var off=player.getOffhandItem();
        boolean spectatorTarget=mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof MenuProvider
            || mc.hitResult instanceof BlockHitResult block && block.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK
                && mc.level.getBlockState(block.getBlockPos()).getMenuProvider(mc.level,block.getBlockPos())!=null;
        return new CrosshairDesign.Visibility(!mc.options.getCameraType().isFirstPerson(),player.isSpectator(),spectatorTarget,
            mc.gui.hud.isHidden(),mc.getDebugOverlay().showDebugScreen()||debugAxes(),mc.gui.screen() instanceof AbstractContainerScreen<?>,player.isScoping(),
            ranged(held)||ranged(off),throwable(held)||throwable(off));
    }
    public static CrosshairDesign.Rules rules() { return new CrosshairDesign.Rules(flag("Show in Third Person"),flag("Show in Spectator"),flag("Visible with Hidden HUD"),flag("Visible with Debug"),flag("Hide in Containers"),flag("Visible Normally"),flag("Visible Using Spyglass"),flag("Visible Holding Ranged Weapon"),flag("Visible Holding Throwable")); }
    public static boolean suppressDebugAxes() {
        var mc=Minecraft.getInstance(); return active() && mc.player!=null && (!flag("Keep Vanilla Debug")||flag("Disable Crosshair")||!CrosshairDesign.visible(context(),rules()));
    }
    public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        if(Boolean.getBoolean("thelads.verifyCrosshair"))verificationExtractions++;
        var mc=Minecraft.getInstance(); if(!active()||mc.player==null||mc.level==null)return;
        var context=context(); if(!CrosshairDesign.visible(context,rules()))return;
        double alpha=number(context.thirdPerson()?"Opacity Third Person":"Opacity First Person")/100;
        boolean axes=debugAxes()&&flag("Keep Vanilla Debug");
        float partial=delta.getGameTimeDeltaPartialTick(false);
        int cx=graphics.guiWidth()/2+(int)number("Offset X"),cy=graphics.guiHeight()/2+(int)number("Offset Y");
        graphics.nextStratum();
        if(!flag("Disable Crosshair")&&!axes) {
            double bow=chargeProgress(mc.player.getUseItem(),mc.player.getTicksUsingItem(),partial,mc.player.isUsingItem());
            double gap=CrosshairDesign.gap(number("Gap"),flag("Dynamic Attack Gap"),mc.player.getAttackStrengthScale(partial),flag("Dynamic Bow Gap"),bow);
            draw(graphics,cx,cy,gap,alpha,context.thirdPerson(),null);
        }
        if(!flag("Disable Attack Indicator")&&(!axes||flag("Debug Attack Indicator"))) attack(graphics,cx,cy,context.thirdPerson());
        if(!flag("Disable Crosshair")&&!axes) indicators(graphics,cx,cy,partial,alpha);
    }
    public static void draw(GuiGraphicsExtractor graphics,int cx,int cy,double gap,double opacity,boolean third,CrosshairDrawing preview) {
        var module=module(); if(module==null)return;
        int shape=preview!=null?8:NativeQualityOfLife.choice("Crosshair Tweaks","Shape",0), main=targetColor();
        boolean invert=(flag("Adaptive Color")||shape==3)&&!flag(third?"Remove Blend Third Person":"Remove Blend First Person");
        var pipeline=invert?RenderPipelines.GUI_INVERT:RenderPipelines.GUI;
        main=CrosshairDesign.tint(main,opacity,invert);
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(cx,cy); graphics.pose().rotate((float)Math.toRadians(number("Rotation"))); graphics.pose().scale((float)module.scale.get());
            if(shape==3) {
                int width=(int)number("Width")*2+5,height=(int)number("Height")*2+5;
                graphics.blitSprite(invert?RenderPipelines.CROSSHAIR:RenderPipelines.GUI_TEXTURED,VANILLA,-width/2,-height/2,width,height,main);
            } else if(shape==7) debugShape(graphics,main,opacity);
            else {
                int w=(int)number("Width"),h=(int)number("Height"),t=Math.max(1,(int)Math.round(module.thickness.get())),g=(int)Math.round(gap);
                String key=shape+":"+w+":"+h+":"+t+":"+g;
                List<CrosshairDesign.Rect> parts,edge;
                if(preview!=null){parts=preview.rectangles();edge=merge(parts,1);}
                else { if(!geometryKey.equals(key)){geometry=merge(shape==8?drawing.rectangles():CrosshairDesign.geometry(shape,w,h,g,t),0);outline=merge(geometry,1);geometryKey=key;}parts=geometry;edge=outline; }
                if(flag("Outline")) fill(graphics,RenderPipelines.GUI,edge,CrosshairDesign.alpha(color("Outline Color"),opacity));
                fill(graphics,pipeline,parts,main);
            }
            if(flag("Center Dot")) {int t=Math.max(1,(int)Math.round(module.thickness.get()));graphics.fill(RenderPipelines.GUI,-t/2,-t/2,t-t/2,t-t/2,CrosshairDesign.alpha(color("Dot Color"),opacity));}
        } finally { graphics.pose().popMatrix(); }
    }
    static int targetColor() {
        var mc=Minecraft.getInstance(); int base=color("Color");
        if(flag("Rainbow")){float hue=(float)((System.nanoTime()/1e9*number("Rainbow Speed")/6+number("Rainbow Phase")/360)%1);base=(base&0xff000000)|(Color.HSBtoRGB(hue,1,1)&0xffffff);}
        if(mc.crosshairPickEntity instanceof Player&&flag("Highlight Players"))return color("Player Color");
        if((mc.crosshairPickEntity instanceof Enemy||mc.crosshairPickEntity instanceof net.minecraft.world.entity.NeutralMob)&&flag("Highlight Hostiles"))return color("Hostile Color");
        if(mc.crosshairPickEntity instanceof net.minecraft.world.entity.animal.Animal&&flag("Highlight Passives"))return color("Passive Color");
        return base;
    }
    private static void debugShape(GuiGraphicsExtractor graphics,int color,double opacity) {
        var player=Minecraft.getInstance().player; double yaw=player==null?0:Math.toRadians(player.getYRot()),pitch=player==null?0:Math.toRadians(player.getXRot());
        double[][] axes={{Math.cos(yaw),Math.sin(yaw)*Math.sin(pitch)},{0,-Math.cos(pitch)},{-Math.sin(yaw),Math.cos(yaw)*Math.sin(pitch)}};
        int[] colors={0xffff5555,0xff55ff55,0xff5555ff};
        for(int i=0;i<3;i++){var parts=new ArrayList<CrosshairDesign.Rect>();CrosshairDesign.line(parts,0,0,(int)Math.round(axes[i][0]*(number("Width")+number("Gap"))),(int)Math.round(axes[i][1]*(number("Height")+number("Gap"))),Math.max(1,(int)Math.round(module().thickness.get())));fill(graphics,RenderPipelines.GUI,merge(parts,0),CrosshairDesign.alpha(colors[i],opacity));}
    }
    private static void attack(GuiGraphicsExtractor graphics,int cx,int cy,boolean third) {
        var mc=Minecraft.getInstance(); if(mc.options.attackIndicator().get()!=AttackIndicatorStatus.CROSSHAIR)return;
        float charge=mc.player.getAttackStrengthScale(0); int x=cx-8,y=cy+9;
        var pipeline=flag(third?"Remove Attack Blend Third Person":"Remove Attack Blend First Person")?RenderPipelines.GUI_TEXTURED:RenderPipelines.CROSSHAIR;
        int tint=CrosshairDesign.tint(0xffffffff,number(third?"Attack Opacity Third Person":"Attack Opacity First Person")/100,pipeline==RenderPipelines.CROSSHAIR);
        if((tint>>>24)==0)return;
        var range=mc.player.getMainHandItem().get(DataComponents.ATTACK_RANGE);
        boolean full=charge>=1&&mc.crosshairPickEntity instanceof LivingEntity target&&target.isAlive()&&mc.player.getCurrentItemAttackStrengthDelay()>5&&(range==null||mc.hitResult!=null&&range.isInRange(mc.player,mc.hitResult.getLocation()));
        if(full)graphics.blitSprite(pipeline,ATTACK_FULL,x,y,16,16,tint);
        else if(charge<1){graphics.blitSprite(pipeline,ATTACK_BG,x,y,16,4,tint);int width=Math.max(0,Math.min(16,(int)(charge*17)));if(width>0)graphics.blitSprite(pipeline,ATTACK_PROGRESS,16,4,0,0,x,y,width,4,tint);}
    }
    private static void indicators(GuiGraphicsExtractor graphics,int cx,int cy,float partial,double opacity) {
        if(opacity<=0)return;
        var mc=Minecraft.getInstance(); var held=mc.player.getMainHandItem(); int x=cx+14,y=cy+18;
        if(flag("Item Cooldown"))cooldowns(graphics,cx,cy,partial,opacity);
        if(flag("Tool Durability Indicator")&&held.isDamageableItem()){graphics.item(held,x,y);graphics.text(mc.font,Integer.toString(Math.max(0,held.getMaxDamage()-held.getDamageValue())),x+18,y+4,CrosshairDesign.alpha(0xffffffff,opacity));y+=18;}
        if(flag("Projectile Indicator")){var weapon=held.getItem() instanceof ProjectileWeaponItem||throwable(held)?held:mc.player.getOffhandItem();var ammo=projectiles(weapon);if(ammo!=null){graphics.item(ammo.icon,x,y);graphics.text(mc.font,ammo.infinite?"∞":Integer.toString(ammo.count),x+18,y+4,CrosshairDesign.alpha(0xffffffff,opacity));}}
    }
    public record Ammo(ItemStack icon,int count,boolean infinite) {}
    public static Ammo projectiles(ItemStack held) {
        var player=Minecraft.getInstance().player; if(player==null)return null;
        if(held.getItem() instanceof ProjectileWeaponItem weapon){
            var projectile=player.getProjectile(held);int count=0;var inventory=player.getInventory();
            for(int i=0;i<inventory.getContainerSize();i++){var stack=inventory.getItem(i);if(weapon.getAllSupportedProjectiles().test(stack))count+=stack.getCount();}
            var loaded=held.get(DataComponents.CHARGED_PROJECTILES);if(loaded!=null&&!loaded.isEmpty()){for(var stack:loaded.itemCopies())count+=stack.getCount();if(projectile.isEmpty())projectile=loaded.itemCopies().getFirst();}
            boolean infinite=player.getAbilities().instabuild;
            if(held.getItem() instanceof BowItem&&projectile.is(Items.ARROW)){var enchants=held.get(DataComponents.ENCHANTMENTS);if(enchants!=null)infinite|=enchants.keySet().stream().anyMatch(holder->holder.is(net.minecraft.world.item.enchantment.Enchantments.INFINITY));}
            if(projectile.isEmpty())projectile=new ItemStack(Items.ARROW);return new Ammo(projectile,count,infinite);
        }
        if(throwable(held)){int count=0;var inventory=player.getInventory();for(int i=0;i<inventory.getContainerSize();i++){var stack=inventory.getItem(i);if(stack.is(held.getItem()))count+=stack.getCount();}return new Ammo(held,count,player.getAbilities().instabuild);}return null;
    }
    static double chargeProgress(ItemStack item,int usedTicks,float partial,boolean using){
        if(!using)return -1;double ticks=usedTicks+partial;
        if(item.getItem() instanceof BowItem)return Math.min(1,ticks/20);
        if(item.getItem() instanceof net.minecraft.world.item.CrossbowItem)return Math.min(1,ticks/Math.max(1,net.minecraft.world.item.CrossbowItem.getChargeDuration(item,Minecraft.getInstance().player)));
        if(item.is(Items.TRIDENT))return Math.min(1,ticks/10);return -1;
    }
    private static boolean ranged(ItemStack item){return item.getItem() instanceof ProjectileWeaponItem||item.is(Items.TRIDENT);}
    private static boolean throwable(ItemStack item){return item.is(Items.SNOWBALL)||item.is(Items.EGG)||item.is(Items.ENDER_PEARL)||item.is(Items.ENDER_EYE)||item.is(Items.EXPERIENCE_BOTTLE)||item.is(Items.SPLASH_POTION)||item.is(Items.LINGERING_POTION);}
    private static void cooldowns(GuiGraphicsExtractor graphics,int cx,int cy,float partial,double opacity){
        var player=Minecraft.getInstance().player;var cooldowns=player.getCooldowns();var seen=new java.util.HashSet<Identifier>();int ring=0;
        graphics.pose().pushMatrix();try{graphics.pose().translate(cx,cy);graphics.pose().scale((float)module().scale.get());
            for(var item:List.of(new ItemStack(Items.ENDER_PEARL),new ItemStack(Items.CHORUS_FRUIT),player.getMainHandItem(),player.getOffhandItem())){
                if(item.isEmpty()||!seen.add(cooldowns.getCooldownGroup(item)))continue;float remaining=cooldowns.getCooldownPercent(item,partial);if(remaining<=0)continue;
                int radius=Math.min(120,(int)(Math.max(number("Width"),number("Height"))+number("Gap")+3+ring++*3));
                var pixels=rings.computeIfAbsent(radius,r->merge(CrosshairDesign.geometry(4,Math.min(64,r),Math.min(64,r),Math.max(0,r-64),2),0));
                int tint=CrosshairDesign.alpha(color("Cooldown Color"),opacity);double progress=1-remaining;
                for(var rect:pixels)for(int x=rect.left();x<rect.right();x++){double angle=(Math.atan2(rect.top(),x)+Math.PI/2+2*Math.PI)%(2*Math.PI)/(2*Math.PI);if(angle<=progress)graphics.fill(x,rect.top(),x+1,rect.bottom(),tint);}
            }
        }finally{graphics.pose().popMatrix();}
    }
    private static void fill(GuiGraphicsExtractor graphics,RenderPipeline pipeline,List<CrosshairDesign.Rect> rectangles,int color){if((color>>>24)==0)return;for(var r:rectangles)graphics.fill(pipeline,r.left(),r.top(),r.right(),r.bottom(),color);}
    /** Union prevents duplicate alpha blending at joined corners; outline is a one-pixel dilation. */
    private static List<CrosshairDesign.Rect> merge(List<CrosshairDesign.Rect> rectangles,int border){
        if(rectangles.isEmpty())return List.of();int minX=1000,minY=1000,maxX=-1000,maxY=-1000;
        for(var r:rectangles){minX=Math.min(minX,r.left()-border);minY=Math.min(minY,r.top()-border);maxX=Math.max(maxX,r.right()+border);maxY=Math.max(maxY,r.bottom()+border);}
        boolean[][] pixels=new boolean[maxY-minY][maxX-minX];for(var r:rectangles)for(int y=r.top()-border;y<r.bottom()+border;y++)for(int x=r.left()-border;x<r.right()+border;x++)pixels[y-minY][x-minX]=true;
        var result=new ArrayList<CrosshairDesign.Rect>();for(int y=0;y<pixels.length;y++)for(int x=0;x<pixels[y].length;){if(!pixels[y][x]){x++;continue;}int start=x;while(x<pixels[y].length&&pixels[y][x])x++;result.add(new CrosshairDesign.Rect(start+minX,y+minY,x+minX,y+minY+1));}return result;
    }
}
