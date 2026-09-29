package com.thelads.core.client;

import java.util.ArrayList;
import java.util.List;

/** Original pixel geometry shared by the HUD and drawing editor. Coordinates are GUI pixels. */
public final class CrosshairDesign {
    public record Rect(int left, int top, int right, int bottom) {}
    public record Visibility(boolean thirdPerson, boolean spectator, boolean spectatorTarget,
        boolean hiddenHud, boolean debug, boolean container, boolean spyglass, boolean ranged, boolean throwable) {}
    public record Rules(boolean thirdPerson, boolean spectator, boolean hiddenHud, boolean debug,
        boolean hideContainers, boolean normally, boolean spyglass, boolean ranged, boolean throwable) {}
    private CrosshairDesign() {}
    public static boolean visible(Visibility state, Rules rules) {
        if (!rules.normally || state.thirdPerson && !rules.thirdPerson || state.spectator && !state.spectatorTarget && !rules.spectator
            || state.hiddenHud && !rules.hiddenHud || state.debug && !rules.debug || state.container && rules.hideContainers) return false;
        return (!state.spyglass || rules.spyglass) && (!state.ranged || rules.ranged) && (!state.throwable || rules.throwable);
    }
    public static double gap(double base, boolean attackDynamic, double attack, boolean bowDynamic, double bow) {
        return Math.max(0, base) + (attackDynamic ? (1 - clamp(attack)) * 8 : 0) + (bowDynamic && bow >= 0 ? (1 - clamp(bow)) * 10 : 0);
    }
    public static int alpha(int color, double opacity) { return (color & 0xffffff) | ((int) Math.round((color >>> 24) * clamp(opacity)) << 24); }
    /** Inverse blending uses source RGB, so opacity must attenuate RGB as well as alpha. */
    public static int tint(int color,double opacity,boolean inverse) {
        int value=alpha(color,opacity);if(!inverse)return value;
        double factor=(value>>>24)/255.0;
        return (value&0xff000000)|((int)Math.round(((value>>>16)&255)*factor)<<16)|((int)Math.round(((value>>>8)&255)*factor)<<8)|(int)Math.round((value&255)*factor);
    }
    private static double clamp(double value) { return Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0; }
    public static List<Rect> geometry(int shape, int width, int height, int gap, int thickness) {
        int w = Math.max(1, Math.min(64, width)), h = Math.max(1, Math.min(64, height));
        int g = Math.max(0, Math.min(64, gap)), t = Math.max(1, Math.min(10, thickness)), lo = t / 2, hi = t - lo;
        List<Rect> result = new ArrayList<>();
        switch (shape) {
            case 1 -> result.add(new Rect(-lo, -lo, hi, hi));
            case 2 -> {
                result.add(new Rect(-w-g, -h-g, w+g+1, -h-g+t)); result.add(new Rect(-w-g, h+g+1-t, w+g+1, h+g+1));
                result.add(new Rect(-w-g, -h-g, -w-g+t, h+g+1)); result.add(new Rect(w+g+1-t, -h-g, w+g+1, h+g+1));
            }
            case 4 -> {
                int rx = w+g, ry = h+g;
                for (int y=-ry; y<=ry; y++) for (int x=-rx; x<=rx; x++) {
                    double outer=(double)x*x/(rx*rx)+(double)y*y/(ry*ry);
                    int ix=Math.max(1,rx-t), iy=Math.max(1,ry-t);
                    double inner=(double)x*x/(ix*ix)+(double)y*y/(iy*iy);
                    if (outer<=1.08 && inner>=1) result.add(new Rect(x,y,x+1,y+1));
                }
            }
            case 5 -> { line(result,0,-h-g,-w-g,h+g,t); line(result,-w-g,h+g,w+g,h+g,t); line(result,w+g,h+g,0,-h-g,t); }
            case 6 -> { line(result,-w-g,h+g,0,-h-g,t); line(result,0,-h-g,w+g,h+g,t); }
            default -> {
                result.add(new Rect(-g-w,-lo,-g,hi)); result.add(new Rect(g+1,-lo,g+w+1,hi));
                result.add(new Rect(-lo,-g-h,hi,-g)); result.add(new Rect(-lo,g+1,hi,g+h+1));
            }
        }
        return result;
    }
    public static void line(List<Rect> result, int x0, int y0, int x1, int y1, int thickness) {
        int dx=Math.abs(x1-x0), sx=x0<x1?1:-1, dy=-Math.abs(y1-y0), sy=y0<y1?1:-1, err=dx+dy;
        for (;;) {
            int lo=thickness/2; result.add(new Rect(x0-lo,y0-lo,x0-lo+thickness,y0-lo+thickness));
            if (x0==x1 && y0==y1) break;
            int twice=2*err; if(twice>=dy){err+=dy;x0+=sx;} if(twice<=dx){err+=dx;y0+=sy;}
        }
    }
}
