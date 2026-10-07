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
                if(names)g.text(font,event.getName(),x+(182-font.width(event.getName()))/2,yy,0xFFFFFFFF);
            }
        } catch (Throwable ignored) {}
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

    private static final Identifier HOTBAR = Identifier.withDefaultNamespace("hud/hotbar");
    private static final Identifier[] EMPTY_ARMOR = {Identifier.withDefaultNamespace("container/slot/helmet"),
        Identifier.withDefaultNamespace("container/slot/chestplate"), Identifier.withDefaultNamespace("container/slot/leggings"),
        Identifier.withDefaultNamespace("container/slot/boots")};

    @Override public void drawSprite(String sprite, int x, int y, int size) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.parse(sprite), x, y, size, size);
    }

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
        Identifier loc = Identifier.tryParse(texture != null ? texture : "minecraft:textures/gui/icons.png");
        if (loc != null) {
            var gpuTexture = Minecraft.getInstance().getTextureManager().getTexture(loc).getTexture();
            g.blit(RenderPipelines.GUI_TEXTURED, loc, x, y, u, v, width, height,
                gpuTexture.getWidth(0), gpuTexture.getHeight(0));
        }
    }

    @Override
    public void drawHead(String username, String uuid, int x, int y, int size) {
        try {
            Minecraft mc = Minecraft.getInstance();
            java.util.UUID parsedUuid = null;
            if (uuid != null && !uuid.isBlank()) {
                String clean = uuid.replace("-", "").trim();
                if (clean.length() == 32) {
                    try {
                        parsedUuid = java.util.UUID.fromString(clean.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"));
                    } catch (Exception ignored) {}
                } else {
                    try {
                        parsedUuid = java.util.UUID.fromString(uuid.trim());
                    } catch (Exception ignored) {}
                }
            }
            if (parsedUuid == null) {
                parsedUuid = java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + (username != null ? username : "Player")).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(parsedUuid, username != null ? username : "Player");
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
            com.mojang.authlib.GameProfile fallback = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "Player");
            PlayerSkin skin = net.minecraft.client.resources.DefaultPlayerSkin.get(fallback);
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
}
