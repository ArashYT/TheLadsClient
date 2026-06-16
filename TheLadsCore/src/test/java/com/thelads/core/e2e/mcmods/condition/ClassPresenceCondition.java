package com.thelads.core.e2e.mcmods.condition;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

import java.util.Optional;

/**
 * Execution condition that dynamically skips tests if the class specified in {@link EnabledIfClassPresent}
 * is not present on the classpath.
 */
public class ClassPresenceCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        Optional<EnabledIfClassPresent> annotation = AnnotationSupport.findAnnotation(
                context.getElement(), EnabledIfClassPresent.class);

        if (annotation.isPresent()) {
            String[] classNames = annotation.get().value();
            java.util.List<String> presentClasses = new java.util.ArrayList<>();
            java.util.List<String> missingClasses = new java.util.ArrayList<>();
            for (String className : classNames) {
                if (isClassPresent(className, context.getRequiredTestClass().getClassLoader())) {
                    presentClasses.add(className);
                } else {
                    missingClasses.add(className);
                }
            }
            if (!presentClasses.isEmpty()) {
                return ConditionEvaluationResult.enabled("At least one class is present on the classpath: " + presentClasses);
            } else {
                return ConditionEvaluationResult.disabled("None of the classes are present on the classpath: " + missingClasses + ". Skipping test.");
            }
        }
        return ConditionEvaluationResult.enabled("No @EnabledIfClassPresent annotation found.");
    }

    private boolean isClassPresent(String className, ClassLoader loader) {
        try {
            Class.forName(className, false, loader);
            return true;
        } catch (ClassNotFoundException e) {
            try {
                Class.forName(className, false, Thread.currentThread().getContextClassLoader());
                return true;
            } catch (ClassNotFoundException e2) {
                try {
                    Class.forName(className, false, ClassPresenceCondition.class.getClassLoader());
                    return true;
                } catch (ClassNotFoundException e3) {
                    return false;
                }
            }
        }
    }
}
