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
public record CachedPoseReferenceNode(String poseCacheName) implements PureNode {
    public static final Codec<CachedPoseReferenceNode> CODEC =
        RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Codec.STRING.fieldOf("name").forGetter(CachedPoseReferenceNode::poseCacheName)
                )
                .apply(instance, CachedPoseReferenceNode::new)
        );

    @SuppressWarnings("unchecked")
    @Override
    public <T> T execute(final DataDrivenScript<?, ?> script, final ExecutionContext context) {
        BlendingAnimator animator = context.locals().getValue("animator");
        Map<String, AnimationDriver> poseCaches = context.locals().getValue("poseCaches");
        CachedPoseReference cachedPoseReference = new CachedPoseReference(poseCacheName(), animator);
        poseCaches.put(poseCacheName(), cachedPoseReference);

        return (T)cachedPoseReference;
    }
}
