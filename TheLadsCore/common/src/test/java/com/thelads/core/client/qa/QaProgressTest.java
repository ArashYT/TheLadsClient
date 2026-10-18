package com.thelads.core.client.qa;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.qa.QaProgress.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 1.9.12: the QA progress bar's model: counts, colour state, ETA, hiding, and that players (bar off) get nothing. */
class QaProgressTest {
    private final AtomicLong now = new AtomicLong(1_000_000);

    @BeforeEach void on() { QaProgress.reset(true, now::get); }
    @AfterEach void off() { QaProgress.reset(false, System::currentTimeMillis); }

    private void takes(String label, long ms) { QaProgress.step(label); now.addAndGet(ms); }

    @Test void countsStepsAndFillsTheBar() {
        QaProgress.begin("anim", 4);
        assertEquals(0f, QaProgress.progress());
        takes("a", 1000); takes("b", 1000);
        assertArrayEquals(new int[] {1, 0, 0, 0}, QaProgress.counts()); // b is still open
        assertEquals(0.25f, QaProgress.progress());
        QaProgress.step("c");
        assertArrayEquals(new int[] {2, 0, 0, 0}, QaProgress.counts());
        assertEquals(0.5f, QaProgress.progress());
        QaProgress.done();
        assertArrayEquals(new int[] {3, 0, 0, 0}, QaProgress.counts());
        assertEquals(1f, QaProgress.progress());
        assertFalse(QaProgress.running());
        assertTrue(QaProgress.text().contains("step 3/4 · done"), QaProgress.text());
    }

    @Test void colourFollowsTheWorstResult() {
        QaProgress.begin("colour", 5);
        assertEquals(0, QaProgress.state());
        takes("fine", 10);
        QaProgress.slowAfter(500);
        takes("slow", 600); // passes as SLOW once the next step starts
        QaProgress.step("next");
        assertArrayEquals(new int[] {1, 1, 0, 0}, QaProgress.counts());
        assertEquals(1, QaProgress.state());
        QaProgress.result(Result.FAIL);
        QaProgress.step("after the fail"); // the failed step is not passed a second time
        assertArrayEquals(new int[] {1, 1, 1, 0}, QaProgress.counts());
        assertEquals(2, QaProgress.state());
        QaProgress.result(Result.CRASH);
        assertArrayEquals(new int[] {1, 1, 1, 1}, QaProgress.counts());
        assertEquals(2, QaProgress.state());
    }

    @Test void etaIsTheAverageFinishedStepTimesTheStepsLeft() {
        QaProgress.begin("eta", 10);
        assertEquals("--:--", QaProgress.eta());
        takes("one", 10_000);
        QaProgress.step("two");
        assertEquals("1:30", QaProgress.eta()); // 9 left at 10 s
        now.addAndGet(20_000);
        QaProgress.step("three"); // average 15 s, 8 left
        assertEquals("2:00", QaProgress.eta());
        QaProgress.begin("unknown total", 0);
        takes("x", 1000);
        QaProgress.step("y");
        assertEquals("--:--", QaProgress.eta());
        assertTrue(QaProgress.text().contains("step 2/?"), QaProgress.text());
    }

    @Test void moreStepsThanAnnouncedRaiseTheTotal() {
        QaProgress.begin("grow", 1);
        QaProgress.step("a");
        QaProgress.step("b");
        assertTrue(QaProgress.text().contains("step 2/2"), QaProgress.text());
    }

    @Test void aNewRunStartsClean() {
        QaProgress.begin("first", 2);
        takes("a", 1); QaProgress.result(Result.FAIL);
        QaProgress.done();
        QaProgress.begin("second", 2);
        assertArrayEquals(new int[] {0, 0, 0, 0}, QaProgress.counts());
        assertEquals(0, QaProgress.state());
        assertEquals("second", QaProgress.run());
    }

    @Test void aRunInsideARunIsNestedAndHandsBackItsWorstResult() {
        QaProgress.begin("outer", 3);
        QaProgress.step("hud layout");
        QaProgress.begin("inner", 2);
        assertEquals("inner", QaProgress.run());
        takes("a", 10);
        QaProgress.step("b");
        QaProgress.result(Result.FAIL);
        QaProgress.done();
        assertEquals("outer", QaProgress.run());
        assertTrue(QaProgress.running());
        assertArrayEquals(new int[] {0, 0, 1, 0}, QaProgress.counts()); // the outer step failed because the inner run did
        QaProgress.step("next");
        QaProgress.done();
        assertArrayEquals(new int[] {1, 0, 1, 0}, QaProgress.counts());
        assertFalse(QaProgress.running());
    }

