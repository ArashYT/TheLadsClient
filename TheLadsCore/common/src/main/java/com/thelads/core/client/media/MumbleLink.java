package com.thelads.core.client.media;

import com.sun.jna.Function;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;

/**
 * Mumble Link Shared Memory Integration (Lunar / Badlion Mumble Link):
 * Connects Minecraft to external Mumble VoIP clients for 3D positional audio.
 * Writes player coordinates (avatar and camera vectors, identity, and server context)
 * to Windows shared memory ("MumbleLink" file mapping) each tick using standard JNA.
 * Client-only, visual/audio, not gated.
 */
public final class MumbleLink {
    public static final String NAME = "Mumble Link";
    public static boolean on = false;

    // Struct LinkedMem size in bytes: 4+4+12+12+12+512+12+12+12+512+4+256+4096 = 5460
    public static final int MEM_SIZE = 5460;
    private static Module module;
    private static Pointer fileMapping = null;
    private static Pointer sharedMemory = null;
    private static int tickCounter = 0;
    private static boolean failed = false;

    private static Function createFileMapping;
    private static Function mapViewOfFile;
    private static Function unmapViewOfFile;
    private static Function closeHandle;

    private MumbleLink() {}

    public static Module createModule() {
        Module m = new Module(NAME, "Transmits player coordinates and camera orientation to Mumble positional audio via shared memory.");
        m.setEnabled(false);
        return m;
    }

    /** Called once per client tick. */
    public static void tick() {
        if (module == null) module = ModuleManager.getInstance().getModule(NAME);
        on = module != null && module.isEnabled();
        if (!on || failed || !Platform.isWindows()) return;

        LadsGameBridge bridge = LadsGameBridge.get();
        if (bridge == null || !bridge.hasPlayer()) return;

        double px = bridge.getPlayerX();
        double py = bridge.getPlayerY();
        double pz = bridge.getPlayerZ();
        float yaw = bridge.getYaw();
        float pitch = bridge.getPitch();
        String name = bridge.getPlayerName();
        String server = bridge.getServerAddress();

        update(px, py, pz, yaw, pitch, name != null ? name : "Player", server != null ? server : "singleplayer");
    }

    public static synchronized void update(double x, double y, double z, float yaw, float pitch, String identity, String context) {
        if (!ensureMapping()) return;

        try {
            tickCounter++;
            // Calculate front look unit vector
            double yawRad = Math.toRadians(yaw);
            double pitchRad = Math.toRadians(pitch);
            float fx = (float) (-Math.sin(yawRad) * Math.cos(pitchRad));
            float fy = (float) (-Math.sin(pitchRad));
            float fz = (float) (Math.cos(yawRad) * Math.cos(pitchRad));

            // Top vector (head up)
            float tx = 0.0f;
            float ty = 1.0f;
            float tz = 0.0f;

            // 1. uiVersion (uint32_t, offset 0)
            sharedMemory.setInt(0, 2);

            // 2. uiTick (uint32_t, offset 4)
            sharedMemory.setInt(4, tickCounter);

            // 3. fAvatarPosition[3] (offset 8)
            sharedMemory.setFloat(8, (float) x);
            sharedMemory.setFloat(12, (float) y);
            sharedMemory.setFloat(16, (float) z);

            // 4. fAvatarFront[3] (offset 20)
            sharedMemory.setFloat(20, fx);
            sharedMemory.setFloat(24, fy);
            sharedMemory.setFloat(28, fz);

            // 5. fAvatarTop[3] (offset 32)
            sharedMemory.setFloat(32, tx);
            sharedMemory.setFloat(36, ty);
            sharedMemory.setFloat(40, tz);

            // 6. name[256] (wchar_t, offset 44)
            writeWideString(sharedMemory, 44, 256, "The Lads Client");

            // 7. fCameraPosition[3] (offset 556)
            sharedMemory.setFloat(556, (float) x);
            sharedMemory.setFloat(560, (float) y);
            sharedMemory.setFloat(564, (float) z);

            // 8. fCameraFront[3] (offset 568)
            sharedMemory.setFloat(568, fx);
            sharedMemory.setFloat(572, fy);
            sharedMemory.setFloat(576, fz);

            // 9. fCameraTop[3] (offset 580)
            sharedMemory.setFloat(580, tx);
            sharedMemory.setFloat(584, ty);
            sharedMemory.setFloat(588, tz);

            // 10. identity[256] (wchar_t, offset 592)
            writeWideString(sharedMemory, 592, 256, identity);

            // 11. context_len (uint32_t, offset 1104)
            byte[] ctxBytes = context != null ? context.getBytes(java.nio.charset.StandardCharsets.UTF_8) : new byte[0];
            int ctxLen = Math.min(ctxBytes.length, 255);
            sharedMemory.setInt(1104, ctxLen);

            // 12. context[256] (char, offset 1108)
            for (int i = 0; i < ctxLen; i++) {
                sharedMemory.setByte(1108 + i, ctxBytes[i]);
            }
            sharedMemory.setByte(1108 + ctxLen, (byte) 0);

            // 13. description[2048] (wchar_t, offset 1364)
            writeWideString(sharedMemory, 1364, 2048, "The Lads Client Positional Audio");
        } catch (Throwable t) {
            failed = true;
        }
    }

