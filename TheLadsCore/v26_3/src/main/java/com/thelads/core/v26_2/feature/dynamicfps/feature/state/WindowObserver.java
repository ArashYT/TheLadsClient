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
    public void poll() {
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
