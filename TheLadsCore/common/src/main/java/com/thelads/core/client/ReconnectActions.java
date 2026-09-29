package com.thelads.core.client;

import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Delayed actions are tied to one connection and account. The sink is supplied only at dispatch. */
public final class ReconnectActions {
    private record Action(long offset, long order, Object connection, UUID account, String text, boolean signed) {}
    private final PriorityQueue<Action> pending = new PriorityQueue<>(Comparator.comparingLong(Action::offset).thenComparingLong(Action::order));
    private long start, sequence;
    public void begin(long now) { clear(); start = now; }
    public void clear() { pending.clear(); sequence = 0; }
    public int size() { return pending.size(); }
    public void add(Object connection, UUID account, double seconds, List<String> messages, boolean signed) {
        if (connection == null || account == null || !Double.isFinite(seconds) || seconds < .1 || seconds > 3600) return;
        long delay = (long) (seconds * 1_000_000_000L);
        for (int i = 0; i < Math.min(100, messages.size()); i++) {
            String text = messages.get(i);
            if (text == null || text.isBlank() || text.length() > (text.startsWith("/") ? 32767 : 256)) continue;
            pending.add(new Action(delay * (i + 1), sequence++, connection, account, text, signed));
        }
    }
    public void drain(Object connection, UUID account, boolean enabled, long now, BiConsumer<String, Boolean> sink) {
        if (!enabled || connection == null || account == null) { clear(); return; }
        long elapsed = now - start;
        while (!pending.isEmpty() && elapsed >= pending.peek().offset) {
            Action action = pending.remove();
            if (action.connection == connection && action.account.equals(account)) sink.accept(action.text, action.signed);
        }
    }
}
