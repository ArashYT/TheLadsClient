package com.thelads.core.v26_2.embedded.emf.models.animation.math.asm;

import org.objectweb.asm.MethodVisitor;
import com.thelads.core.v26_2.embedded.emf.models.animation.math.EMFMathException;

public interface ASMVisitable {
    void asmVisit(MethodVisitor mv, ASMVariableHandler varNames) throws EMFMathException;
}
