package com.thelads.core.client.util;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.thelads.core.client.bridge.LadsGameBridge;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every file and folder picker the client opens, on every game version. On Windows it is the modern Explorer dialog
 * (IFileOpenDialog, through the JNA every game version ships: 3.4 on 1.8.9, 5.x on the others), owned by the game window so it
 * stays on top of it, and shown on its own thread so the game keeps drawing (the game window takes no input while it is open).
 * Elsewhere, or when that dialog cannot open, the game's own dialog opens instead (LadsGameBridge.gameFileDialog: SDL on 26.x,
 * tinyfd on 1.20.1, AWT on 1.8.9). The future completes off the game thread: the chosen path, or null when cancelled.
 */
public final class FileDialogs {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final AtomicBoolean OPEN = new AtomicBoolean();

    private FileDialogs() {}

    /** One file; {@code extensions} without dots ("png", "mp3"); none lists every file. */
    public static CompletableFuture<Path> openFile(String title, String filterName, String... extensions) {
        return open(title, false, filterName, extensions);
    }

    public static CompletableFuture<Path> openFolder(String title) {
        return open(title, true, null);
    }

    private static CompletableFuture<Path> open(String title, boolean folder, String filterName, String... extensions) {
        if (!System.getProperty("os.name", "").startsWith("Windows")) return LadsGameBridge.get().gameFileDialog(title, folder, filterName, extensions);
        if (!OPEN.compareAndSet(false, true)) return failed(new IllegalStateException("A file dialog is already open"));
        CompletableFuture<Path> result = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            Path picked;
            try {
                picked = Explorer.pick(title, folder, filterName, extensions);
            } catch (Throwable failure) { // no JNA or no COM: the game's own dialog
                OPEN.set(false);
                LOGGER.warn("Lads file dialog: the Windows dialog did not open ({}); using the game's own", failure.toString());
                LadsGameBridge.get().gameFileDialog(title, folder, filterName, extensions)
                    .whenComplete((path, error) -> { if (error != null) result.completeExceptionally(error); else result.complete(path); });
                return;
            }
            OPEN.set(false);
            result.complete(picked);
        }, "Lads file dialog");
        thread.setDaemon(true);
        thread.start();
        return result;
    }

    /** The game's own dialog where it has none of its own (1.8.9): AWT's, files only, on its own thread so the game keeps drawing. */
    public static CompletableFuture<Path> awt(String title, boolean folder, String filterName, String... extensions) {
        if (folder || java.awt.GraphicsEnvironment.isHeadless())
            return failed(new IllegalStateException("No " + (folder ? "folder" : "file") + " dialog in this game"));
        if (!OPEN.compareAndSet(false, true)) return failed(new IllegalStateException("A file dialog is already open"));
        CompletableFuture<Path> result = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            java.awt.FileDialog dialog = null;
            try {
                dialog = new java.awt.FileDialog((java.awt.Frame) null, title, java.awt.FileDialog.LOAD);
                if (extensions.length > 0) {
                    dialog.setFile("*." + String.join(";*.", extensions)); // the Windows dialog's filter
                    dialog.setFilenameFilter((dir, name) -> matches(name, extensions));
                }
                dialog.setAlwaysOnTop(true);
                dialog.setVisible(true);
                String file = dialog.getFile(), directory = dialog.getDirectory();
                result.complete(file == null ? null : directory == null ? Paths.get(file) : Paths.get(directory, file));
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            } finally {
                if (dialog != null) dialog.dispose();
                OPEN.set(false);
            }
        }, "Lads file dialog");
        thread.setDaemon(true);
        thread.start();
        return result;
    }

    static boolean matches(String name, String... extensions) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String extension : extensions) if (lower.endsWith("." + extension.toLowerCase(Locale.ROOT))) return true;
        return extensions.length == 0;
    }

    private static <T> CompletableFuture<T> failed(Throwable failure) {
        CompletableFuture<T> result = new CompletableFuture<>();
        result.completeExceptionally(failure);
        return result;
    }

    /**
     * IFileOpenDialog through JNA's raw function calls: only API that JNA 3.4 (1.8.9) and 5.x share, no jna-platform. Throws
     * before the dialog shows when it can't (the caller falls back); a cancel is null.
     */
    static final class Explorer {
        private static final String CLSID_FILE_OPEN_DIALOG = "DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7";
        private static final String IID_IFILE_OPEN_DIALOG = "D57C7288-D4AD-4768-BE02-9D969532D960";
        private static final int CANCELLED = 0x800704C7; // HRESULT_FROM_WIN32(ERROR_CANCELLED)
        private static final int FOS_NOCHANGEDIR = 0x8, FOS_PICKFOLDERS = 0x20, FOS_FORCEFILESYSTEM = 0x40, FOS_PATHMUSTEXIST = 0x800,
            FOS_FILEMUSTEXIST = 0x1000;
        // IFileOpenDialog's vtable (IUnknown, IModalWindow, IFileDialog) and IShellItem's.
        private static final int RELEASE = 2, SHOW = 3, SET_FILE_TYPES = 4, SET_OPTIONS = 9, GET_OPTIONS = 10, SET_TITLE = 17, GET_RESULT = 20,
            GET_DISPLAY_NAME = 5;
        private static final int SIGDN_FILESYSPATH = 0x80058000;
        /** The filter strings, kept alive (and reachable) while a dialog that read them is open. */
        private static volatile Object filters;

        private Explorer() {}

        static Path pick(String title, boolean folder, String filterName, String[] extensions) {
            Pointer owner = gameWindow();
            int init = call("ole32", "CoInitializeEx", Pointer.NULL, 0x2 | 0x4); // apartment threaded, no OLE1 DDE
            if (init < 0) throw new IllegalStateException(hresult("CoInitializeEx", init));
            try {
                Memory out = new Memory(Native.POINTER_SIZE);
                check("CoCreateInstance", call("ole32", "CoCreateInstance", guid(CLSID_FILE_OPEN_DIALOG), Pointer.NULL, 1, guid(IID_IFILE_OPEN_DIALOG), out));
                Pointer dialog = out.getPointer(0);
                try {
                    Memory options = new Memory(4);
                    check("GetOptions", com(dialog, GET_OPTIONS, options));
                    check("SetOptions", com(dialog, SET_OPTIONS, options.getInt(0) | FOS_NOCHANGEDIR | FOS_FORCEFILESYSTEM | FOS_PATHMUSTEXIST
                        | (folder ? FOS_PICKFOLDERS : FOS_FILEMUSTEXIST)));
                    check("SetTitle", com(dialog, SET_TITLE, new WString(title)));
                    if (!folder && extensions.length > 0) {
                        Memory name = wide(filterName != null ? filterName : String.join(", ", extensions)), spec = wide("*." + String.join(";*.", extensions));
                        Memory specs = new Memory(2L * Native.POINTER_SIZE); // one COMDLG_FILTERSPEC {name, spec}
                        specs.setPointer(0, name);
                        specs.setPointer(Native.POINTER_SIZE, spec);
                        filters = new Object[] {name, spec, specs};
                        check("SetFileTypes", com(dialog, SET_FILE_TYPES, 1, specs));
                    }
                    int shown = com(dialog, SHOW, owner);
                    if (shown == CANCELLED) return null;
                    check("Show", shown);
                    check("GetResult", com(dialog, GET_RESULT, out));
                    Pointer item = out.getPointer(0);
                    try {
                        check("GetDisplayName", com(item, GET_DISPLAY_NAME, SIGDN_FILESYSPATH, out));
                        Pointer text = out.getPointer(0);
                        try {
                            return Paths.get(read(text));
                        } finally {
                            call("ole32", "CoTaskMemFree", text);
                        }
                    } finally {
                        com(item, RELEASE);
                    }
                } finally {
                    com(dialog, RELEASE);
                    filters = null;
                }
            } finally {
                call("ole32", "CoUninitialize");
            }
        }

        /**
         * The game's window, which owns the dialog: the foreground window when it is this process's (the player just clicked in the
         * game), else this process's largest visible top-level window. Null: the dialog opens unowned.
         */
        static Pointer gameWindow() {
            int pid = call("kernel32", "GetCurrentProcessId");
            Pointer foreground = pointer("user32", "GetForegroundWindow");
            if (foreground != null && ours(foreground, pid)) return foreground;
            Pointer best = null;
            long bestArea = 0;
            Memory rect = new Memory(16);
            for (Pointer window = pointer("user32", "FindWindowExW", null, null, null, null); window != null;
                 window = pointer("user32", "FindWindowExW", null, window, null, null)) {
                if (!ours(window, pid) || call("user32", "IsWindowVisible", window) == 0 || pointer("user32", "GetWindow", window, 4) != null) continue;
                call("user32", "GetWindowRect", window, rect);
                long area = (long) (rect.getInt(8) - rect.getInt(0)) * (rect.getInt(12) - rect.getInt(4));
                if (area > bestArea) { best = window; bestArea = area; }
            }
            return best;
        }

        static boolean ours(Pointer window, int pid) {
            Memory owner = new Memory(4);
            call("user32", "GetWindowThreadProcessId", window, owner);
            return owner.getInt(0) == pid;
        }

        static int call(String library, String name, Object... args) {
            return Function.getFunction(library, name, Function.ALT_CONVENTION).invokeInt(args);
        }

        static Pointer pointer(String library, String name, Object... args) {
            return Function.getFunction(library, name, Function.ALT_CONVENTION).invokePointer(args);
        }

        /** A COM method: slot {@code index} of the object's vtable, the object first. */
        static int com(Pointer object, int index, Object... args) {
            Object[] all = new Object[args.length + 1];
            all[0] = object;
            System.arraycopy(args, 0, all, 1, args.length);
            Pointer method = object.getPointer(0).getPointer((long) index * Native.POINTER_SIZE);
            return Function.getFunction(method, Function.ALT_CONVENTION).invokeInt(all);
        }

        static void check(String what, int hresult) {
            if (hresult < 0) throw new IllegalStateException(hresult(what, hresult));
        }

        static String hresult(String what, int hresult) {
            return what + " failed: 0x" + Integer.toHexString(hresult);
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

        static String read(Pointer text) {
            StringBuilder out = new StringBuilder();
            for (long at = 0; ; at += 2) {
                char c = text.getChar(at);
                if (c == 0) return out.toString();
                out.append(c);
            }
        }
    }
}
