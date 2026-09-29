package com.thelads.core.v26_2.feature;
/** Legacy player cameraPitch: tick smoothing followed by partial-tick interpolation. */
public final class LegacyVerticalBob {
    private int lastTick=Integer.MIN_VALUE;
    private float previous,current;
    public float sample(int tick,double velocity,boolean grounded,float partial,boolean active){
        if(!active){lastTick=Integer.MIN_VALUE;previous=current=0;return 0;}
        if(tick!=lastTick){
            if(lastTick==Integer.MIN_VALUE||tick<lastTick||tick-lastTick>5)previous=current=0;
            previous=current;
            float target=grounded?0:(float)(Math.atan(-velocity*.2)*15);
            current+=(target-current)*.8f;
            lastTick=tick;
        }
        return interpolate(partial);
    }
    public float interpolate(float partial){return previous+(current-previous)*Math.clamp(partial,0,1);}
    public void reset(){lastTick=Integer.MIN_VALUE;previous=current=0;}
}
