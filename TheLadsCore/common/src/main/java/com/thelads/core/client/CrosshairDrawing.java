package com.thelads.core.client;

import java.util.ArrayList;
import java.util.List;

/** Bounded monochrome bitmap. Persisted rows never contain executable or external-resource data. */
public final class CrosshairDrawing {
    private final int width, height;
    private final boolean[] pixels;
    public CrosshairDrawing(int width, int height) {
        this.width=Math.max(3,Math.min(64,width)); this.height=Math.max(3,Math.min(64,height)); pixels=new boolean[this.width*this.height];
    }
    public int width(){return width;} public int height(){return height;}
    public boolean get(int x,int y){return x>=0&&y>=0&&x<width&&y<height&&pixels[y*width+x];}
    public void set(int x,int y,boolean value){if(x>=0&&y>=0&&x<width&&y<height)pixels[y*width+x]=value;}
    public CrosshairDrawing copy(){return fromRows(width,height,rows());}
    public List<String> rows(){var rows=new ArrayList<String>();for(int y=0;y<height;y++){var row=new StringBuilder();for(int x=0;x<width;x++)row.append(get(x,y)?'#':'.');rows.add(row.toString());}return rows;}
    public static CrosshairDrawing fromRows(int width,int height,List<String> rows){
        var drawing=new CrosshairDrawing(width,height); if(rows==null)return drawing;
        for(int y=0;y<Math.min(drawing.height,rows.size());y++){String row=rows.get(y);if(row!=null)for(int x=0;x<Math.min(drawing.width,row.length());x++)drawing.set(x,y,row.charAt(x)=='#');}return drawing;
    }
    public CrosshairDrawing resized(int width,int height){
        var next=new CrosshairDrawing(width,height); int dx=(next.width-this.width)/2,dy=(next.height-this.height)/2;
        for(int y=0;y<this.height;y++)for(int x=0;x<this.width;x++)next.set(x+dx,y+dy,get(x,y));return next;
    }
    public List<CrosshairDesign.Rect> rectangles(){
        var result=new ArrayList<CrosshairDesign.Rect>();
        for(int y=0;y<height;y++)for(int x=0;x<width;){if(!get(x,y)){x++;continue;}int start=x;while(x<width&&get(x,y))x++;result.add(new CrosshairDesign.Rect(start-width/2,y-height/2,x-width/2,y-height/2+1));}return result;
    }
}
