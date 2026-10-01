// Adapted from Searchables 1.0.4 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core with Controlling.
package com.thelads.core.v1_21_11.embedded.controlling.searchables.api.context;

import com.thelads.core.v1_21_11.embedded.controlling.searchables.api.SearchableType;

import java.util.function.Predicate;

interface SearchPredicate<T> {
    
    /**
     * Create a predicate for the given {@link SearchableType<T>}
     *
     * @param type The type to search for
     *
     * @return A predicate that can be used to filter elements that the {@link SearchableType<T>} deals with.
     */
    Predicate<T> predicateFrom(final SearchableType<T> type);
    
}