package com.thelads.core.client.title;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.social.FriendEntry;
import com.thelads.core.client.social.SocialState;
import com.thelads.core.client.title.TitleScreenTheme.Rect;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import java.util.List;
import java.util.UUID;

/**
 * What the custom title screens share for their two popups, the Panorama dropdown and the chat sidebar. A version's title screen calls
 * {@link #render} last in its scaled canvas (before tooltips), offers every press, scroll, key and typed character first, and has the
 * Panorama button call {@link #openPanorama}. Coordinates are canvas pixels, keys are GLFW codes. Visual only: no packets of its own.
 */
public final class TitleOverlay {
    private static final PanoramaMenu PANORAMA = new PanoramaMenu();
    private static ChatSidebar sidebar;

    private TitleOverlay() {}

    private static ChatSidebar sidebar() {
        if (sidebar == null) {
            sidebar = new ChatSidebar(new ChatSidebar.Source() {
                @Override public List<FriendEntry> friends() { return SocialState.get().friends(); }
                @Override public List<SocialState.Dm> dms(UUID friend) { return SocialState.get().dms(friend); }
                @Override public String status() { return SocialState.get().connectionStatus(); }
                @Override public void openChat(UUID friend) { SocialState.get().handleAction("dm:" + friend); }
                @Override public void openFriends() { SocialState.get().handleAction("open_friends"); }
            });
        }
        return sidebar;
    }

    private static boolean chatSupported;

    /** A version with a Friends screen calls this when it builds its title: the chat sidebar needs one to open (1.21.11 and 26.2 have none). */
    public static void enableChat() { chatSupported = true; }

    /** The chat sidebar belongs to the Friends & Social module: off, no tab. */
    private static boolean socialOn() {
        Module module = ModuleManager.getInstance().getModule(SocialState.MODULE);
        return chatSupported && (module == null || module.isEnabled());
    }

    /** A title screen was (re)built: both popups close. */
    public static void reset() {
        PANORAMA.close();
        if (sidebar != null) sidebar.close();
    }

    /** The Panorama button was pressed: toggles the list under it (a note instead while the release list is still loading). */
    public static void openPanorama(int x, int y, int width, int height) {
        if (PANORAMA.isOpen()) PANORAMA.close();
        else PANORAMA.open(new Rect(x, y, width, height));
    }

    /** Last in the title's frame, mouse in canvas pixels, dt seconds since the last frame. */
    public static void render(LadsGraphics g, int sw, int sh, int mx, int my, float dt, boolean reducedMotion) {
        if (socialOn()) sidebar().render(g, sw, sh, mx, my, dt, reducedMotion);
        PANORAMA.render(g, sw, sh, mx, my, dt);
    }

    /** True when a popup took the press: the title must not pass it on. */
    public static boolean mouseClicked(double x, double y, int button, int sw, int sh) {
        if (PANORAMA.click(x, y, button)) return true;
        return socialOn() && sidebar().click(x, y, button, sw, sh);
    }

    public static boolean mouseScrolled(double amount) {
        return PANORAMA.wheel(amount) || (sidebar != null && sidebar.wheel(amount));
    }

    public static boolean keyPressed(int glfwKey) {
        return PANORAMA.key(glfwKey) || (sidebar != null && sidebar.key(glfwKey));
    }

    public static boolean charTyped(char c) {
        return PANORAMA.isOpen() || (sidebar != null && sidebar.typed(c));
    }

    /** A popup is out: input belongs to it (the sidebar's filter takes typed text). */
    public static boolean active() {
        return PANORAMA.isOpen() || (sidebar != null && sidebar.isOpen());
    }

    /** The sidebar's filter box has the keyboard: the game's own single-key shortcuts (Friends on O...) must wait. */
    public static boolean capturingText() {
        return sidebar != null && sidebar.capturing();
    }
}
