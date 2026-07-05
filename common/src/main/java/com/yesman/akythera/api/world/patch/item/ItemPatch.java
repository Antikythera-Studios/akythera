package com.yesman.akythera.api.world.patch.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.node.FlowFunctionNode;
import com.yesman.akythera.api.data.serializable.AttributeModifierEntry;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.world.patch.item.*;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// An item plugin for extra data used by Epic Fight
///
/// Item patches can depend on other patches internally or externally. Internal dependencies
/// are applied to stance variations in one [Item]. Suppose situations you hold a sword in main
/// hand, with and without another sword in offhand. The weapon attack motions varied on those
/// situations, but they would have the same hit sounds and hit particles since the two-handed
/// stance variation patch is coming from the root case.
///
/// External dependencies, on the other hand, are applied between [Item]s, but [ItemPatch] is
/// not necessarily be assigned to specific [Item]. For example, Sword items have material
/// variations like wooden, stone, golden, etc. An [ItemPatch] called "sword" has core information
/// that are applied to all variations, and patches for each variation has *external dependency*
/// to the "sword" patch, having some unique attributes based on material level.
///
/// Internal dependencies are implemented by [#stanceProviders] that returns [WeaponStance]
/// by the item holding entity's state.
///
/// You specify external dependencies by [ItemPatch.Builder#parent] that will copy the parent
/// builder from [RegistryAccess] when an item firstly references a patch. Check details in
/// [AkytheraPatches#getItemPatch].
///
/// When an inheritance occurs, a builder is deep copied from parent item patch, meaning the
/// original containers are kept. You need to care about this fact when inheriting item builders
/// especially for [#attributeModifiers].
public final class ItemPatch {
    /// [EnumerableDataBlock] to tag the item
    private final WeaponCategory weaponCategory;

    /// A weaponStance determinate function depending on the item owner entity's state
    /// Null for root item patches
    @Nullable
    private final List<StanceProvider> stanceProviders;

    /// Combo attack animation sequence
    /// TODO: Manage by universal data record
    private final ComboSequenceAnim comboSequenceAnim;

    /// Living motion variations
    //private final Map<LivingMotion, AnimationAccessor<? extends StaticAnimation>> livingMotionVariations;

    /// Attribute modifiers
    private final List<AttributeModifierEntry> attributeModifiers;

    /// An innate weapon ability used by players
    //@Nullable
    //private final Skill<?> weaponSkill;

    /// An innate weapon ability that is activated when fully charged used by players
    //@Nullable
    //private final Skill<?> weaponChargeSkill;

    /// A sound played when wield the item
    private final SoundEvent swingSound;

    /// A sound played when hitting entities
    private final SoundEvent hitSound;

    /// A particleOptions played when hitting entities
    private final ParticleType<?> hitParticle;

    // A callback function of combo attack to determine next attack sequence. Used for [ComboAttackAbility]
    // private final ModifyComboCounter.ComboCounterHandler comboCounterHandler;

    /// A zoom-in behavior when an item starts using
    private final ZoomInType zoomInType;

    /// Whether the item can be put on offhand
    private final boolean canPutOnOffhand;

    /// A minimum distance the item holder will keep when attacking
    private final float reach;

    private Item original;

    private ItemPatch(Builder builder) {
        weaponCategory = builder.weaponCategory;
        stanceProviders = builder.stanceProviders;
        comboSequenceAnim = builder.comboSequenceAnim;
        //livingMotionVariations = builder.livingMotionVariations;
        attributeModifiers = builder.attributeModifiers;
        //weaponSkill = builder.weaponSkill;
        //weaponChargeSkill = builder.weaponChargeSkill;
        swingSound = builder.swingSound;
        hitSound = builder.hitSound;
        hitParticle = builder.hitParticle;
        //comboCounterHandler = builder.comboCounterHandler;
        zoomInType = builder.zoomInType;
        canPutOnOffhand = builder.canPutOnOffhand;
        reach = builder.reach;
    }

    /// Returns weapon category
    public WeaponCategory getWeaponCategory() {
        return weaponCategory;
    }

