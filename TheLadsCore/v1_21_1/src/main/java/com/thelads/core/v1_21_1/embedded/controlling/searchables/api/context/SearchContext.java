// Adapted from Searchables 1.0.2 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core with Controlling.
package com.thelads.core.v1_21_1.embedded.controlling.searchables.api.context;

import com.thelads.core.v1_21_1.embedded.controlling.searchables.api.SearchableType;

import java.util.*;
import java.util.function.Predicate;

/**
 * Holds {@link SearchPredicate<T>} to be reduced to a single {@link Predicate<T>} for use in filtering.
 */
public final class SearchContext<T> {
    
    private final List<SearchPredicate<T>> predicates;
    
    public SearchContext() {
        
        this.predicates = new ArrayList<>();
    }
    
    public Predicate<T> createPredicate(final SearchableType<T> type) {
        
        return predicates.stream()
                .map(tSearchPredicate -> tSearchPredicate.predicateFrom(type))
                .reduce(t -> true, Predicate::and);
    }
    
    public void add(final SearchPredicate<T> literal) {
        
        this.predicates.add(literal);
    }
    
}