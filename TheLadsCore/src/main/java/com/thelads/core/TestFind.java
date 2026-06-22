package com.thelads.core.mixin.client.dynamiclights;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.client.Minecraft;

public class TestFind {
    public static void test(BlockGetter bg) {
        System.out.println(bg);
    }
}
