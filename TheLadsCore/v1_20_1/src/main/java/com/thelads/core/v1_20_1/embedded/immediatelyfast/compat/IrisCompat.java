/*
 * This file is part of ImmediatelyFast - https://github.com/RaphiMC/ImmediatelyFast
 * Copyright (C) 2023-2026 RK_01/RaphiMC and contributors
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
// Modified by The Lads: repackaged into Lads Core (com.thelads.core.v1_20_1.embedded.immediatelyfast).
package com.thelads.core.v1_20_1.embedded.immediatelyfast.compat;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import com.thelads.core.v1_20_1.embedded.immediatelyfast.ImmediatelyFast;

import java.lang.invoke.*;
import java.lang.reflect.Field;
import java.util.function.BooleanSupplier;

public class IrisCompat {

    public static boolean IRIS_LOADED = false;

    public static BooleanSupplier isRenderingLevel;
    public static BooleanConsumer renderWithExtendedVertexFormat;
    public static TriConsumer<BufferBuilder, VertexFormat.Mode, VertexFormat> iris$beginWithoutExtending;

    private static String[] PACKAGE_NAMES = new String[] {
            "net.coderbot.iris.vertices",
            "net.irisshaders.iris.vertices"
    };

    public static void init() {
        IRIS_LOADED = true;
        Throwable throwable = null;
        for (int i = 0; i < PACKAGE_NAMES.length; i++) {
            String packageName = PACKAGE_NAMES[i];
            try {
                final MethodHandles.Lookup lookup = MethodHandles.lookup();
                final Class<?> immediateStateClass = Class.forName(packageName + ".ImmediateState");
                final Class<?> extendingBufferBuilderClass = Class.forName(packageName + ".ExtendingBufferBuilder");

                // The Lads: plain method handles instead of the Reflect library's FieldAccessor
                final Field isRenderingLevelField = immediateStateClass.getDeclaredField("isRenderingLevel");
                final Field renderWithExtendedVertexFormatField = immediateStateClass.getDeclaredField("renderWithExtendedVertexFormat");
                isRenderingLevelField.setAccessible(true);
                renderWithExtendedVertexFormatField.setAccessible(true);
                final MethodHandle getter = lookup.unreflectGetter(isRenderingLevelField);
                final MethodHandle setter = lookup.unreflectSetter(renderWithExtendedVertexFormatField);
                isRenderingLevel = () -> {
                    try {
                        return (boolean) getter.invoke();
                    } catch (Throwable t) {
                        throw new RuntimeException(t);
                    }
                };
                renderWithExtendedVertexFormat = value -> {
                    try {
                        setter.invoke(value);
                    } catch (Throwable t) {
                        throw new RuntimeException(t);
                    }
                };

                final MethodHandle iris$beginWithoutExtendingMH = lookup.findVirtual(extendingBufferBuilderClass, "iris$beginWithoutExtending", MethodType.methodType(void.class, VertexFormat.Mode.class, VertexFormat.class));
                final CallSite iris$beginWithoutExtendingCallSite = LambdaMetafactory.metafactory(lookup, "accept", MethodType.methodType(TriConsumer.class), MethodType.methodType(void.class, Object.class, Object.class, Object.class), iris$beginWithoutExtendingMH, iris$beginWithoutExtendingMH.type());
                iris$beginWithoutExtending = (TriConsumer<BufferBuilder, VertexFormat.Mode, VertexFormat>) iris$beginWithoutExtendingCallSite.getTarget().invoke();
                return;
            } catch (Throwable t) {
                throwable = t;
            }
        }

        ImmediatelyFast.LOGGER.error("Failed to initialize Iris compatibility. Try updating Iris and ImmediatelyFast before reporting this on GitHub", throwable);
        System.exit(-1);
    }

}
