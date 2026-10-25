package com.thelads.core.v1_20_1.hud.chat;

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
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

/**
 * The parity-1023 chat and inventory tweaks on 1.20.1 (common ChatTweaks, InventoryTweaks, ChatTranslator, ChatServerHistory, Auto
 * Text): their keys (Chat Peek, Toggle Chat, Lock Slot, Auto Text 1-6, all unbound until set in Controls), the client tick, and the
 * message rewrites. Hooks: ChatComponentMixin (messages, layout, peek, hide, history per server), ChatScreenTweaksMixin1201 (tabs,
 * search box, Shift+Enter, Shortcuts, long commands), InventoryTweaksMixin1201 (search box, lock and search overlays, Lock key, Armor
 * Swap), SlotLockGameModeMixin1201 and SlotLockDropMixin1201 (Slot Lock), KeyboardInputMixin (Inventory Move).
 */
public final class ChatTweaks1201 {
    private static final String CATEGORY = "key.category.theladscore.controls";
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
        GuiMessage lads$hovered(double mouseY);
        int lads$hoverTop();
        int lads$hoverRight();
    }

    /** A chat line's stored message (1.20.1 lines have no parent of their own; ChatLineMixin keeps it). */
    public interface Parent {
        GuiMessage lads$parent();
        void lads$parent(GuiMessage message);
    }

    private ChatTweaks1201() {}

    private static KeyMapping key(String name) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(name, InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY));
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
        ClientTickEvents.END_CLIENT_TICK.register(ChatTweaks1201::tick);
    }

    private static void bind(Module module, String option, KeyMapping key) {
        if (module != null && module.getOption(option) instanceof ActionOption action) bind(action, key);
    }

    private static void bind(ActionOption action, KeyMapping key) {
        action.setLabel(() -> key.isUnbound() ? "Not bound" : key.getTranslatedKeyMessage().getString());
        action.setAction(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new KeyBindsScreen(mc.screen, mc.options));
        });
    }

    private static void tick(Minecraft mc) {
        ChatTweaks.tick();
        InventoryTweaks.tick();
        boolean playing = mc.screen == null && mc.player != null;
        ChatTweaks.peeking = playing && PEEK.isDown();
        while (TOGGLE.consumeClick()) ChatTweaks.hidden = !ChatTweaks.hidden;
        for (int i = 0; i < AUTO_TEXT.length; i++) while (AUTO_TEXT[i].consumeClick()) if (playing) AutoTextModule.press(i);
        Access chat = (Access) mc.gui.getChat();
        if (mc.player != null) server(chat);
        if (filterSeen != ChatTweaks.filterRevision) refilter();
    }

    /** A tab or the search changed: the chat is laid out again with only what they show. */
    public static void refilter() {
        filterSeen = ChatTweaks.filterRevision;
        Minecraft.getInstance().gui.getChat().rescaleChat();
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
        Minecraft mc = Minecraft.getInstance();
        if (ChatTweaks.ping(marks, System.currentTimeMillis()))
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F));
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
     * Inventory Move (gated, KeyboardInputMixin): with a container screen open (not creative, no text box or recipe book in use) the
     * walk and jump keys count as held while held on the keyboard. Sneak and Sprint never do: Shift is for shift-clicks.
     */
    public static boolean moveKey(KeyMapping key) {
        if (!InventoryTweaks.moveOn) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen) || screen instanceof CreativeModeInventoryScreen) return false;
        if (screen.getFocused() instanceof EditBox box && box.isFocused()) return false;
        if (screen instanceof RecipeUpdateListener book && book.getRecipeBookComponent().isVisible()) return false;
        Options options = mc.options;
        if (key != options.keyUp && key != options.keyDown && key != options.keyLeft && key != options.keyRight && key != options.keyJump) return false;
        InputConstants.Key bound = KeyBindingHelper.getBoundKeyOf(key);
        return bound.getType() == InputConstants.Type.KEYSYM && bound.getValue() != InputConstants.UNKNOWN.getValue()
            && InputConstants.isKeyDown(mc.getWindow().getWindow(), bound.getValue());
    }

    /** Copy and Delete Buttons (ChatScreenTweaksMixin1201): Copy puts the message's text on the clipboard, Delete takes it out of the chat. */
    public static String toFormatted(Component component) {
        if (component == null) return "";
        StringBuilder sb = new StringBuilder();
        component.visit((style, text) -> {
            if (text.isEmpty()) return Optional.empty();
            sb.append("§r");
            if (style != null) {
                if (style.getColor() != null) {
                    sb.append('§').append(com.thelads.core.client.LegacyText.code(style.getColor().getValue()));
                }
                if (style.isBold()) sb.append("§l");
                if (style.isItalic()) sb.append("§o");
                if (style.isUnderlined()) sb.append("§n");
                if (style.isStrikethrough()) sb.append("§m");
                if (style.isObfuscated()) sb.append("§k");
            }
            sb.append(text);
            return Optional.empty();
        }, Style.EMPTY);
        return sb.toString();
    }

    public static void messageButton(GuiMessage message, int button) {
        Minecraft mc = Minecraft.getInstance();
        if (button == 0) {
            mc.keyboardHandler.setClipboard(message.content().getString());
            return;
        }
        if (button == 2) {
            ChatTweaks.exportChatImage(toFormatted(message.content()), com.thelads.core.shared.SharedContentPaths.screenshotsDir());
            return;
        }
        Access chat = (Access) mc.gui.getChat();
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
        Access chat = (Access) Minecraft.getInstance().gui.getChat();
        List<GuiMessage> messages = chat.lads$messages();
        int index = -1;
        for (int i = 0; old != null && i < messages.size() && index < 0; i++) if (messages.get(i) == old) index = i;
        if (index < 0) return; // deleted, or the chat was cleared meanwhile
        Component content = old.content().copy().append(Component.literal("\n» " + translated).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        GuiMessage now = new GuiMessage(old.addedTime(), content, old.signature(), old.tag());
        NativeChatHeads1201.Data from = (NativeChatHeads1201.Data) (Object) old;
        ((NativeChatHeads1201.Data) (Object) now).lads$set(from.lads$head(), from.lads$at(), from.lads$first());
        messages.set(index, now);
        chat.lads$relayout();
    }
}
