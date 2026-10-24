package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.ChatHistory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiUtilRenderComponents;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

/**
 * The Chat module's messages on 1.8.9 (GuiNewChatMixin, which also sizes, backs and animates the chat): timestamps, and the
 * screenshot message's [Open File] [Open Folder] buttons as on 1.21.x. 1.8.9 chat is unsigned, so it has no indicators to hide.
 */
public final class Chat189 {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    /** QA only (Probe150): new messages seen, and newest-message lines drawn while sliding in. */
    public static long messages, animated;
    /** The new message's plain text before any change (Compact Chat's key) and its pending translation, for GuiNewChatMixin.ladsTweaks. */
    public static String plain;
    public static ChatTweaks189.Pending pending;

    private Chat189() {}

    /** QA (Probe170Hud): GuiNewChatMixin's view of the chat's history. */
    public interface History {
        int ladsScrollPos();
        int ladsDrawnLines();
        int ladsMessages();
    }

    /**
     * Infinite History (GuiNewChatMixin): stored messages from laidOut on, wrapped as setChatLine wraps them, below the drawn lines.
     * Each line keeps its message's chat head (ChatHeads189) and leaves room for it, as setChatLine's lines do.
     */
    public static int layOut(List<ChatLine> chatLines, int laidOut, List<ChatLine> drawnLines, int lines, final int width) {
        final FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
        return ChatHistory.layOut(chatLines, laidOut, drawnLines, lines, message -> ChatTweaks189.shown(message.getChatComponent()), message -> {
            ChatHeads189.Line sender = (ChatHeads189.Line) message;
            List<ChatLine> parts = new ArrayList<ChatLine>();
            for (IChatComponent line : GuiUtilRenderComponents.splitText(message.getChatComponent(), width - ChatHeads189.offset(sender.ladsHead()), font, false, false)) {
                ChatLine part = new ChatLine(message.getUpdatedCounter(), line, message.getChatLineID());
                ((ChatHeads189.Line) part).ladsHead(sender.ladsHead(), sender.ladsAt(), parts.isEmpty());
                parts.add(part);
            }
            return parts;
        });
    }

    /** A new chat message (printChatMessageWithOptionalDeletion); refreshChat re-wraps stored messages without coming here. */
    public static IChatComponent message(IChatComponent message) {
        if (message == null) return null;
        messages++;
        if (!Options189.enabled("Chat")) return message;
        plain = message.getUnformattedText();
        pending = ChatTweaks189.translate(plain);
        if (Options189.bool("Chat", "Screenshot Link Buttons", true)) screenshotButtons(message);
        message = ChatTweaks189.incoming(message, plain); // Profanity Filter, mentions (ChatTweaks189)
        if (!Options189.bool("Chat", "Timestamps", false)) return message;
        IChatComponent prefix = new ChatComponentText("[" + LocalTime.now().format(CLOCK) + "] ");
        prefix.getChatStyle().setColor(EnumChatFormatting.GRAY);
        // An unstyled root, so the message keeps its own colour, click and hover style.
        return new ChatComponentText("").appendSibling(prefix).appendSibling(message);
    }

    private static final String ACTION_COPY = "lads-screenshot:copy:";
    private static final String ACTION_IMGUR = "lads-screenshot:imgur:";

    private static void screenshotButtons(IChatComponent message) {
        if (!(message instanceof ChatComponentTranslation)) return;
        ChatComponentTranslation success = (ChatComponentTranslation) message;
        Object[] args = success.getFormatArgs();
        if (!"screenshot.success".equals(success.getKey()) || args.length == 0 || !(args[0] instanceof IChatComponent)) return;
        ClickEvent open = ((IChatComponent) args[0]).getChatStyle().getChatClickEvent();
        if (open == null || open.getAction() != ClickEvent.Action.OPEN_FILE) return;
        File file = new File(open.getValue());
        File dir = file.getAbsoluteFile().getParentFile();
        if (dir == null) return;
        message.appendSibling(actionButton(" [Copy]", EnumChatFormatting.GOLD, ACTION_COPY + file.getAbsolutePath(), "Copy image to clipboard"))
            .appendSibling(button(" [Open File]", EnumChatFormatting.GREEN, file, "Open screenshot file"))
            .appendSibling(button(" [Open Folder]", EnumChatFormatting.AQUA, dir, "Open screenshots folder"))
            .appendSibling(actionButton(" [Upload to Imgur]", EnumChatFormatting.LIGHT_PURPLE, ACTION_IMGUR + file.getAbsolutePath(), "Upload to Imgur and copy link"));
    }

    public static boolean handleClick(IChatComponent component) {
        if (component == null || component.getChatStyle() == null) return false;
        ClickEvent click = component.getChatStyle().getChatClickEvent();
        if (click == null || click.getAction() != ClickEvent.Action.OPEN_FILE || click.getValue() == null) return false;
        String value = click.getValue();
        if (value.startsWith(ACTION_COPY)) {
            Screenshots189.copyToClipboard(new File(value.substring(ACTION_COPY.length())));
            return true;
        }
        if (value.startsWith(ACTION_IMGUR)) {
            Screenshots189.uploadToImgur(new File(value.substring(ACTION_IMGUR.length())));
            return true;
        }
        return false;
    }

    private static IChatComponent button(String label, EnumChatFormatting color, File target, String hover) {
        return actionButton(label, color, target.getAbsolutePath(), hover);
    }

    private static IChatComponent actionButton(String label, EnumChatFormatting color, String openFileValue, String hover) {
        IChatComponent button = new ChatComponentText(label);
        button.getChatStyle().setColor(color).setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, openFileValue))
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText(hover)));
        return button;
    }
}
