package com.thelads.core.v1_8_9.feature;

import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.SprintTrace;
import com.thelads.core.modules.ToggleSprintModule;
import com.thelads.core.v1_8_9.mixin.EntityPlayerSPAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Toggle Sprint &amp; Sneak on 1.8.9 (ToggleSprintModule decides). EntityPlayerSPMixin and MovementInputFromOptionsMixin hand
 * Minecraft the Sprint and Sneak keys as the module reads them, so onLivingUpdate's own rules decide every sprint, as for a held
 * key; nothing calls setSprinting(true). The toggle keys are followed through their own events, like Zoom189: unbound (the
 * default), the Sprint and Sneak keys toggle; bound, those keys toggle and Sprint and Sneak are hold keys again.
 */
public final class Toggles189 {
    public static final KeyBinding TOGGLE_SPRINT = new KeyBinding("key.theladscore.toggle_sprint", Keyboard.KEY_NONE, "key.categories.movement");
    public static final KeyBinding TOGGLE_SNEAK = new KeyBinding("key.theladscore.toggle_sneak", Keyboard.KEY_NONE, "key.categories.movement");
    private static boolean sprintHeld, sneakHeld;
    /** QA (Probe170Sprint): every tick's sprint state and the state last sent to the server. */
    static SprintTrace trace;

    static ToggleSprintModule toggles() { return (ToggleSprintModule) ModuleManager.getInstance().getModule(ToggleSprintModule.NAME); }

    public static void register() {
        ClientRegistry.registerKeyBinding(TOGGLE_SPRINT);
        ClientRegistry.registerKeyBinding(TOGGLE_SNEAK);
        MinecraftForge.EVENT_BUS.register(new Toggles189());
        ToggleSprintModule toggles = toggles();
        toggles.sprintKey.setLabel(() -> label(TOGGLE_SPRINT, "Sprint"));
        toggles.sneakKey.setLabel(() -> label(TOGGLE_SNEAK, "Sneak"));
        Runnable controls = () -> Minecraft.getMinecraft().displayGuiScreen(new GuiControls(Minecraft.getMinecraft().currentScreen, Minecraft.getMinecraft().gameSettings));
        toggles.sprintKey.setAction(controls);
        toggles.sneakKey.setAction(controls);
    }

    private static String label(KeyBinding toggle, String vanilla) {
        return toggle.getKeyCode() == 0 ? "Same as " + vanilla : GameSettings.getKeyDisplayString(toggle.getKeyCode());
    }

    private static int sprintCode() {
        return TOGGLE_SPRINT.getKeyCode() != 0 ? TOGGLE_SPRINT.getKeyCode() : Minecraft.getMinecraft().gameSettings.keyBindSprint.getKeyCode();
    }

    private static int sneakCode() {
        return TOGGLE_SNEAK.getKeyCode() != 0 ? TOGGLE_SNEAK.getKeyCode() : Minecraft.getMinecraft().gameSettings.keyBindSneak.getKeyCode();
    }

    @SubscribeEvent
    public void key(InputEvent.KeyInputEvent event) {
        if (Keyboard.isRepeatEvent()) return;
        // Keys LWJGL cannot name arrive as character + 256, the code KeyBinding stores for them.
        press(Keyboard.getEventKey() == 0 ? Keyboard.getEventCharacter() + 256 : Keyboard.getEventKey(), Keyboard.getEventKeyState());
    }

    @SubscribeEvent
    public void mouse(InputEvent.MouseInputEvent event) {
        if (Mouse.getEventButton() >= 0) press(Mouse.getEventButton() - 100, Mouse.getEventButtonState()); // KeyBinding stores button - 100
    }

    private static void press(int code, boolean down) {
        Minecraft mc = Minecraft.getMinecraft();
        if (code == 0 || mc.currentScreen != null || mc.thePlayer == null) return;
        if (code == sprintCode()) {
            if (down && !sprintHeld && toggles().pressSprint()) ConfigManager.save();
            sprintHeld = down;
        }
        if (code == sneakCode()) {
            if (down && !sneakHeld && toggles().pressSneak()) ConfigManager.save();
            sneakHeld = down;
        }
    }

    /** The mixins: Minecraft's own read of a key, with Sprint and Sneak as Toggle Sprint &amp; Sneak has them. */
    public static boolean isKeyDown(KeyBinding binding) {
        GameSettings settings = Minecraft.getMinecraft().gameSettings;
        if (binding == settings.keyBindSneak) return toggles().sneakInput(binding.isKeyDown(), TOGGLE_SNEAK.getKeyCode() != 0);
        if (binding == settings.keyBindSprint) return toggles().sprintInput(binding.isKeyDown(), TOGGLE_SPRINT.getKeyCode() != 0, sneaking());
        return binding.isKeyDown();
    }

    private static boolean sneaking() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.thePlayer != null && mc.thePlayer.movementInput != null && mc.thePlayer.movementInput.sneak;
    }

    /** Every client tick (END): a toggle that ended stops the sprint it started, which Minecraft would keep; the HUD line's state. */
    public static void tick(Minecraft mc) {
        // A screen or focus loss can swallow the key's release; the next press must toggle.
        if (mc.currentScreen != null || !org.lwjgl.opengl.Display.isActive()) sprintHeld = sneakHeld = false;
        if (mc.thePlayer == null) return;
        // What this tick sent, and whether its input could still sprint (onLivingUpdate stops below 0.8 forward).
        toggles().sent(((EntityPlayerSPAccessor) mc.thePlayer).ladsSentSprint(), mc.thePlayer.movementInput.moveForward >= 0.8F);
        boolean keyDown = mc.gameSettings.keyBindSprint.isKeyDown();
        if (toggles().sprintEnded(keyDown, TOGGLE_SPRINT.getKeyCode() != 0, sneaking())) mc.thePlayer.setSprinting(false);
        toggles().observe(mc.thePlayer.isSprinting(), sneaking(), keyDown);
        if (trace != null) trace.tick(mc.thePlayer.isSprinting(), ((EntityPlayerSPAccessor) mc.thePlayer).ladsSentSprint(),
            "collision=" + mc.thePlayer.isCollidedHorizontally + " food=" + mc.thePlayer.getFoodStats().getFoodLevel() + " forward="
                + mc.thePlayer.movementInput.moveForward + " using=" + mc.thePlayer.isUsingItem() + " sneak=" + sneaking() + " water="
                + mc.thePlayer.isInWater() + " flying=" + mc.thePlayer.capabilities.isFlying + " z=" + (float) mc.thePlayer.posZ
                + " toggled=" + toggles().isSprintToggled());
    }
}
