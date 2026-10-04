package com.thelads.core.client;
/** Legacy player cameraPitch: tick smoothing followed by partial-tick interpolation. */
public final class LegacyVerticalBob {
    /**
     * Share of the gap to the target closed each tick: 1.8's 0.8 while the player can bob, and a gentler 0.4 back to level once a
     * state without bob starts (flying, swimming, riding), as 1.8's own camera settles when flying stops a fall (its fall speed
     * drops 40% a tick). Per tick and interpolated per frame, so the ease looks the same at every frame rate.
     */
    static final float FOLLOW=.8f,SETTLE=.4f;
    private int lastTick=Integer.MIN_VALUE;
    private float previous,current;
    /** @param bobbing false in a state that shows no bob (flying, swimming, riding): the pitch eases back to level */
    public float sample(int tick,double velocity,boolean grounded,boolean bobbing,float partial,boolean active){
        if(!active){lastTick=Integer.MIN_VALUE;previous=current=0;return 0;}
        if(tick!=lastTick){
            if(lastTick==Integer.MIN_VALUE||tick<lastTick||tick-lastTick>5)previous=current=0;
            previous=current;
            float target=grounded||!bobbing?0:(float)(Math.atan(-velocity*.2)*15);
            current+=(target-current)*(bobbing?FOLLOW:SETTLE);
            lastTick=tick;
        }
        return interpolate(partial);
    }
    public float interpolate(float partial){return previous+(current-previous)*Math.clamp(partial,0,1);}
    public void reset(){lastTick=Integer.MIN_VALUE;previous=current=0;}
}
