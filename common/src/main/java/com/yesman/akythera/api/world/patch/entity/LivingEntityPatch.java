package com.yesman.akythera.api.world.patch.entity;

import com.yesman.akythera.api.animation.animator.AnimationInterfaceKey;
import com.yesman.akythera.api.component.UniqueComponent;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.api.world.patch.entity.component.EquipmentHolder;
import com.yesman.akythera.api.world.patch.item.ItemPatch;
import com.yesman.akythera.api.world.patch.item.WeaponStance;
import com.yesman.akythera.api.world.patch.item.component.ReplaceAnimations;
import com.yesman.akythera.api.world.patch.item.component.WeaponStanceVariationAdapter;
import com.yesman.akythera.core.animation.animator.BlendingAnimator;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.blueprint.EntityPatchBlueprint;
import com.yesman.akythera.core.reflection.ScriptFunction;
import com.yesman.akythera.core.reflection.ScriptType;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ScriptType(autoRegister = false)
public class LivingEntityPatch<T extends LivingEntity> extends EntityPatch<T> implements EquipmentHolder {
    /// Slots equipment is read from, in priority order — the main hand answers before the off hand.
    private static final List<EquipmentSlot> STANCE_SLOTS = List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND);

    /// What [#preTick] last resolved, or null while no [WeaponStanceVariationAdapter] matches.
    private WeaponStanceVariationAdapter.@Nullable Resolution stance;

    /// The replacements [#refreshAnimationReplacements] last wrote into the animator, which the next
    /// call diffs against.
    private Map<AnimationInterfaceKey, ResourceKey<? extends PlayableAnimation>> animationReplacements = Map.of();

    /// Whether [#animationReplacements] were written through their mirrored twins.
    private boolean replacementsMirrored;

    /// This entity's own item patch per equipment slot, and the shared patch it was built from.
    private final Map<EquipmentSlot, SlotItemPatch> slotItemPatches = new EnumMap<>(EquipmentSlot.class);

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
        ItemStack itemStack = getItemInSlot(equipmentSlot);
        ItemPatch shared = AkytheraPatches.getItemPatch(itemStack);

        if (shared == null) {
            slotItemPatches.remove(equipmentSlot);

            return null;
        }

        SlotItemPatch slotItemPatch = slotItemPatches.get(equipmentSlot);

        if (slotItemPatch == null || slotItemPatch.shared() != shared) {
            slotItemPatch = new SlotItemPatch(shared, shared.getBlueprint().instantiate(itemStack.getItem(), this, equipmentSlot));
            slotItemPatches.put(equipmentSlot, slotItemPatch);
        }

        return slotItemPatch.instance();
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

    /// An entity's own item patch for one slot, and the shared patch it was built from, which tells when the slot's
    /// item, or the loaded blueprint, changed.
    private record SlotItemPatch(ItemPatch shared, ItemPatch instance) {
    }
}
