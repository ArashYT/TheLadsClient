package com.thelads.core.v1_21_11.embedded.emf.models.animation.math.asm;

import org.objectweb.asm.MethodVisitor;
import com.thelads.core.v1_21_11.embedded.emf.models.animation.math.EMFMathException;

public interface ASMVisitable {
    void asmVisit(MethodVisitor mv, ASMVariableHandler varNames) throws EMFMathException;
}
