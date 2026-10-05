package com.thelads.core.v1_8_9.feature;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiUtilRenderComponents;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;

/**
 * 1.7.3 rendering: work vanilla 1.8.9 (and OptiFine M5, which keeps it) redoes every frame for an answer that did not change.
 * Each returns exactly what vanilla would draw; nothing looks different. Render thread only.
 * - Armour textures: Forge builds each armour piece's texture path with String.format and (under OptiFine) a reflective call
 *   to ForgeHooksClient, per piece, per entity, per frame. Vanilla armour items always get the same path: built once.
 * - Sign text: every line of every sign in view is re-wrapped (GuiUtilRenderComponents.splitText) and re-formatted per frame.
 *   Kept per sign until the line, the font (Unicode mode, resource reload) changes.
 * - Name tags: getDisplayName() on players and armour-stand holograms builds a hover event (NBT written to a string, two UUID
 *   strings) per tag per frame only to be thrown away; the tag only needs the formatted name, which is built directly. Every
 *   256th tag is also built the vanilla way and compared: should another mod change display names, this turns itself off.
 */
public final class RenderTweaks189 {
    /** QA switch (Probe173Rx's A/B runs); always on otherwise. */
    public static boolean enabled = true;
    /** How often each path answered from here (QA: proves the hooks are in place, also under OptiFine). */
    public static long armourHits, signHits, nameHits;
    private static final String RESET = EnumChatFormatting.RESET.toString();
    private static final Map<ItemArmor.ArmorMaterial, ResourceLocation[]> ARMOUR = new IdentityHashMap<ItemArmor.ArmorMaterial, ResourceLocation[]>();
    private static final Map<TileEntitySign, Lines> SIGNS = new WeakHashMap<TileEntitySign, Lines>();
    private static final IChatComponent NAME = new ChatComponentText("");
    private static TileEntitySign sign;
    private static Lines lines;
    private static int line;
    private static boolean reloadHooked, namesOff;
    private static String name;
    private static int names;

    private RenderTweaks189() {}

    // ---- Armour textures (LayerArmorBaseMixin) ----

    /** LayerArmorBase.getArmorResource's answer for a vanilla armour item, or null for Forge's own path (modded armour, other layer types). */
    public static ResourceLocation armourTexture(ItemStack stack, int slot, String type) {
        if (!enabled || stack.getItem().getClass() != ItemArmor.class) return null; // a subclass may override getArmorTexture
        int variant = type == null ? 0 : "overlay".equals(type) ? 2 : -1;
        if (variant < 0) return null;
        ItemArmor.ArmorMaterial material = ((ItemArmor) stack.getItem()).getArmorMaterial();
        ResourceLocation[] paths = ARMOUR.get(material);
        if (paths == null) ARMOUR.put(material, paths = new ResourceLocation[4]);
        int index = variant + (slot == 2 ? 1 : 0);
        if (paths[index] == null) paths[index] = new ResourceLocation(armourPath(material.getName(), slot, type));
        armourHits++;
        return paths[index];
    }

    /** Forge's path: "domain:textures/models/armor/<name>_layer_<1|2>[_type].png", the domain from a "domain:" prefix in the name. */
    static String armourPath(String texture, int slot, String type) {
        String domain = "minecraft";
        int colon = texture.indexOf(':');
        if (colon != -1) {
            domain = texture.substring(0, colon);
            texture = texture.substring(colon + 1);
        }
        return domain + ":textures/models/armor/" + texture + "_layer_" + (slot == 2 ? 2 : 1) + (type == null ? "" : "_" + type) + ".png";
    }

    // ---- Sign text (TileEntitySignRendererMixin) ----

    /** The sign renderTileEntityAt is drawing. */
    public static void sign(TileEntitySign te) { sign = te; }