    /// Returns a weaponStance by the item holder's state
    public WeaponStance getStance(EntityPatch<LivingEntity> entityPatch) {
        for (StanceProvider stanceProvider : stanceProviders) {
            if (stanceProvider.checkAllConditions(entityPatch)) {
                return stanceProvider.stance();
            }
        }

        return EpicFightWeaponStance.COMMON;
    }

    /// Returns a sound played when wield the item
    public SoundEvent getSwingSound() {
        return swingSound;
    }

    /// Returns a sound played when hitting entities
    public SoundEvent getHitSound() {
        return hitSound;
    }

    /// Returns a particleOptions played when hitting entities
    public ParticleType<?> getHitParticle() {
        return hitParticle;
    }

    /// Returns a callback function of combo attack to determine next attack sequence
    /*
    public ModifyComboCounter.ComboCounterHandler getComboCounterHandler() {
        return comboCounterHandler;
    }
    */

    /// Returns a zoom-in behavior when an item starts using
    public ZoomInType getZoomInType() {
        return zoomInType;
    }

    /// Returns whether the item can be put on offhand
    public boolean canPutOnOffhand() {
        return canPutOnOffhand;
    }

    /// Returns a minimum distance the item holder will keep when attacking
    public float getReach() {
        return reach;
    }

    /// Original item reference reloaded
    @ApiStatus.Internal
    public void setOriginal(final Item original) {
        this.original = original;
    }

    public Item getOriginal() {
        return original;
    }

