package com.thelads.core.v1_21_1.embedded.emf.models.animation.math.expression_tree;

import org.objectweb.asm.MethodVisitor;
import com.thelads.core.v1_21_1.embedded.emf.models.animation.AnimSetupContext;
import com.thelads.core.v1_21_1.embedded.emf.models.animation.math.asm.ASMVariableHandler;
import com.thelads.core.v1_21_1.embedded.emf.models.animation.math.variables.VariableRegistry;


public class MathVariable extends MathValue implements MathComponent {

    private final ResultSupplier resultSupplier;
    private final String name;

    public MathVariable(String variableName, boolean isNegative, ResultSupplier supplier) {
        super(isNegative);
        resultSupplier = supplier;
        name = variableName;
    }

    public MathVariable(String variableName, ResultSupplier supplier) {
        resultSupplier = supplier;
        name = variableName;
    }

    static MathComponent getOptimizedVariable(String variableName, boolean isNegative, AnimSetupContext context) {
        if (variableName.startsWith("-")) {//catch mistake of double negative
            return VariableRegistry.getInstance().getVariable(variableName.substring(1), true, context);
        }
        return VariableRegistry.getInstance().getVariable(variableName, isNegative, context);
    }

    @Override
    ResultSupplier getResultSupplier() {
        return resultSupplier;
    }

    @Override
    public String toString() {
        return "variable[" + name + "]=" + getResult();
    }


    @Override
    public void asmVisit(MethodVisitor mv, ASMVariableHandler vars) {

        boolean inverted = name.startsWith("!");

        boolean negativeAgain = name.startsWith("-"); // .. TODO something is surely wrong but this works

        var name = inverted || negativeAgain ? this.name.substring(1) : this.name;

        vars.asmVisitVar(mv, name);

        if (inverted) vars.asmInvertBoolean(mv);
        if (isNegative || negativeAgain) vars.asmNegateFloat(mv);
    }
}
