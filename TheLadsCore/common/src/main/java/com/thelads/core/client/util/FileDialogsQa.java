package com.thelads.core.client.util;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.thelads.core.client.bridge.LadsGameBridge;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only, Windows, a machine nobody watches (it opens dialogs on the desktop): every version's file pickers. FileDialogs' file and
 * folder pickers, then the game's own ones (LadsGameBridge.gameFileDialog) for comparison: each dialog is found among this process's
 * windows, checked for the modern Explorer dialog (its DUIViewWndClassName view; the old one has none) and an owner window,
 * photographed (PrintWindow) into {@code folder}/dialog-&lt;name&gt;.png and closed, which must complete its future with null.
 * Logs "Lads dialogs capture END: n passed, 0 failed" (or FAILED).
 */
public final class FileDialogsQa {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final String[] NAMES = {"lads-file", "lads-folder", "game-file", "game-folder"};
    private final Path folder;
    private final List<String> failures = new ArrayList<>();
    private int step, wait, passed, patience;
    private CompletableFuture<Path> open;
    private Pointer dialog;

    public FileDialogsQa(Path folder) { this.folder = folder; }

    public List<String> failures() { return failures; }

    /** One client tick (any screen); true once finished. */
    public boolean tick() {
        if (wait > 0) { wait--; return false; }
        int which = step / 4;
        if (which >= NAMES.length) {
            if (failures.isEmpty()) LOGGER.info("Lads dialogs capture END: {} passed, 0 failed", passed);
            else LOGGER.error("Lads dialogs capture FAILED: {} passed, {} failed: {}", passed, failures.size(), failures);
            return true;
        }
        String name = NAMES[which];
        boolean lads = which < 2, isFolder = which % 2 == 1;
        try {
            switch (step % 4) {
                case 0 -> {
                    open = lads ? (isFolder ? FileDialogs.openFolder("Lads QA folder dialog") : FileDialogs.openFile("Lads QA file dialog", "Images (PNG)", "png"))
                        : LadsGameBridge.get().gameFileDialog("Game QA " + (isFolder ? "folder" : "file") + " dialog", isFolder, "Images (PNG)", "png");
                    dialog = null;
                    patience = 0;
                    step++;
                    wait = 20;
                }
                case 1 -> {
                    if (open.isDone()) { // refused (AWT has no folder dialog) or failed before showing
                        String why = open.isCompletedExceptionally() ? open.handle((p, e) -> e.toString()).join() : "completed with " + open.join();
                        check(!lads, name + ": no dialog showed (" + why + ")");
                        step += 3;
                        return false;
                    }
                    if (dialog == null) dialog = findDialog();
                    if (dialog == null) {
                        if (++patience > 400) throw new IllegalStateException(name + ": no dialog window in 20 s");
                        return false;
                    }
                    wait = 40; // let it paint
                    step++;
                }
                case 2 -> {
                    boolean modern = hasDescendant(dialog, "DUIViewWndClassName"), owned = FileDialogs.Explorer.pointer("user32", "GetWindow", dialog, 4) != null;
                    Path shot = folder.resolve("dialog-" + name + ".png");
                    capture(dialog, shot);
                    LOGGER.info("Lads dialogs capture {}: modern Explorer dialog {}, owned by the game window {}, title '{}', photo {}", name, modern, owned, text(dialog), shot);
                    if (lads) check(modern && owned, name + ": the modern dialog, on top of the game window (modern " + modern + ", owned " + owned + ")");
                    else check(true, name + ": the game's own dialog is " + (modern ? "the modern one" : "the old one") + (owned ? ", owned" : ", unowned"));
                    FileDialogs.Explorer.call("user32", "PostMessageW", dialog, 0x10, Pointer.NULL, Pointer.NULL); // WM_CLOSE: a cancel
                    patience = 0;
                    step++;
                }
                case 3 -> { // never block this thread on it: the closing dialog hands focus back to the game window, owned by this thread
                    if (!open.isDone()) {
                        if (++patience > 400) throw new IllegalStateException(name + ": still open 20 s after it was closed");
                        return false;
                    }
                    Path picked = open.get();
                    check(picked == null, name + ": closing it cancels (null: " + picked + ")");
                    step++;
                    wait = 20;
                }
                default -> throw new IllegalStateException();
            }
        } catch (Exception | Error failure) {
            check(false, name + ": " + failure);
            if (dialog != null) FileDialogs.Explorer.call("user32", "PostMessageW", dialog, 0x10, Pointer.NULL, Pointer.NULL);
            step = (which + 1) * 4;
            wait = 40;
        }
        return false;
    }

    /** A picker opened by a screen's own button (1.8.9's Skin changer): a dialog of this process shows now. */
    public static boolean showing() {
        return findDialog() != null;
    }

