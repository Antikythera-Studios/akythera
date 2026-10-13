package com.yesman.akythera.api.world.patch.item.component;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.UniqueComponent;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.modularize.node.ScriptCaller;
import com.yesman.akythera.api.data.modularize.node.ScriptNode;
import com.yesman.akythera.api.data.modularize.node.ScriptRecorder;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.api.world.patch.item.ItemPatch;
import com.yesman.akythera.api.world.patch.item.WeaponStance;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.blueprint.ItemPatchBlueprint;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.LogicalSide;
import net.minecraft.world.entity.EquipmentSlot;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// One [WeaponStance] an item can be held in, and everything that stance changes about the item.
///
/// The stance's data rides as attached child components. The patch's other components are folded in
/// alongside them by [ItemPatchBlueprint], so an adapter's component set is the item's own with the
/// adapter's declarations laid over it.
///
/// An adapter establishes its stance when [#getCondition] evaluates true against the equipper and the
/// slot the item is held in. Adapters are resolved in the order their [ItemPatch] lists them, so the
/// first match wins. When none matches, the item's own components are in force.
///
/// e.g. Equipper is in one-handed stance with a sword in default. While switching to a two-handed
///      stance when equipping another weapon tagged as "sword" in offhand.
public final class WeaponStanceVariationAdapter
    implements PatchComponent, ComponentAttachable, ComponentBlueprint<WeaponStanceVariationAdapter, ItemPatch>
{
    /// The context local [#matches] binds the equipper to before evaluating the condition. Must stay
    /// a compile-time constant: an editor annotation names it.
    public static final String EQUIPPER_PARAM = "equipper";

    /// The context local [#matches] binds the [EquipmentSlot] to. Same constraint as
    /// [#EQUIPPER_PARAM].
    public static final String SLOT_PARAM = "slot";

    public static final MapCodec<WeaponStanceVariationAdapter> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                WeaponStance.ENUM_MANAGER.getNameCodec()
                    .fieldOf("stance").forGetter(WeaponStanceVariationAdapter::getStance),
                PureNode.CODEC
                    .fieldOf("condition").forGetter(WeaponStanceVariationAdapter::getCondition),
                ComponentBlueprint.componentMapCodec(() -> AkytheraRegistries.Frozen.ITEM_PATCH_COMPONENT_TYPES)
                    .optionalFieldOf("components", Map.of()).forGetter(WeaponStanceVariationAdapter::getChildBlueprints),
                ENABLED_CODEC.forGetter(WeaponStanceVariationAdapter::enabled)
            )
            .apply(instance, WeaponStanceVariationAdapter::new)
    );

    private final WeaponStance stance;

    /// Boolean tree over the equipper and the slot, evaluated by [#matches].
    private final PureNode condition;

    /// What this adapter builds its children from. Carried by both the authored adapter and its
    /// instantiated copy.
    private final Map<String, ComponentBlueprint<?, ?>> childBlueprints;

    /// [ComponentAttachable] implementation. Empty on the authored adapter, filled on the copy.
    private final Map<String, PatchComponent> components = new LinkedHashMap<>();

    /// [#components] indexed by [UniqueComponent] type.
    private final Map<Class<? extends UniqueComponent>, ? extends UniqueComponent> uniqueComponents;

    /// Whether this adapter takes part, see [PatchComponent#isEnabled]. On the authored adapter, the
    /// state its copy starts in.
    private boolean enabled;

    /// The authored form, as the data file declares it. Its own child map stays empty.
    public WeaponStanceVariationAdapter(
        final WeaponStance stance,
        final PureNode condition,
        final Map<String, ComponentBlueprint<?, ?>> childBlueprints,
        final boolean enabled
    ) {
        this.stance = stance;
        this.condition = condition;
        this.childBlueprints = childBlueprints;
        this.enabled = enabled;
        this.uniqueComponents = Map.of();
    }

    /// The instantiated copy: same declaration, children built.
    @SuppressWarnings("unchecked")
    private WeaponStanceVariationAdapter(final WeaponStanceVariationAdapter blueprint, final ItemPatch blueprintOwner) {
        this.stance = blueprint.stance;
        this.condition = blueprint.condition;
        this.childBlueprints = blueprint.childBlueprints;
        this.enabled = blueprint.enabled;

        blueprint.childBlueprints.forEach((name, child) -> components.put(name, ((ComponentBlueprint<?, Object>)child).instantiate(blueprintOwner)));

        this.uniqueComponents = ComponentAttachable.indexUnique(components);
    }

    /// A copy of `overlay` whose children are `base` with `overlay`'s own laid over them. The stance,
    /// the condition and the switch are `overlay`'s.
    private WeaponStanceVariationAdapter(final WeaponStanceVariationAdapter overlay, final Map<String, ? extends PatchComponent> base) {
        this.stance = overlay.stance;
        this.condition = overlay.condition;
        this.childBlueprints = overlay.childBlueprints;
        this.enabled = overlay.enabled;

        components.putAll(PatchComponent.fold(List.of(base, overlay.components)));

        this.uniqueComponents = ComponentAttachable.indexUnique(components);
    }

    /// A copy of this adapter carrying `base` under its own children, its own declarations winning.
    public WeaponStanceVariationAdapter withInheritedComponents(final Map<String, ? extends PatchComponent> base) {
        return new WeaponStanceVariationAdapter(this, base);
    }

    /// The stance, so an adapter folds into the one a parent blueprint declares for the same stance
    /// whatever name either carries.
    @Override
    public WeaponStance mergeKey() {
        return stance;
    }

    /// An adapter for the same stance inherits this one's children.
    @Override
    public PatchComponent overwrite(final PatchComponent overwrite) {
        if (!(overwrite instanceof WeaponStanceVariationAdapter other)) {
            return overwrite;
        }

        return other.withInheritedComponents(components);
    }

    public WeaponStance getStance() {
        return stance;
    }

    public PureNode getCondition() {
        return condition;
    }

    public Map<String, ComponentBlueprint<?, ?>> getChildBlueprints() {
        return childBlueprints;
    }

    /// Whether the equipper's current state establishes this stance while the item is held in `slot`.
    /// A throwing condition establishes nothing.
    public boolean matches(final LivingEntityPatch<?> equipper, final EquipmentSlot slot) {
        ScriptVariables variables = new ScriptVariables();
        variables.setValue(EQUIPPER_PARAM, equipper);
        variables.setValue(SLOT_PARAM, slot);

        ScriptNode.ExecutionContext context = ScriptRecorder.newContext(
            new ScriptCaller(equipper, null, "stance " + stance.identifier()), variables);

        try {
            return condition.execute(null, context);
        } catch (Throwable throwable) {
            ScriptRecorder.recordFailure(context, throwable);

            return false;
        }
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

    /// An adapter lives in an [ItemPatch], shared by both sides.
    @Override
    public LogicalSide logicalSide() {
        return LogicalSide.BOTH;
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nullable <C extends UniqueComponent> C getComponent(final Class<C> type) {
        return (C)uniqueComponents.get(type);
    }

    @Override
    public WeaponStanceVariationAdapter instantiate(final ItemPatch blueprintOwner) {
        return new WeaponStanceVariationAdapter(this, blueprintOwner);
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public MapCodec<? extends ComponentBlueprint<WeaponStanceVariationAdapter, ItemPatch>> mapCodec() {
        return CODEC;
    }

    /// The stance one [LivingEntityPatch] holds: the adapter whose condition held, the [ItemPatch]
    /// that declares it, and the slot that patch sits in.
    public record Resolution(EquipmentSlot slot, ItemPatch itemPatch, WeaponStanceVariationAdapter adapter) {}
}
