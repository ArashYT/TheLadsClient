// Adapted from Searchables 1.0.1 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core with Controlling.
package com.thelads.core.v26_2.embedded.controlling.searchables.lang.expression.type;

import com.thelads.core.v26_2.embedded.controlling.searchables.lang.expression.Expression;
import com.thelads.core.v26_2.embedded.controlling.searchables.lang.expression.visitor.ContextAwareVisitor;
import com.thelads.core.v26_2.embedded.controlling.searchables.lang.expression.visitor.Visitor;

public class PairedExpression extends Expression {
    
    private final Expression first;
    private final Expression second;
    
    public PairedExpression(final Expression first, final Expression second) {
        
        this.first = first;
        this.second = second;
    }
    
    public Expression first() {
        
        return first;
    }
    
    public Expression second() {
        
        return second;
    }
    
    @Override
    public <R> R accept(final Visitor<R> visitor) {
        
        return visitor.visitPaired(this);
    }
    
    @Override
    public <R, C> R accept(final ContextAwareVisitor<R, C> visitor, final C context) {
        
        return visitor.visitPaired(this, context);
    }
    
}
