// Adapted from Searchables 1.0.1 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core with Controlling.
package com.thelads.core.v26_2.embedded.controlling.searchables.lang;

import com.thelads.core.v26_2.embedded.controlling.searchables.lang.expression.Expression;
import com.thelads.core.v26_2.embedded.controlling.searchables.lang.expression.type.ComponentExpression;
import com.thelads.core.v26_2.embedded.controlling.searchables.lang.expression.type.GroupingExpression;
import com.thelads.core.v26_2.embedded.controlling.searchables.lang.expression.type.LiteralExpression;
import com.thelads.core.v26_2.embedded.controlling.searchables.lang.expression.type.PairedExpression;

import java.util.List;
import java.util.Optional;

public class SLParser {
    
    private final List<Token> tokens;
    private int current = 0;
    
    public SLParser(final List<Token> tokens) {
        
        this.tokens = tokens;
    }
    
    public Optional<Expression> parse() {
        
        if(tokens.size() == 1 && check(TokenType.EOL)) {
            return Optional.empty();
        }
        
        return Optional.of(expression());
    }
    
    private Expression expression() {
        
        return grouping();
    }
    
    
    private Expression grouping() {
        
        Expression expr = literal();
        
        while(match(TokenType.SPACE)) {
            Token operator = previous();
            Expression right = literal();
            
            expr = operator.type() == TokenType.COLON ? new ComponentExpression(expr, operator, right) : new GroupingExpression(expr, operator, right);
        }
        return expr;
    }
    
    private Expression literal() {
        
        if(match(TokenType.COLON)) {
            Token prevColon = previous();
            if(match(TokenType.IDENTIFIER)) {
                Token prevIdent = previous();
                LiteralExpression first = new LiteralExpression(prevColon.literal(), prevColon.lexeme());
                LiteralExpression second = new LiteralExpression(prevIdent.literal(), prevIdent.lexeme());
                return new PairedExpression(first, second);
            }
        }
        if(match(TokenType.IDENTIFIER)) {
            Token previous = previous();
            if(check(TokenType.COLON)) {
                return new ComponentExpression(new LiteralExpression(previous.literal(), previous.lexeme()), advance(), literal());
            }
            return new LiteralExpression(previous.literal(), previous.lexeme());
        }
        
        if(match(TokenType.STRING)) {
            Token previous = previous();
            LiteralExpression literalExpression = new LiteralExpression(previous.literal(), previous.lexeme());
            if(!check(TokenType.SPACE) && !isAtEnd()) {
                return new PairedExpression(literalExpression, expression());
            }
            return literalExpression;
        }
        
        return new LiteralExpression("", "");
    }
    
    private boolean match(final TokenType... types) {
        
        for(TokenType type : types) {
            if(check(type)) {
                advance();
                return true;
            }
        }
        
        return false;
    }
    
    private boolean check(final TokenType type) {
        
        if(isAtEnd()) {
            return false;
        }
        return peek().type() == type;
    }
    
    private Token advance() {
        
        if(!isAtEnd()) {
            current++;
        }
        return previous();
    }
    
    private boolean isAtEnd() {
        
        return peek().type() == TokenType.EOL;
    }
    
    private Token peek() {
        
        return tokens.get(current);
    }
    
    private Token previous() {
        
        return tokens.get(current - 1);
    }
    
}
