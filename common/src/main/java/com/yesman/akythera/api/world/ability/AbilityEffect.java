package com.yesman.akythera.api.world.ability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.serializable.AttributeModifierEntry;
import com.yesman.akythera.core.reflection.ScriptType;
import com.yesman.akythera.core.util.helper.ParseUtils;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.jspecify.annotations.Nullable;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

/// A change an ability makes to its owner's [Attribute]s and [AbilityResource]s. One effect fills
/// any of three roles: a Duration Effect, a Cooldown Effect or a Cost.
@ScriptType(autoRegister = false)
public final class AbilityEffect {
    public static final MapCodec<AbilityEffect> MAP_CODEC =
        RecordCodecBuilder.mapCodec(
            instance ->
                instance.group(
                    Identifier.CODEC.fieldOf("id").forGetter(AbilityEffect::getId),
                    Modifier.CODEC.listOf().optionalFieldOf("modifiers", List.of())
                        .forGetter(AbilityEffect::getModifiers),
                    ParseUtils.enumCodec(ApplicationType.class).fieldOf("type")
                        .forGetter(AbilityEffect::getApplicationType),
                    Codec.INT.optionalFieldOf("duration", 0).forGetter(AbilityEffect::getDuration),
                    Codec.INT.optionalFieldOf("max_stack", 1).forGetter(AbilityEffect::getMaxStack)
                )
                .apply(instance, AbilityEffect::new)
        );

    public static final Codec<AbilityEffect> CODEC = MAP_CODEC.codec();

    /// Names the effect, and is the stem of every [AttributeModifier] it applies.
    ///
    /// A data pack entry authors this itself, so it can disagree with the registry key it is filed
    /// under. Nothing reconciles them.
    private final Identifier id;

    private final List<Modifier<?>> modifiers;
    private final ApplicationType applicationType;

    /// How long the effect lasts, in ticks. Read only for [ApplicationType#DURATION], and a
    /// recharge timer in the Cooldown role.
    private final int duration;

    /// The most charges this effect banks. One means no stacking.
    private final int maxStack;

    public AbilityEffect(
        final Identifier id,
        final List<Modifier<?>> modifiers,
        final ApplicationType applicationType,
        final int duration,
        final int maxStack
    ) {
        if (applicationType == ApplicationType.DURATION && duration <= 0) {
            throw new IllegalArgumentException("A Duration effect needs a positive duration, got " + duration);
        }

        if (maxStack < 1) {
            throw new IllegalArgumentException("maxStack must be at least 1, got " + maxStack);
        }

        // Every Attribute modifier of one application shares its instance's modifier id, and
        // AttributeInstance refuses a duplicate id.
        final List<Holder<Attribute>> seen = new ArrayList<>();

        for (Modifier<?> modifier : modifiers) {
            AttributeModifierEntry entry = modifier.asAttributeModifier();

            if (entry == null) continue;

            if (seen.contains(entry.attribute())) {
                throw new IllegalArgumentException(
                    "An effect may modify an attribute only once, and " + id + " names "
                        + entry.attribute().getRegisteredName() + " twice."
                );
            }

            seen.add(entry.attribute());
        }

        this.id = id;
        this.modifiers = List.copyOf(modifiers);
        this.applicationType = applicationType;
        this.duration = duration;
        this.maxStack = maxStack;
    }

    public Identifier getId() {
        return id;
    }

    public List<Modifier<?>> getModifiers() {
        return modifiers;
    }

    public ApplicationType getApplicationType() {
        return applicationType;
    }

    public int getDuration() {
        return duration;
    }

    public int getMaxStack() {
        return maxStack;
    }

    public enum ApplicationType {
        INSTANT, DURATION, INFINITE
    }

    public record Modifier<T>(ModifierType modifierType, T modifyTarget) {
        public static final Codec<Modifier<?>> CODEC =
            ParseUtils.enumCodec(ModifierType.class).dispatch(
                "modifier_type",
                Modifier::modifierType,
                modifierType -> switch (modifierType) {
                    case ATTRIBUTE -> AttributeModifierEntry.CODEC
                        .xmap(
                            entry -> new Modifier<>(ModifierType.ATTRIBUTE, entry),
                            Modifier::modifyTarget
                        )
                        .fieldOf("attribute_modifier");
                    case RESOURCE -> AbilityResourceSpec.CODEC
                        .xmap(
                            spec -> new Modifier<>(ModifierType.RESOURCE, spec),
                            Modifier::modifyTarget
                        )
                        .fieldOf("resource");
                }
            );

        public Modifier {
            Class<?> expected = modifierType == ModifierType.ATTRIBUTE
                ? AttributeModifierEntry.class
                : AbilityResourceSpec.class;

            if (!expected.isInstance(modifyTarget)) {
                throw new IllegalArgumentException(
                    MessageFormat.format(
                        "A {0} modifier carries {1}, not {2}.",
                        modifierType,
                        expected.getSimpleName(),
                        modifyTarget == null ? "nothing" : modifyTarget.getClass().getSimpleName()
                    )
                );
            }
        }

        public @Nullable AttributeModifierEntry asAttributeModifier() {
            if (modifierType == ModifierType.RESOURCE) return null;

            return (AttributeModifierEntry)modifyTarget;
        }

        public @Nullable AbilityResourceSpec asAbilitySource() {
            if (modifierType == ModifierType.ATTRIBUTE) return null;

            return (AbilityResourceSpec)modifyTarget;
        }
    }

    public record AbilityResourceSpec(AbilityResource abilityResource, float amount) {
        public static final Codec<AbilityResourceSpec> CODEC =
            RecordCodecBuilder.create(
                instance ->
                    instance.group(
                        AbilityResources.CODEC.fieldOf("resource")
                            .forGetter(AbilityResourceSpec::abilityResource),
                        Codec.FLOAT.fieldOf("amount").forGetter(AbilityResourceSpec::amount)
                    )
                    .apply(instance, AbilityResourceSpec::new)
            );
    }

    public enum ModifierType {
        /// The modifier modifies [Attribute]
        ATTRIBUTE,

        /// The modifier modifies [AbilityResource]
        RESOURCE
    }
}
