package com.thelads.ladsapi.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An option rule in rules.yml or API override.
 */
public final class RuleOption {
    private Object value;
    private Double min;
    private Double max;
    private List<String> choices;
    private Boolean lock;

    public RuleOption() {}

    public RuleOption(Object value, Double min, Double max, List<String> choices, Boolean lock) {
        this.value = value;
        this.min = min;
        this.max = max;
        this.choices = choices != null ? new ArrayList<>(choices) : null;
        this.lock = lock;
    }

    public static RuleOption copyOf(RuleOption other) {
        if (other == null) return null;
        return new RuleOption(other.value, other.min, other.max, other.choices, other.lock);
    }

    public Object getValue() { return value; }
    public void setValue(Object value) { this.value = value; }

    public Double getMin() { return min; }
    public void setMin(Double min) { this.min = min; }

    public Double getMax() { return max; }
    public void setMax(Double max) { this.max = max; }

    public List<String> getChoices() { return choices != null ? Collections.unmodifiableList(choices) : null; }
    public void setChoices(List<String> choices) { this.choices = choices != null ? new ArrayList<>(choices) : null; }

    public Boolean getLock() { return lock; }
    public void setLock(Boolean lock) { this.lock = lock; }

    public boolean isEmpty() {
        return value == null && min == null && max == null && choices == null && lock == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RuleOption that = (RuleOption) o;
        return Objects.equals(value, that.value) &&
               Objects.equals(min, that.min) &&
               Objects.equals(max, that.max) &&
               Objects.equals(choices, that.choices) &&
               Objects.equals(lock, that.lock);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, min, max, choices, lock);
    }
}
