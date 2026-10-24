package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.platform.InputConstants;
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
import java.util.Optional;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;

/**
 * The parity-1023 chat and inventory tweaks on 26.3 (common ChatTweaks, InventoryTweaks, ChatTranslator, ChatServerHistory, Auto
 * Text): their keys (Chat Peek, Toggle Chat, Lock Slot, Auto Text 1-6, all unbound until set in Controls), the client tick, and the
 * message rewrites. Hooks: ClientToolsChatMixin (messages, layout, hide, history per server), ChatTweaksHudMixin26 (peek),
 * ChatScreenTweaksMixin26 (tabs, search box, Shift+Enter, Shortcuts, long commands), InventoryTweaksMixin26 (search box, lock and
 * search overlays, Lock key, Armor Swap), MultiPlayerGameModeMixin (Slot Lock), KeyboardInputMixin (Inventory Move).
 */
public final class ChatTweaks26 {
    public static final KeyMapping PEEK = key("key.theladscore.chat_peek"), TOGGLE = key("key.theladscore.toggle_chat"),
        LOCK = key("key.theladscore.lock_slot");
    private static final KeyMapping[] AUTO_TEXT = new KeyMapping[AutoTextModule.SLOTS];
    private static int filterSeen;

    static {
        for (int i = 0; i < AUTO_TEXT.length; i++) AUTO_TEXT[i] = key("key.theladscore.auto_text." + (i + 1));
    }

    /** What the chat mixin exposes to the tweaks. */
    public interface Access {
        List<GuiMessage> lads$messages();
        /** Lays the chat out again and forgets the message Compact Chat would stack onto. */
        void lads$relayout();
        /** The stored message of the chat line at the pointer's height (open chat), or null; then its line's top and the chat's right edge. */
        GuiMessage lads$hovered(int screenHeight, double mouseY);
        int lads$hoverTop();
        int lads$hoverRight();
    }

    private ChatTweaks26() {}

