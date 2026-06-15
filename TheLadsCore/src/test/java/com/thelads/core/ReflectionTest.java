package com.thelads.core;
public class ReflectionTest {
    public static void main(String[] args) throws Exception {
        Class<?> clazz = Class.forName("net.minecraft.client.gui.GuiGraphicsExtractor");
        for (java.lang.reflect.Method m : clazz.getMethods()) {
            System.out.println("METHOD: " + m.getName() + " " + m.getReturnType().getName());
        }
        System.out.println("POSE METHOD RETURN TYPE:");
        try {
            Class<?> returnType = clazz.getMethod("pose").getReturnType();
            System.out.println("POSE RETURN TYPE: " + returnType.getName());
            for (java.lang.reflect.Method m : returnType.getMethods()) {
                System.out.println("POSE CLASS METHOD: " + m.getName());
            }
        } catch (Exception e) {}
    }
}