    @Test void theFinalStateIsPhotographedOnceAMomentAfterTheOutermostRunIsDone() {
        QaProgress.begin("anim 1.9.11", 1);
        QaProgress.begin("inner", 1);
        QaProgress.step("a");
        QaProgress.done(); // nested: not the end
        now.addAndGet(5000);
        assertNull(QaProgress.finalShot());
        QaProgress.done();
        assertNull(QaProgress.finalShot(), "too soon");
        now.addAndGet(300);
        assertEquals("qa-bar-final-anim-1.9.11", QaProgress.finalShot());
        assertNull(QaProgress.finalShot(), "only once");
    }

    @Test void withNoRunTheSessionShowsPhaseAndElapsedTimeAndTheLastRunsFinalStateStaysAMoment() {
        Recorder g = new Recorder();
        g.width = 700;
        QaProgress.draw(g);
        assertEquals("Lads QA · QA · loading · runs 0 · elapsed 0:00", g.texts.get(0));
        now.addAndGet(125_000);
        g.clear();
        QaProgress.draw(g);
        assertEquals("Lads QA · QA · loading · runs 0 · elapsed 2:05", g.texts.get(0));
        QaProgress.begin("capture", 1);
        QaProgress.check("a check", false);
        QaProgress.done();
        g.clear();
        QaProgress.draw(g);
        assertTrue(g.texts.get(0).startsWith("Lads QA · capture · step 1/1 · done"), g.texts.get(0)); // the final state first
        now.addAndGet(3_500);
        g.clear();
        QaProgress.draw(g);
        assertTrue(g.texts.get(0).startsWith("Lads QA · QA · loading · runs 1 · elapsed 2:08"), g.texts.get(0));
        assertTrue(g.fills.stream().anyMatch(f -> f[4] == 0xFFD83A3A), "the session is red after a failed run");
    }

    @Test void theDrawnTextIsBuiltOnlyWhenSomethingChanged() {
        QaProgress.begin("cache", 3);
        QaProgress.step("a");
        Recorder g = new Recorder();
        g.width = 900;
        QaProgress.draw(g);
        String first = g.texts.get(0);
        g.clear();
        QaProgress.draw(g);
        assertSame(first, g.texts.get(0), "the same String object on the next frame");
        QaProgress.step("b");
        g.clear();
        QaProgress.draw(g);
        assertNotSame(first, g.texts.get(0));
    }

    /** The owner's benchmark rule: the bar costs nothing worth measuring: after the first frame, drawing it allocates no memory. */
    @Test void drawingAllocatesNothingOnceTheTextIsBuilt() {
        QaProgress.begin("alloc", 10);
        QaProgress.step("a step");
        Counting g = new Counting();
        for (int i = 0; i < 2_000; i++) QaProgress.draw(g); // warm up: the first frame builds the text, the JIT compiles
        var threads = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        long before = threads.getThreadAllocatedBytes(Thread.currentThread().getId()), t0 = System.nanoTime();
        for (int i = 0; i < 50_000; i++) QaProgress.draw(g);
        long took = System.nanoTime() - t0;
        long bytes = threads.getThreadAllocatedBytes(Thread.currentThread().getId()) - before;
        System.out.println("QaProgress.draw: " + took / 50_000 + " ns per frame (the game's calls excluded), " + bytes + " bytes allocated over 50000 frames");
        assertTrue(bytes < 4_096, "allocated " + bytes + " bytes over 50000 frames");
        assertTrue(g.calls > 100_000);
    }

    @Test void aResultWithNoStepOpenIsCountedAnyway() {
        QaProgress.begin("r", 2);
        QaProgress.result(Result.FAIL);
        assertArrayEquals(new int[] {0, 0, 1, 0}, QaProgress.counts());
        assertEquals(2, QaProgress.state());
    }

    @Test void drawsTheBarAndLeavesItOutWhenHidden() {
        QaProgress.begin("draw", 2);
        takes("a", 1000);
        Recorder g = new Recorder();
        g.width = 600;
        QaProgress.draw(g);
        assertTrue(g.texts.get(0).startsWith("Lads QA · draw · step 1/2 · a · OK 0 · SLOW 0 · FAIL 0 · CRASH 0 · ETA"), g.texts.toString());
        assertEquals(1, g.texts.size());
        assertTrue(g.fills.stream().anyMatch(f -> f[0] == (600 - 182) / 2), "the 182 wide bar is centred");
        g.clear();
        QaProgress.hideFrames(2);
        QaProgress.draw(g); QaProgress.draw(g);
        assertTrue(g.texts.isEmpty() && g.fills.isEmpty(), "two frames left out");
        QaProgress.draw(g);
        assertFalse(g.texts.isEmpty(), "then it is back");
        g.clear();
        QaProgress.hide(true);
        QaProgress.draw(g);
        assertTrue(g.texts.isEmpty());
        QaProgress.hide(false);
        QaProgress.draw(g);
        assertFalse(g.texts.isEmpty());
    }

