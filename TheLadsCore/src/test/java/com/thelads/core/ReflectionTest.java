package com.thelads.core;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;

public class ReflectionTest {
    public static void main(String[] args) {
        System.out.println("=== Minecraft Fields ===");
        for (Field f : Minecraft.class.getDeclaredFields()) {
            if (f.getType().getName().contains("Overlay") || f.getType().getName().contains("Screen") || f.getName().contains("overlay")) {
                System.out.println("FIELD: " + f.getType().getName() + " " + f.getName());
            }
        }
        System.out.println("=== Minecraft Methods ===");
        for (Method m : Minecraft.class.getDeclaredMethods()) {
            if (m.getName().contains("Overlay") || m.getName().contains("overlay") || m.getName().contains("Screen") || m.getName().contains("screen")) {
                System.out.println("METHOD: " + m.getReturnType().getName() + " " + m.getName());
            }
        }
    }
}
