// Adapted from Searchables 1.0.2 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core with Controlling.
package com.thelads.core.v1_21_1.embedded.controlling.searchables.lang.expression.type;

import com.thelads.core.v1_21_1.embedded.controlling.searchables.lang.Token;
import com.thelads.core.v1_21_1.embedded.controlling.searchables.lang.expression.Expression;
import com.thelads.core.v1_21_1.embedded.controlling.searchables.lang.expression.visitor.ContextAwareVisitor;
import com.thelads.core.v1_21_1.embedded.controlling.searchables.lang.expression.visitor.Visitor;

public class GroupingExpression extends Expression {
    
    private final Expression left;
    private final Token operator;
    private final Expression right;
    
    public GroupingExpression(final Expression left, final Token operator, final Expression right) {
        
        this.left = left;
        this.operator = operator;
        this.right = right;
    }
    
    @Override
    public <R> R accept(final Visitor<R> visitor) {
        
        return visitor.visitGrouping(this);
    }
    
    @Override
    public <R, C> R accept(final ContextAwareVisitor<R, C> visitor, final C context) {
        
        return visitor.visitGrouping(this, context);
    }
    
    public Expression left() {
        
        return left;
    }
    
    public Token operator() {
        
        return operator;
    }
    
    public Expression right() {
        
        return right;
    }
    
    
    @Override
    public String toString() {
        
        return "(%s%s%s)".formatted(left, operator.literal(), right);
    }
    
}
