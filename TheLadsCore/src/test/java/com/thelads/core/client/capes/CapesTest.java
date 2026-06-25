package com.thelads.core.client.capes;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.thelads.core.client.capes.handler.CosmeticaData;
import com.thelads.core.client.capes.handler.MCMData;
import com.thelads.core.client.capes.util.CapesUtils;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class CapesTest {

    @Test
    public void testCapeConfigDefaults() {
        CapeConfig config = new CapeConfig();
        assertEquals(CapeType.MINECRAFT, config.getClientCapeType());
        assertTrue(config.getEnableOptifine());
        assertFalse(config.getEnableLabyMod());
        assertTrue(config.getEnableMinecraftCapesMod());
        assertTrue(config.getEnableCosmetica());
        assertTrue(config.getEnableCloaksPlus());
        assertTrue(config.getEnableElytraTexture());

        config.setClientCapeType(CapeType.OPTIFINE);
        assertEquals(CapeType.OPTIFINE, config.getClientCapeType());
    }

    @Test
    public void testCapeTypeCycle() {
        assertEquals(CapeType.OPTIFINE, CapeType.MINECRAFT.cycle());
        assertEquals(CapeType.LABYMOD, CapeType.OPTIFINE.cycle());
        assertEquals(CapeType.COSMETICA, CapeType.LABYMOD.cycle());
        assertEquals(CapeType.MINECRAFTCAPES, CapeType.COSMETICA.cycle());
        assertEquals(CapeType.CLOAKSPLUS, CapeType.MINECRAFTCAPES.cycle());
        assertEquals(CapeType.MINECRAFT, CapeType.CLOAKSPLUS.cycle());
    }

    @Test
    public void testCosmeticaDataRecord() {
        CosmeticaData.CapeData capeData = new CosmeticaData.CapeData("Cosmetica", "image_data", 5);
        CosmeticaData data = new CosmeticaData(capeData);

        assertEquals(capeData, data.cape());
        assertEquals(capeData, data.getCape());
        assertEquals("Cosmetica", capeData.origin());
        assertEquals("Cosmetica", capeData.getOrigin());
        assertEquals("image_data", capeData.image());
        assertEquals("image_data", capeData.getImage());
        assertEquals(5, capeData.extraInfo());
        assertEquals(5, capeData.getExtraInfo());
        assertTrue(capeData.isAnimated());

        CosmeticaData.CapeData staticCape = new CosmeticaData.CapeData("Cosmetica", "image_data", 0);
        assertFalse(staticCape.isAnimated());
    }

    @Test
    public void testMCMDataRecord() {
        MCMData data = new MCMData("http://cape", "http://animated");
        assertEquals("http://cape", data.cape_url());
        assertEquals("http://cape", data.getCape_url());
        assertEquals("http://animated", data.animated_cape_url());
        assertEquals("http://animated", data.getAnimated_cape_url());
    }

    @Test
    public void testIsValidProfile() {
        GameProfile profile = org.mockito.Mockito.mock(GameProfile.class);
        assertTrue(CapesUtils.isValidProfile(profile));
        assertFalse(CapesUtils.isValidProfile(null));
    }
}
