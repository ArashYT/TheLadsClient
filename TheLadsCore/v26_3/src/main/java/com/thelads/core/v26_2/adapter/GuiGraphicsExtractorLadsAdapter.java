package com.thelads.core.v26_2.adapter;

import com.thelads.core.client.bridge.LadsGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

public class GuiGraphicsExtractorLadsAdapter implements LadsGraphics {
    private final GuiGraphicsExtractor g;
    private final Font font;
    private static volatile Object metricsEpoch = new Object();
    private static Font metricsFont;
    public static void invalidateMetrics() { metricsEpoch = new Object(); }
    @Override public Object textMetricsKey() {
        if (metricsFont != font) { metricsFont = font; invalidateMetrics(); }
        return metricsEpoch;
    }

    public GuiGraphicsExtractorLadsAdapter(GuiGraphicsExtractor g, Font font) {
        this.g = g;
        this.font = (font != null) ? font : Minecraft.getInstance().font;
    }

    public GuiGraphicsExtractorLadsAdapter(GuiGraphicsExtractor g) {
        this(g, Minecraft.getInstance().font);
    }

    public GuiGraphicsExtractor getVanilla() {
        return g;
    }

    @Override public boolean drawKillBanner(String skin, int variant, int x, int y, int width, int height) {
        return com.thelads.core.v26_2.feature.NativeKillBanner.drawThumb(g, skin, variant, x, y, width, height);
    }
    @Override public boolean drawKillBannerPreview(String skin, int variant, int x, int y, int width, int height, double clock) {
        return com.thelads.core.v26_2.feature.NativeKillBanner.drawPreview(g, skin, variant, x, y, width, height, clock);
    }
    @Override public boolean drawCrosshairPreview(int cx, int cy) {
        return com.thelads.core.v26_2.feature.crosshair.NativeCrosshair.preview(g, cx, cy);
    }
    @Override public boolean drawImage(String source, int x, int y, int width, int height) {
        return com.thelads.core.v26_2.feature.crosshair.CrosshairBackdrop.draw(g, source, x, y, width, height);
    }
    @Override public void drawModIcon(String id,int x,int y,int size){if(!com.thelads.core.v26_2.gui.ModIcons.draw(g,id,x,y,size))LadsGraphics.super.drawModIcon(id,x,y,size);}

    @Override public void drawBossBars(int x,int y,int max,boolean names,boolean preview) {
        var mc = Minecraft.getInstance();
        if (mc.gui == null || mc.gui.hud == null || mc.gui.hud.getBossOverlay() == null) return;
        try {
            var overlay=(com.thelads.core.v26_2.mixin.BossBarAccessor)mc.gui.hud.getBossOverlay();
            var map = overlay.ladsEvents();
            var events=new java.util.ArrayList<net.minecraft.world.BossEvent>();
            if (map != null) {
                synchronized (map) {
                    events.addAll(map.values());
                }
            }
            if(events.isEmpty()&&preview){
                var sample=new net.minecraft.client.gui.components.LerpingBossEvent(java.util.UUID.randomUUID(),net.minecraft.network.chat.Component.literal("Boss bar preview"),.65f,net.minecraft.world.BossEvent.BossBarColor.PURPLE,net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS,false,false,false);
                events.add(sample);
            }
            int row=0;
            for(var event:events){if(row>=max)break;int yy=y+row++*19;
                overlay.ladsDrawBar(g,x,yy+10,event);
                if(names)g.text(font,event.getName(),x+(182-font.width(event.getName()))/2,yy,0xFFFFFFFF,com.thelads.core.config.HudSettings.getInstance().isTextShadow());
            }
        } catch (Throwable ignored) {}
    }

    @Override public int actionBarWidth(Object text) {
        return text instanceof net.minecraft.network.chat.Component message ? font.width(message) : LadsGraphics.super.actionBarWidth(text);
    }

