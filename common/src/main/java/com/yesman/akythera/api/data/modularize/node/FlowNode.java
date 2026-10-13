package com.yesman.akythera.api.data.modularize.node;

import com.mojang.serialization.Codec;

/// A node that script flow passes. Has contrary concept with [PureNode].
///
/// In AkytheraLabs script editor, this node has flow pin.
public interface FlowNode extends ScriptNode {
    Codec<FlowNode> CODEC = Codec.STRING.dispatch(
        "execution_node",
        node ->
            switch (node) {
                case SequenceNode _             -> "sequence_node";
                case BranchNode _               -> "branching_node";
                case CastNode _                 -> "cast_node";
                case FlowFunctionNode _         -> "flow_function_node";
                case ScriptVariableSetterNode _ -> "script_variable_setter";
                case ReturnNode _               -> "return_node";
                case ParentCallNode _           -> "parent_call";
                case FunctionCallNode _         -> "function_call";
                case BlueprintFieldSetterNode _ -> "blueprint_field_setter";
                default -> throw new IllegalArgumentException(
                    "Unsupported execution node type " + node.getClass().getSimpleName()
                );
            },
        literal ->
            switch (literal) {
                case "sequence_node"          -> SequenceNode.CODEC.fieldOf("sequence_node");
                case "branching_node"         -> BranchNode.CODEC.fieldOf("branching_node");
                case "cast_node"              -> CastNode.CODEC.fieldOf("cast_node");
                case "flow_function_node"     -> FlowFunctionNode.CODEC.fieldOf("flow_function_node");
                case "script_variable_setter" -> ScriptVariableSetterNode.CODEC.fieldOf("script_variable_setter");
                case "return_node"            -> ReturnNode.CODEC.fieldOf("return_node");
                case "parent_call"            -> ParentCallNode.CODEC.fieldOf("parent_call");
                case "function_call"          -> FunctionCallNode.CODEC.fieldOf("function_call");
                case "blueprint_field_setter" -> BlueprintFieldSetterNode.CODEC.fieldOf("blueprint_field_setter");
                default -> throw new IllegalArgumentException(
                    "Unsupported execution node type " + literal
                );
            }
    );
}
