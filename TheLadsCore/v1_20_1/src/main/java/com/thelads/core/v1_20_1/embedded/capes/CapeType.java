// Ported from Capes 1.5.10+26.3 by Cael (LGPL-2.1-only) from Kotlin to Java; modified by The Lads: ported to Minecraft 1.20.1 from the Lads 26.3 embedded copy.
package com.thelads.core.v1_20_1.embedded.capes;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public enum CapeType {
    MINECRAFT("Minecraft"), OPTIFINE("OptiFine"), LABYMOD("LabyMod"), MINECRAFTCAPES("MinecraftCapes"), COSMETICA("Cosmetica"), CLOAKSPLUS("Cloaks+");

    private final String stylized;

    CapeType(String stylized) {
        this.stylized = stylized;
    }

    public CapeType cycle() {
        return switch (this) {
            case MINECRAFT -> OPTIFINE;
            case OPTIFINE -> LABYMOD;
            case LABYMOD -> COSMETICA;
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
            case COSMETICA -> config.getEnableCosmetica() ? "https://api.cosmetica.cc/users/" + profile.getId() + "/cape" : null;
            case MINECRAFTCAPES -> config.getEnableMinecraftCapesMod() ? "https://api.minecraftcapes.net/profile/" + profile.getId().toString().replace("-", "") : null;
            case CLOAKSPLUS -> config.getEnableCloaksPlus() ? "http://161.35.130.99/capes/" + profile.getName() + ".png" : null;
            case MINECRAFT -> null;
        };
    }

    public Component getToggleText(boolean enabled) {
        return CommonComponents.optionStatus(Component.literal(stylized), enabled);
    }

    public Component getText() {
        return Component.translatable("options.capes.capetype", stylized);
    }

}
