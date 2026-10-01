// Adapted from Searchables 1.0.4 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core with Controlling.
package com.thelads.core.v1_21_11.embedded.controlling.searchables.lang.expression.visitor;

import com.thelads.core.v1_21_11.embedded.controlling.searchables.lang.expression.type.ComponentExpression;
import com.thelads.core.v1_21_11.embedded.controlling.searchables.lang.expression.type.GroupingExpression;
import com.thelads.core.v1_21_11.embedded.controlling.searchables.lang.expression.type.LiteralExpression;
import com.thelads.core.v1_21_11.embedded.controlling.searchables.lang.expression.type.PairedExpression;

public interface ContextAwareVisitor<R, C> {
    
    R visitGrouping(GroupingExpression expr, C context);
    
    R visitComponent(ComponentExpression expr, C context);
    
    R visitLiteral(LiteralExpression expr, C context);
    
    R visitPaired(PairedExpression expr, C context);
    
    default R postVisit(R obj, C context) {
        
        return obj;
    }
    
}