    private static boolean ensureMapping() {
        if (sharedMemory != null) return true;
        if (!Platform.isWindows()) return false;

        try {
            if (createFileMapping == null) {
                createFileMapping = Function.getFunction("kernel32", "CreateFileMappingW");
                mapViewOfFile = Function.getFunction("kernel32", "MapViewOfFile");
                unmapViewOfFile = Function.getFunction("kernel32", "UnmapViewOfFile");
                closeHandle = Function.getFunction("kernel32", "CloseHandle");
            }

            // CreateFileMappingW(INVALID_HANDLE_VALUE (-1), null, PAGE_READWRITE (4), 0, MEM_SIZE, "MumbleLink")
            fileMapping = (Pointer) createFileMapping.invoke(Pointer.class, new Object[] {
                Pointer.createConstant(-1L),
                null,
                4, // PAGE_READWRITE
                0,
                MEM_SIZE,
                new WString("MumbleLink")
            });
            if (fileMapping == null || Pointer.nativeValue(fileMapping) == 0) {
                fileMapping = null;
                return false;
            }

            // MapViewOfFile(fileMapping, FILE_MAP_ALL_ACCESS (0xF001F), 0, 0, MEM_SIZE)
            sharedMemory = (Pointer) mapViewOfFile.invoke(Pointer.class, new Object[] {
                fileMapping,
                0xF001F, // FILE_MAP_ALL_ACCESS
                0,
                0,
                MEM_SIZE
            });
            return sharedMemory != null && Pointer.nativeValue(sharedMemory) != 0;
        } catch (Throwable t) {
            failed = true;
            return false;
        }
    }

    private static void writeWideString(Pointer ptr, int offset, int maxChars, String str) {
        if (str == null) str = "";
        int len = Math.min(str.length(), maxChars - 1);
        for (int i = 0; i < len; i++) {
            ptr.setShort(offset + (i * 2L), (short) str.charAt(i));
        }
        ptr.setShort(offset + (len * 2L), (short) 0);
    }

    /** Clean-up hook on client shutdown. */
    public static synchronized void shutdown() {
        try {
            if (sharedMemory != null && unmapViewOfFile != null) {
                unmapViewOfFile.invoke(new Object[] { sharedMemory });
                sharedMemory = null;
            }
            if (fileMapping != null && closeHandle != null) {
                closeHandle.invoke(new Object[] { fileMapping });
                fileMapping = null;
            }
        } catch (Throwable ignored) {}
    }
}
