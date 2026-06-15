package com.thelads.core.client.capes;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public enum CapeType {
    MINECRAFT("Minecraft"),
    OPTIFINE("OptiFine"),
    LABYMOD("LabyMod"),
    MINECRAFTCAPES("MinecraftCapes"),
    COSMETICA("Cosmetica"),
    CLOAKSPLUS("Cloaks+");

    private final String stylized;

    CapeType(String stylized) {
        this.stylized = stylized;
    }

    public String getStylized() {
        return this.stylized;
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
        if (profile == null) {
            return null;
        }
        com.thelads.core.config.Module capesMod = com.thelads.core.config.ModuleManager.getInstance().getModule("Capes");
        if (capesMod != null) {
            if (!capesMod.isEnabled()) {
                return null;
            }
            return switch (this) {
                case OPTIFINE -> {
                    boolean enabled = true;
                    var opt = capesMod.getOption("OptiFine Capes");
                    if (opt instanceof com.thelads.core.config.BoolOption) enabled = ((com.thelads.core.config.BoolOption) opt).get();
                    yield enabled ? "http://s.optifine.net/capes/" + profile.name() + ".png" : null;
                }
                case LABYMOD -> {
                    boolean enabled = false;
                    var opt = capesMod.getOption("LabyMod Capes");
                    if (opt instanceof com.thelads.core.config.BoolOption) enabled = ((com.thelads.core.config.BoolOption) opt).get();
                    yield enabled ? "https://dl.labymod.net/capes/" + profile.id() : null;
                }
                case COSMETICA -> {
                    boolean enabled = true;
                    var opt = capesMod.getOption("Cosmetica Capes");
                    if (opt instanceof com.thelads.core.config.BoolOption) enabled = ((com.thelads.core.config.BoolOption) opt).get();
                    yield enabled ? "https://api.cosmetica.cc/v2/get/info?uuid=" + profile.id() : null;
                }
                case MINECRAFTCAPES -> {
                    boolean enabled = true;
                    var opt = capesMod.getOption("MinecraftCapes");
                    if (opt instanceof com.thelads.core.config.BoolOption) enabled = ((com.thelads.core.config.BoolOption) opt).get();
                    if (enabled) {
                        String uuidStr = profile.id().toString().replace("-", "");
                        yield "https://api.minecraftcapes.net/profile/" + uuidStr;
                    }
                    yield null;
                }
                case CLOAKSPLUS -> {
                    boolean enabled = true;
                    var opt = capesMod.getOption("CloaksPlus Capes");
                    if (opt instanceof com.thelads.core.config.BoolOption) enabled = ((com.thelads.core.config.BoolOption) opt).get();
                    yield enabled ? "http://161.35.130.99/capes/" + profile.name() + ".png" : null;
                }
                case MINECRAFT -> null;
            };
        }
        
        // Fallback for standalone/JUnit environments
        CapeConfig config = Capes.getCONFIG();
        if (config == null) return null;
        return switch (this) {
            case OPTIFINE -> config.getEnableOptifine() ? "http://s.optifine.net/capes/" + profile.name() + ".png" : null;
            case LABYMOD -> config.getEnableLabyMod() ? "https://dl.labymod.net/capes/" + profile.id() : null;
            case COSMETICA -> config.getEnableCosmetica() ? "https://api.cosmetica.cc/v2/get/info?uuid=" + profile.id() : null;
            case MINECRAFTCAPES -> {
                if (config.getEnableMinecraftCapesMod()) {
                    String uuidStr = profile.id().toString().replace("-", "");
                    yield "https://api.minecraftcapes.net/profile/" + uuidStr;
                }
                yield null;
            }
            case CLOAKSPLUS -> config.getEnableCloaksPlus() ? "http://161.35.130.99/capes/" + profile.name() + ".png" : null;
            case MINECRAFT -> null;
        };
    }

    public Component getToggleText(boolean enabled) {
        MutableComponent mutableComponent = CommonComponents.optionStatus(Component.literal(this.stylized), enabled);
        return mutableComponent;
    }

    public Component getText() {
        return Component.translatable("options.capes.capetype", this.stylized);
    }
}
