package com.thelads.core.v1_21_11.embedded.emf.models.animation.math.variables.factories;

import org.jetbrains.annotations.Nullable;
import com.thelads.core.v1_21_11.embedded.emf.models.animation.AnimSetupContext;
import com.thelads.core.v1_21_11.embedded.emf.models.animation.math.expression_tree.MathValue;
import com.thelads.core.v1_21_11.embedded.emf.models.animation.math.variables.EMFModelOrRenderVariable;
import com.thelads.core.v1_21_11.embedded.emf.utils.EMFUtils;

import java.util.function.BooleanSupplier;

public class RenderVariableFactory extends UniqueVariableFactory {
    @Override
    public MathValue.ResultSupplier getSupplierOrNull(final String variableKey, AnimSetupContext context) {
        //requires calculation instance check before global check so must be a factory
        MathValue.ResultSupplier renderVariableCalculator = context.oldAnimationHandler.getLastResultGetter(variableKey);
        if (renderVariableCalculator != null) {
            return renderVariableCalculator;
        }
        //try get default
        EMFModelOrRenderVariable variable = EMFModelOrRenderVariable.getRenderVariable(variableKey);
        if (variable != null && variable.isRenderVariable())
            return variable::getValue;
        if (printing()) EMFUtils.logWarn("no render variable found for: [" + variableKey + "]");
        return null;
    }

    @Override
    public boolean createsThisVariable(final String variableKey) {
        if (variableKey == null) return false;
        return variableKey.matches("(render)\\.\\w+");
    }

    @Override
    public @Nullable String getExplanationTranslationKey() {
        return "entity_model_features.config.variable_explanation.render_variable";
    }

    @Override
    public @Nullable String getTitleTranslationKey() {
        return "entity_model_features.config.variable_explanation.render_variable.title";
    }
}
