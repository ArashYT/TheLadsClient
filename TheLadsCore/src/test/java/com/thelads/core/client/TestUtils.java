package com.thelads.core.client;

import net.minecraft.client.renderer.texture.TextureManager;
import java.lang.reflect.Method;

public class TestUtils {
    public static void main(String[] args) {
        for (Method m : TextureManager.class.getDeclaredMethods()) {
            System.out.println(m.getName());
        }
    }
}
