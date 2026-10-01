// Adapted from Searchables 1.0.2 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core with Controlling.
package com.thelads.core.v1_21_1.embedded.controlling.searchables.api.context;

import com.thelads.core.v1_21_1.embedded.controlling.searchables.api.SearchableComponent;
import com.thelads.core.v1_21_1.embedded.controlling.searchables.api.SearchableType;

import java.util.function.Predicate;

record SearchLiteral<T>(String value) implements SearchPredicate<T> {
    
    @Override
    public Predicate<T> predicateFrom(final SearchableType<T> type) {
        
        return type.defaultComponent()
                .map(SearchableComponent::filter)
                .<Predicate<T>> map(filter -> t -> filter.test(t, value()))
                .orElse(t -> true);
    }
    
}