    public record ComboSequenceAnim(
        List<ResourceKey<@NotNull AnimationMontage>> comboAttacks,  // Sequential combo animations played in order
        ResourceKey<@NotNull AnimationMontage> dash,                // Attack animation played while sprinting
        ResourceKey<@NotNull AnimationMontage> airSlash             // Attack animation played while jumping
    ) {
        public static final Codec<ComboSequenceAnim> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    ResourceKey.codec(AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY).listOf().fieldOf("combo_attacks").forGetter(ComboSequenceAnim::comboAttacks),
                    ResourceKey.codec(AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY).fieldOf("dash").forGetter(ComboSequenceAnim::dash),
                    ResourceKey.codec(AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY).fieldOf("air_slash").forGetter(ComboSequenceAnim::airSlash)
                )
                .apply(instance, ComboSequenceAnim::new)
        );
    }

    public record StanceProvider(
        FlowFunctionNode validators,
        WeaponStance stance
    ) {
        public static final Codec<StanceProvider> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    FlowFunctionNode.CODEC.fieldOf("validators").forGetter(stanceProvider -> stanceProvider.validators),
                    WeaponStance.ENUM_MANAGER.getNameCodec().fieldOf("stance").forGetter(StanceProvider::stance)
                )
                .apply(instance, StanceProvider::new)
        );

        public boolean checkAllConditions(EntityPatch<LivingEntity> entityPatch) {
            // TODO: return validatio result by execution node
            /*
            for (ModularizedFunction.ModularizedUniFunction<LivingEntityPatch<?>, Boolean> validator : validators) {
                if (!validator.apply(entityPatch)) {
                    return false;
                }
            }
            */

            return true;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        public static final Codec<Builder> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    ResourceKey.codec(AkytheraRegistries.DataPack.ITEM_PATCH_KEY).optionalFieldOf("parent").forGetter(builder -> Optional.ofNullable(builder.parent)),
                    WeaponCategory.ENUM_MANAGER.getNameCodec().fieldOf("category").forGetter(builder -> builder.weaponCategory),
                    StanceProvider.CODEC.listOf().optionalFieldOf("stance_providers").forGetter(builder -> Optional.ofNullable(builder.stanceProviders)),
                    ComboSequenceAnim.CODEC.fieldOf("attack_motions").forGetter(builder -> builder.comboSequenceAnim),
                    AttributeModifierEntry.CODEC.listOf().fieldOf("attribute_modifiers").forGetter(builder -> builder.attributeModifiers),
                    //AkytheraRegistries.Registries.SKILL.byNameCodec().optionalFieldOf("weapon_ability").forGetter(builder -> Optional.ofNullable(builder.weaponSkill)),
                    //AkytheraRegistries.Registries.SKILL.byNameCodec().optionalFieldOf("weapon_charge_ability").forGetter(builder -> Optional.ofNullable(builder.weaponChargeSkill)),
                    BuiltInRegistries.SOUND_EVENT.byNameCodec().fieldOf("swing_sound").forGetter(builder -> builder.swingSound),
                    BuiltInRegistries.SOUND_EVENT.byNameCodec().fieldOf("hit_sound").forGetter(builder -> builder.hitSound),
                    BuiltInRegistries.PARTICLE_TYPE.byNameCodec().fieldOf("hit_particle").forGetter(builder -> builder.hitParticle),
                    StringRepresentable.fromEnum(ZoomInType::values).fieldOf("zoom_in_type").forGetter(builder -> builder.zoomInType),
                    Codec.BOOL.fieldOf("can_put_on_offhand").forGetter(builder -> builder.canPutOnOffhand),
                    Codec.FLOAT.fieldOf("reach").forGetter(builder -> builder.reach)
                )
                .apply(
                    instance,
                    (arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11) -> {
                        Builder builder = new Builder();
                        arg1.ifPresent(builder::parent);
                        builder.weaponCategory(arg2);
                        arg3.ifPresent(builder::stanceProviders);
                        builder.comboSequenceAnim(arg4);
                        builder.attributeModifiers(arg5);
                        return builder;
                    }
                )
        );

        final boolean root;
        // for weaponStance variation builder
        @Nullable final WeaponStance weaponStance;
        @Nullable final Builder rootBuilder;

        /// Registry name of parent builder
        @Nullable ResourceKey<@NotNull Builder> parent;
        WeaponCategory weaponCategory;
        List<StanceProvider> stanceProviders;
        ComboSequenceAnim comboSequenceAnim;
        //Map<LivingMotion, AnimationAccessor<? extends StaticAnimation>> livingMotionVariations = new HashMap<>();
        List<AttributeModifierEntry> attributeModifiers = new ArrayList<>();
        //@Nullable Skill<?> weaponSkill;
        //@Nullable Skill<?> weaponChargeSkill;
        SoundEvent swingSound;
        SoundEvent hitSound;
        ParticleType<?> hitParticle;
        //ModifyComboCounter.ComboCounterHandler comboCounterHandler;
        ZoomInType zoomInType;
        Boolean canPutOnOffhand;
        Float reach;
        Map<WeaponStance, Builder> stanceVariations;

        /// Creates a root builder
        public Builder() {
            root = true;
            rootBuilder = null;
            weaponStance = null;
            weaponCategory = EpicFightWeaponCategory.NOT_WEAPON;
            //swingSound = EpicFightSounds.WHOOSH.value();
            //hitSound = EpicFightSounds.BLUNT_HIT.value();
            //hitParticle = EpicFightParticles.HIT_BLUNT.value();
            //comboCounterHandler = ModifyComboCounter.ComboCounterHandler.DEFAULT_COMBO_HANDLER;
            zoomInType = ZoomInType.NONE;
            canPutOnOffhand = true;
            reach = 0.0F;
        }

        /// Copy constructor for internal inheritance
        ///
        /// @param weaponStance null when it's an external inheritance
        public Builder(Builder other, @NotNull WeaponStance weaponStance) {
            if (!other.root) {
                throw new IllegalArgumentException("Can't inherits item patch builder from non-root builder");
            }

            root = false;
            rootBuilder = other;
            this.weaponStance = weaponStance;
        }

        /// Copy constructor for external inheritance
        ///
        /// @param externalParent builder found by `child`'s parent
        public Builder(Builder externalParent, Builder child) {
            this();

            parent = null;
            weaponCategory = ParseUtils.nvl(child.weaponCategory, externalParent.weaponCategory);
            stanceProviders = ParseUtils.nvl(child.stanceProviders, externalParent.stanceProviders);
            comboSequenceAnim = ParseUtils.nvl(child.comboSequenceAnim, externalParent.comboSequenceAnim);
            attributeModifiers = Stream.concat(child.attributeModifiers.stream(), externalParent.attributeModifiers.stream()).collect(Collectors.toList());
            //weaponSkill = ParseUtils.nvl(child.weaponSkill, externalParent.weaponSkill);
            //weaponChargeSkill = ParseUtils.nvl(child.weaponChargeSkill, externalParent.weaponChargeSkill);
            swingSound = ParseUtils.nvl(child.swingSound, externalParent.swingSound);
            hitSound = ParseUtils.nvl(child.hitSound, externalParent.hitSound);
            hitParticle = ParseUtils.nvl(child.hitParticle, externalParent.hitParticle);
            zoomInType = ParseUtils.nvl(child.zoomInType, externalParent.zoomInType);

            ZoomInType zoomInType = ZoomInType.NONE;
            boolean canPutOnOffhand = true;
            float reach = 0.0F;

            //SoundEvent swingSound = EpicFightSounds.WHOOSH.value();
            //SoundEvent hitSound = EpicFightSounds.BLUNT_HIT.value();
            //ParticleType<?> hitParticle = EpicFightParticles.HIT_BLUNT.value();
        }

        public Builder parent(ResourceKey<@NotNull Builder> parent) {
            this.parent = parent;
            return this;
        }

        public Builder weaponCategory(WeaponCategory weaponCategory) {
            this.weaponCategory = weaponCategory;
            return this;
        }

        public Builder stanceProviders(List<StanceProvider> stanceProviders) {
            this.stanceProviders = stanceProviders;
            return this;
        }

        public Builder comboSequenceAnim(ComboSequenceAnim comboSequenceAnim) {
            this.comboSequenceAnim = comboSequenceAnim;
            return this;
        }
        /*
                public Builder livingMotionModifiers(LivingMotion livingMotion, AnimationAccessor<? extends StaticAnimation> animation) {
                    this.livingMotionVariations.put(livingMotion, animation);
                    return this;
                }
        */
        public Builder attributeModifier(Holder<@NotNull Attribute> attribute, AttributeModifier attributeModifier) {
            this.attributeModifiers.add(new AttributeModifierEntry(attribute, attributeModifier));
            return this;
        }

        public Builder attributeModifiers(List<AttributeModifierEntry> attributeModifiers) {
            this.attributeModifiers.addAll(attributeModifiers);
            return this;
        }
