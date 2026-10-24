package com.thelads.core.client.hud;

/** GUI-space layout matching the editor reference; saved positions always win. */
public final class HudDefaults {
    private HudDefaults() {}
    /** Right of the default left column (FPS to Potion Effects) and of the Armor HUD beside the hotbar on the smallest GUIs. */
    private static final int LEFT=146;
    /** hotbar: the vanilla hotbar's box (HudAnchors.hotbar); what sits beside it lines up with its bottom and follows its lift. */
    public static int[] origin(String name,int w,int h,int ew,int eh,HudGroupLayout.Rect hotbar) {
        if(name==null)return null;
        int x=5,y=40;
        // The lowest a widget near the middle may start: its bottom just above the action bar (and the hotbar group under it).
        int floor=hotbar.y()-54-eh;
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
            // Parity 1.9.19's widgets (all off by default), each in a slot of its own clear of the default HUD (DefaultHudOverlapTest):
            // rows under the boss bar, the crosshair cluster, a band right of the left column, a band left of the minimap and
            // sidebar, and beside the hotbar. On GUIs of 960x540 and more they all fit at once.
            case "CompassStrip" -> {x=w/2-ew/2;y=30;}
            case "ViewAngles" -> {x=80;y=30;}
            case "SystemResources" -> {x=w/2+80;y=30;}
            case "DeathInfo" -> {x=Math.min(w/2+104,w-104-ew);y=48;}
            case "ShaderPack" -> {x=w-ew-5;y=38;}
            case "TPS" -> {x=80;y=48;}
            case "Playtime" -> {x=144;y=48;}
            case "PlayerCounter" -> {x=80;y=66;}
            case "EntityCounter" -> {x=216;y=66;}
            case "BlockCounter" -> {x=80;y=84;}
            case "PotionCounter" -> {x=156;y=84;}
            case "BlockInfo" -> {x=w/2-ew/2;y=50;}
            case "BlockAbove" -> {x=w/2-ew/2;y=100;}
            case "DamageIndicator" -> {x=w/2-ew/2;y=h/2-52-eh;}
            case "HitIndicator" -> {x=(w-ew)/2;y=(h-eh)/2;}
            case "JumpReset" -> {x=w/2-ew/2;y=Math.min(h/2+52,floor);}
            case "HeldItemLore" -> {x=w/2-ew/2;y=Math.min(h/2+70,floor);}
            case "ComboCounter" -> {x=Math.max(LEFT,w/2-52-ew);y=Math.min(h/2-eh/2,floor);}
            case "Cooldowns" -> {x=Math.min(w/2+52,w-126-ew);y=Math.min(h/2-eh/2,floor);}
            case "PvPInfo" -> {x=LEFT;y=Math.min(120,floor);}
            case "MouseTrail" -> {x=LEFT+76;y=Math.min(120,floor);}
            case "MediaPlayer" -> {x=LEFT;y=Math.min(172,floor);}
            case "CustomText" -> {x=LEFT;y=Math.min(217,floor);}
            case "CustomShape" -> {x=LEFT;y=Math.min(237,floor);}
            case "PlayerHead" -> {x=LEFT+104;y=Math.min(237,floor);}
            case "TotemPopCounter" -> {x=Math.max(LEFT,w-130-ew);y=66;}
            case "HorseStats" -> {x=Math.max(LEFT,w-130-ew);y=Math.min(96,floor);}
            case "ItemTracker" -> {x=Math.max(LEFT,w-130-ew);y=Math.min(137,floor);}
            case "CustomImage" -> {x=Math.max(LEFT,w-130-ew);y=Math.min(193,floor);}
            // Right of the hotbar above Toggle Sprint; where it does not fit there (the smallest GUIs), above the action bar.
            case "InventoryHUD" -> {
                if(w-ew-5>=hotbar.right()+2){x=w-ew-5;y=h-eh-20;}
                else{x=134;y=floor;}
            }
            case "Hearts" -> {x=hotbar.right()+7;y=hotbar.bottom()-eh-44;}
            case "Protection" -> {x=hotbar.right()+7;y=hotbar.bottom()-eh-24;}
            default -> {return null;}
        }
        return new int[]{Math.max(0,Math.min(x,w-ew)),Math.max(0,Math.min(y,h-eh))};
    }
}
