package com.thelads.core.v26_2.embedded.emf.models.animation.math.variables.factories;

import org.jetbrains.annotations.Nullable;
import com.thelads.core.v26_2.embedded.emf.models.animation.AnimSetupContext;
import com.thelads.core.v26_2.embedded.emf.models.animation.math.EMFMath;
import com.thelads.core.v26_2.embedded.emf.models.animation.math.expression_tree.MathValue;

import java.util.function.BooleanSupplier;

import static com.thelads.core.v26_2.embedded.emf.models.animation.math.expression_tree.MathValue.FALSE;

public class ModelVariableFactory extends UniqueVariableFactory {
    @Override
    public MathValue.ResultSupplier getSupplierOrNull(final String variableKey, AnimSetupContext context) {
        if (variableKey.startsWith("varb")) {
            return () -> EMFMath.getEntityVariable(variableKey, FALSE);
        } else {
            return () -> EMFMath.getEntityVariable(variableKey, 0);
        }

    }


    @Override
    public boolean createsThisVariable(final String variableKey) {
        if (variableKey == null) return false;
        return variableKey.matches("(var|varb)\\.\\w+");
    }

    @Override
    public @Nullable String getExplanationTranslationKey() {
        return "entity_model_features.config.variable_explanation.entity_variable";
    }

    @Override
    public @Nullable String getTitleTranslationKey() {
        return "entity_model_features.config.variable_explanation.entity_variable.title";
    }
}
