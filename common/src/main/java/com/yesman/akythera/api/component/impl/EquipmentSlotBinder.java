package com.yesman.akythera.api.component.impl;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.TransformComponent;
import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.api.world.patch.entity.component.EquipmentHolder;
import com.yesman.akythera.api.world.patch.item.ItemPatch;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.asset.armature.TransformSocket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/// Binds one equipment slot of its holder to the [TransformSocket] it is attached under, in a bone's hand.
///
/// [#refresh] attaches the [TransformComponent]s of the slot's item patch—its sockets and colliders—under that same
/// socket, beside this binder, so they sit where the item is held and are reached by their path under the socket
/// (`armature/right_hand_socket/trail_start`). [EquipmentHolder#getSlotBinder] reaches the binder by its slot.
///
/// Built through [ComponentsFactory#createEquipmentSlotBinder], which also draws the item on the logical client.
public class EquipmentSlotBinder implements PatchComponent {
    /// The slot whose item is bound.
    private final EquipmentSlot equipmentSlot;

    /// The context the item model is resolved and posed in.
    private final ItemDisplayContext itemDisplayContext;

    /// Whether the item is worn as a 3D model in the armature's space, not held as its item model.
    private final boolean model3d;

    /// The armature a 3D model sits in, null where the host chain reaches none.
    protected final @Nullable ArmatureComponent armature;

    /// The host this component is attached under.
    protected final @Nullable ComponentAttachable host;

    /// Whether this component takes part, see [PatchComponent#isEnabled].
    private boolean enabled;

    /// The item patch [#attached] were built from, null while nothing is attached.
    private @Nullable ItemPatch builtFrom;

    /// The transform components of [#builtFrom] this binder attached under [#host], by name.
    private Map<String, PatchComponent> attached = Map.of();

    public EquipmentSlotBinder(final EquipmentSlotBinderBlueprint blueprint, final ComponentAttachable host) {
        this.equipmentSlot = blueprint.equipmentSlot();
        this.itemDisplayContext = blueprint.itemDisplayContext();
        this.model3d = blueprint.model3d();
        this.armature = ArmatureComponent.armatureOf(host);
        this.host = host;
        this.enabled = blueprint.enabled();
    }

    public EquipmentSlot equipmentSlot() {
        return equipmentSlot;
    }

    public ItemDisplayContext itemDisplayContext() {
        return itemDisplayContext;
    }

    public boolean isModel3d() {
        return model3d;
    }

    /// Attaches the transform components of the slot's item patch under [#host] again, when that patch changed since
    /// they were attached. A switched-off binder attaches nothing, and a name the socket already holds is skipped.
    public void refresh() {
        if (!(host instanceof TransformSocket socket)) return;

        ItemPatch itemPatch = enabled && TransformComponent.entityOf(host) instanceof EquipmentHolder holder
            ? AkytheraPatches.getItemPatch(holder.getItemInSlot(equipmentSlot))
            : null;

        if (itemPatch == builtFrom) return;

        attached.forEach(socket::detach);
        attached = new LinkedHashMap<>();
        builtFrom = itemPatch;

        if (itemPatch == null) return;

        itemPatch.getBlueprint().instantiateTransformComponents(itemPatch, socket).forEach((name, component) -> {
            if (socket.attach(name, component)) {
                attached.put(name, component);
            } else {
                Akythera.LOGGER.warn("{} of the item in {} is not attached: its socket already holds a component of that name", name, equipmentSlot);
            }
        });
    }

    /// The component at `path` among the ones [#refresh] attached, path's first name being one of them. Null when none
    /// sits there.
    public @Nullable PatchComponent getItemComponentAt(final String path) {
        return attached.containsKey(path.split(ComponentAttachable.PATH_SEPARATOR, 2)[0]) && host != null ? host.getComponentAt(path) : null;
    }

    // ***************************************************************
    // PatchComponent
    // ***************************************************************

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }
}
