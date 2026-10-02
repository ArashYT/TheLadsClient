// Ported from Capes 1.5.11+26.2 by Cael (LGPL-2.1-only) from Kotlin to Java; modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.capes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;

public class CapeConfig {
    // Field names are the JSON keys of config/capes.json5.
    private CapeType clientCapeType = CapeType.MINECRAFT;
    private boolean enableOptifine = true;
    private boolean enableLabyMod = false;
    private boolean enableMinecraftCapesMod = false;
    private boolean enableCosmetica = false;
    private boolean enableCloaksPlus = false;
    private boolean enableElytraTexture = true;

    public CapeType getClientCapeType() {
        return clientCapeType;
    }

    public void setClientCapeType(CapeType clientCapeType) {
        this.clientCapeType = clientCapeType;
    }

    public boolean getEnableOptifine() {
        return enableOptifine;
    }

    public void setEnableOptifine(boolean enableOptifine) {
        this.enableOptifine = enableOptifine;
    }

    public boolean getEnableLabyMod() {
        return enableLabyMod;
    }

    public void setEnableLabyMod(boolean enableLabyMod) {
        this.enableLabyMod = enableLabyMod;
    }

    public boolean getEnableMinecraftCapesMod() {
        return enableMinecraftCapesMod;
    }

    public void setEnableMinecraftCapesMod(boolean enableMinecraftCapesMod) {
        this.enableMinecraftCapesMod = enableMinecraftCapesMod;
    }

    public boolean getEnableCosmetica() {
        return enableCosmetica;
    }

    public void setEnableCosmetica(boolean enableCosmetica) {
        this.enableCosmetica = enableCosmetica;
    }

    public boolean getEnableCloaksPlus() {
        return enableCloaksPlus;
    }

    public void setEnableCloaksPlus(boolean enableCloaksPlus) {
        this.enableCloaksPlus = enableCloaksPlus;
    }

    public boolean getEnableElytraTexture() {
        return enableElytraTexture;
    }

    public void setEnableElytraTexture(boolean enableElytraTexture) {
        this.enableElytraTexture = enableElytraTexture;
    }

    public void save() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        File configFile = new File(FabricLoader.getInstance().getConfigDir() + File.separator + "capes.json5");
        String json = gson.toJson(JsonParser.parseString(gson.toJson(this)));
        try (PrintWriter out = new PrintWriter(configFile)) {
            out.println(json);
        } catch (FileNotFoundException e) {
            throw new UncheckedIOException(e);
        }
    }

}
