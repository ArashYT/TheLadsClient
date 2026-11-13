package com.thelads.smp.qa;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Client half of the smoke sweep: draws every item icon and tooltip, then every living entity, one page per frame. */
public class SmokeClient implements ClientModInitializer {
    private int ticks;
    private boolean opened, quitting;

    @Override
    public void onInitializeClient() {
        if (!SmokeMod.ON) return;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null || mc.level == null) return;
            ticks++;
            if (!opened && ticks > 100) { opened = true; mc.setScreenAndShow(new Sweep()); }
            if (!quitting && Boolean.getBoolean("ladsqa.halt") && Files.exists(SmokeMod.DIR.resolve("summary.txt"))
                    && Files.exists(SmokeMod.DIR.resolve("client-summary.txt"))) { quitting = true; mc.stop(); }
        });
    }

    static final class Sweep extends Screen {
        final List<Item> items = new ArrayList<>();
        final List<EntityType<?>> types = new ArrayList<>();
        final Map<String, int[]> perMod = new TreeMap<>();
        int page, fails;
        static final int PER = 120;

        Sweep() {
            super(Component.literal("LadsQA sweep"));
            BuiltInRegistries.ITEM.forEach(items::add);
            BuiltInRegistries.ENTITY_TYPE.forEach(types::add);
        }

        @Override public boolean isPauseScreen() { return false; }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
            Minecraft mc = Minecraft.getInstance();
            int itemPages = (items.size() + PER - 1) / PER;
            if (page < itemPages) {
                for (int i = page * PER, k = 0; i < Math.min(items.size(), (page + 1) * PER); i++, k++) {
                    Item it = items.get(i);
                    String id = BuiltInRegistries.ITEM.getKey(it).toString();
                    int x = 4 + (k % 20) * 18, y = 4 + (k / 20) * 18;
                    run("item", id, () -> {
                        ItemStack s = new ItemStack(it);
                        g.item(s, x, y);
                        g.itemDecorations(font, s, x, y);
                        s.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.ADVANCED);
                    });
                }
            } else if (page - itemPages < types.size()) {
                EntityType<?> t = types.get(page - itemPages);
                String id = BuiltInRegistries.ENTITY_TYPE.getKey(t).toString();
                run("entity", id, () -> {
                    if (t == net.minecraft.world.entity.EntityTypes.PLAYER) return;
                    Entity e = t.create(mc.level, EntitySpawnReason.COMMAND);
                    if (e instanceof LivingEntity le) InventoryScreen.extractEntityInInventoryFollowsMouse(g, 40, 40, 200, 200, 40, 0.0625f, 120, 120, le);
                });
            } else {
                StringBuilder sb = new StringBuilder("mod ok fail\n");
                perMod.forEach((k, v) -> sb.append(k).append(' ').append(v[0]).append(' ').append(v[1]).append('\n'));
                sb.append("TOTAL_FAILS ").append(fails).append('\n');
                write("client-summary.txt", sb.toString(), false);
                write("client-current.txt", "DONE", false);
                onClose();
                return;
            }
            g.text(font, "LadsQA sweep page " + page, 4, height - 12, 0xFFFFFFFF);
            page++;
        }

        void run(String phase, String id, Runnable r) {
            write("client-current.txt", phase + " " + id + " page " + page, false);
            int[] c = perMod.computeIfAbsent(id.substring(0, id.indexOf(':')), k -> new int[2]);
            try { r.run(); c[0]++; }
            catch (Throwable t) {
                c[1]++; fails++;
                StringBuilder sb = new StringBuilder("FAIL " + id.substring(0, id.indexOf(':')) + " | client " + phase + " " + id + " | " + t);
                for (StackTraceElement el : t.getStackTrace()) { sb.append("\n    at ").append(el); if (sb.length() > 2500) break; }
                write("client-report.txt", sb + "\n", true);
            }
        }
    }

    static void write(String name, String s, boolean append) {
        try {
            Files.createDirectories(SmokeMod.DIR);
            Files.writeString(SmokeMod.DIR.resolve(name), s, append ? new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.APPEND}
                    : new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING});
        } catch (IOException ignored) { }
    }
}
