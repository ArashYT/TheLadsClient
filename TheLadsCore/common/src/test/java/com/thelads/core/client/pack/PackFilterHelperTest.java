package com.thelads.core.client.pack;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PackFilterHelperTest {

    @Test
    public void testEmptyOrNullQueryMatchesAll() {
        assertTrue(PackFilterHelper.matches(null, "Faithful", "A great pack"));
        assertTrue(PackFilterHelper.matches("", "Faithful", "A great pack"));
        assertTrue(PackFilterHelper.matches("   ", "Faithful", "A great pack"));
    }

    @Test
    public void testNameMatchesCaseInsensitive() {
        assertTrue(PackFilterHelper.matches("faith", "Faithful 32x", "Standard textures"));
        assertTrue(PackFilterHelper.matches("FAITHFUL", "Faithful 32x", "Standard textures"));
        assertTrue(PackFilterHelper.matches("32x", "Faithful 32x", "Standard textures"));
        assertFalse(PackFilterHelper.matches("bare", "Faithful 32x", "Standard textures"));
    }

    @Test
    public void testDescriptionMatchesCaseInsensitive() {
        assertTrue(PackFilterHelper.matches("standard", "MyPack", "Standard textures for PvP"));
        assertTrue(PackFilterHelper.matches("pvp", "MyPack", "Standard textures for PvP"));
        assertFalse(PackFilterHelper.matches("rpg", "MyPack", "Standard textures for PvP"));
    }

    @Test
    public void testFormattingCodesStripped() {
        assertTrue(PackFilterHelper.matches("clean", "§6§lClean§r Pack", "§7Nice colors"));
        assertTrue(PackFilterHelper.matches("nice", "§6§lClean§r Pack", "§7Nice colors"));
        assertFalse(PackFilterHelper.matches("§6", "§6§lClean§r Pack", "§7Nice colors"));
    }

    @Test
    public void testNullFieldsSafe() {
        assertTrue(PackFilterHelper.matches(null, null, null));
        assertFalse(PackFilterHelper.matches("test", null, null));
        assertTrue(PackFilterHelper.matches("test", "test", null));
        assertTrue(PackFilterHelper.matches("test", null, "test"));
    }
}
