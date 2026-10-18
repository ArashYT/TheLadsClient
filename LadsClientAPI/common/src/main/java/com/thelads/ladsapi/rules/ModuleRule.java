package com.thelads.ladsapi.rules;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A module's state and option rules.
 */
public final class ModuleRule {
    private String state; // "allow", "block", "on", or null (inherit)
    private final Map<String, RuleOption> options = new LinkedHashMap<>();

    public ModuleRule() {}

    public ModuleRule(String state) {
        this.state = state;
    }

    public static ModuleRule copyOf(ModuleRule other) {
        if (other == null) return null;
        ModuleRule copy = new ModuleRule(other.state);
        for (Map.Entry<String, RuleOption> entry : other.options.entrySet()) {
            copy.options.put(entry.getKey(), RuleOption.copyOf(entry.getValue()));
        }
        return copy;
    }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public Map<String, RuleOption> getOptions() { return options; }

    public void setOption(String optionName, RuleOption option) {
        if (option == null) {
            options.remove(optionName);
        } else {
            options.put(optionName, option);
        }
    }

    public RuleOption getOption(String optionName) {
        return options.get(optionName);
    }

    public void mergeFrom(ModuleRule other) {
        if (other == null) return;
        if (other.state != null && !other.state.isBlank()) {
            this.state = other.state;
        }
        for (Map.Entry<String, RuleOption> entry : other.options.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                RuleOption existing = this.options.get(entry.getKey());
                if (existing == null) {
                    this.options.put(entry.getKey(), RuleOption.copyOf(entry.getValue()));
                } else {
                    if (entry.getValue().getValue() != null) existing.setValue(entry.getValue().getValue());
                    if (entry.getValue().getMin() != null) existing.setMin(entry.getValue().getMin());
                    if (entry.getValue().getMax() != null) existing.setMax(entry.getValue().getMax());
                    if (entry.getValue().getChoices() != null) existing.setChoices(entry.getValue().getChoices());
                    if (entry.getValue().getLock() != null) existing.setLock(entry.getValue().getLock());
                }
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModuleRule that = (ModuleRule) o;
        return Objects.equals(state, that.state) && Objects.equals(options, that.options);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, options);
    }
}
