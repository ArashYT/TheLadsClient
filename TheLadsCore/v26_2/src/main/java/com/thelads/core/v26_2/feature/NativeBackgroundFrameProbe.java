package com.thelads.core.v26_2.feature;
/** Entry used by the combined in-world native check. Full profile checks also support isolated title QA. */
final class NativeBackgroundFrameProbe {
    private NativeBackgroundFrameProbe() {}
    static int run() throws ReflectiveOperationException {
        return com.thelads.core.v26_2.feature.dynamicfps.NativeDynamicFPSProbe.run();
    }
}