    @Override public void drawActionBar(Object text, int x, int y, int color, boolean shadow, boolean plain) {
        if (!(text instanceof net.minecraft.network.chat.Component message)) { LadsGraphics.super.drawActionBar(text, x, y, color, shadow, plain); return; }
        if (!plain) { g.text(font, message, x, y, color, shadow); return; }
        // Its own colours left out (formatting codes in its text included, as the font reads them): the rest of its style stays.
        net.minecraft.util.FormattedCharSequence styled = message.getVisualOrderText();
        g.text(font, (net.minecraft.util.FormattedCharSequence) sink -> styled.accept((i, style, c) -> sink.accept(i, style.withColor((net.minecraft.network.chat.TextColor) null), c)), x, y, color, shadow);
    }

    @Override public void drawArmorItem(int index,int x,int y,boolean preview) {
        var player=Minecraft.getInstance().player;
        var slots=new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.FEET,net.minecraft.world.entity.EquipmentSlot.LEGS,net.minecraft.world.entity.EquipmentSlot.CHEST,net.minecraft.world.entity.EquipmentSlot.HEAD};
        net.minecraft.world.item.ItemStack stack=net.minecraft.world.item.ItemStack.EMPTY;
        if(preview){
            var items=new net.minecraft.world.item.Item[]{net.minecraft.world.item.Items.DIAMOND_HELMET,net.minecraft.world.item.Items.DIAMOND_CHESTPLATE};
            stack=new net.minecraft.world.item.ItemStack(items[Math.min(index,1)]);
            stack.setDamageValue(stack.getMaxDamage()/4);
        }else if(player!=null){
            int visible=0;
            for(var slot:slots){var candidate=player.getItemBySlot(slot);if(!candidate.isEmpty()&&visible++==index){stack=candidate;break;}}
        }
        if(!stack.isEmpty()){g.item(stack,x,y);g.itemDecorations(font,stack,x,y);}
    }

    @Override public void drawItemIcon(String itemKey, int x, int y) {
        var stack = resolveItem(itemKey);
        if (!stack.isEmpty()) {
            g.item(stack, x, y);
        }
    }

    private static net.minecraft.world.item.ItemStack resolveItem(String key) {
        if (key == null) return net.minecraft.world.item.ItemStack.EMPTY;
        return switch (key) {
            case "bed" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BED.red());
            case "feather" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FEATHER);
            case "diamond_sword" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD);
            case "iron_sword" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD);
            case "stone_sword" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_SWORD);
            case "golden_sword" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_SWORD);
            case "bow" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW);
            case "tnt" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TNT);
            case "slime_ball" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SLIME_BALL);
            case "painting" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAINTING);
            case "golden_apple" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE);
            case "gold_block" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLD_BLOCK);
            case "obsidian" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OBSIDIAN);
            case "ender_pearl" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_PEARL);
            case "magma_cream" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MAGMA_CREAM);
            case "potion" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.POTION);
            case "raw_fish" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COD);
            case "diamond_chestplate" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE);
            case "leather_chestplate" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_CHESTPLATE);
            case "leather_boots" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_BOOTS);
            case "clay_ball" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CLAY_BALL);
            case "banner" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BANNER.white());
            case "water_bucket" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WATER_BUCKET);
            case "skull" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SKELETON_SKULL);
            case "rotten_flesh" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ROTTEN_FLESH);
            case "clock" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CLOCK);
            case "brick" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BRICKS);
            case "wheat" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WHEAT);
            case "cake" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CAKE);
            case "glass" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GLASS);
            case "paper" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER);
            case "wool" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WOOL.white());
            case "dragon_egg" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DRAGON_EGG);
            case "iron_door" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_DOOR);
            case "hay_block" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.HAY_BLOCK);
            case "coal" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL);
            case "note_block" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NOTE_BLOCK);
            case "diamond_shovel" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SHOVEL);
            case "blaze_rod" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BLAZE_ROD);
            case "blaze_powder" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BLAZE_POWDER);
            case "iron_ingot" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT);
            case "snowball" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SNOWBALL);
            case "wither_skeleton_skull" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WITHER_SKELETON_SKULL);
            case "firework_charge" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FIREWORK_STAR);
            case "grass" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GRASS_BLOCK);
            case "door" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_DOOR);
            case "compass" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COMPASS);
            case "barrier" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BARRIER);
            case "nether_star" -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHER_STAR);
            default -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COMPASS);
        };
    }

    private static final Identifier HOTBAR = Identifier.withDefaultNamespace("hud/hotbar");
    private static final Identifier[] EMPTY_ARMOR = {Identifier.withDefaultNamespace("container/slot/helmet"),
        Identifier.withDefaultNamespace("container/slot/chestplate"), Identifier.withDefaultNamespace("container/slot/leggings"),
        Identifier.withDefaultNamespace("container/slot/boots")};
    private static final java.util.concurrent.ConcurrentHashMap<String, Identifier> IDENTIFIER_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, Identifier> MOB_EFFECT_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, com.mojang.authlib.GameProfile> HEAD_PROFILE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final com.mojang.authlib.GameProfile FALLBACK_PROFILE = new com.mojang.authlib.GameProfile(new java.util.UUID(0L, 0L), "Player");

    @Override public void drawSprite(String sprite, int x, int y, int size) {
        if (sprite == null) return;
        Identifier id = IDENTIFIER_CACHE.computeIfAbsent(sprite, Identifier::parse);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, id, x, y, size, size);
    }

    @Override public void drawPotionIcon(String effectId, int iconIndex, int x, int y, int size) {
        if (effectId != null && !effectId.isEmpty()) {
            try {
                Identifier id = MOB_EFFECT_CACHE.computeIfAbsent(effectId, k -> Identifier.withDefaultNamespace("mob_effect/" + k));
                g.blitSprite(RenderPipelines.GUI_TEXTURED, id, x, y, size, size);
            } catch (Throwable ignored) {}
        }
    }

    @Override public void drawPackIcon(String pack, int x, int y, int size) { com.thelads.core.v26_2.gui.PackIcons.draw(g, pack, x, y, size); }

    @Override public void drawHotbarSlots(int x, int y, int slots) {
        int width = 1 + slots * 20;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, 182, 22, 0, 0, x, y, width, 22);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, 182, 22, 181, 0, x + width, y, 1, 22);
    }

    @Override public void drawArmorSlot(int slot, int x, int y, boolean preview) {
        var equipment = new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.HEAD,
            net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET};
        var player = Minecraft.getInstance().player;
        net.minecraft.world.item.ItemStack stack = net.minecraft.world.item.ItemStack.EMPTY;
        if (preview) {
            // The editor sample: a worn helmet, no chestplate, leggings and boots.
            var items = new net.minecraft.world.item.Item[]{net.minecraft.world.item.Items.TURTLE_HELMET, null,
                net.minecraft.world.item.Items.IRON_LEGGINGS, net.minecraft.world.item.Items.GOLDEN_BOOTS};
            if (items[slot] != null) {
                stack = new net.minecraft.world.item.ItemStack(items[slot]);
                if (slot != 2) stack.setDamageValue(stack.getMaxDamage() * (slot == 0 ? 9 : 2) / 10);
            }
        } else if (player != null) stack = player.getItemBySlot(equipment[slot]);
        if (stack.isEmpty()) g.blitSprite(RenderPipelines.GUI_TEXTURED, EMPTY_ARMOR[slot], x, y, 16, 16, 0.35f);
        else { g.item(stack, x, y); g.itemDecorations(font, stack, x, y); }
    }

    @Override public void drawPlayerModel(int x, int y, int width, int height, boolean editor) {
        com.thelads.core.v26_2.feature.paperdoll.PaperDoll26.render(g, x, y, width, height, editor);
    }

    @Override
    public void fill(int minX, int minY, int maxX, int maxY, int color) {
        g.fill(minX, minY, maxX, maxY, color);
    }

    @Override
    public void drawText(String text, int x, int y, int color, boolean shadow) {
        if (text != null && font != null) {
            g.text(font, text, x, y, color, shadow);
        }
    }

    @Override
    public void drawCenteredText(String text, int centerX, int y, int color, boolean shadow) {
        if (text != null && font != null) {
            int tw = font.width(text);
            g.text(font, text, centerX - tw / 2, y, color, shadow);
        }
    }

    @Override
    public int textWidth(String text) {
        return (text != null && font != null) ? font.width(text) : 0;
    }

    @Override
    public int fontHeight() {
        return (font != null) ? font.lineHeight : 9;
    }

    @Override
    public void pushPose() {
        g.pose().pushMatrix();
    }

    @Override
    public void popPose() {
        g.pose().popMatrix();
    }

    @Override
    public void translate(float x, float y) {
        g.pose().translate(x, y);
    }

    @Override
    public void scale(float sx, float sy) {
        g.pose().scale(sx, sy);
    }

    @Override
    public void enableScissor(int minX, int minY, int maxX, int maxY) {
        g.enableScissor(minX, minY, maxX, maxY);
    }

    @Override
    public void disableScissor() {
        g.disableScissor();
    }

    @Override
    public void blit(String texture, int x, int y, int u, int v, int width, int height) {
        String key = texture != null ? texture : "minecraft:textures/gui/icons.png";
        Identifier loc = IDENTIFIER_CACHE.get(key);
        if (loc == null) {
            loc = Identifier.tryParse(key);
            if (loc != null) IDENTIFIER_CACHE.put(key, loc);
        }
        if (loc != null) {
            var gpuTexture = Minecraft.getInstance().getTextureManager().getTexture(loc).getTexture();
            g.blit(RenderPipelines.GUI_TEXTURED, loc, x, y, u, v, width, height,
                gpuTexture.getWidth(0), gpuTexture.getHeight(0));
        }
    }

    private static com.mojang.authlib.GameProfile resolveHeadProfile(String username, String uuid) {
        String safeName = username != null ? username : "Player";
        String cacheKey = safeName + "|" + (uuid != null ? uuid : "");
        com.mojang.authlib.GameProfile cached = HEAD_PROFILE_CACHE.get(cacheKey);
        if (cached != null) return cached;
        if (HEAD_PROFILE_CACHE.size() > 512) HEAD_PROFILE_CACHE.clear();
        java.util.UUID parsedUuid = null;
        if (uuid != null && !uuid.isBlank()) {
            String trimmed = uuid.trim();
            if (trimmed.length() == 36) {
                try {
                    parsedUuid = java.util.UUID.fromString(trimmed);
                } catch (Exception ignored) {}
            }
            if (parsedUuid == null) {
                String clean = trimmed.replace("-", "");
                if (clean.length() == 32) {
                    try {
                        long msb = Long.parseUnsignedLong(clean.substring(0, 16), 16);
                        long lsb = Long.parseUnsignedLong(clean.substring(16, 32), 16);
                        parsedUuid = new java.util.UUID(msb, lsb);
                    } catch (Exception ignored) {}
                }
            }
        }
        if (parsedUuid == null) {
            parsedUuid = java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + safeName).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(parsedUuid, safeName);
        HEAD_PROFILE_CACHE.put(cacheKey, profile);
        return profile;
    }

    @Override
    public void drawHead(String username, String uuid, int x, int y, int size) {
        try {
            Minecraft mc = Minecraft.getInstance();
            com.mojang.authlib.GameProfile profile = resolveHeadProfile(username, uuid);
            java.util.function.Supplier<PlayerSkin> lookup = mc.getSkinManager().createLookup(profile, false);
            PlayerSkin skin = lookup != null ? lookup.get() : null;
            if (skin == null) {
                skin = net.minecraft.client.resources.DefaultPlayerSkin.get(profile);
            }
            if (skin != null) {
                PlayerFaceExtractor.extractRenderState(g, skin, x, y, size);
                return;
            }
        } catch (Exception ignored) {}
        try {
            PlayerSkin skin = net.minecraft.client.resources.DefaultPlayerSkin.get(FALLBACK_PROFILE);
            if (skin != null) {
                PlayerFaceExtractor.extractRenderState(g, skin, x, y, size);
                return;
            }
        } catch (Exception ignored) {}
    }

    @Override
    public int getScaledWidth() {
        return g.guiWidth();
    }

    @Override
    public int getScaledHeight() {
        return g.guiHeight();
    }

    @Override
    public int hotbarLift() {
        return com.thelads.core.v26_2.embedded.hoveringhotbar.HoveringHotbar.hotbarLift() + com.thelads.core.v26_2.feature.Raised26.hotbar();
    }

    @Override
    public int guiScale() {
        return Minecraft.getInstance().getWindow().getGuiScale();
    }
}
