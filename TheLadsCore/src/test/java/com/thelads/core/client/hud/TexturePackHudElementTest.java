package com.thelads.core.client.hud;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class TexturePackHudElementTest {

    @Test
    public void testTopPackName() throws Exception {
        TexturePackHudElement element = new TexturePackHudElement();
        Method m = TexturePackHudElement.class.getDeclaredMethod("topPackName", Minecraft.class);
        m.setAccessible(true);

        Minecraft mc = mock(Minecraft.class);
        Options optionsMock = mock(Options.class);
        java.lang.reflect.Field optionsField = Minecraft.class.getField("options");
        optionsField.setAccessible(true);
        optionsField.set(mc, optionsMock);

        // Edge case: Empty list
        optionsMock.resourcePacks = new ArrayList<>();
        assertEquals("Default", m.invoke(element, mc));

        // Edge case: Null options
        optionsField.set(mc, null);
        assertEquals("Default", m.invoke(element, mc));
        
        optionsMock = mock(Options.class);
        optionsField.set(mc, optionsMock);

        // Edge case: Single vanilla pack
        optionsMock.resourcePacks = new ArrayList<>(List.of("vanilla"));
        assertEquals("vanilla", m.invoke(element, mc));

        // Edge case: Fabric pack string manipulation
        optionsMock.resourcePacks = new ArrayList<>(List.of("vanilla", "file/mypack.zip"));
        assertEquals("mypack", m.invoke(element, mc));

        // Edge case: Fabric directory pack
        optionsMock.resourcePacks = new ArrayList<>(List.of("vanilla", "fabric/myfabricpack"));
        assertEquals("myfabricpack", m.invoke(element, mc));
    }
}
