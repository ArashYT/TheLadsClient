/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.platform.InputConstants
 *  com.mojang.blaze3d.platform.InputConstants$Key
 *  com.mojang.blaze3d.platform.InputConstants$Type
 *  com.mojang.blaze3d.platform.Window
 *  net.minecraft.client.KeyMapping
 *  net.minecraft.client.KeyMapping$Category
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.input.InputQuirks
 *  org.lwjgl.glfw.GLFW
 */
package com.thelads.core.features.alwayson.clientsort.util;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.thelads.core.mixin.alwayson.clientsort.client.accessor.KeyMappingAccessor;
import com.thelads.core.features.alwayson.clientsort.util.Localization;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.InputQuirks;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class KeybindManager {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register((Identifier)Identifier.fromNamespaceAndPath("clientsort", "main"));
    public static final KeyMapping EDIT_KEY = new KeyMapping(Localization.translationKey("key", "edit"), InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping CANCEL_AUTO_KEY = new KeyMapping(Localization.translationKey("key", "cancelAuto"), InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping SORT_KEY = new KeyMapping(Localization.translationKey("key", "op.sort"), InputConstants.Type.MOUSE, 2, CATEGORY);
    public static final KeyMapping STACK_FILL_KEY = new KeyMapping(Localization.translationKey("key", "op.stackFill"), InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping MATCH_TRANSFER_KEY = new KeyMapping(Localization.translationKey("key", "op.matchTransfer"), InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping TRANSFER_KEY = new KeyMapping(Localization.translationKey("key", "op.transfer"), InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final List<KeyMapping> KEYBINDS = List.of(EDIT_KEY, CANCEL_AUTO_KEY, SORT_KEY, STACK_FILL_KEY, MATCH_TRANSFER_KEY, TRANSFER_KEY);

    public static void bindKey(KeyMapping keybind, InputConstants.Key key) {
        keybind.setKey(key);
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
    }

    public static boolean isDown(KeyMapping keybind) {
        return KeybindManager.isKeyDown(((KeyMappingAccessor)keybind).clientsort$getKey());
    }

    public static boolean isKeyDown(InputConstants.Key key) {
        long window = Minecraft.getInstance().getWindow().handle();
        if (key.equals((Object)InputConstants.UNKNOWN)) {
            return false;
        }
        if (key.getType().equals((Object)InputConstants.Type.MOUSE)) {
            return GLFW.glfwGetMouseButton((long)window, (int)key.getValue()) == 1;
        }
        return GLFW.glfwGetKey((long)window, (int)key.getValue()) == 1;
    }

    public static boolean hasControlDown() {
        if (InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY) {
            return InputConstants.isKeyDown((Window)Minecraft.getInstance().getWindow(), (int)343) || InputConstants.isKeyDown((Window)Minecraft.getInstance().getWindow(), (int)347);
        }
        return InputConstants.isKeyDown((Window)Minecraft.getInstance().getWindow(), (int)341) || InputConstants.isKeyDown((Window)Minecraft.getInstance().getWindow(), (int)345);
    }

    public static boolean hasShiftDown() {
        return InputConstants.isKeyDown((Window)Minecraft.getInstance().getWindow(), (int)340) || InputConstants.isKeyDown((Window)Minecraft.getInstance().getWindow(), (int)344);
    }

    public static boolean hasAltDown() {
        return InputConstants.isKeyDown((Window)Minecraft.getInstance().getWindow(), (int)342) || InputConstants.isKeyDown((Window)Minecraft.getInstance().getWindow(), (int)346);
    }
}
