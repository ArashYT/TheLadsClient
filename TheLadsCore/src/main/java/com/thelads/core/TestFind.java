package com.thelads.core;
import net.minecraft.client.player.AbstractClientPlayer;
public class TestFind {
    public static void run() {
        java.lang.reflect.Method[] methods = AbstractClientPlayer.class.getDeclaredMethods();
        for (java.lang.reflect.Method m : methods) {
            if (m.getName().equals("getSkin")) {
                System.out.println("Return type: " + m.getReturnType().getName());
            }
        }
    }
}
