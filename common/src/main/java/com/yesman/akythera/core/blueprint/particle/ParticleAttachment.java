package com.yesman.akythera.core.blueprint.particle;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.TransformComponent;
import com.yesman.akythera.api.component.impl.EquipmentSlotBinder;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.core.math.Transform;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/// A [TransformComponent] a particle system follows: one of an entity patch's own, or one of the item patch the
/// entity holds in an equipment slot.
///
/// @param socketPath the component's [ComponentAttachable#getComponentAt] path under the entity patch,
///                   `<slot>/<path>` for a component of the item in that [EquipmentSlot] (`mainhand/blade_tip`, see
///                   [LivingEntityPatch#getComponentAt]), where `<path>` is under the item patch, or empty for the entity's root
///                   ([EntityPatch#getModelTransform])
/// @param follow     whether the system moves with the component. A system that does not stays where it was played,
///                   and only its overlay particles are tied to the entity
public record ParticleAttachment(int entityId, String socketPath, boolean follow) {
    public static final StreamCodec<ByteBuf, ParticleAttachment> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        ParticleAttachment::entityId,
        ByteBufCodecs.STRING_UTF8,
        ParticleAttachment::socketPath,
        ByteBufCodecs.BOOL,
        ParticleAttachment::follow,
        ParticleAttachment::new
    );

    /// An attachment the system follows.
    public ParticleAttachment(final int entityId, final String socketPath) {
        this(entityId, socketPath, true);
    }

    /// The component's transform in world space with the entity placed as it is drawn `partialTick` after its last tick,
    /// or null when the entity, its patch or the component is gone. An item patch's component is the one the slot's
    /// [EquipmentSlotBinder] attached, and is gone while none binds the slot.
    public @Nullable Transform worldTransform(final Level level, final float partialTick) {
        EntityPatch<?> patch = AkytheraPatches.getEntityPatch(level.getEntity(entityId));

        if (patch == null) return null;

        if (socketPath.isEmpty()) return patch.getModelTransform(partialTick);

        return patch.getComponentAt(socketPath) instanceof TransformComponent component ? component.worldTransform(partialTick) : null;
    }
}
