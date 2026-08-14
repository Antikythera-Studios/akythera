package com.yesman.akythera.api.data.modularize.node;

import com.mojang.serialization.Codec;
import com.yesman.akythera.api.data.modularize.node.animgraph.AnimationStateMachineNode;
import com.yesman.akythera.api.data.modularize.node.animgraph.CachedPoseReferenceNode;

/// Nodes that are used for getting parameters of another
public interface PureNode extends ScriptNode {
    Codec<PureNode> CODEC = Codec.STRING.dispatch(
        "pure_node",
        node ->
            switch (node) {
                case SelfNode _                  -> "self_node";
                case LiteralNode<?> _            -> "literal_node";
                case PureFunctionNode _          -> "pure_task_node";
                case ScriptVariableGetterNode _  -> "variable_getter";
                case ContextVariableAccessNode _ -> "local_variable_node";
                case NumericOperatorNode _       -> "basic_operator";
                case SupplierAdapterNode _       -> "supplier_adapter";
                case InvertBooleanNode _         -> "invert_boolean";
                case LogicalOperationNode _      -> "logical_operation";
                case CachedPoseReferenceNode _   -> "cached_pose_reference";
                case AnimationStateMachineNode _ -> "anim_state_machine";
                case TypeConvertNode _           -> "type_converter";
                case MeshAccessorNode _          -> "mesh_accessor";
                default -> throw new IllegalArgumentException(
                    "Unsupported pure node type " + node.getClass().getSimpleName()
                );
            },
        literal ->
            switch (literal) {
                case "self_node"             -> SelfNode.CODEC.fieldOf("self_node");
                case "literal_node"          -> LiteralNode.CODEC.fieldOf("literal_node");
                case "pure_task_node"        -> PureFunctionNode.CODEC.fieldOf("pure_task_node");
                case "variable_getter"       -> ScriptVariableGetterNode.CODEC.fieldOf("variable_getter");
                case "local_variable_node"   -> ContextVariableAccessNode.CODEC.fieldOf("local_variable_node");
                case "basic_operator"        -> NumericOperatorNode.CODEC.fieldOf("basic_operator");
                case "supplier_adapter"      -> SupplierAdapterNode.CODEC.fieldOf("supplier_adapter");
                case "invert_boolean"        -> InvertBooleanNode.CODEC.fieldOf("invert_boolean");
                case "logical_operation"     -> LogicalOperationNode.CODEC.fieldOf("logical_operation");
                case "cached_pose_reference" -> CachedPoseReferenceNode.CODEC.fieldOf("cached_pose_reference");
                case "anim_state_machine"    -> AnimationStateMachineNode.CODEC.fieldOf("anim_state_machine");
                case "type_converter"        -> TypeConvertNode.CODEC.fieldOf("type_converter");
                case "mesh_accessor"         -> MeshAccessorNode.CODEC.fieldOf("mesh_accessor");
                default -> throw new IllegalArgumentException(
                    "Unsupported pure node type " + literal
                );
            }
    );
}