    /** splitText for a line of the current sign: the last answer while the line and font are the same objects as then. */
    public static List<IChatComponent> splitSignLine(IChatComponent text, int width, FontRenderer font, boolean styles, boolean force) {
        lines = null;
        if (!enabled || sign == null || styles || !force) return GuiUtilRenderComponents.splitText(text, width, font, styles, force);
        if (!reloadHooked) { // a resource reload can change glyph widths (packs, HD fonts): start over
            reloadHooked = true;
            ((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager()).registerReloadListener(manager -> SIGNS.clear());
        }
        Lines cached = SIGNS.get(sign);
        if (cached == null || cached.font != font || cached.unicode != font.getUnicodeFlag() || cached.width != width) {
            SIGNS.put(sign, cached = new Lines(font, width));
        }
        int j = 0;
        while (j < sign.signText.length && sign.signText[j] != text) j++;
        if (j >= cached.text.length) return GuiUtilRenderComponents.splitText(text, width, font, styles, force);
        if (cached.text[j] != text) {
            cached.text[j] = text;
            cached.split[j] = GuiUtilRenderComponents.splitText(text, width, font, styles, force);
            cached.formatted[j] = null;
        } else signHits++;
        lines = cached;
        line = j;
        return cached.split[j];
    }

    /** getFormattedText of the first wrapped piece of the line splitSignLine just answered. */
    public static String signLineText(IChatComponent piece) {
        Lines cached = lines;
        if (cached == null || cached.split[line] == null || cached.split[line].isEmpty() || cached.split[line].get(0) != piece) return piece.getFormattedText();
        if (cached.formatted[line] == null) cached.formatted[line] = piece.getFormattedText();
        return cached.formatted[line];
    }

    private static final class Lines {
        final FontRenderer font;
        final boolean unicode;
        final int width;
        final IChatComponent[] text = new IChatComponent[4];
        @SuppressWarnings("unchecked") final List<IChatComponent>[] split = new List[4];
        final String[] formatted = new String[4];

        Lines(FontRenderer font, int width) {
            this.font = font;
            this.unicode = font.getUnicodeFlag();
            this.width = width;
        }
    }

    // ---- Name tags (NametagLivingMixin) ----

    /** In place of entity.getDisplayName() in RendererLivingEntity.renderName: a marker whose text formattedName gives. */
    public static IChatComponent displayName(EntityLivingBase entity) {
        String fast = enabled && !namesOff ? formattedName(entity) : null;
        if (fast == null) return entity.getDisplayName();
        if ((++names & 255) == 0) {
            String real = entity.getDisplayName().getFormattedText();
            if (!real.equals(fast)) {
                namesOff = true;
                LogManager.getLogger("TheLadsCore").warn("Lads name tags: {} has a different display name than vanilla builds ({} vs {}); "
                    + "using vanilla's from now on", entity.getClass().getName(), real, fast);
                fast = real;
            }
        }
        nameHits++;
        name = fast;
        return NAME;
    }

    /** In place of getFormattedText() on what displayName returned. */
    public static String formattedName(IChatComponent component) {
        return component == NAME ? name : component.getFormattedText();
    }

    /**
     * What getDisplayName().getFormattedText() gives, for the classes whose getDisplayName is vanilla's: an armour stand's is
     * its name plus a reset; a player's (no Forge name prefixes or suffixes) is its team-formatted name between resets, the
     * styles carrying only click and hover events. Null: ask vanilla.
     */
    static String formattedName(EntityLivingBase entity) {
        Class<?> type = entity.getClass();
        if (type == EntityArmorStand.class) return entity.getName() + RESET;
        if (type != EntityOtherPlayerMP.class && type != EntityPlayerSP.class) return null;
        EntityPlayer player = (EntityPlayer) entity;
        if (!player.getPrefixes().isEmpty() || !player.getSuffixes().isEmpty()) return null;
        return RESET + ScorePlayerTeam.formatPlayerName(player.getTeam(), player.getDisplayNameString()) + RESET;
    }

    /** QA: false once the name-tag check found a display name built differently. */
    public static boolean namesOn() { return !namesOff; }
}
