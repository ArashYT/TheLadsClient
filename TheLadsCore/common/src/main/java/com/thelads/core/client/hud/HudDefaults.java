package com.thelads.core.client.hud;

/** GUI-space layout matching the editor reference; saved positions always win. */
public final class HudDefaults {
    private HudDefaults() {}
    /** hotbar: the vanilla hotbar's box (HudAnchors.hotbar); what sits beside it lines up with its bottom and follows its lift. */
    public static int[] origin(String name,int w,int h,int ew,int eh,HudGroupLayout.Rect hotbar) {
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
            // Vanilla's place: its text at w/2 - width/2, its backdrop's bottom 61 px above the screen bottom (over the hotbar's lift).
            case ActionBar.MODULE -> {x=w/2-(ew-4)/2-2;y=hotbar.bottom()-61*hotbar.height()/22-eh;}
            case "Scoreboard" -> {x=w-ew-5;y=(h-eh)/2;}
            case "Potion Effects" -> y=h*3/5;
            case "ArmorHUD" -> {x=hotbar.right()+7;y=hotbar.bottom()-eh;}
            case "Health" -> {x=w/2-190;y=h-eh-42;}
            case "Hunger" -> {x=w-ew-5;y=h-eh-42;}
            case "XP" -> {x=w-ew-5;y=h-eh-22;}
            case "TexturePacks" -> {x=w-ew-5;y=h-eh-5;}
            case "Voice Chat" -> {x=hotbar.right()+6;y=hotbar.bottom()-eh;}
            case "Voice Chat Group" -> {x=4;y=4;}
            case com.thelads.core.modules.ToggleSprintModule.NAME -> {x=5;y=h-eh-64;}
            case "Session Stats" -> {x=5;y=h/3;}
            case "Bed Wars Height Limit" -> {x=(w-ew)/2;y=52;}
            case "ComboCounter" -> {x=w/5;y=h/4+132;}
            case "PvPInfo" -> {x=5;y=h/3+36;}
            case "PotionCounter" -> {x=5;y=h-eh-84;}
            case "ItemTracker" -> {x=w-ew-5;y=h/3;}
            case "Cooldowns" -> {x=w/2-ew/2;y=h-eh-70;}
            case "TPS" -> {x=5;y=76;}
            case "SystemResources" -> {x=5;y=190;}
            case "Playtime" -> {x=5;y=208;}
            case "CompassStrip" -> {x=(w-ew)/2;y=20;}
            case "ViewAngles" -> {x=(w-ew)/2;y=48;}
            case "MouseTrail" -> {x=w/5+72;y=h/4;}
            case "BlockCounter" -> {x=5;y=h-eh-104;}
            case "Protection" -> {x=hotbar.right()+7;y=hotbar.bottom()-eh-24;}
            case "DeathInfo" -> {x=5;y=h/3+72;}
            case "Hearts" -> {x=w/2-190;y=h-eh-58;}
            case "HitIndicator" -> {x=(w-ew)/2;y=(h-eh)/2;}
            case "DamageIndicator" -> {x=(w-ew)/2;y=h/2+28;}
            case "JumpReset" -> {x=(w-ew)/2;y=h/2+48;}
            case "MediaPlayer" -> {x=5;y=h/2-20;}
            case "HorseStats" -> {x=(w-ew)/2;y=h-eh-60;}
            case "PlayerCounter" -> {x=5;y=190;}
            case "TotemPopCounter" -> {x=w-ew-5;y=h/4;}
            case "BlockInfo" -> {x=(w-ew)/2;y=68;}
            case "TierTagger" -> {x=(w-ew)/2;y=h/2-40;}
            case "CustomText" -> {x=5;y=208;}
            case "CustomShape" -> {x=5;y=226;}
            case "PlayerHead" -> {x=5;y=244;}
            case "BlockAbove" -> {x=(w-ew)/2;y=84;}
            case "EntityCounter" -> {x=5;y=262;}
            case "ShaderPack" -> {x=w-ew-5;y=28;}
            case "HeldItemLore" -> {x=(w-ew)/2;y=h-eh-80;}
            default -> {return null;}
        }
        return new int[]{Math.max(0,Math.min(x,w-ew)),Math.max(0,Math.min(y,h-eh))};
    }
}
