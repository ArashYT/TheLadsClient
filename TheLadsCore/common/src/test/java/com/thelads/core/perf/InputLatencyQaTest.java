package com.thelads.core.perf;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InputLatencyQaTest {
    @Test
    void runsEveryPhaseOnceThenTheWindowStepsThenTheVerdict() {
        List<String> calls = new ArrayList<>();
        LatencyMeter meter = new LatencyMeter(Object::new, fence -> true, fence -> {});
        InputLatencyQa qa = new InputLatencyQa(new InputLatencyQa.Target() {
            @Override public void paced(boolean on) { calls.add("paced " + on); }
            @Override public void gpuLoad(int units) { calls.add("load " + units); }
            @Override public void flood(int mode) { calls.add("flood " + mode); }
            @Override public void window(String action) { calls.add("window " + action); }
            @Override public void log(String line) { calls.add(line); }
        }, meter, new FramePacer(), 1, 1, 2);
        long[] now = {0};
        qa.clock = () -> now[0];
        for (int tick = 0; tick < 100_000 && !qa.tick(); tick++) {
            now[0] += 50_000_000L;
            meter.input();
            meter.fence();
        }
        assertTrue(qa.done(), "finished");
        assertEquals(9, calls.stream().filter(c -> c.contains("probe: phase ")).count(), "6 pacing phases, 2 floods, 1 baseline");
        assertEquals(1, calls.stream().filter(c -> c.contains("phase 9 light paced flood 0")).count());
        assertEquals(List.of("window resize", "window restore", "window fullscreen", "window windowed"),
            calls.stream().filter(c -> c.startsWith("window ")).toList());
        String last = calls.get(calls.size() - 1);
        assertTrue(last.startsWith("Lads input probe END: ") || last.startsWith("Lads input probe FAILED: "), last);
        assertEquals("paced true", calls.stream().filter(c -> c.startsWith("paced")).reduce((a, b) -> b).orElseThrow());
    }
}