    private static KeyMapping key(String name) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(name, InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(),
            NativeKeyBindings.CATEGORY));
    }

    public static void register() {
        ModuleSupport.registerBuiltIn(ChatTweaks.LONG_COMMANDS, AutoTextModule.NAME);
        ModuleSupport.registerBuiltIn(InventoryTweaks.MODULES);
        Module chat = ModuleManager.getInstance().getModule("Chat");
        bind(chat, ChatTweaks.PEEK_KEY, PEEK);
        bind(chat, ChatTweaks.TOGGLE_KEY, TOGGLE);
        bind(ModuleManager.getInstance().getModule(InventoryTweaks.SLOT_LOCK), InventoryTweaks.LOCK_KEY, LOCK);
        AutoTextModule auto = AutoTextModule.get();
        for (int i = 0; i < AUTO_TEXT.length; i++) bind(auto.keys[i], AUTO_TEXT[i]);
        ClientTickEvents.END_CLIENT_TICK.register(ChatTweaks26::tick);
    }

    private static void bind(Module module, String option, KeyMapping key) {
        if (module != null && module.getOption(option) instanceof ActionOption action) bind(action, key);
    }

    private static void bind(ActionOption action, KeyMapping key) {
        action.setLabel(() -> key.isUnbound() ? "Not bound" : key.getTranslatedKeyMessage().getString());
        action.setAction(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.setScreen(new KeyBindsScreen(mc.gui.screen(), mc.options));
        });
    }

    private static void tick(Minecraft mc) {
        ChatTweaks.tick();
        InventoryTweaks.tick();
        boolean playing = mc.gui.screen() == null && mc.player != null;
        ChatTweaks.peeking = playing && PEEK.isDown();
        while (TOGGLE.consumeClick()) ChatTweaks.hidden = !ChatTweaks.hidden;
        for (int i = 0; i < AUTO_TEXT.length; i++) while (AUTO_TEXT[i].consumeClick()) if (playing) AutoTextModule.press(i);
        if (mc.gui.hud == null) return;
        if (mc.player != null) server((Access) mc.gui.hud.getChat());
        if (filterSeen != ChatTweaks.filterRevision) refilter();
    }

    /** A tab or the search changed: the chat is laid out again with only what they show. */
    public static void refilter() {
        filterSeen = ChatTweaks.filterRevision;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud != null) mc.gui.hud.getChat().rescaleChat();
    }

    /** History per Server: the chat shows this server's messages (checked each tick and before each message). */
    public static void server(Access chat) {
        if (ChatServerHistory.joined(ChatTweaks.serverKey(LadsGameBridge.get().getServerAddress()), chat.lads$messages())) chat.lads$relayout();
    }

    /** The message's plain text when a tab or search filters the chat; every message shows otherwise (no string work). */
    public static boolean shown(Component content) {
        return !ChatTweaks.filtering() || ChatTweaks.shows(content.getString());
    }

    /**
     * A new message, before its timestamp: Profanity Filter and Mention Highlight (each run keeps its style, links and hover text),
     * Mention Sound, and Compact Chat's " (x2)" (the copy before it is removed by the caller, which passes the count).
     */
    public static Component incoming(Component content, String plain, int repeat) {
        int[][] marks = ChatTweaks.marks(plain, LadsGameBridge.get().getPlayerName());
        if (ChatTweaks.ping(marks, System.currentTimeMillis()))
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F));
        Component message = ChatTweaks.rewrites(marks) ? rewrite(content, marks) : content;
        if (repeat <= 1) return message;
        return Component.empty().append(message).append(Component.literal(ChatTweaks.repeatSuffix(repeat)).withStyle(ChatFormatting.GRAY));
    }

    static Component rewrite(Component content, int[][] marks) {
        List<String> texts = new ArrayList<>();
        List<Style> styles = new ArrayList<>();
        content.visit((style, text) -> {
            if (!text.isEmpty()) {
                texts.add(text);
                styles.add(style);
            }
            return Optional.empty();
        }, Style.EMPTY);
        MutableComponent out = Component.empty();
        ChatTweaks.rewrite(marks, texts, styles, (text, style, mention) ->
            out.append(Component.literal(text).setStyle(mention ? style.withColor(ChatFormatting.YELLOW).withBold(true) : style)));
        return out;
    }

    /**
     * Inventory Move (gated, KeyboardInputMixin): with a container screen open (not creative, nothing typing: a text box or the
     * recipe book's search) the walk and jump keys count as held while held on the keyboard. Sneak and Sprint never do: Shift is
     * for shift-clicks.
     */
    public static boolean moveKey(KeyMapping key) {
        if (!InventoryTweaks.moveOn) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen) || screen instanceof CreativeModeInventoryScreen) return false;
        if (screen.isInputCaptured()) return false;
        Options options = mc.options;
        if (key != options.keyUp && key != options.keyDown && key != options.keyLeft && key != options.keyRight && key != options.keyJump) return false;
        InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(key);
        return bound.getType() == InputConstants.Type.KEYBOARD && bound.getValue() != InputConstants.UNKNOWN.getValue()
            && InputConstants.isKeyDown(bound.getValue());
    }

    /** Copy and Delete Buttons (ChatScreenTweaksMixin26): Copy puts the message's text on the clipboard, Delete takes it out of the chat. */
    public static void messageButton(GuiMessage message, int button) {
        Minecraft mc = Minecraft.getInstance();
        if (button == 0) {
            mc.keyboardHandler.setClipboard(message.content().getString());
            return;
        }
        if (button == 2) {
            ChatTweaks.exportChatImage(message.content().getString(), com.thelads.core.shared.SharedContentPaths.screenshotsDir());
            return;
        }
        Access chat = (Access) mc.gui.hud.getChat();
        List<GuiMessage> messages = chat.lads$messages();
        for (int i = 0; i < messages.size(); i++)
            if (messages.get(i) == message) {
                messages.remove(i);
                chat.lads$relayout();
                return;
            }
    }

    /** Translate Chat: a message waiting for its translation; the chat mixin fills in the stored message once it is added. */
    public static final class Pending {
        public GuiMessage message;
    }

    /** Translate Chat: asks for the plain text's translation; null while the option is off. */
    public static Pending translate(String plain) {
        if (!ChatTweaks.translate) return null;
        Pending pending = new Pending();
        Minecraft mc = Minecraft.getInstance();
        ChatTranslator.request(plain, mc.options.languageCode, translated -> mc.execute(() -> translated(pending, translated)));
        return pending;
    }

    /** On the client thread: the translation goes under the message, which is laid out again where it is. */
    private static void translated(Pending pending, String translated) {
        GuiMessage old = pending.message;
        Minecraft mc = Minecraft.getInstance();
        if (old == null || mc.gui.hud == null) return;
        Access chat = (Access) mc.gui.hud.getChat();
        List<GuiMessage> messages = chat.lads$messages();
        int index = -1;
        for (int i = 0; i < messages.size() && index < 0; i++) if (messages.get(i) == old) index = i;
        if (index < 0) return; // deleted, or the chat was cleared meanwhile
        Component content = old.content().copy().append(Component.literal("\n» " + translated).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        GuiMessage now = new GuiMessage(old.addedTime(), content, old.signature(), old.source(), old.tag());
        NativeChatHeads.Sender from = (NativeChatHeads.Sender) (Object) old;
        ((NativeChatHeads.Sender) (Object) now).lads$head(from.lads$head(), from.lads$at());
        messages.set(index, now);
        chat.lads$relayout();
    }
}
