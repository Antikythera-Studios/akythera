package com.yesman.akythera.core.asset.armature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.UniqueComponent;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/// A runtime bone position mapper for in-game objects, like item models or colliders attached to a
/// hand bone.
///
/// A socket is both blueprint and component: [#instantiate] copies the authored socket per
/// [ArmatureComponent] and builds that copy's own children, so two entities sharing an armature
/// blueprint do not share a socket's placed children. The authored socket keeps a null armature and
/// an empty component map. Sockets are only attachable under an [ArmatureComponent], which is what
/// binds them to a concrete bone.
public final class BoneSocket implements PatchComponent, ComponentBlueprint<BoneSocket, ArmatureComponent>, ComponentAttachable {
    public static final MapCodec<BoneSocket> CODEC =
        RecordCodecBuilder.mapCodec(
            instance ->
                instance.group(
                    Transform.CODEC.fieldOf("transform").forGetter(BoneSocket::getLocalTransform),
                    Codec.STRING.fieldOf("bone_name").forGetter(BoneSocket::getBoneName),
                    ComponentBlueprint.componentMapCodec(() -> AkytheraRegistries.Frozen.ENTITY_PATCH_COMPONENT_TYPES)
                        .optionalFieldOf("components", Map.of()).forGetter(BoneSocket::getChildBlueprints)
                )
                .apply(instance, BoneSocket::new)
        );

    /// Standalone form of [#CODEC], for the socket maps [ArmatureComponent]s are built from.
    public static final Codec<BoneSocket> DIRECT_CODEC = CODEC.codec();

    /// A relative position from the bound bone
    private final Transform localTransform;

    /// Name of the [Bone] this socket is bound to. The socket's *own* name is the key it is
    /// attached under in its host's [ComponentAttachable#getComponents], not this.
    private final String boneName;

    /// What this socket builds its children from. Carried by both the authored socket and its
    /// per-armature copies, so a copy can be made from a copy.
    private final Map<String, ComponentBlueprint<?, ?>> childBlueprints;

    /// [ComponentAttachable] implementation. All attached component positions are based on socket
    /// position. Empty on the authored socket; filled by [#instantiate].
    private final Map<String, PatchComponent> components = new HashMap<>();

    /// The armature this socket rides, resolving [#boneName]. Null on the authored socket.
    private @Nullable ArmatureComponent armature;

    public BoneSocket(final Transform localTransform, final String boneName) {
        this(localTransform, boneName, Map.of());
    }

    public BoneSocket(
        final Transform localTransform,
        final String boneName,
        final Map<String, ComponentBlueprint<?, ?>> childBlueprints
    ) {
        this.localTransform = localTransform;
        this.boneName = boneName;
        this.childBlueprints = childBlueprints;
    }

    public Transform getLocalTransform() {
        return localTransform;
    }

    public String getBoneName() {
        return boneName;
    }

    public Map<String, ComponentBlueprint<?, ?>> getChildBlueprints() {
        return childBlueprints;
    }

    /// The armature this socket rides. Null on the authored socket.
    public @Nullable ArmatureComponent getArmature() {
        return armature;
    }

    /// This socket's transform in its armature's model space: the bound bone's transform with
    /// [#localTransform] applied on top.
    public Transform getBoundTransform() {
        return armature == null
            ? localTransform
            : armature.getBoneModelTransform(boneName).mul(localTransform);
    }

    @Override
    public Map<String, PatchComponent> getComponents() {
        return components;
    }

    @Override
    public @Nullable <C extends UniqueComponent> C getComponent(Class<C> type) {
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public BoneSocket instantiate(final ArmatureComponent host) {
        BoneSocket socket = new BoneSocket(localTransform, boneName, childBlueprints);
        socket.armature = host;

        childBlueprints.forEach((name, blueprint) ->
            socket.components.put(name, ((ComponentBlueprint<?, Object>)blueprint).instantiate(host.getOwner()))
        );

        return socket;
    }

    @Override
    public MapCodec<? extends ComponentBlueprint<BoneSocket, ArmatureComponent>> mapCodec() {
        return CODEC;
    }
}
