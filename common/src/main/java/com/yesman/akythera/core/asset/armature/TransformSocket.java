package com.yesman.akythera.core.asset.armature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.TransformComponent;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.api.component.UniqueComponent;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.world.patch.item.ItemPatch;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.LogicalSide;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/// A named transform other things hang off: an item model, a collider, a mesh.
///
/// Under an [ArmatureComponent] the socket rides [#boneName] and moves with it. Anywhere else—an
/// [ItemPatch]'s own socket, say—there is no bone and the socket is [#localTransform] alone, inside its
/// host's space ([TransformComponent]).
///
/// A socket is both blueprint and component: [#instantiate] copies the authored socket per host and
/// builds that copy's own children against the copy, so two entities sharing an armature blueprint
/// do not share a socket's placed children. The authored socket keeps a null armature and an empty
/// component map.
public final class TransformSocket implements TransformComponent, ComponentBlueprint<TransformSocket, ComponentAttachable>, ComponentAttachable {
    public static final MapCodec<TransformSocket> CODEC =
        RecordCodecBuilder.mapCodec(
            instance ->
                instance.group(
                    TRANSFORM_CODEC.forGetter(TransformSocket::localTransform),
                    TRANSFORM_INHERIT_POLICY_CODEC.forGetter(TransformSocket::transformInheritPolicy),
                    Codec.STRING.fieldOf("bone_name").forGetter(TransformSocket::getBoneName),
                    ComponentBlueprint.componentMapCodec(() -> AkytheraRegistries.Frozen.ENTITY_PATCH_COMPONENT_TYPES)
                        .optionalFieldOf("components", Map.of()).forGetter(TransformSocket::getChildBlueprints),
                    ENABLED_CODEC.forGetter(TransformSocket::enabled)
                )
                .apply(instance, TransformSocket::new)
        );

    /// Standalone form of [#CODEC].
    public static final Codec<TransformSocket> DIRECT_CODEC = CODEC.codec();

    /// A relative position from the bound bone, or from whatever places this socket when it rides no
    /// bone.
    private Transform localTransform;

    /// The elements of the bone, or of whatever places this socket, the socket takes on.
    private final TransformInheritPolicy transformInheritPolicy;

    /// Name of the [Bone] this socket is bound to, empty where it rides none. The socket's *own* name
    /// is the key it is attached under in its host's [ComponentAttachable#getComponents], not this.
    private final String boneName;

    /// What this socket builds its children from. Carried by both the authored socket and its copies,
    /// so a copy can be made from a copy.
    private final Map<String, ComponentBlueprint<?, ?>> childBlueprints;

    /// [ComponentAttachable] implementation. All attached component positions are based on socket
    /// position. Empty on the authored socket; filled by [#instantiate].
    private final Map<String, PatchComponent> components = new LinkedHashMap<>();

    /// The armature this socket rides, resolving [#boneName]. Null on the authored socket and on one
    /// attached anywhere but an [ArmatureComponent].
    private @Nullable ArmatureComponent armature;

    /// The host this socket is attached to. Null on the authored socket.
    private @Nullable ComponentAttachable host;

    /// Whether this socket takes part, see [PatchComponent#isEnabled]. On the authored socket, the
    /// state every copy starts in.
    private boolean enabled;

    public TransformSocket(final Transform localTransform, final String boneName) {
        this(localTransform, TransformInheritPolicy.LOC_ROT_SCA, boneName, Map.of(), true);
    }

    public TransformSocket(
        final Transform localTransform,
        final TransformInheritPolicy transformInheritPolicy,
        final String boneName,
        final Map<String, ComponentBlueprint<?, ?>> childBlueprints,
        final boolean enabled
    ) {
        this.localTransform = localTransform;
        this.transformInheritPolicy = transformInheritPolicy;
        this.boneName = boneName;
        this.childBlueprints = childBlueprints;
        this.enabled = enabled;
    }

    public String getBoneName() {
        return boneName;
    }

    public Map<String, ComponentBlueprint<?, ?>> getChildBlueprints() {
        return childBlueprints;
    }

    /// The armature this socket rides. Null where it rides none.
    public @Nullable ArmatureComponent getArmature() {
        return armature;
    }

    /// This socket's transform in its armature's model space: [#localTransform] placed in the bound bone's
    /// transform by [#transformInheritPolicy]. Riding no bone, it is [#localTransform].
    public Transform getBoundTransform() {
        return armature == null
            ? localTransform
            : transformInheritPolicy.inherit(armature.getBoneModelTransform(boneName), localTransform);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public Map<String, PatchComponent> getComponents() {
        return components;
    }

    /// Attaches `component` under `name`, unless a component already sits there. Whether it was attached.
    public boolean attach(final String name, final PatchComponent component) {
        return components.putIfAbsent(name, component) == null;
    }

    /// Removes `component` from `name`, if it still sits there.
    public void detach(final String name, final PatchComponent component) {
        components.remove(name, component);
    }

    @Override
    public @Nullable <C extends UniqueComponent> C getComponent(Class<C> type) {
        return null;
    }

    /// The side of the host this socket is attached to, [LogicalSide#BOTH] on the authored socket.
    @Override
    public LogicalSide logicalSide() {
        return host == null ? LogicalSide.BOTH : host.logicalSide();
    }

    @Override
    public TransformSocket instantiate(final ComponentAttachable host) {
        TransformSocket socket = new TransformSocket(localTransform, transformInheritPolicy, boneName, childBlueprints, enabled);
        socket.armature = host instanceof ArmatureComponent armatureHost ? armatureHost : null;
        socket.host = host;

        ComponentBlueprint.instantiateAll(childBlueprints, socket, host.logicalSide().validInClientSide(), socket.components::put);

        return socket;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public MapCodec<? extends ComponentBlueprint<TransformSocket, ComponentAttachable>> mapCodec() {
        return CODEC;
    }

    // ***************************************************************
    // TransformComponent
    // ***************************************************************

    @Override
    public Transform localTransform() {
        return localTransform;
    }

    @Override
    public void setLocalTransform(final Transform transform) {
        localTransform = transform;
    }

    @Override
    public @Nullable ComponentAttachable host() {
        return host;
    }

    @Override
    public TransformInheritPolicy transformInheritPolicy() {
        return transformInheritPolicy;
    }

    /// Riding a bone, the bone in its armature's model space.
    @Override
    public Transform parentModelTransform() {
        return armature == null
            ? TransformComponent.super.parentModelTransform()
            : armature.modelTransform().mul(armature.getBoneModelTransform(boneName));
    }

    @Override
    public Transform previousParentModelTransform() {
        return armature == null
            ? TransformComponent.super.previousParentModelTransform()
            : armature.previousModelTransform().mul(armature.getPreviousBoneModelTransform(boneName));
    }
}
