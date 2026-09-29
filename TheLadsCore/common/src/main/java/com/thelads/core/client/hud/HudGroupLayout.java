package com.thelads.core.client.hud;

import java.util.Collection;

/** Shared screen-space geometry. Every group receives one translation, including at viewport edges. */
public final class HudGroupLayout {
    public record Rect(int x,int y,int width,int height) {
        public int right(){return add(x,width);} public int bottom(){return add(y,height);}
        public boolean contains(double px,double py){return px>=x&&py>=y&&px<right()&&py<bottom();}
        public boolean intersects(Rect other){return x<other.right()&&right()>other.x&&y<other.bottom()&&bottom()>other.y;}
    }
    public record Delta(int x,int y) {}
    private HudGroupLayout() {}
    private static int add(int a,int b){return (int)Math.max(Integer.MIN_VALUE,Math.min(Integer.MAX_VALUE,(long)a+b));}
    public static Rect union(Collection<Rect> bounds){
        if(bounds.isEmpty())return new Rect(0,0,0,0);
        int left=Integer.MAX_VALUE,top=Integer.MAX_VALUE,right=Integer.MIN_VALUE,bottom=Integer.MIN_VALUE;
        for(Rect value:bounds){left=Math.min(left,value.x);top=Math.min(top,value.y);right=Math.max(right,value.right());bottom=Math.max(bottom,value.bottom());}
        return new Rect(left,top,(int)Math.min(Integer.MAX_VALUE,(long)right-left),(int)Math.min(Integer.MAX_VALUE,(long)bottom-top));
    }
    public static Rect translate(Rect bounds,Delta delta){return new Rect(add(bounds.x,delta.x),add(bounds.y,delta.y),bounds.width,bounds.height);}
    public static Delta clampDelta(Rect bounds,int dx,int dy,int viewportWidth,int viewportHeight){
        return new Delta(clampAxis(bounds.x,bounds.width,dx,viewportWidth),clampAxis(bounds.y,bounds.height,dy,viewportHeight));
    }
    private static int clampAxis(int origin,int size,int delta,int viewport){
        long first=-(long)origin,second=(long)Math.max(0,viewport)-origin-size;
        // Oversized groups can pan between their two edges, rather than collapsing members together.
        return (int)Math.max(Integer.MIN_VALUE,Math.min(Integer.MAX_VALUE,Math.max(Math.min(first,second),Math.min(Math.max(first,second),delta))));
    }
    public static Delta snapDelta(Rect bounds,int dx,int dy,int grid,int threshold,Collection<Rect> targets,int viewportWidth,int viewportHeight){
        int sx=snapAxis(bounds.x,bounds.width,dx,grid,threshold,targets,true,viewportWidth);
        int sy=snapAxis(bounds.y,bounds.height,dy,grid,threshold,targets,false,viewportHeight);
        return clampDelta(bounds,sx,sy,viewportWidth,viewportHeight);
    }
    private static int snapAxis(int origin,int size,int delta,int grid,int threshold,Collection<Rect> targets,boolean horizontal,int viewport){
        long moved=(long)origin+delta;long best=Long.MAX_VALUE;
        for(long edge:new long[]{moved,moved+size/2,moved+size}){
            for(long target:new long[]{0,viewport/2,viewport})if(Math.abs(target-edge)<Math.abs(best))best=target-edge;
            for(Rect other:targets){int p=horizontal?other.x:other.y,n=horizontal?other.width:other.height;
                for(long target:new long[]{p,(long)p+n/2,(long)p+n})if(Math.abs(target-edge)<Math.abs(best))best=target-edge;
            }
        }
        if(grid>0){long snapped=Math.round((double)moved/grid)*grid; if(Math.abs(snapped-moved)<Math.abs(best))best=snapped-moved;}
        return Math.abs(best)<=threshold?add(delta,(int)best):delta;
    }
}