    /** That dialog photographed into {@code shot} and closed (a cancel); true when it is the modern one, owned by the game window. */
    public static boolean photographAndClose(Path shot) throws Exception {
        Pointer dialog = findDialog();
        boolean modern = hasDescendant(dialog, "DUIViewWndClassName"), owned = FileDialogs.Explorer.pointer("user32", "GetWindow", dialog, 4) != null;
        LOGGER.info("Lads dialogs capture {}: modern Explorer dialog {}, owned by the game window {}, title '{}'", shot.getFileName(), modern, owned, text(dialog));
        try {
            capture(dialog, shot);
        } finally {
            FileDialogs.Explorer.call("user32", "PostMessageW", dialog, 0x10, Pointer.NULL, Pointer.NULL); // WM_CLOSE: a cancel
        }
        return modern && owned;
    }

    /** This process's visible dialog-class (#32770) top-level window. */
    private static Pointer findDialog() {
        int pid = FileDialogs.Explorer.call("kernel32", "GetCurrentProcessId");
        for (Pointer window = FileDialogs.Explorer.pointer("user32", "FindWindowExW", null, null, null, null); window != null;
             window = FileDialogs.Explorer.pointer("user32", "FindWindowExW", null, window, null, null))
            if (FileDialogs.Explorer.ours(window, pid) && FileDialogs.Explorer.call("user32", "IsWindowVisible", window) != 0 && "#32770".equals(className(window)))
                return window;
        return null;
    }

    private static boolean hasDescendant(Pointer parent, String wanted) {
        for (Pointer child = FileDialogs.Explorer.pointer("user32", "FindWindowExW", parent, null, null, null); child != null;
             child = FileDialogs.Explorer.pointer("user32", "FindWindowExW", parent, child, null, null))
            if (wanted.equals(className(child)) || hasDescendant(child, wanted)) return true;
        return false;
    }

    private static String className(Pointer window) {
        Memory buffer = new Memory(512);
        int length = FileDialogs.Explorer.call("user32", "GetClassNameW", window, buffer, 256);
        return length <= 0 ? "" : new String(buffer.getCharArray(0, length));
    }

    private static String text(Pointer window) {
        Memory buffer = new Memory(512);
        int length = FileDialogs.Explorer.call("user32", "GetWindowTextW", window, buffer, 256);
        return length <= 0 ? "" : new String(buffer.getCharArray(0, length));
    }

    /** The window as it draws itself (PrintWindow, full content), whatever covers it, as a PNG. */
    private static void capture(Pointer window, Path file) throws Exception {
        Memory rect = new Memory(16);
        FileDialogs.Explorer.call("user32", "GetWindowRect", window, rect);
        int width = rect.getInt(8) - rect.getInt(0), height = rect.getInt(12) - rect.getInt(4);
        Pointer screen = FileDialogs.Explorer.pointer("user32", "GetDC", Pointer.NULL);
        Pointer memory = FileDialogs.Explorer.pointer("gdi32", "CreateCompatibleDC", screen);
        Pointer bitmap = FileDialogs.Explorer.pointer("gdi32", "CreateCompatibleBitmap", screen, width, height);
        Pointer old = FileDialogs.Explorer.pointer("gdi32", "SelectObject", memory, bitmap);
        try {
            FileDialogs.Explorer.call("user32", "PrintWindow", window, memory, 2);
            Memory info = new Memory(44); // BITMAPINFOHEADER, top-down 32-bit, and one spare colour
            for (int at = 0; at < 44; at += 4) info.setInt(at, 0);
            info.setInt(0, 40);
            info.setInt(4, width);
            info.setInt(8, -height);
            info.setShort(12, (short) 1);
            info.setShort(14, (short) 32);
            Memory pixels = new Memory(4L * width * height);
            FileDialogs.Explorer.pointer("gdi32", "SelectObject", memory, old);
            FileDialogs.Explorer.call("gdi32", "GetDIBits", memory, bitmap, 0, height, pixels, info, 0);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            int[] row = pixels.getIntArray(0, width * height);
            image.setRGB(0, 0, width, height, row, 0, width);
            Files.createDirectories(file.getParent());
            ImageIO.write(image, "png", file.toFile());
        } finally {
            FileDialogs.Explorer.call("gdi32", "DeleteObject", bitmap);
            FileDialogs.Explorer.call("gdi32", "DeleteDC", memory);
            FileDialogs.Explorer.call("user32", "ReleaseDC", Pointer.NULL, screen);
        }
    }

    private void check(boolean ok, String what) {
        if (ok) { passed++; LOGGER.info("Lads dialogs QA ok: {}", what); }
        else { failures.add(what); LOGGER.error("Lads dialogs QA FAILED: {}", what); }
    }
}
