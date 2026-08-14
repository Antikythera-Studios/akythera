package com.yesman.akythera.api.data.modularize.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akythera.core.util.helper.ParseUtils;
import org.jspecify.annotations.NonNull;

/// Pure node that applies a basic arithmetic operator to two numeric operands, widening to the
/// wider of the two runtime types (e.g. int + float = float, byte + double = double).
public record NumericOperatorNode(Operator operator, PureNode a, PureNode b, int nodeId) implements PureNode {
    /// A node with no canvas row behind it — any hand-assembled tree.
    public NumericOperatorNode(final Operator operator, final PureNode a, final PureNode b) {
        this(operator, a, b, NO_NODE_ID);
    }

    public static final Codec<Operator> OPERATOR_CODEC = Codec.STRING.xmap(
        name -> Operator.valueOf(ParseUtils.toUpperCase(name)),
        op -> ParseUtils.toLowerCase(op.name())
    );

    public static final Codec<NumericOperatorNode> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            OPERATOR_CODEC.fieldOf("operator").forGetter(NumericOperatorNode::operator),
            PureNode.CODEC.fieldOf("a").forGetter(NumericOperatorNode::a),
            PureNode.CODEC.fieldOf("b").forGetter(NumericOperatorNode::b),
            Codec.INT.optionalFieldOf("node_id", NO_NODE_ID).forGetter(NumericOperatorNode::nodeId)
        ).apply(instance, NumericOperatorNode::new)
    );

    @Override
    public <T> T execute(DataDrivenScript<?, ?> script, ExecutionContext context) {
        context.enter(this);
        Number numA = a.execute(script, context);
        Number numB = b.execute(script, context);

        return context.returnVal(compute(numA, numB));
    }

    /// Applies [#operator] to `x` and `y` in the wider of their two numeric types, mirroring
    /// Java's binary numeric promotion (double > float > long > int; byte/short fold into int).
    private Number compute(Number x, Number y) {
        if (x instanceof Double || y instanceof Double) {
            double p = x.doubleValue(), q = y.doubleValue();
            return switch (operator) {
                case ADD -> p + q;
                case SUBTRACT -> p - q;
                case MULTIPLY -> p * q;
                case DIVIDE -> p / q;
            };
        }

        if (x instanceof Float || y instanceof Float) {
            float p = x.floatValue(), q = y.floatValue();
            return switch (operator) {
                case ADD -> p + q;
                case SUBTRACT -> p - q;
                case MULTIPLY -> p * q;
                case DIVIDE -> p / q;
            };
        }

        if (x instanceof Long || y instanceof Long) {
            long p = x.longValue(), q = y.longValue();
            return switch (operator) {
                case ADD -> p + q;
                case SUBTRACT -> p - q;
                case MULTIPLY -> p * q;
                case DIVIDE -> p / q;
            };
        }

        int p = x.intValue(), q = y.intValue();

        return switch (operator) {
            case ADD -> p + q;
            case SUBTRACT -> p - q;
            case MULTIPLY -> p * q;
            case DIVIDE -> p / q;
        };
    }

    public enum Operator {
        ADD("+"), SUBTRACT("-"), MULTIPLY("x"), DIVIDE("/");

        final String sign;

        Operator(final String sign) {
            this.sign = sign;
        }
    }

    @Override
    public @NonNull String toString() {
        return "Number Operator " + operator().sign;
    }
}