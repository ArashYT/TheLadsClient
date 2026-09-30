package com.thelads.core.v26_2.feature;
import com.thelads.core.client.LegacyVerticalBob;
import net.minecraft.client.Minecraft;
/** Update on every completed game tick, including ticks between low-FPS rendered frames. */
public final class NativeVerticalBob {
    private static final LegacyVerticalBob motion=new LegacyVerticalBob();
    private static Object level,player;
    private NativeVerticalBob(){}
    public static void tick(){
        var mc=Minecraft.getInstance();
        if(level!=mc.level||player!=mc.player){motion.reset();level=mc.level;player=mc.player;}
        if(mc.player!=null&&mc.level!=null&&!mc.isPaused()){
            var p=mc.player;
            motion.sample(p.tickCount,p.getDeltaMovement().y,p.onGround()||p.isDeadOrDying(),1,true);
        }
    }
    public static float value(float partial,boolean active){return active?motion.interpolate(partial):0;}
}