/*
        public Builder weaponSkill(Skill<?> weaponSkill) {
            this.weaponSkill = weaponSkill;
            return this;
        }

        public Builder weaponChargeSkill(Skill<?> weaponChargeSkill) {
            this.weaponChargeSkill = weaponChargeSkill;
            return this;
        }
*/
        public Builder swingSound(SoundEvent swingSound) {
            this.swingSound = swingSound;
            return this;
        }

        public Builder hitSound(SoundEvent hitSound) {
            this.hitSound = hitSound;
            return this;
        }

        public Builder canPutOnOffhand(boolean canPutOnOffhand) {
            this.canPutOnOffhand = canPutOnOffhand;
            return this;
        }
/*
        public Builder comboCounterHandler(ModifyComboCounter.ComboCounterHandler comboCounterHandler) {
            this.comboCounterHandler = comboCounterHandler;
            return this;
        }
*/
        public Builder zoomInType(ZoomInType zoomInType) {
            this.zoomInType = zoomInType;
            return this;
        }

        public Builder reach(float reach) {
            this.reach = reach;
            return this;
        }

        /// Call [#backToRoot] to get back to the root builder
        public Builder newStanceVariation(WeaponStance weaponStance) {
            if (!root) {
                throw new IllegalStateException("Can't create a variation of a variation");
            }

            Builder stanceVariationBuilder = new Builder(this, weaponStance);
            stanceVariations.put(weaponStance, stanceVariationBuilder);

            return stanceVariationBuilder;
        }

        /// Throws an error when the build is root
        public Builder backToRoot() throws IllegalStateException {
            if (root) {
                throw new IllegalStateException("No root builder for root builder");
            }

            return rootBuilder;
        }

        /// Build an item patch. Requires [RegistryAccess] to get a parent type
        public ItemPatch build(RegistryAccess registryAccess) {
            Builder builder = this;

            if (parent != null) {
                builder = new Builder(registryAccess.getOrThrow(parent).value(), this);
            }

            return new ItemPatch(builder);
        }
    }
}
