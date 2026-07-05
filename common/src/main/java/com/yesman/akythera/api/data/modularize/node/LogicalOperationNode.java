package com.yesman.akythera.api.data.modularize.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import org.jspecify.annotations.NonNull;

import java.util.Locale;
import java.util.function.BiFunction;

/// A pure node that returns `boolean` of each logical operation
public record LogicalOperationNode(PureNode bool1, PureNode bool2, Operator operator) implements PureNode {
    public static final Codec<Operator> OPERATOR_CODEC = Codec.STRING.xmap(
        name -> Operator.valueOf(name.toUpperCase(Locale.ROOT)),
        op -> op.name().toLowerCase(Locale.ROOT)
    );

    public static final Codec<LogicalOperationNode> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            PureNode.CODEC.fieldOf("bool1").forGetter(LogicalOperationNode::bool1),
            PureNode.CODEC.fieldOf("bool2").forGetter(LogicalOperationNode::bool2),
            OPERATOR_CODEC.fieldOf("operator").forGetter(LogicalOperationNode::operator)
        ).apply(instance, LogicalOperationNode::new)
    );

    @Override
    public <T> T execute(final DataDrivenScript<?, ?> script, final ExecutionContext context) {
        context.enter(this);

        return context.returnVal(
            operator.operation.apply(bool1().execute(script, context), bool2().execute(script, context))
        );
    }

    @Override
    public @NonNull String toString() {
        return "Logical Operator " + operator().sign;
    }

    public enum Operator {
        AND((b1, b2) -> b1 && b2, "&&"),
        OR((b1, b2) -> b1 || b2, "||"),
        NAND((b1, b2) -> !(b1 && b2), "!&&"),
        NOR((b1, b2) -> !(b1 || b2), "!||"),
        XOR((b1, b2) -> b1 ^ b2, "^")
        ;

        final BiFunction<Boolean, Boolean, Boolean> operation;
        final String sign;

        Operator(final BiFunction<Boolean, Boolean, Boolean> operation, final String sign) {
            this.operation = operation;
            this.sign = sign;
        }
    }
}
