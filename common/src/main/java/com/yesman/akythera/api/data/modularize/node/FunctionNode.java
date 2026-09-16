package com.yesman.akythera.api.data.modularize.node;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akythera.core.reflection.FunctionSignature;

import java.util.List;

public interface FunctionNode extends ScriptNode {
    ScriptFunction function();

    List<PureNode> params();

    @Override
    default <T> T execute(final DataDrivenScript<?, ?> script, final ExecutionContext context) {
        FunctionSignature signature = function().functionSignature();
        boolean returnsVoid = signature.returnType() == void.class || signature.returnType() == Void.class;

        context.enter(this);

        List<PureNode> params = params();
        Object[] args = new Object[params.size()];
        ScriptVariables enclosingScope = context.beginCallbackScope();

        try {
            for (int i = 0; i < args.length; i++) {
                args[i] = params.get(i).execute(script, context);
            }
        } finally {
            context.endCallbackScope(enclosingScope);
        }

        context.recordInputs(args);

        if (returnsVoid) {
            invokeVoid(args);
            return context.done();
        }

        return context.returnVal(invokeReturning(args));
    }

    private void invokeVoid(final Object[] args) {
        switch (args.length) {
            case 0 -> function().castToRunnable().runnable().run();
            case 1 -> function().castToConsumer().consumer().accept(args[0]);
            case 2 -> function().castToBiConsumer().biConsumer().accept(args[0], args[1]);
            case 3 -> function().castToTriConsumer().triConsumer().accept(args[0], args[1], args[2]);
            case 4 -> function().castToQuadConsumer().quadConsumer().accept(args[0], args[1], args[2], args[3]);
            case 5 -> function().castToQuintConsumer().quintConsumer().accept(args[0], args[1], args[2], args[3], args[4]);
            default -> throw unsupportedParameterCount(args.length);
        }
    }

    private Object invokeReturning(final Object[] args) {
        return switch (args.length) {
            case 0 -> function().castToSupplier().supplier().get();
            case 1 -> function().castToUniFunction().function().apply(args[0]);
            case 2 -> function().castToBiFunction().biFunction().apply(args[0], args[1]);
            case 3 -> function().castToTriFunction().triFunction().apply(args[0], args[1], args[2]);
            case 4 -> function().castToQuadFunction().quadFunction().apply(args[0], args[1], args[2], args[3]);
            case 5 -> function().castToQuintFunction().quintFunction().apply(args[0], args[1], args[2], args[3], args[4]);
            default -> throw unsupportedParameterCount(args.length);
        };
    }

    private IllegalStateException unsupportedParameterCount(final int count) {
        return new IllegalStateException(
            function().metadata().label() + " has an unsupported parameter count: " + count + " (max 5)."
        );
    }
}