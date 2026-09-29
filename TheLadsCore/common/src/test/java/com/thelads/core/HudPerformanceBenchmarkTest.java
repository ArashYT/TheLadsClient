package com.thelads.core;

import com.google.gson.JsonElement;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.hud.*;
import com.thelads.core.config.*;
import com.thelads.core.config.Module;
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CPU/allocation microbenchmark of common code, not a Minecraft FPS benchmark.
 * Run this unchanged before/after a patch with:
 * gradlew :common:test --tests com.thelads.core.HudPerformanceBenchmarkTest --rerun-tasks --offline
 * Output is in the normal JUnit XML system-out. No real configuration or game state is read/written.
 */
class HudPerformanceBenchmarkTest {
    private static final int FRAMES = 262144, WARMUP_BATCHES = 2, SAMPLE_BATCHES = 7;
    private static volatile long consumed;

    @Test void benchmarkCommonHudWithStableOutput() {
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        assertTrue(bean.isThreadAllocatedMemorySupported());
        assertTrue(bean.isCurrentThreadCpuTimeSupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        bean.setThreadCpuTimeEnabled(true);
        System.out.println("HUD_BENCH_ENV java=" + System.getProperty("java.version")
            + " vm=" + System.getProperty("java.vm.name") + " warmup_frames=" + FRAMES * WARMUP_BATCHES
            + " measured_batches=" + SAMPLE_BATCHES + " frames_per_batch=" + FRAMES);
        for (boolean formatted : new boolean[] { false, true })
            for (int stride : new int[] { 0, 8, 1 })
                measure(bean, formatted, stride);
    }

    private static void measure(ThreadMXBean bean, boolean formatted, int stride) {
        HudManager manager = HudManager.getInstance();
        List<HudElement> savedElements = new ArrayList<>(manager.getElements());
        LadsGameBridge savedBridge = LadsGameBridge.get();
        HudSettings settings = HudSettings.getInstance();
        int oldColor = settings.getGlobalColor(), oldBackground = settings.getGlobalBackground();
        boolean oldShadow = settings.isTextShadow();
        List<HudElement> elements = formatted
            ? List.of(new DirectionHudElement(), new SpeedHudElement(), new HealthHudElement(), new HungerHudElement())
            : List.of(new ArmorHudElement(), new ScoreboardHudElement());
        String[] names = formatted ? new String[] {"Direction", "Speed", "Health", "Hunger"}
            : new String[] {"ArmorHUD", "Scoreboard"};
        Map<Module, Boolean> enabled = new LinkedHashMap<>();
        Map<Module, Long> modified = new LinkedHashMap<>();
        Map<Option, JsonElement> options = new LinkedHashMap<>();
        Map<String, int[]> positions = new LinkedHashMap<>();
        Game game = new Game();
        Graphics graphics = new Graphics(game);
        try {
            settings.setGlobalColor(0xFFFFFFFF);
            settings.setGlobalBackground(0x80000000);
            settings.setTextShadow(true);
            for (int i = 0; i < names.length; i++) {
                Module module = ModuleManager.getInstance().getModule(names[i]);
                assertNotNull(module);
                enabled.put(module, module.isEnabled()); modified.put(module, module.getLastModified());
                for (Option option : module.getOptions()) { options.put(option, option.save()); option.reset(); }
                positions.put(names[i], settings.getPosition(names[i]));
                settings.getPositions().remove(names[i]);
                module.setEnabled(true);
                elements.get(i).setModuleName(names[i]);
            }
            if (formatted) {
                cycle("Direction", "Format", 2);
                bool("Health", "Show absorption", true);
                bool("Hunger", "Show saturation", true);
            } else {
                bool("ArmorHUD", "Attach to hotbar", false);
            }
            manager.getElements().clear(); manager.getElements().addAll(elements);
            LadsGameBridge.set(game);
            for (int i = 0; i < WARMUP_BATCHES; i++) renderBatch(manager, graphics, game, formatted, stride);
            double[] cpu = new double[SAMPLE_BATCHES], wall = new double[SAMPLE_BATCHES], allocated = new double[SAMPLE_BATCHES];
            long checksum = 0;
            long thread = Thread.currentThread().threadId();
            for (int i = 0; i < SAMPLE_BATCHES; i++) {
                long startBytes = bean.getThreadAllocatedBytes(thread), startCpu = bean.getCurrentThreadCpuTime();
                long startWall = System.nanoTime();
                long output = renderBatch(manager, graphics, game, formatted, stride);
                wall[i] = (double) (System.nanoTime() - startWall) / FRAMES;
                long elapsed = bean.getCurrentThreadCpuTime() - startCpu;
                long bytes = bean.getThreadAllocatedBytes(thread) - startBytes;
                cpu[i] = (double) elapsed / FRAMES; allocated[i] = (double) bytes / FRAMES;
                if (i == 0) checksum = output;
                else assertEquals(checksum, output, "Output drift between identical measured batches");
                assertEquals(0, graphics.depth, "Unbalanced poses");
            }
            double aggregateCpu = Arrays.stream(cpu).average().orElseThrow();
            Arrays.sort(cpu); Arrays.sort(wall); Arrays.sort(allocated);
            String scenario = (formatted ? "formatted" : "armor_scoreboard")
                + (stride == 0 ? "_static" : stride == 8 ? "_tick_changes" : "_every_frame_changes");
            System.out.printf(Locale.ROOT,
                "HUD_BENCH scenario=%s cpu_ns=%.2f min_cpu_ns=%.2f max_cpu_ns=%.2f median_wall_ns=%.2f allocated_bytes=%.2f width_calls=%.2f draw_calls=%.2f checksum=%016x%n",
                scenario, aggregateCpu, cpu[0], cpu[SAMPLE_BATCHES - 1], wall[SAMPLE_BATCHES / 2], allocated[SAMPLE_BATCHES / 2],
                (double) graphics.measurements / FRAMES, (double) graphics.draws / FRAMES, checksum);
            assertNotEquals(0, checksum);
        } finally {
            manager.getElements().clear(); manager.getElements().addAll(savedElements);
            LadsGameBridge.set(savedBridge);
            options.forEach(Option::load);
            enabled.forEach(Module::setEnabled);
            modified.forEach(Module::setLastModified);
            for (var position : positions.entrySet()) {
                if (position.getValue() == null) settings.getPositions().remove(position.getKey());
                else settings.setPosition(position.getKey(), position.getValue()[0], position.getValue()[1]);
            }
            settings.setGlobalColor(oldColor); settings.setGlobalBackground(oldBackground); settings.setTextShadow(oldShadow);
        }
    }

    private static long renderBatch(HudManager manager, Graphics graphics, Game game, boolean formatted, int stride) {
        graphics.reset();
        for (int frame = 0; frame < FRAMES; frame++) {
            game.state = stride == 0 ? 0 : (frame / stride) & 15;
            // Exercise configuration changes as well as data changes without allocations in the fixture.
            if ((frame & 511) == 0) {
                int mode = stride == 0 ? 0 : (frame / 512) & 1;
                if (formatted) {
                    cycle("Direction", "Format", mode == 0 ? 2 : 1);
                    cycle("Speed", "Unit", mode);
                    cycle("Speed", "Precision", mode + 1);
                    bool("Hunger", "Show label", mode == 0);
                } else {
                    cycle("ArmorHUD", "Durability", mode + 1);
                    bool("Scoreboard", "Hide Red Numbers", mode != 0);
                }
            }
            manager.render(graphics);
        }
        consumed = graphics.checksum;
        return graphics.checksum;
    }

    private static void cycle(String module, String option, int index) {
        ((DropdownOption) ModuleManager.getInstance().getModule(module).getOption(option)).setIndex(index);
    }
    private static void bool(String module, String option, boolean value) {
        ((BoolOption) ModuleManager.getInstance().getModule(module).getOption(option)).set(value);
    }

    private static final class Game extends DefaultGameBridge {
        final List<List<ArmorPiece>> armor = new ArrayList<>();
        final List<ScoreboardSnapshot> boards = new ArrayList<>();
        int state;
        Game() {
            for (int i = 0; i < 16; i++) {
                armor.add(List.of(new ArmorPiece("Diamond Helmet", 340 - i, 363),
                    new ArmorPiece("Diamond Chestplate", 500 - i, 528),
                    new ArmorPiece("Diamond Leggings", 470 - i, 495), new ArmorPiece("Diamond Boots", 400 - i, 429)));
                List<ScoreLine> lines = new ArrayList<>();
                for (int row = 0; row < 15; row++) lines.add(new ScoreLine("Team " + row, String.valueOf(row * 10 + i)));
                boards.add(new ScoreboardSnapshot("The Lads scoreboard", lines));
            }
        }
        @Override public List<ArmorPiece> getArmor() { return armor.get(state); }
        @Override public ScoreboardSnapshot getScoreboard() { return boards.get(state); }
        @Override public String getPlayerDirection() { return state < 8 ? "East" : "South"; }
        @Override public float getYaw() { return 45.67f + state; }
        @Override public double getSpeed() { return 4.325 + state / 10.0; }
        @Override public float getHealth() { return 20 - state / 4f; }
        @Override public float getAbsorption() { return 4.5f + state / 4f; }
        @Override public int getFoodLevel() { return 20 - state / 4; }
        @Override public float getSaturation() { return 7.25f + state / 4f; }
    }

    /** No draw-call lists/strings: consume rendered output without adding fixture allocation noise. */
    private static final class Graphics implements LadsGraphics {
        final Game game;
        long checksum, measurements, draws;
        int depth;
        Graphics(Game game) { this.game = game; }
        void reset() { checksum = 17; measurements = 0; draws = 0; depth = 0; }
        void number(int value) { checksum = checksum * 31 + value; }
        void text(String value) { for (int i = 0; i < value.length(); i++) number(value.charAt(i)); }
        @Override public void fill(int left, int top, int right, int bottom, int color) {
            draws++; number(1); number(left); number(top); number(right); number(bottom); number(color);
        }
        @Override public void drawText(String text, int x, int y, int color, boolean shadow) {
            draws++; number(2); text(text); number(x); number(y); number(color); number(shadow ? 1 : 0);
        }
        @Override public void drawCenteredText(String text, int centerX, int y, int color, boolean shadow) {
            drawText(text, centerX - textWidth(text) / 2, y, color, shadow);
        }
        @Override public int textWidth(String text) {
            measurements++; int width = 0;
            for (int i = 0; i < text.length(); i++) width += text.charAt(i) == 'i' ? 2 : text.charAt(i) == 'W' ? 7 : 6;
            return width;
        }
        @Override public int fontHeight() { return 9; }
        @Override public void pushPose() { depth++; number(3); }
        @Override public void popPose() { depth--; number(4); }
        @Override public void translate(float x, float y) { number(5); number(Float.floatToIntBits(x)); number(Float.floatToIntBits(y)); }
        @Override public void scale(float x, float y) { number(6); number(Float.floatToIntBits(x)); number(Float.floatToIntBits(y)); }
        @Override public void enableScissor(int left, int top, int right, int bottom) { throw new AssertionError("Unexpected scissor"); }
        @Override public void disableScissor() { throw new AssertionError("Unexpected scissor"); }
        @Override public void blit(String texture, int x, int y, int u, int v, int width, int height) { throw new AssertionError("Unexpected texture"); }
        @Override public void drawHead(String username, String uuid, int x, int y, int size) { throw new AssertionError("Unexpected skin"); }
        @Override public int getScaledWidth() { return 640; }
        @Override public int getScaledHeight() { return 360; }
        @Override public LadsGameBridge getGame() { return game; }
    }
}
