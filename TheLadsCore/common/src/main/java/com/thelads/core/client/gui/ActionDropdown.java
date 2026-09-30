package com.thelads.core.client.gui;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.PlayerActionOption;
import java.util.*;
import static com.thelads.core.client.gui.MenuGraphics.*;
/** Searchable multiple-selection dropdown; settings are saved by the owning screen. */
final class ActionDropdown {
    private boolean open;
    private String query = "";
    private int offset, selected, x, y, w, h;
    private double scroll;
    private long frame;
    private List<PlayerActionOption> actions = List.of(), filtered = List.of();
    private Runnable changed = () -> {};
    boolean isOpen() { return open; }
    void open(List<PlayerActionOption> values, Runnable save) { actions=values;changed=save;query="";offset=selected=0;scroll=0;frame=0;open=true;filter(); }
    private void filter() { filtered=actions.stream().filter(a->a.getName().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();offset=selected=0;scroll=0; }
    void render(LadsGraphics g,int mx,int my) {
        if(!open)return;
        w=Math.min(440,g.getScaledWidth()-24);h=Math.min(390,g.getScaledHeight()-24);x=(g.getScaledWidth()-w)/2;y=(g.getScaledHeight()-h)/2;
        long now=System.nanoTime();double dt=frame==0?0:Math.min(.1,(now-frame)/1e9);frame=now;
        int max=Math.max(0,filtered.size()*25-(h-99));offset=Math.clamp(offset,0,max);scroll+=(offset-scroll)*(1-Math.exp(-dt*20));
        g.fill(0,0,g.getScaledWidth(),g.getScaledHeight(),0xB8000000);round(g,x,y,w,h,PANEL);
        g.drawText("Display actions",x+12,y+11,TEXT);g.drawText("Done",x+w-43,y+11,ACCENT);
        g.fill(x+10,y+30,x+w-10,y+55,CARD);g.drawText(query.isEmpty()?"Search actions or states...":query+"|",x+17,y+38,TEXT);
        g.drawText("Select triggers. A dot marks a detected state.",x+12,y+64,MUTED);
        g.enableScissor(x+8,y+80,x+w-8,y+h-18);
        for(int i=0;i<filtered.size();i++) {
            var a=filtered.get(i);int ry=y+80+i*25-(int)Math.round(scroll);
            if(ry+24<y+80||ry>=y+h-18)continue;
            boolean hover=mx>=x+9&&mx<x+w-9&&my>=Math.max(ry,y+80)&&my<Math.min(ry+24,y+h-18);
            if(hover||i==selected)g.fill(x+9,ry,x+w-9,ry+24,CARD);
            g.drawText(a.get()?"[x]":"[ ]",x+15,ry+8,a.get()?ACCENT:MUTED);
            String label=a.getName().equals("Riding")?"Sitting / riding":a.getName();
            g.drawText(fit(g,label,w-83),x+40,ry+8,TEXT);
            if(a.detected())g.fill(x+w-23,ry+10,x+w-18,ry+15,0xFF88D8A0);
        }
        g.disableScissor();g.drawText(filtered.size()+" actions",x+12,y+h-12,MUTED);
    }
    boolean click(double mx,double my,int button) {
        if(!open)return false;
        if(button!=0)return true;
        if(mx<x||mx>=x+w||my<y||my>=y+h||mx>=x+w-54&&my<y+28){open=false;return true;}
        if(my>=y+80&&my<y+h-18){int index=(int)Math.floor((my-(y+80)+Math.round(scroll))/25);if(index>=0&&index<filtered.size()){selected=index;filtered.get(index).toggle();changed.run();}}
        return true;
    }
    boolean wheel(double amount){if(!open)return false;offset-=(int)(amount*30);return true;}
    boolean key(int key,int modifiers){
        if(!open)return false;
        if(key==256){open=false;return true;}
        if(key==259&&!query.isEmpty()){query=query.substring(0,query.offsetByCodePoints(query.length(),-1));filter();}
        if(key==65&&(modifiers&2)!=0){query="";filter();}
        if(key==264||key==265){selected=Math.clamp(selected+(key==264?1:-1),0,Math.max(0,filtered.size()-1));offset=Math.clamp(offset,selected*25-(h-125),selected*25);}
        if((key==257||key==32)&&!filtered.isEmpty()){filtered.get(selected).toggle();changed.run();}
        return true;
    }
    boolean type(int cp){if(!open)return false;if(Character.isValidCodePoint(cp)&&!Character.isISOControl(cp)&&query.length()<80){query+=new String(Character.toChars(cp));filter();}return true;}
}
