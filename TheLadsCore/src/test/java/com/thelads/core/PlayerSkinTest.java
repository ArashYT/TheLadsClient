package com.thelads.core;
import org.junit.jupiter.api.Test;
public class PlayerSkinTest {
    @Test
    public void test() throws Exception {
        Class<?> clz = Class.forName("net.minecraft.core.ClientAsset$Texture");
        for (java.lang.reflect.Method m : clz.getMethods()) {
            System.out.println("TEX METHOD: " + m);
        }
    }
}
