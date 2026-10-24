package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.ChatServerHistory;
import com.thelads.core.client.ChatTranslator;
import com.thelads.core.client.ChatTweaks;
import com.thelads.core.client.InventoryTweaks;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.ActionOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.AutoTextModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.gui.GuiRepair;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * The parity-1023 chat and inventory tweaks on 1.8.9 (common ChatTweaks, InventoryTweaks, ChatTranslator, ChatServerHistory, Auto
 * Text): their keys (Chat Peek, Toggle Chat, Lock Slot, Auto Text 1-6, all unbound until set in Controls; followed through their own
 * key events like PvpView189, as 1.8.9 gives each key code to one binding), the client tick, and the message rewrites. Hooks:
 * GuiNewChatMixin with Chat189 (messages, layout, peek, hide, history per server), GuiChatMixin (tabs, search box, Shift+Enter,
 * Shortcuts, long commands), C01ChatLengthMixin189, InventoryTweaksMixin189 (search box, lock and search overlays, Lock key, Armor
 * Swap), SlotLockControllerMixin189 and EntityPlayerSPMixin (Slot Lock), MovementInputFromOptionsMixin (Inventory Move).
 */
public final class ChatTweaks189 {
    private static final String CATEGORY = "key.category.theladscore.controls";
    public static final KeyBinding PEEK = new KeyBinding("key.theladscore.chat_peek", Keyboard.KEY_NONE, CATEGORY),
        TOGGLE = new KeyBinding("key.theladscore.toggle_chat", Keyboard.KEY_NONE, CATEGORY),
        LOCK = new KeyBinding("key.theladscore.lock_slot", Keyboard.KEY_NONE, CATEGORY);
    private static final KeyBinding[] AUTO_TEXT = new KeyBinding[AutoTextModule.SLOTS];
    private static int filterSeen;

    static {
        for (int i = 0; i < AUTO_TEXT.length; i++) AUTO_TEXT[i] = new KeyBinding("key.theladscore.auto_text." + (i + 1), Keyboard.KEY_NONE, CATEGORY);
    }

    /** What GuiNewChatMixin exposes to the tweaks. */
    public interface Access {
        List<ChatLine> lads$messages();
        /** Lays the chat out again and forgets the message Compact Chat would stack onto. */
        void lads$relayout();
        /** The stored message of the chat line at the pointer's height (open chat), or null; then its line's top and the chat's right edge. */
        ChatLine lads$hovered(int screenHeight, int mouseY);
        int lads$hoverTop();
        int lads$hoverRight();
    }

    /** A drawn chat line's stored message (ChatLineMixin keeps it). */
    public interface Parent {
        ChatLine ladsParent();
        void ladsParent(ChatLine message);
    }

    /** A container screen with Inventory Search's box (InventoryTweaksMixin189): the player is typing in it. */
    public interface Typing {
        boolean lads$typing();
        /** The point is on the search box (Container QoL's click outside to close leaves it alone). */
        boolean lads$overSearch(int x, int y);
    }

    /** The modules this class makes built in, with InventoryTweaks.MODULES (CoreProbe's catalog check reads them too). */
    public static final String[] BUILT_IN = {ChatTweaks.LONG_COMMANDS, AutoTextModule.NAME};

    private ChatTweaks189() {}

    public static void register() {
        ClientRegistry.registerKeyBinding(PEEK);
        ClientRegistry.registerKeyBinding(TOGGLE);
        ClientRegistry.registerKeyBinding(LOCK);
        for (KeyBinding key : AUTO_TEXT) ClientRegistry.registerKeyBinding(key);
        ModuleSupport.registerBuiltIn(BUILT_IN);
        ModuleSupport.registerBuiltIn(InventoryTweaks.MODULES);
        Module chat = ModuleManager.getInstance().getModule("Chat");
        bind(chat, ChatTweaks.PEEK_KEY, PEEK);
        bind(chat, ChatTweaks.TOGGLE_KEY, TOGGLE);
        bind(ModuleManager.getInstance().getModule(InventoryTweaks.SLOT_LOCK), InventoryTweaks.LOCK_KEY, LOCK);
        AutoTextModule auto = AutoTextModule.get();
        for (int i = 0; i < AUTO_TEXT.length; i++) bind(auto.keys[i], AUTO_TEXT[i]);
        MinecraftForge.EVENT_BUS.register(new ChatTweaks189());
    }

