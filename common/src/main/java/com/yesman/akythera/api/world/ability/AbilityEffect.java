package com.yesman.akythera.api.world.ability;

import com.yesman.akythera.api.data.serializable.AttributeModifierEntry;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class AbilityEffect {
    private final List<Modifier<?>> modifiers;
    private final ApplicationType applicationType;



    public enum ApplicationType {
        INSTANT, DURATION, INFINITE
    }

    public record Modifier<T>(ModifierType modifierType, T modifyTarget) {
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
    }

    public enum ModifierType {
        /// The modifier modifies [Attribute]
        ATTRIBUTE,

        /// The modifier modifies [AbilityResource]
        RESOURCE
    }
}
