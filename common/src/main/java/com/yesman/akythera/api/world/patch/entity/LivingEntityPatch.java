package com.yesman.akythera.api.world.patch.entity;

import com.yesman.akythera.api.animation.animator.AnimationInterfaceKey;
import com.yesman.akythera.api.animation.notify.impl.LockMovementNotifyState;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.UniqueComponent;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.component.impl.EquipmentSlotBinder;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.api.world.patch.entity.component.EquipmentHolder;
import com.yesman.akythera.api.world.patch.item.ItemPatch;
import com.yesman.akythera.api.world.patch.item.WeaponStance;
import com.yesman.akythera.api.world.patch.item.component.ReplaceAnimations;
import com.yesman.akythera.api.world.patch.item.component.WeaponStanceVariationAdapter;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.animator.BlendingAnimator;
import com.yesman.akythera.core.animation.driver.AnimationMontagePlayer;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.blueprint.EntityPatchBlueprint;
import com.yesman.akythera.core.reflection.ScriptFunction;
import com.yesman.akythera.core.reflection.ScriptType;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ScriptType(autoRegister = false)
public class LivingEntityPatch<T extends LivingEntity> extends EntityPatch<T> implements EquipmentHolder {
    /// Slots equipment is read from, in priority order — the main hand answers before the off hand.
    private static final List<EquipmentSlot> STANCE_SLOTS = List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND);

    /// Horizontal distance over one tick, in blocks, below which [#getMovementYaw] keeps its last direction.
    private static final double MOVING_DISTANCE = 0.015D;

    /// What [#preTick] last resolved, or null while no [WeaponStanceVariationAdapter] matches.
    private WeaponStanceVariationAdapter.@Nullable Resolution stance;

    /// The replacements [#refreshAnimationReplacements] last wrote into the animator, which the next
    /// call diffs against.
    private Map<AnimationInterfaceKey, ResourceKey<? extends PlayableAnimation>> animationReplacements = Map.of();

    /// The [EquipmentSlotBinder] of each slot in the component tree, collected on first use. Null until then.
    private @Nullable Map<EquipmentSlot, EquipmentSlotBinder> slotBinders;

    /// Whether [#animationReplacements] were written through their mirrored twins.
    private boolean replacementsMirrored;

    /// What [#getMovementYaw] last returned.
    private float movementYaw;

    /// Whether [#faceBodyToView] ran since the last tick's body turn.
    private boolean bodyFacesViewRequested;

    public LivingEntityPatch(@NonNull T original) {
        super(original);
    }

    public LivingEntityPatch(final Holder.@Nullable Reference<EntityPatchBlueprint> blueprint, @NonNull final T original) {
        super(blueprint, original);
    }

    public LivingEntityPatch(final EntityPatchBlueprint blueprint, @NonNull final T original) {
        super(blueprint, original);
    }

    public LivingEntityPatch(final LivingEntityPatch<T> copyTarget) {
        super(copyTarget);
    }

    /// Resolves the stance this entity holds its equipment in, then rewrites the animator to whatever
    /// [ReplaceAnimations] that stance puts in force.
    ///
    /// [#STANCE_SLOTS] is walked in order and the first slot whose [ItemPatch] answers an adapter
    /// wins, so an adapter on the main hand is never overruled by one on the off hand.
    @Override
    public void preTick() {
        super.preTick();

        stance = null;

        for (EquipmentSlot slot : STANCE_SLOTS) {
            ItemPatch itemPatch = getItemPatchInSlot(slot);

            if (itemPatch == null) continue;

            WeaponStanceVariationAdapter adapter = itemPatch.getStanceAdapter(this, slot);

            if (adapter != null) {
                stance = new WeaponStanceVariationAdapter.Resolution(slot, itemPatch, adapter);
                break;
            }
        }

        refreshAnimationReplacements(getEquippedComponent(ReplaceAnimations.class));
    }

    /// Refreshes the slot binders before the components tick, so an item changed during this tick has its sockets
    /// in place when the animator fires its notifies.
    @Override
    public void postTick() {
        slotBinders().values().forEach(EquipmentSlotBinder::refresh);

        super.postTick();
    }

    /// The adapter that established the current stance, the item patch declaring it and the slot that
    /// patch sits in. Null while no adapter matches, which is the equipment's default state.
    public WeaponStanceVariationAdapter.@Nullable Resolution getStanceResolution() {
        return stance;
    }

    @ScriptFunction(pure = true, label = "Get Weapon Stance", desc = "Returns the stance the entity holds its equipment in. Null while it holds none.")
    public @Nullable WeaponStance getStance() {
        return stance == null ? null : stance.adapter().getStance();
    }

    /// The [UniqueComponent] of `type` in force for this entity: the one attached under the adapter
    /// that established the current stance, falling back to the first of [#STANCE_SLOTS] whose
    /// [ItemPatch] declares one. Null when nothing does.
    ///
    /// An adapter carries the patch's other components under its own children, so the stance branch
    /// needs no second lookup on the patch.
    public <C extends UniqueComponent> @Nullable C getEquippedComponent(final Class<C> type) {
        if (stance != null) {
            return stance.adapter().getComponent(type);
        }

        for (EquipmentSlot slot : STANCE_SLOTS) {
            ItemPatch itemPatch = getItemPatchInSlot(slot);
            C component = itemPatch == null ? null : itemPatch.getComponent(type);

            if (component != null) return component;
        }

        return null;
    }

    /// Rewrites this entity's [BlendingAnimator] interfaces to what `replaceAnimations` imposes,
    /// touching only the interfaces whose animation actually changes. A null block rolls back every
    /// replacement in force.
    ///
    /// Does nothing while the entity carries no [BlendingAnimator].
    public void refreshAnimationReplacements(final @Nullable ReplaceAnimations replaceAnimations) {
        ArmatureComponent armatureComponent = getArmatureComponent();

        if (armatureComponent == null || !(armatureComponent.getAnimator() instanceof BlendingAnimator animator)) {
            return;
        }

        Map<AnimationInterfaceKey, ResourceKey<? extends PlayableAnimation>> replacements = new HashMap<>();
        boolean mirrored = replaceAnimations != null && replaceAnimations.mirrorAnimations();
        boolean mirrorChanged = mirrored != replacementsMirrored;

        if (replaceAnimations != null) {
            replaceAnimations.animationsToReplace().forEach(replaceEntry ->
                replacements.put(replaceEntry.interfaceKey(), replaceEntry.animResourceKey()));
        }

        if (!mirrorChanged && replacements.equals(animationReplacements)) return;

        if (replaceAnimations != null && replaceAnimations.resetAnimations()) {
            animator.resetInterfacesExcept(replacements.keySet());
        } else {
            animationReplacements.keySet().stream()
                .filter(interfaceKey -> !replacements.containsKey(interfaceKey))
                .forEach(animator::resetAnimInterface);
        }

        RegistryAccess registryAccess = getLevel().registryAccess();

        replacements.forEach((interfaceKey, animResourceKey) -> {
            if (!mirrorChanged && animResourceKey.equals(animationReplacements.get(interfaceKey))) return;

            Holder<? extends PlayableAnimation> animation = registryAccess.getOrThrow(animResourceKey);

            animator.replaceAnimation(
                interfaceKey, mirrored ? Holder.direct(animation.value().mirrored()) : animation);
        });

        animationReplacements = replacements;
        replacementsMirrored = mirrored;
    }

    // ***************************************************************
    // [EquipmentHolder]
    // ***************************************************************

    @ScriptFunction(pure = true, label = "Get Item In Slot")
    @Override
    public ItemStack getItemInSlot(final EquipmentSlot equipmentSlot) {
        return getOriginal().getItemBySlot(equipmentSlot);
    }

    @ScriptFunction(pure = true, label = "Get Item Patch In Slot", desc = "Returns the item patch of the item in the slot. Null when the item has none.")
    @Override
    public @Nullable ItemPatch getItemPatchInSlot(final EquipmentSlot equipmentSlot) {
        return AkytheraPatches.getItemPatch(getItemInSlot(equipmentSlot));
    }

    @Override
    public @Nullable EquipmentSlotBinder getSlotBinder(final EquipmentSlot equipmentSlot) {
        EquipmentSlotBinder binder = slotBinders().get(equipmentSlot);

        return binder != null && binder.isEnabled() ? binder : null;
    }

    /// [#slotBinders], collected from the component tree on the first call. A second binder of a slot is
    /// switched off.
    private Map<EquipmentSlot, EquipmentSlotBinder> slotBinders() {
        if (slotBinders != null) return slotBinders;

        slotBinders = new EnumMap<>(EquipmentSlot.class);

        Deque<ComponentAttachable> hosts = new ArrayDeque<>(List.of(this));

        while (!hosts.isEmpty()) {
            List<PatchComponent> children = List.copyOf(hosts.pop().getComponents().values());

            for (PatchComponent component : children.reversed()) {
                if (component instanceof ComponentAttachable host) hosts.push(host);
            }

            for (PatchComponent component : children) {
                if (!(component instanceof EquipmentSlotBinder binder) || slotBinders.putIfAbsent(binder.equipmentSlot(), binder) == null) continue;

                binder.setEnabled(false);
                Akythera.LOGGER.warn("{} binds {} a second time and is switched off", getOriginal().getType(), binder.equipmentSlot());
            }
        }

        return slotBinders;
    }

    /// Also reaches the components a slot's [EquipmentSlotBinder] attached by the slot's name, `mainhand/trail_start`
    /// for the binder's `trail_start`, when no component of the patch sits at `path`.
    @Override
    public @Nullable PatchComponent getComponentAt(final String path) {
        PatchComponent component = super.getComponentAt(path);

        if (component != null) return component;

        String[] parts = path.split(PATH_SEPARATOR, 2);
        EquipmentSlot slot = parts.length == 2
            ? Arrays.stream(EquipmentSlot.values()).filter(candidate -> candidate.getSerializedName().equals(parts[0])).findFirst().orElse(null)
            : null;
        EquipmentSlotBinder binder = slot == null ? null : getSlotBinder(slot);

        return binder == null ? null : binder.getItemComponentAt(parts[1]);
    }

    @ScriptFunction(pure = true, label = "Get Main Hand Item Patch")
    @Override
    public @Nullable ItemPatch getMainHandItemPatch() {
        return getItemPatchInSlot(EquipmentSlot.MAINHAND);
    }

    @ScriptFunction(pure = true, label = "Get Off Hand Item Patch")
    @Override
    public @Nullable ItemPatch getOffHandItemPatch() {
        return getItemPatchInSlot(EquipmentSlot.OFFHAND);
    }

    // ***************************************************************
    // Common Getters
    // ***************************************************************

    @ScriptFunction(pure = true, label = "Get Health")
    public float getHealth() {
        return getOriginal().getHealth();
    }

    @ScriptFunction(pure = true, label = "Get Max Health")
    public float getMaxHealth() {
        return getOriginal().getMaxHealth();
    }

    @ScriptFunction(pure = true, label = "Get Armor Value", desc = "Armor points the entity currently wears.")
    public int getArmorValue() {
        return getOriginal().getArmorValue();
    }

    @ScriptFunction(pure = true, label = "Get Movement Speed", desc = "The movement speed attribute, not the distance moved this tick.")
    public float getSpeed() {
        return getOriginal().getSpeed();
    }

    @ScriptFunction(pure = true, label = "Get Y Head Rot", desc = "The head's own yaw, which turns ahead of the body.")
    public float getYHeadRot() {
        return getOriginal().getYHeadRot();
    }

    @ScriptFunction(pure = true, label = "Get Y Body Rot (Partial)", desc = "The body's yaw between ticks, as the renderer turns the model.")
    public float getYBodyRot(final float partialTick) {
        return Mth.rotLerp(partialTick, getOriginal().yBodyRotO, getOriginal().yBodyRot);
    }

    @ScriptFunction(pure = true, label = "Get Head Yaw Offset", desc = "How far the head turns from the body between ticks, in degrees, as the renderer draws it. Positive is right.")
    public float getHeadYawOffset(final float partialTick) {
        float headRot = Mth.rotLerp(partialTick, getOriginal().yHeadRotO, getOriginal().yHeadRot);

        if (!(getOriginal().getVehicle() instanceof LivingEntity vehicle)) {
            return Mth.wrapDegrees(headRot - getYBodyRot(partialTick));
        }

        float headDiff = Mth.clamp(Mth.wrapDegrees(headRot - Mth.rotLerp(partialTick, vehicle.yBodyRotO, vehicle.yBodyRot)), -85.0F, 85.0F);

        return Math.abs(headDiff) > 50.0F ? headDiff * 0.8F : headDiff;
    }

    @ScriptFunction(pure = true, label = "Get Movement Yaw", desc = "Direction of the last tick's horizontal movement relative to the body, in degrees. Negative is left. Keeps the last direction while the entity barely moves.")
    public float getMovementYaw() {
        T original = getOriginal();
        double dx = original.getX() - original.xo;
        double dz = original.getZ() - original.zo;

        if (dx * dx + dz * dz > MOVING_DISTANCE * MOVING_DISTANCE) {
            movementYaw = Mth.wrapDegrees((float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F - original.yBodyRot);
        }

        return movementYaw;
    }

    /// Asks the next tick to aim the vanilla body turn at [LivingEntity#getYRot], which eases the body toward it.
    @ScriptFunction(pure = true, label = "Face Body To View", desc = "Turns the body smoothly toward the view yaw while this node is sampled, e.g. in a blend space input. Returns the value unchanged.")
    public float faceBodyToView(final float value) {
        bodyFacesViewRequested = true;
        return value;
    }

    /// Returns whether [#faceBodyToView] ran since the last call, and clears the request.
    public boolean consumeBodyFacesViewRequest() {
        boolean requested = bodyFacesViewRequested;
        bodyFacesViewRequested = false;

        return requested;
    }

    /// Returns the [LockMovementNotifyState] a playing montage holds at its playhead, or null.
    public @Nullable LockMovementNotifyState getMovementLock() {
        ArmatureComponent armatureComponent = getArmatureComponent();

        if (armatureComponent == null) return null;

        for (AnimationMontagePlayer montagePlayer : armatureComponent.getAnimator().getMontagePlayers()) {
            LockMovementNotifyState lock = montagePlayer.getActiveNotifyState(LockMovementNotifyState.class);

            if (lock != null) return lock;
        }

        return null;
    }

    @ScriptFunction(pure = true, label = "Is Baby")
    public boolean isBaby() {
        return getOriginal().isBaby();
    }

    @ScriptFunction(pure = true, label = "Is Blocking")
    public boolean isBlocking() {
        return getOriginal().isBlocking();
    }

    @ScriptFunction(pure = true, label = "Is Using Item", desc = "Returns whether the entity is holding a use action, e.g. drawing a bow or eating.")
    public boolean isUsingItem() {
        return getOriginal().isUsingItem();
    }

    @ScriptFunction(pure = true, label = "Is Fall Flying", desc = "Returns whether the entity is gliding on elytra.")
    public boolean isFallFlying() {
        return getOriginal().isFallFlying();
    }

    @ScriptFunction(pure = true, label = "Is Sleeping")
    public boolean isSleeping() {
        return getOriginal().isSleeping();
    }

    @ScriptFunction(pure = true, label = "Is Dead or Dying")
    public boolean isDeadOrDying() {
        return getOriginal().isDeadOrDying();
    }
}
