package com.thelads.core.v26_2.embedded.emf.models.animation.math.methods.simple;

import org.apache.commons.lang3.function.TriFunction;
import org.objectweb.asm.MethodVisitor;
import com.thelads.core.v26_2.embedded.emf.models.animation.math.EMFMathException;
import com.thelads.core.v26_2.embedded.emf.models.animation.math.expression_tree.MathMethod;
import com.thelads.core.v26_2.embedded.emf.models.animation.math.asm.ASMVariableHandler;
import com.thelads.core.v26_2.embedded.emf.models.animation.math.methods.MethodRegistry;

import java.util.List;

@Deprecated(forRemoval = true)
public class TriFunctionMethods extends MathMethod {

    @Deprecated(forRemoval = true)
    protected TriFunctionMethods(final List<String> args,
                                 final boolean isNegative,
                                 @SuppressWarnings("removal") final com.thelads.core.v26_2.embedded.emf.models.animation.EMFAnimation calculationInstance,
                                 final TriFunction<Float, Float, Float, Float> function) throws EMFMathException {
        super(isNegative, null, args);
    }

    @Deprecated(forRemoval = true)
    public static MethodRegistry.MethodFactory makeFactory(final String methodName, final TriFunction<Float, Float, Float, Float> function) throws EMFMathException {
        throw new EMFMathException("Failed to create " + methodName + "() method, because: FunctionMethods is deprecated");

    }

    @Override
    public void asmVisitInner(MethodVisitor mv, ASMVariableHandler varNames) throws EMFMathException {
        throw new UnsupportedOperationException("Deprecated");
    }

    @Override
    protected boolean hasCorrectArgCount(final int argCount) {
        return argCount == 3;
    }
}