    private static void bind(Module module, String option, KeyBinding key) {
        if (module != null && module.getOption(option) instanceof ActionOption) bind((ActionOption) module.getOption(option), key);
    }

    private static void bind(ActionOption action, final KeyBinding key) {
        action.setLabel(() -> key.getKeyCode() == 0 ? "Not bound" : GameSettings.getKeyDisplayString(key.getKeyCode()));
        action.setAction(() -> Minecraft.getMinecraft().displayGuiScreen(new GuiControls(Minecraft.getMinecraft().currentScreen, Minecraft.getMinecraft().gameSettings)));
    }

    private static boolean gameplay(Minecraft mc) {
        return mc.currentScreen == null && mc.thePlayer != null && mc.theWorld != null;
    }

    @SubscribeEvent
    public void key(InputEvent.KeyInputEvent event) {
        if (!Keyboard.isRepeatEvent() && Keyboard.getEventKeyState())
            press(Keyboard.getEventKey() == 0 ? Keyboard.getEventCharacter() + 256 : Keyboard.getEventKey());
    }

    @SubscribeEvent
    public void mouse(InputEvent.MouseInputEvent event) {
        if (Mouse.getEventButton() >= 0 && Mouse.getEventButtonState()) press(Mouse.getEventButton() - 100); // KeyBinding stores button - 100
    }

    private static void press(int code) {
        if (code == 0 || !gameplay(Minecraft.getMinecraft())) return;
        if (code == TOGGLE.getKeyCode()) ChatTweaks.hidden = !ChatTweaks.hidden;
        for (int i = 0; i < AUTO_TEXT.length; i++) if (code == AUTO_TEXT[i].getKeyCode()) AutoTextModule.press(i);
    }

    static boolean held(int code) {
        if (code == 0) return false;
        return code < 0 ? Mouse.isButtonDown(code + 100) : code < 256 && Keyboard.isKeyDown(code);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ChatTweaks.tick();
        InventoryTweaks.tick();
        Minecraft mc = Minecraft.getMinecraft();
        ChatTweaks.peeking = gameplay(mc) && held(PEEK.getKeyCode());
        if (mc.ingameGUI == null) return;
        if (mc.thePlayer != null) server((Access) mc.ingameGUI.getChatGUI());
        if (filterSeen != ChatTweaks.filterRevision) refilter();
    }

