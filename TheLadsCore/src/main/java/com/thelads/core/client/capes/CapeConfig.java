package com.thelads.core.client.capes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.PrintWriter;
import net.fabricmc.loader.api.FabricLoader;

public final class CapeConfig {
    private CapeType clientCapeType = CapeType.MINECRAFT;
    private boolean enableOptifine = true;
    private boolean enableLabyMod;
    private boolean enableMinecraftCapesMod = true;
    private boolean enableCosmetica = true;
    private boolean enableCloaksPlus = true;
    private boolean enableElytraTexture = true;

    public CapeType getClientCapeType() {
        return this.clientCapeType;
    }

    public void setClientCapeType(CapeType capeType) {
        if (capeType != null) {
            this.clientCapeType = capeType;
        }
    }

    public boolean getEnableOptifine() {
        return this.enableOptifine;
    }

    public void setEnableOptifine(boolean bl) {
        this.enableOptifine = bl;
    }

    public boolean getEnableLabyMod() {
        return this.enableLabyMod;
    }

    public void setEnableLabyMod(boolean bl) {
        this.enableLabyMod = bl;
    }

    public boolean getEnableMinecraftCapesMod() {
        return this.enableMinecraftCapesMod;
    }

    public void setEnableMinecraftCapesMod(boolean bl) {
        this.enableMinecraftCapesMod = bl;
    }

    public boolean getEnableCosmetica() {
        return this.enableCosmetica;
    }

    public void setEnableCosmetica(boolean bl) {
        this.enableCosmetica = bl;
    }

    public boolean getEnableCloaksPlus() {
        return this.enableCloaksPlus;
    }

    public void setEnableCloaksPlus(boolean bl) {
        this.enableCloaksPlus = bl;
    }

    public boolean getEnableElytraTexture() {
        return this.enableElytraTexture;
    }

    public void setEnableElytraTexture(boolean bl) {
        this.enableElytraTexture = bl;
    }

    public void save() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        File configFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), "capes.json5");
        String json = gson.toJson(JsonParser.parseString(gson.toJson(this)));
        try (PrintWriter out = new PrintWriter(configFile)) {
            out.println(json);
        } catch (Exception exception) {
            // Log/ignore exception to mimic original behaviour where exception would be swallowed/handled by closeFinally or caller
        }
    }
}
