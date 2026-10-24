package com.thelads.core.client.hud;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Media Player (Lunar's Media Player, Badlion's Music): what Windows says is playing, from its media session API
 * (GlobalSystemMediaTransportControlsSessionManager: Spotify, browsers, Media Player, any app that shows in the volume flyout). Read
 * once a second on a daemon thread while the widget is on, through WinRT's raw calls with the JNA every game version ships (3.4 on
 * 1.8.9, 5.x on the others; the same subset as FileDialogs). Nothing is sent anywhere. Elsewhere than Windows it is unavailable.
 */
public final class MediaSession {
    private static final Logger LOG = LoggerFactory.getLogger("TheLadsCore");
    public static final boolean SUPPORTED = System.getProperty("os.name", "").startsWith("Windows");
    // WinRT interface ids (windows.media.control.idl) and IAsyncInfo's.
    static final String MANAGER_CLASS = "Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager";
    static final String IID_MANAGER_STATICS = "2050C4EE-11A0-57DE-AED7-C97C70338245", IID_ASYNC_INFO = "00000036-0000-0000-C000-000000000046";
    // Vtable slots: IUnknown 0-2, IInspectable 3-5, then each interface's own methods in declaration order.
    private static final int RELEASE = 2, QUERY_INTERFACE = 0, REQUEST_ASYNC = 6, GET_CURRENT_SESSION = 6, ASYNC_STATUS = 7, GET_RESULTS = 8,
        TRY_GET_MEDIA_PROPERTIES = 7, GET_TIMELINE = 8, GET_PLAYBACK_INFO = 9, TITLE = 6, ARTIST = 9, END_TIME = 7, POSITION = 10,
        LAST_UPDATED = 11, PLAYBACK_STATUS = 7;
    private static final int STATUS_PLAYING = 4, ASYNC_COMPLETED = 1;
    /** DateTime and TimeSpan count 100 ns ticks; DateTime from 1601-01-01 UTC. */
    private static final long EPOCH_1601_MS = 11644473600000L;

    /** What is playing, as last read (title empty: nothing). Written by the reader thread, read by the widget. */
    public static volatile String title = "", artist = "";
    public static volatile boolean playing;
    /** Position and length in ms, and System.currentTimeMillis the position was true at (it runs on from there while playing). */
    public static volatile long positionMs, durationMs, positionAt;
    private static volatile long wantedAt;
    private static Thread reader;

    private MediaSession() {}

    /** The widget is on (asked once a tick): the reader runs while it is wanted and stops a few seconds after. */
    public static synchronized void want() {
        wantedAt = System.currentTimeMillis();
        if (!SUPPORTED || reader != null) return;
        reader = new Thread(MediaSession::run, "Lads media session");
        reader.setDaemon(true);
        reader.start();
    }

    /** Where the track is now, in ms: the last read position, run on while playing, within its length. */
    public static long position(long millis) {
        long at = positionMs + (playing ? millis - positionAt : 0);
        return durationMs > 0 ? Math.max(0, Math.min(durationMs, at)) : Math.max(0, at);
    }

    private static void run() {
        Pointer manager = null;
        try {
            int init = call("combase", "RoInitialize", 1); // multithreaded
            if (init < 0 && init != 0x80010106) throw new IllegalStateException("RoInitialize failed: 0x" + Integer.toHexString(init));
            manager = manager();
            while (System.currentTimeMillis() - wantedAt < 5000) {
                read(manager);
                Thread.sleep(1000);
            }
        } catch (Throwable failure) { // no JNA, no WinRT media API (Windows 8 or older): the widget shows nothing
            LOG.warn("Lads media player: the Windows media session can't be read ({})", failure.toString());
            title = "";
        } finally {
            if (manager != null) com(manager, RELEASE);
            synchronized (MediaSession.class) {
                reader = null;
            }
        }
    }

    private static Pointer manager() throws InterruptedException {
        Memory hstring = new Memory(Native.POINTER_SIZE), out = new Memory(Native.POINTER_SIZE);
        check("WindowsCreateString", call("combase", "WindowsCreateString", wide(MANAGER_CLASS), MANAGER_CLASS.length(), hstring));
        try {
            check("RoGetActivationFactory", call("combase", "RoGetActivationFactory", hstring.getPointer(0), guid(IID_MANAGER_STATICS), out));
        } finally {
            call("combase", "WindowsDeleteString", hstring.getPointer(0));
        }
        Pointer statics = out.getPointer(0);
        try {
            check("RequestAsync", com(statics, REQUEST_ASYNC, out));
            return await(out.getPointer(0));
        } finally {
            com(statics, RELEASE);
        }
    }

