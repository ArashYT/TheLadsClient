package com.thelads.core.v26_2.feature;
import net.minecraft.client.Minecraft;
/** Independent native implementation. No Auto Hide HUD code or engine is included. */
public final class NativeAutohide {
    private static Object player;
    private static long activity,frame;
    private static float health,opacity=1;
    private static int food,air,slot,xp;
    public static float scopeOpacity=1;
    private NativeAutohide(){}
    /** Share the hotbar fade with every native Lads draw, including deferred text/items. */
    public static void renderLadsHud(net.minecraft.client.gui.GuiGraphicsExtractor graphics){
        var mc=Minecraft.getInstance();
        if(mc.gui.screen() instanceof com.thelads.core.v26_2.gui.DraggableHudScreen26)return;
        float previous=scopeOpacity;
        try{
            scopeOpacity=update();
            if(scopeOpacity>0)com.thelads.core.client.hud.HudManager.getInstance().render(new com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter(graphics,mc.font));
        }finally{scopeOpacity=previous;}
    }
    public static float update(){
        var mc=Minecraft.getInstance();long now=System.nanoTime();
        if(mc.player==null||!NativeQualityOfLife.enabled("Autohide")){player=null;activity=frame=now;return opacity=1;}
        var p=mc.player;
        int nextFood=p.getFoodData().getFoodLevel(),nextSlot=p.getInventory().getSelectedSlot();
        boolean changed=player!=p||health!=p.getHealth()||food!=nextFood||air!=p.getAirSupply()||slot!=nextSlot||xp!=p.totalExperience;
        player=p;health=p.getHealth();food=nextFood;air=p.getAirSupply();slot=nextSlot;xp=p.totalExperience;
        boolean action=mc.gui.screen()!=null||p.isUsingItem()||mc.options.keyAttack.isDown()||mc.options.keyUse.isDown();
        if(NativeQualityOfLife.bool("Autohide","Show while moving",false))action|=p.getDeltaMovement().lengthSqr()>.001;
        if(NativeQualityOfLife.bool("Autohide","Show when hurt or hungry",true))action|=health<p.getMaxHealth()||food<20||air<p.getMaxAirSupply();
        if(changed||action||activity==0)activity=now;
        float target=(now-activity)/1e9<NativeQualityOfLife.number("Autohide","Hide after seconds",4)?1:0;
        double duration=NativeQualityOfLife.number("Autohide","Fade milliseconds",350)/1000;
        float step=duration<=0?1:(float)(Math.min(.1,(now-frame)/1e9)/duration);frame=now;
        opacity=target>opacity?Math.min(target,opacity+step):Math.max(target,opacity-step);
        if(opacity<.02)opacity=0;
        return opacity;
    }
    public static int tint(int color,float alpha){return (color&0xFFFFFF)|(Math.round((color>>>24)*alpha)<<24);}
}
