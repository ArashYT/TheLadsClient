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
        UUID v4Uuid = UUID.fromString("00000000-0000-4000-a000-000000000000");

        // No texture properties -> returns false
        GameProfile profileV4NoTextures = org.mockito.Mockito.mock(GameProfile.class);
        org.mockito.Mockito.when(profileV4NoTextures.id()).thenReturn(v4Uuid);
        org.mockito.Mockito.when(profileV4NoTextures.name()).thenReturn("Player1");
        org.mockito.Mockito.when(profileV4NoTextures.properties()).thenReturn(new com.mojang.authlib.properties.PropertyMap(com.google.common.collect.HashMultimap.create()));
        assertFalse(CapesUtils.isValidProfile(profileV4NoTextures));

        // Add valid textures property
        String json = "{\"profileName\":\"Player1\"}";
        String base64Value = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        com.google.common.collect.Multimap<String, Property> backing = com.google.common.collect.HashMultimap.create();
        backing.put("textures", new Property("textures", base64Value));
        com.mojang.authlib.properties.PropertyMap mutableMap = new com.mojang.authlib.properties.PropertyMap(backing);

        GameProfile profileV4 = org.mockito.Mockito.mock(GameProfile.class);
        org.mockito.Mockito.when(profileV4.id()).thenReturn(v4Uuid);
        org.mockito.Mockito.when(profileV4.name()).thenReturn("Player1");
        org.mockito.Mockito.when(profileV4.properties()).thenReturn(mutableMap);

        // V4 uuid with valid textures -> true
        assertTrue(CapesUtils.isValidProfile(profileV4));

        // V2 UUID (version 2)
        UUID v2Uuid = UUID.fromString("00000000-0000-2000-a000-000000000000");
        GameProfile profileV2 = org.mockito.Mockito.mock(GameProfile.class);
        org.mockito.Mockito.when(profileV2.id()).thenReturn(v2Uuid);
        org.mockito.Mockito.when(profileV2.name()).thenReturn("Player1");
        org.mockito.Mockito.when(profileV2.properties()).thenReturn(mutableMap);
        assertTrue(CapesUtils.isValidProfile(profileV2));

        // V2 UUID with non-matching profile name
        GameProfile profileV2WrongName = org.mockito.Mockito.mock(GameProfile.class);
        org.mockito.Mockito.when(profileV2WrongName.id()).thenReturn(v2Uuid);
        org.mockito.Mockito.when(profileV2WrongName.name()).thenReturn("WrongPlayer");
        org.mockito.Mockito.when(profileV2WrongName.properties()).thenReturn(mutableMap);
        assertFalse(CapesUtils.isValidProfile(profileV2WrongName));
    }
}
