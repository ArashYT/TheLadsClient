/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  it.unimi.dsi.fastutil.booleans.BooleanConsumer
 *  net.lenni0451.reflect.accessor.FieldAccessor
 *  net.lenni0451.reflect.stream.RStream
 */
package com.thelads.core.features.alwayson.immediatelyfast.util;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import java.lang.reflect.Field;
import java.util.function.BooleanSupplier;
import com.thelads.core.features.alwayson.immediatelyfast.ImmediatelyFast;

public class IrisCompat {
    public static boolean IRIS_LOADED = false;
    public static BooleanSupplier isRenderingLevel;
    public static BooleanConsumer renderWithExtendedVertexFormat;
    public static ThreadLocal<Boolean> skipExtension;

    public static void init() {
        IRIS_LOADED = true;
        try {
            Class<?> immediateStateClass = Class.forName("net.irisshaders.iris.vertices.ImmediateState");
            final Field isRenderingLevelField = immediateStateClass.getDeclaredField("isRenderingLevel");
            isRenderingLevelField.setAccessible(true);
            isRenderingLevel = () -> {
                try {
                    return isRenderingLevelField.getBoolean(null);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
            
            final Field renderWithExtendedVertexFormatField = immediateStateClass.getDeclaredField("renderWithExtendedVertexFormat");
            renderWithExtendedVertexFormatField.setAccessible(true);
            renderWithExtendedVertexFormat = (boolean value) -> {
                try {
                    renderWithExtendedVertexFormatField.setBoolean(null, value);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
            
            final java.lang.reflect.Field skipExtensionField = immediateStateClass.getDeclaredField("skipExtension");
            skipExtensionField.setAccessible(true);
            skipExtension = (ThreadLocal<Boolean>) skipExtensionField.get(null);
        }
        catch (Throwable t) {
            ImmediatelyFast.LOGGER.error("Failed to initialize Iris compatibility.", t);
            IRIS_LOADED = false;
        }
    }
}

