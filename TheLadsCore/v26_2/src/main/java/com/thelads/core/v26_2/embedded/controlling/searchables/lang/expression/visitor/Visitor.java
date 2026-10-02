// Adapted from Searchables 1.0.1 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core with Controlling.
package com.thelads.core.v26_2.embedded.controlling.searchables.lang.expression.visitor;

import com.thelads.core.v26_2.embedded.controlling.searchables.lang.expression.type.ComponentExpression;
import com.thelads.core.v26_2.embedded.controlling.searchables.lang.expression.type.GroupingExpression;
import com.thelads.core.v26_2.embedded.controlling.searchables.lang.expression.type.LiteralExpression;
import com.thelads.core.v26_2.embedded.controlling.searchables.lang.expression.type.PairedExpression;

public interface Visitor<R> {
    
    R visitGrouping(GroupingExpression expr);
    
    R visitComponent(ComponentExpression expr);
    
    R visitLiteral(LiteralExpression expr);
    
    R visitPaired(PairedExpression expr);
    
    default R postVisit(R obj) {
        return obj;
    }
    
}