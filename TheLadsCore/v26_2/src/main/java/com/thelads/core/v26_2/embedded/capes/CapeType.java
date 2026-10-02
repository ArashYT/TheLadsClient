// Ported from Capes 1.5.11+26.2 by Cael (LGPL-2.1-only) from Kotlin to Java; modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.capes;

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
            case OPTIFINE -> config.getEnableOptifine() ? "http://s.optifine.net/capes/" + profile.name() + ".png" : null;
            case LABYMOD -> config.getEnableLabyMod() ? "https://dl.labymod.net/capes/" + profile.id() : null;
            case COSMETICA -> config.getEnableCosmetica() ? "https://api.cosmetica.cc/users/" + profile.id() + "/cape" : null;
            case MINECRAFTCAPES -> config.getEnableMinecraftCapesMod() ? "https://api.minecraftcapes.net/profile/" + profile.id().toString().replace("-", "") : null;
            case CLOAKSPLUS -> config.getEnableCloaksPlus() ? "http://161.35.130.99/capes/" + profile.name() + ".png" : null;
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
