package com.thelads.core.client.hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.lang.reflect.Method;
public class Test2 {
    public static void main(String[] args) {
        for (Method m : GuiGraphicsExtractor.class.getDeclaredMethods()) {
            System.out.println(m.getName());
        }
    }
}
