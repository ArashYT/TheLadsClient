package com.thelads.core.client.hud;

/** GUI-space layout matching the editor reference; saved positions always win. */
public final class HudDefaults {
    private HudDefaults() {}
    public static int[] origin(String name,int w,int h,int ew,int eh) {
        if(name==null)return null;
        int x=5,y=40;
        switch(name){
            case "FPS" -> y=40;
            case "Coordinates" -> y=58;
            case "Memory" -> y=100;
            case "Biome" -> y=118;
            case "Speed" -> y=136;
            case "Time" -> y=154;
            case "Day" -> y=172;
            case "Clock" -> y=4;
            case "Stopwatch" -> y=22;
            case "ItemCounter" -> {x=5;y=h-eh-44;}
            case "ReachDisplay" -> {x=5;y=h-eh-24;}
            case "ServerAddress" -> {x=5;y=h-eh-4;}
            case "PortalCoordinates" -> {x=w/3;y=43;}
            case "Direction" -> {x=(w-ew)/2;y=32;}
            case "Keystrokes" -> {x=w/5;y=h/4;}
            case "CPS" -> {x=w/5;y=h/4+96;}
            case "PingHUD" -> {x=w/5;y=h/4+114;}
            case "Paperdoll" -> {
                x=w-ew-5;y=12;
                var bridge=com.thelads.core.client.bridge.LadsGameBridge.get();
                if(bridge!=null&&bridge.hasMinimap())x-=bridge.minimapSize()[0]+8;
            }
            case "Minimap", "XaeroMinimap", "XaeroWorldmap" -> {x=w-ew-5;y=44;}
            case "BossBar" -> {x=(w-ew)/2;y=8;}
            case "Scoreboard" -> {x=w-ew-5;y=(h-eh)/2;}
            case "Potion Effects" -> y=h*3/5;
            case "ArmorHUD" -> {x=w/2+98;y=h-eh-6;}
            case "Health" -> {x=w/2-190;y=h-eh-42;}
            case "Hunger" -> {x=w-ew-5;y=h-eh-42;}
            case "XP" -> {x=w-ew-5;y=h-eh-22;}
            case "TexturePacks" -> {x=w-ew-5;y=h-eh-5;}
            case "Voice Chat" -> {x=w/2+99;y=h-eh-1;}
            case "Voice Chat Group" -> {x=4;y=4;}
            case "ToggleSprint" -> {x=5;y=h-eh-64;}
            case "ToggleSneak" -> {x=5;y=h-eh-84;}
            default -> {return null;}
        }
        return new int[]{Math.max(0,Math.min(x,w-ew)),Math.max(0,Math.min(y,h-eh))};
    }
}
