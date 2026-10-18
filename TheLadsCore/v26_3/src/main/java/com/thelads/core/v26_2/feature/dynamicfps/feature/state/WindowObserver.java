// SDL implementation for Minecraft 26.3. Dynamic FPS policy remains MIT-derived.
package com.thelads.core.v26_2.feature.dynamicfps.feature.state;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDLMouse;
import com.thelads.core.v26_2.feature.dynamicfps.DynamicFPSMod;
public final class WindowObserver {
    private final long address;
    private long previousFlags;
    private boolean isFocused, isHovered, isIconified;
    public WindowObserver(long address) { this.address = address; previousFlags = flags(); refresh(); }
    private long flags() { return SDLVideo.SDL_GetWindowFlags(address); }
    public long address() { return address; }
    public boolean isFocused() { return isFocused; }
    public boolean isHovered() { return isHovered; }
    public boolean isIconified() { return isIconified; }
    private void refresh() { isFocused = (flags() & SDLVideo.SDL_WINDOW_INPUT_FOCUS) != 0; isHovered = SDLMouse.SDL_GetMouseFocus() == address; isIconified = (flags() & SDLVideo.SDL_WINDOW_MINIMIZED) != 0; }
    /** 1.9.12 perf: SDL_GetWindowFlags runs on the main thread under Ixeris, so every call from the render thread waits for a
     *  round trip (with Sodium's own per-frame check: 5-10 % of render-thread time in the Legion bench). The flags are read at
     *  most every 50 ms, and at once when vanilla's own focus flag (set by the focus event) disagrees with ours, so the click
     *  that focuses the window is still seen by ClickIgnoreHandler. */
    private long nextPoll;

    public void poll() {
        long now = System.nanoTime();
        if (now - nextPoll < 0 && net.minecraft.client.Minecraft.getInstance().isWindowActive() == isFocused) return;
        nextPoll = now + 50_000_000L;
        long current = flags();
        if (current != previousFlags) {
            boolean gainedFocus = (current & SDLVideo.SDL_WINDOW_INPUT_FOCUS) != 0 && (previousFlags & SDLVideo.SDL_WINDOW_INPUT_FOCUS) == 0;
            previousFlags = current;
            refresh();
            if (gainedFocus) ClickIgnoreHandler.onFocus();
            DynamicFPSMod.onStatusChanged(true);
        }
    }
}
