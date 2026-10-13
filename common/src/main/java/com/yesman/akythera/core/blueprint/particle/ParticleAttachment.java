package com.yesman.akythera.core.blueprint.particle;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.EquipmentSlotAnchor;
import com.yesman.akythera.api.component.TransformComponent;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.api.world.patch.entity.component.EquipmentHolder;
import com.yesman.akythera.api.world.patch.item.ItemPatch;
import com.yesman.akythera.core.math.Transform;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/// A [TransformComponent] a particle system follows: one of an entity patch's own, or one of the item patch the
/// entity holds in an equipment slot.
///
/// @param socketPath the component's [ComponentAttachable#getComponentAt] path under the entity patch,
///                   `<slot>/<path>` for a component of the item in that [EquipmentSlot] (`mainhand/blade_tip`), where
///                   `<path>` is under the item patch, or empty for the entity's root ([EntityPatch#getModelTransform])
public record ParticleAttachment(int entityId, String socketPath) {
    public static final StreamCodec<ByteBuf, ParticleAttachment> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        ParticleAttachment::entityId,
        ByteBufCodecs.STRING_UTF8,
        ParticleAttachment::socketPath,
        ParticleAttachment::new
    );

    /// The component's transform in world space, or null when the entity, its patch or the component is gone. An item
    /// patch's component is placed under the [EquipmentSlotAnchor] drawing the slot's item, and is gone while none
    /// draws it.
    public @Nullable Transform worldTransform(final Level level) {
        EntityPatch<?> patch = AkytheraPatches.getEntityPatch(level.getEntity(entityId));

        if (patch == null) return null;

        if (socketPath.isEmpty()) return patch.getModelTransform();

        String[] parts = socketPath.split(ComponentAttachable.PATH_SEPARATOR, 2);
        EquipmentSlot slot = parts.length == 2
            ? Arrays.stream(EquipmentSlot.values()).filter(candidate -> candidate.getSerializedName().equals(parts[0])).findFirst().orElse(null)
            : null;

        if (slot != null && patch instanceof EquipmentHolder holder) {
            ItemPatch itemPatch = holder.getItemPatchInSlot(slot);

            return itemPatch != null && itemPatch.getAnchor() != null && itemPatch.getComponentAt(parts[1]) instanceof TransformComponent component
                ? component.worldTransform()
                : null;
        }

        return patch.getComponentAt(socketPath) instanceof TransformComponent component ? component.worldTransform() : null;
    }
}
