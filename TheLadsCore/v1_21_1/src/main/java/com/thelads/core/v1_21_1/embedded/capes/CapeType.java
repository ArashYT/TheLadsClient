// Ported from Capes 1.5.4+1.21 by Cael (LGPL-2.1-only) from Kotlin to Java; modified by The Lads: remapped to Mojang mappings and repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.capes;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public enum CapeType {
    MINECRAFT("Minecraft"), OPTIFINE("OptiFine"), LABYMOD("LabyMod"), WYNNTILS("Wynntils"), MINECRAFTCAPES("MinecraftCapes"), COSMETICA("Cosmetica"), CLOAKSPLUS("Cloaks+");

    private final String stylized;

    CapeType(String stylized) {
        this.stylized = stylized;
    }

    public CapeType cycle() {
        return switch (this) {
            case MINECRAFT -> OPTIFINE;
            case OPTIFINE -> LABYMOD;
            case LABYMOD -> WYNNTILS;
            case WYNNTILS -> COSMETICA;
            case COSMETICA -> MINECRAFTCAPES;
            case MINECRAFTCAPES -> CLOAKSPLUS;
            case CLOAKSPLUS -> MINECRAFT;
        };
    }

    public String getURL(GameProfile profile) {
        CapeConfig config = Capes.getConfig();
        return switch (this) {
            case OPTIFINE -> config.getEnableOptifine() ? "http://s.optifine.net/capes/" + profile.getName() + ".png" : null;
            case LABYMOD -> config.getEnableLabyMod() ? "https://dl.labymod.net/capes/" + profile.getId() : null;
            case WYNNTILS -> config.getEnableWynntils() ? "https://athena.wynntils.com/user/getInfo" : null;
            case COSMETICA -> config.getEnableCosmetica() ? "https://api.cosmetica.cc/get/cloak?username=" + profile.getName() + "&uuid=" + profile.getId() + "&nothirdparty" : null;
            case MINECRAFTCAPES -> config.getEnableMinecraftCapesMod() ? "https://api.minecraftcapes.net/profile/" + profile.getId().toString().replace("-", "") : null;
            case CLOAKSPLUS -> config.getEnableCloaksPlus() ? "http://161.35.130.99/capes/" + profile.getName() + ".png" : null;
            case MINECRAFT -> null;
        };
    }

    public Component getToggleText(boolean enabled) {
        return CommonComponents.optionStatus(Component.nullToEmpty(stylized), enabled);
    }

    public Component getText() {
        return Component.translatable("options.capes.capetype", stylized);
    }

}