    @Test void longTextGoesOnTwoLinesAndTheStepLabelIsCut() {
        QaProgress.begin("a very long run name", 99);
        QaProgress.step("a step label that is much too long to fit in a narrow window next to the rest of the line");
        Recorder g = new Recorder();
        g.width = 300;
        QaProgress.draw(g);
        assertEquals(2, g.texts.size(), g.texts.toString());
        assertTrue(g.texts.get(0).endsWith("~"), g.texts.get(0));
        assertTrue(g.texts.get(0).length() * 6 <= 300 - 8);
        assertTrue(g.texts.get(1).startsWith("OK 0 · SLOW 0"), g.texts.get(1));
    }

    @Test void barBoundsMatchDrawnBackdrop() {
        Recorder g = new Recorder();
        g.width = 400;
        QaProgress.begin("run", 2);
        QaProgress.step("step 1");
        QaProgress.draw(g);
        int left = QaProgress.barLeft(400);
        int right = QaProgress.barRight(400);
        int bottom = QaProgress.barBottom(9);
        assertFalse(g.fills.isEmpty(), "should have drawn fills");
        int[] backdrop = g.fills.get(0);
        assertEquals(backdrop[0], left, "barLeft should match backdrop minX");
        assertEquals(backdrop[2], right, "barRight should match backdrop maxX");
        assertEquals(backdrop[3], bottom, "barBottom should match backdrop maxY");
        assertTrue(right > left);
        assertTrue(right - left >= 182);
    }

    @Test void offForPlayers() {
        QaProgress.reset(false, now::get);
        QaProgress.begin("off", 3);
        QaProgress.step("x");
        QaProgress.result(Result.FAIL);
        assertFalse(QaProgress.running());
        assertNull(QaProgress.run());
        Recorder g = new Recorder();
        QaProgress.draw(g);
        assertTrue(g.texts.isEmpty() && g.fills.isEmpty());
    }

    /** Counts calls only (allocates nothing itself). */
    private static final class Counting implements LadsGraphics {
        int calls;
        @Override public void fill(int minX, int minY, int maxX, int maxY, int color) { calls++; }
        @Override public void drawText(String text, int x, int y, int color, boolean shadow) { calls++; }
        @Override public void drawCenteredText(String text, int centerX, int y, int color, boolean shadow) { calls++; }
        @Override public int textWidth(String text) { return text.length() * 6; }
        @Override public int fontHeight() { return 9; }
        @Override public void pushPose() {}
        @Override public void popPose() {}
        @Override public void translate(float x, float y) {}
        @Override public void scale(float sx, float sy) {}
        @Override public void enableScissor(int a, int b, int c, int d) {}
        @Override public void disableScissor() {}
        @Override public void blit(String texture, int x, int y, int u, int v, int w, int h) {}
        @Override public void drawHead(String username, String uuid, int x, int y, int size) {}
        @Override public int getScaledWidth() { return 960; }
        @Override public int getScaledHeight() { return 540; }
        @Override public com.thelads.core.client.bridge.LadsGameBridge getGame() { return null; }
    }

    private static final class Recorder implements LadsGraphics {
        final List<String> texts = new ArrayList<>();
        final List<int[]> fills = new ArrayList<>();
        int width = 400;
        void clear() { texts.clear(); fills.clear(); }
        @Override public void fill(int minX, int minY, int maxX, int maxY, int color) { fills.add(new int[] {minX, minY, maxX, maxY, color}); }
        @Override public void drawText(String text, int x, int y, int color, boolean shadow) { texts.add(text); }
        @Override public void drawCenteredText(String text, int centerX, int y, int color, boolean shadow) { texts.add(text); }
        @Override public int textWidth(String text) { return text.length() * 6; }
        @Override public int fontHeight() { return 9; }
        @Override public void pushPose() {}
        @Override public void popPose() {}
        @Override public void translate(float x, float y) {}
        @Override public void scale(float sx, float sy) {}
        @Override public void enableScissor(int a, int b, int c, int d) {}
        @Override public void disableScissor() {}
        @Override public void blit(String texture, int x, int y, int u, int v, int w, int h) {}
        @Override public void drawHead(String username, String uuid, int x, int y, int size) {}
        @Override public int getScaledWidth() { return width; }
        @Override public int getScaledHeight() { return 300; }
        @Override public com.thelads.core.client.bridge.LadsGameBridge getGame() { return null; }
    }
}
