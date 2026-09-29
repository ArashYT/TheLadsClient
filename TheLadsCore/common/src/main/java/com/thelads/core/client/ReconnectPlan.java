package com.thelads.core.client;

import java.util.List;

/** Reconnect timing independent of rendering cadence. Calls are confined to the client thread. */
public final class ReconnectPlan {
    private int attempts;
    private long due;
    private boolean scheduled;
    private boolean attempting;

    public boolean schedule(List<Integer> seconds, boolean infinite, long now) {
        if (scheduled) return true;
        if (seconds.isEmpty() || (!infinite && attempts >= seconds.size())) return false;
        int delay = seconds.get(Math.min(attempts, seconds.size() - 1));
        if (delay <= 0 || delay > 86400) return false;
        due = now + delay * 1_000_000_000L;
        scheduled = true;
        return true;
    }
    public boolean takeDue(long now) {
        if (!scheduled || now - due < 0) return false;
        scheduled = false; attempts++; attempting = true; return true;
    }
    public int secondsLeft(long now) { return !scheduled ? -1 : (int) Math.max(0, (due - now + 999_999_999) / 1_000_000_000); }
    public boolean pending() { return scheduled; }
    public int attempts() { return attempts; }
    public boolean wasAutomatic() { return attempting; }
    public void cancel() { scheduled = false; attempts = 0; attempting = false; }
    public boolean joined() { boolean automatic = attempting; cancel(); return automatic; }
}
