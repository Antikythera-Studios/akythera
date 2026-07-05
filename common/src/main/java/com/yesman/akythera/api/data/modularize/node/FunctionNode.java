package com.yesman.akythera.api.data.modularize.node;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.core.reflection.DataDrivenScript;

import java.util.List;

public interface FunctionNode extends ScriptNode {
    ScriptFunction function();

    List<PureNode> params();

    @Override
    default <T> T execute(final DataDrivenScript<?, ?> script, final ExecutionContext context) {
        boolean returnsVoid = function().functionSignature().returnType() == void.class
            || function().functionSignature().returnType() == Void.class;

        context.enter(this);

        switch (params().size()) {
            case 0 -> {
                if (returnsVoid) {
                    function().castToRunnable().runnable().run();
                    return context.done();
                } else {
                    return context.returnVal(function().castToSupplier().supplier().get());
                }
            }
            case 1 -> {
                PureNode param1 = params().getFirst();

                if (returnsVoid) {
                    function().castToConsumer().consumer().accept(param1.execute(script, context));
                    return context.done();
                } else {
                    return context.returnVal(function().castToUniFunction().function().apply(
                        param1.execute(script, context)
                    ));
                }
            }
            case 2 -> {
                PureNode param1 = params().get(0);
                PureNode param2 = params().get(1);

                if (returnsVoid) {
                    function().castToBiConsumer().biConsumer().accept(param1.execute(script, context), param2.execute(script, context));
                    return context.done();
                } else {
                    return context.returnVal(function().castToBiFunction().biFunction().apply(
                        param1.execute(script, context),
                        param2.execute(script, context)
                    ));
                }
            }
            case 3 -> {
                PureNode param1 = params().get(0);
                PureNode param2 = params().get(1);
                PureNode param3 = params().get(2);

                if (returnsVoid) {
                    function().castToTriConsumer().triConsumer().accept(
                        param1.execute(script, context),
                        param2.execute(script, context),
                        param3.execute(script, context)
                    );
                    return context.done();
                } else {
                    return context.returnVal(function().castToTriFunction().triFunction().apply(
                        param1.execute(script, context),
                        param2.execute(script, context),
                        param3.execute(script, context)
                    ));
                }
            }
            case 4 -> {
                PureNode param1 = params().get(0);
                PureNode param2 = params().get(1);
                PureNode param3 = params().get(2);
                PureNode param4 = params().get(3);

                if (returnsVoid) {
                    function().castToQuadConsumer().quadConsumer().accept(
                        param1.execute(script, context),
                        param2.execute(script, context),
                        param3.execute(script, context),
                        param4.execute(script, context)
                    );
                    return context.done();
                } else {
                    return context.returnVal(function().castToQuadFunction().quadFunction().apply(
                        param1.execute(script, context),
                        param2.execute(script, context),
                        param3.execute(script, context),
                        param4.execute(script, context)
                    ));
                }
            }
            case 5 -> {
                PureNode param1 = params().get(0);
                PureNode param2 = params().get(1);
                PureNode param3 = params().get(2);
                PureNode param4 = params().get(3);
                PureNode param5 = params().get(4);

                if (returnsVoid) {
                    function().castToQuintConsumer().quintConsumer().accept(
                        param1.execute(script, context),
                        param2.execute(script, context),
                        param3.execute(script, context),
                        param4.execute(script, context),
                        param5.execute(script, context)
                    );
                    return context.done();
                } else {
                    return context.returnVal(function().castToQuintFunction().quintFunction().apply(
                        param1.execute(script, context),
                        param2.execute(script, context),
                        param3.execute(script, context),
                        param4.execute(script, context),
                        param5.execute(script, context)
                    ));
                }
            }
        }

        throw new IllegalStateException(
            function().metadata().label() + " has an unsupported parameter count: " + params().size() + " (max 5)."
        );
    }
}