    /** One read of the current session into the fields; nothing playing clears them. */
    static void read(Pointer manager) throws InterruptedException {
        Memory out = new Memory(Native.POINTER_SIZE);
        check("GetCurrentSession", com(manager, GET_CURRENT_SESSION, out));
        Pointer session = out.getPointer(0);
        if (session == null) {
            title = "";
            playing = false;
            return;
        }
        try {
            check("TryGetMediaPropertiesAsync", com(session, TRY_GET_MEDIA_PROPERTIES, out));
            Pointer properties = await(out.getPointer(0));
            String name, by;
            try {
                name = string(properties, TITLE);
                by = string(properties, ARTIST);
            } finally {
                com(properties, RELEASE);
            }
            check("GetPlaybackInfo", com(session, GET_PLAYBACK_INFO, out));
            Pointer info = out.getPointer(0);
            boolean nowPlaying;
            try {
                Memory status = new Memory(4);
                check("PlaybackStatus", com(info, PLAYBACK_STATUS, status));
                nowPlaying = status.getInt(0) == STATUS_PLAYING;
            } finally {
                com(info, RELEASE);
            }
            check("GetTimelineProperties", com(session, GET_TIMELINE, out));
            Pointer timeline = out.getPointer(0);
            long end, position, updated;
            try {
                end = ticks(timeline, END_TIME);
                position = ticks(timeline, POSITION);
                updated = ticks(timeline, LAST_UPDATED);
            } finally {
                com(timeline, RELEASE);
            }
            long now = System.currentTimeMillis();
            long updatedMs = updated / 10_000 - EPOCH_1601_MS;
            durationMs = end / 10_000;
            // The position is from when the app last reported it; a playing track has moved on since (apps report rarely).
            positionMs = position / 10_000 + (nowPlaying && updated > 0 && updatedMs <= now ? now - updatedMs : 0);
            positionAt = now;
            playing = nowPlaying;
            artist = by;
            title = name;
        } finally {
            com(session, RELEASE);
        }
    }

    /** Waits (up to 2 s) for a WinRT async operation and returns its result, releasing the operation. */
    static Pointer await(Pointer operation) throws InterruptedException {
        Memory out = new Memory(Native.POINTER_SIZE), status = new Memory(4);
        try {
            check("QueryInterface IAsyncInfo", com(operation, QUERY_INTERFACE, guid(IID_ASYNC_INFO), out));
            Pointer info = out.getPointer(0);
            try {
                for (int i = 0; ; i++) {
                    check("get_Status", com(info, ASYNC_STATUS, status));
                    if (status.getInt(0) != 0) break; // 0 = started
                    if (i == 200) throw new IllegalStateException("media session call timed out");
                    Thread.sleep(10);
                }
            } finally {
                com(info, RELEASE);
            }
            if (status.getInt(0) != ASYNC_COMPLETED) throw new IllegalStateException("media session call failed: status " + status.getInt(0));
            check("GetResults", com(operation, GET_RESULTS, out));
            return out.getPointer(0);
        } finally {
            com(operation, RELEASE);
        }
    }

    /** An HSTRING property getter's text (empty for none). */
    static String string(Pointer object, int slot) {
        Memory out = new Memory(Native.POINTER_SIZE), length = new Memory(4);
        check("string property", com(object, slot, out));
        Pointer hstring = out.getPointer(0);
        if (hstring == null) return "";
        try {
            Pointer chars = Function.getFunction("combase", "WindowsGetStringRawBuffer", Function.C_CONVENTION).invokePointer(new Object[] {hstring, length});
            int n = length.getInt(0);
            return chars == null || n <= 0 ? "" : new String(chars.getCharArray(0, Math.min(n, 512)));
        } finally {
            call("combase", "WindowsDeleteString", hstring);
        }
    }

    /** A TimeSpan or DateTime property getter's 100 ns ticks. */
    static long ticks(Pointer object, int slot) {
        Memory out = new Memory(8);
        check("time property", com(object, slot, out));
        return out.getLong(0);
    }

    // The raw calls FileDialogs makes too (C_CONVENTION: JNA 3.4 and 5.x number ALT_CONVENTION differently).
    static int call(String library, String name, Object... args) {
        return Function.getFunction(library, name, Function.C_CONVENTION).invokeInt(args);
    }

    static int com(Pointer object, int index, Object... args) {
        Object[] all = new Object[args.length + 1];
        all[0] = object;
        System.arraycopy(args, 0, all, 1, args.length);
        Pointer method = object.getPointer(0).getPointer((long) index * Native.POINTER_SIZE);
        return Function.getFunction(method, Function.C_CONVENTION).invokeInt(all);
    }

    static void check(String what, int hresult) {
        if (hresult < 0) throw new IllegalStateException(what + " failed: 0x" + Integer.toHexString(hresult));
    }

    /** A GUID in memory ("XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX"): Data1-3 little-endian, Data4 as written. */
    static Memory guid(String text) {
        String hex = text.replace("-", "");
        Memory memory = new Memory(16);
        memory.setInt(0, (int) Long.parseLong(hex.substring(0, 8), 16));
        memory.setShort(4, (short) Integer.parseInt(hex.substring(8, 12), 16));
        memory.setShort(6, (short) Integer.parseInt(hex.substring(12, 16), 16));
        for (int i = 0; i < 8; i++) memory.setByte(8 + i, (byte) Integer.parseInt(hex.substring(16 + 2 * i, 18 + 2 * i), 16));
        return memory;
    }

    /** A NUL-terminated UTF-16 string. */
    static Memory wide(String text) {
        char[] chars = (text + '\0').toCharArray();
        Memory memory = new Memory(2L * chars.length);
        memory.write(0, chars, 0, chars.length);
        return memory;
    }
}
