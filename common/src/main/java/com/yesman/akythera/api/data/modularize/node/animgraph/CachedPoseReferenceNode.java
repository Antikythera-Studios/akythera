package com.yesman.akythera.api.data.modularize.node.animgraph;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.blueprint.script.DataDrivenAnimatorScript;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.core.animation.animator.BlendingAnimator;
import com.yesman.akythera.core.animation.driver.AnimationDriver;
import com.yesman.akythera.core.animation.driver.CachedPoseReference;
import com.yesman.akythera.core.reflection.DataDrivenScript;

import java.util.Map;

/// Returns a cached pose reference, the script must be called by [DataDrivenAnimatorScript#construct]
///
/// `controlReferenceDriver` is [CachedPoseReference#controlReferenceDriver]. The reference is built once
/// per cache name and shared by every node reading it, so the first node to reach a name decides it.
public record CachedPoseReferenceNode(String poseCacheName, boolean controlReferenceDriver) implements PureNode {
    public static final Codec<CachedPoseReferenceNode> CODEC =
        RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Codec.STRING.fieldOf("name").forGetter(CachedPoseReferenceNode::poseCacheName),
                    Codec.BOOL.optionalFieldOf("control_reference_driver", false).forGetter(CachedPoseReferenceNode::controlReferenceDriver)
                )
                .apply(instance, CachedPoseReferenceNode::new)
        );

    @SuppressWarnings("unchecked")
    @Override
    public <T> T execute(final DataDrivenScript<?, ?> script, final ExecutionContext context) {
        BlendingAnimator animator = context.locals().getValue("animator");
        Map<String, AnimationDriver> poseCaches = context.locals().getValue("poseCaches");

        if (poseCaches.containsKey(poseCacheName())) {
            return (T)poseCaches.get(poseCacheName());
        }

        CachedPoseReference cachedPoseReference =
            new CachedPoseReference(poseCacheName(), animator, controlReferenceDriver());
        poseCaches.put(poseCacheName(), cachedPoseReference);

        return (T)cachedPoseReference;
    }
}
