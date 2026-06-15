package com.thelads.core.client;

import net.minecraft.client.renderer.texture.TextureManager;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;

public class TestUtilsTest {
    @Test
    public void printMethods() {
        for (Method m : TextureManager.class.getDeclaredMethods()) {
            System.out.println(m.getName());
        }
    }
}
