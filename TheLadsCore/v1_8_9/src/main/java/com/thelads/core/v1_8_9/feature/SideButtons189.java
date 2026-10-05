package com.thelads.core.v1_8_9.feature;

import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.win32.StdCallLibrary;
import org.apache.logging.log4j.LogManager;

/**
 * Mouse 4 and Mouse 5 on Windows. LWJGL 2.9.4 tells the side buttons apart by WM_XBUTTONDOWN's key-state flags
 * ((wParam &amp; 0xFF) == MK_XBUTTON1) instead of the button it names (the high word): Mouse 4 pressed while Shift, Ctrl or another
 * mouse button is held arrived as Mouse 5 (its release as Mouse 4, so Mouse 5 stayed down). A message hook on the client thread,
 * which pumps the window's messages, sets those flags to the named button before LWJGL's window procedure reads them. LWJGL's
 * classes are loaded untransformed (LaunchWrapper excludes them), so they can't be patched.
 */
public final class SideButtons189 {
    private static final int WH_GETMESSAGE = 3, WM_XBUTTONDOWN = 0x020B, XBUTTON1 = 1, MK_XBUTTON1 = 0x20, MK_XBUTTON2 = 0x40;
    /** Package-private for Probe172Keybinds (QA). */
    static WinUser.HHOOK hook;
    /** Kept for as long as the hook: JNA frees a callback nothing references. */
    private static final Proc PROC = SideButtons189::message;

    public interface Proc extends WinUser.HOOKPROC { LRESULT callback(int code, WPARAM removed, Pointer msg); }
    public interface Kernel32 extends StdCallLibrary { int GetCurrentThreadId(); }

    private SideButtons189() {}

    /** Once, on the client thread. */
    public static void install() {
        if (hook != null || !Platform.isWindows()) return;
        try {
            int thread = ((Kernel32) Native.loadLibrary("kernel32", Kernel32.class)).GetCurrentThreadId();
            hook = User32.INSTANCE.SetWindowsHookEx(WH_GETMESSAGE, PROC, null, thread);
            if (hook == null) LogManager.getLogger("TheLadsCore-1.8.9").warn("Side mouse buttons: no message hook (error {})", Native.getLastError());
        } catch (Throwable t) {
            LogManager.getLogger("TheLadsCore-1.8.9").warn("Side mouse buttons: no message hook: " + t);
        }
    }

    /** QA (Probe172Keybinds): LWJGL as shipped, to show what the hook fixes. */
    static void remove() {
        if (hook != null) User32.INSTANCE.UnhookWindowsHookEx(hook);
        hook = null;
    }

    // MSG: hwnd, message, wParam; the button is wParam's high word, the flags its low word.
    private static LRESULT message(int code, WPARAM removed, Pointer msg) {
        if (code >= 0 && msg != null && msg.getInt(Pointer.SIZE) == WM_XBUTTONDOWN) {
            int wParam = msg.getInt(2L * Pointer.SIZE);
            msg.setInt(2L * Pointer.SIZE, wParam & 0xFFFF0000 | (wParam >>> 16 == XBUTTON1 ? MK_XBUTTON1 : MK_XBUTTON2));
        }
        return User32.INSTANCE.CallNextHookEx(hook, code, removed, msg);
    }
}