    /** A tab or the search changed: the chat is laid out again with only what they show. */
    public static void refilter() {
        filterSeen = ChatTweaks.filterRevision;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.ingameGUI != null) mc.ingameGUI.getChatGUI().refreshChat();
    }

    /** History per Server: the chat shows this server's messages (checked each tick and before each message). */
    public static void server(Access chat) {
        if (ChatServerHistory.joined(ChatTweaks.serverKey(LadsGameBridge.get().getServerAddress()), chat.lads$messages())) chat.lads$relayout();
    }

    /** The message's plain text when a tab or search filters the chat; every message shows otherwise (no string work). */
    public static boolean shown(IChatComponent content) {
        return !ChatTweaks.filtering() || ChatTweaks.shows(content.getUnformattedText());
    }

    /** A new message (its plain text, before its timestamp): Profanity Filter and Mention Highlight, each run keeping its style; Mention Sound. */
    public static IChatComponent incoming(IChatComponent content, String plain) {
        Minecraft mc = Minecraft.getMinecraft();
        int[][] marks = ChatTweaks.marks(plain, LadsGameBridge.get().getPlayerName());
        if (ChatTweaks.ping(marks, System.currentTimeMillis()))
            mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("random.orb"), 1.0F));
        return ChatTweaks.rewrites(marks) ? rewrite(content, marks) : content;
    }

    static IChatComponent rewrite(IChatComponent content, int[][] marks) {
        List<String> texts = new ArrayList<String>();
        List<ChatStyle> styles = new ArrayList<ChatStyle>();
        for (IChatComponent part : content) { // vanilla's own flattening (getUnformattedText): every run with its whole style
            String text = part.getUnformattedTextForChat();
            if (text.isEmpty()) continue;
            texts.add(text);
            styles.add(part.getChatStyle().createDeepCopy());
        }
        final IChatComponent out = new ChatComponentText("");
        ChatTweaks.rewrite(marks, texts, styles, (text, style, mention) -> {
            ChatStyle own = style.createShallowCopy();
            if (mention) own.setColor(EnumChatFormatting.YELLOW).setBold(true);
            out.appendSibling(new ChatComponentText(text).setChatStyle(own));
        });
        return out;
    }

    /** Compact Chat's " (x2)", gray, after the message. */
    public static void repeated(IChatComponent message, int repeat) {
        IChatComponent suffix = new ChatComponentText(ChatTweaks.repeatSuffix(repeat));
        suffix.getChatStyle().setColor(EnumChatFormatting.GRAY);
        message.appendSibling(suffix);
    }

    /**
     * Inventory Move (gated, MovementInputFromOptionsMixin): with a container screen open (not creative, not an anvil, not while
     * typing in Inventory Search) the walk and jump keys count as held while held on the keyboard. Sneak never does: Shift is for
     * shift-clicks.
     */
    public static boolean moveKey(KeyBinding key) {
        if (!InventoryTweaks.moveOn) return false;
        Minecraft mc = Minecraft.getMinecraft();
        if (!(mc.currentScreen instanceof GuiContainer) || mc.currentScreen instanceof GuiContainerCreative || mc.currentScreen instanceof GuiRepair
            || mc.currentScreen instanceof Typing && ((Typing) mc.currentScreen).lads$typing()) return false;
        GameSettings s = mc.gameSettings;
        if (key != s.keyBindForward && key != s.keyBindBack && key != s.keyBindLeft && key != s.keyBindRight && key != s.keyBindJump) return false;
        int code = key.getKeyCode();
        return code > 0 && code < 256 && Keyboard.isKeyDown(code);
    }

    /** Copy and Delete Buttons (GuiChatMixin): Copy puts the message's text on the clipboard, Delete takes it out of the chat. */
    public static void messageButton(ChatLine message, int button) {
        Minecraft mc = Minecraft.getMinecraft();
        if (button == 0) {
            net.minecraft.client.gui.GuiScreen.setClipboardString(message.getChatComponent().getUnformattedText());
            return;
        }
        Access chat = (Access) mc.ingameGUI.getChatGUI();
        List<ChatLine> lines = chat.lads$messages();
        for (int i = 0; i < lines.size(); i++)
            if (lines.get(i) == message) {
                lines.remove(i);
                chat.lads$relayout();
                return;
            }
    }

    /** Translate Chat: a message waiting for its translation; GuiNewChatMixin fills in the stored line once it is added. */
    public static final class Pending {
        public ChatLine line;
    }

    /** Translate Chat: asks for the plain text's translation; null while the option is off. */
    public static Pending translate(String plain) {
        if (!ChatTweaks.translate) return null;
        final Pending pending = new Pending();
        final Minecraft mc = Minecraft.getMinecraft();
        ChatTranslator.request(plain, mc.gameSettings.language, translated -> mc.addScheduledTask(() -> translated(pending, translated)));
        return pending;
    }

    /** On the client thread: the translation goes under the message, which is laid out again where it is. */
    private static void translated(Pending pending, String translated) {
        ChatLine old = pending.line;
        Minecraft mc = Minecraft.getMinecraft();
        if (old == null || mc.ingameGUI == null) return;
        Access chat = (Access) mc.ingameGUI.getChatGUI();
        List<ChatLine> lines = chat.lads$messages();
        int index = -1;
        for (int i = 0; i < lines.size() && index < 0; i++) if (lines.get(i) == old) index = i;
        if (index < 0) return; // deleted, or the chat was cleared meanwhile
        IChatComponent line = new ChatComponentText("\n» " + translated);
        line.getChatStyle().setColor(EnumChatFormatting.GRAY).setItalic(true);
        ChatLine now = new ChatLine(old.getUpdatedCounter(), old.getChatComponent().createCopy().appendSibling(line), old.getChatLineID());
        ChatHeads189.Line from = (ChatHeads189.Line) old;
        ((ChatHeads189.Line) now).ladsHead(from.ladsHead(), from.ladsAt(), from.ladsFirst());
        lines.set(index, now);
        chat.lads$relayout();
    }
}
