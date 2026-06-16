/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.lwjgl.openal.ALC10
 *  org.lwjgl.system.MemoryStack
 */
package com.thelads.core.features.alwayson.raisesoundlimit.common;

import com.thelads.core.features.alwayson.raisesoundlimit.RSLSInjectorFFM;
import java.nio.IntBuffer;
import org.lwjgl.openal.ALC10;
import org.lwjgl.system.MemoryStack;

public class SourcesLimitProber {
    public static int probeSourcesLimit() {
        int sourcesCount;
        RSLSInjectorFFM.init();
        long device = ALC10.alcOpenDevice((CharSequence)null);
        SourcesLimitProber.checkALCError(device, "Open device");
        long context = ALC10.alcCreateContext((long)device, (IntBuffer)null);
        ALC10.alcMakeContextCurrent((long)context);
        try (MemoryStack memoryStack = MemoryStack.stackPush();){
            int attributes_length = ALC10.alcGetInteger((long)device, (int)4098);
            SourcesLimitProber.checkALCError(device, "Get attributes length");
            IntBuffer attributes = memoryStack.mallocInt(attributes_length);
            ALC10.alcGetIntegerv((long)device, (int)4099, (IntBuffer)attributes);
            SourcesLimitProber.checkALCError(device, "Get attributes");
            sourcesCount = SourcesLimitProber.getSourcesCount(attributes);
        }
        ALC10.alcMakeContextCurrent((long)0L);
        ALC10.alcDestroyContext((long)context);
        ALC10.alcCloseDevice((long)device);
        return sourcesCount;
    }

    private static int getSourcesCount(IntBuffer attributes) {
        attributes.position(0);
        while (attributes.remaining() >= 2) {
            int key = attributes.get();
            if (key == 0) {
                break;
            }
            int value = attributes.get();
            if (key == 4112) {
                return value;
            }
        }
        return 30;
    }

    private static void checkALCError(long device, String message) {
        int error = ALC10.alcGetError((long)device);
        if (error != 0) {
            throw new RuntimeException("%s failed: %s".formatted(message, SourcesLimitProber.getAlcErrorMessage(error)));
        }
    }

    private static String getAlcErrorMessage(int errorCode) {
        switch (errorCode) {
            case 40961: {
                return "Invalid device.";
            }
            case 40962: {
                return "Invalid context.";
            }
            case 40963: {
                return "Illegal enum.";
            }
            case 40964: {
                return "Invalid value.";
            }
            case 40965: {
                return "Unable to allocate memory.";
            }
        }
        return "An unrecognized error occurred.";
    }
}

