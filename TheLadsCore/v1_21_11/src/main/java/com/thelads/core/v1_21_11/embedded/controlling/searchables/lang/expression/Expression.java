// Adapted from Searchables 1.0.4 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core with Controlling.
package com.thelads.core.v1_21_11.embedded.controlling.searchables.lang.expression;

import com.thelads.core.v1_21_11.embedded.controlling.searchables.lang.expression.visitor.ContextAwareVisitor;
import com.thelads.core.v1_21_11.embedded.controlling.searchables.lang.expression.visitor.Visitor;

public abstract class Expression {
    
    public abstract <R> R accept(final Visitor<R> visitor);
    
    public abstract <R, C> R accept(final ContextAwareVisitor<R, C> visitor, final C context);
}
