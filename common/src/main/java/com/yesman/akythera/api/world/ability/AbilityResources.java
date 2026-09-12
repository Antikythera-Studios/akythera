package com.yesman.akythera.api.world.ability;

import com.mojang.serialization.Codec;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.core.Akythera;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public final class AbilityResources {
    private static final Map<Identifier, AbilityResource> REGISTRY = new ConcurrentHashMap<>();
    private static final Map<AbilityResource, Identifier> BI_MAP = new HashMap<>();

    public static final Codec<AbilityResource> CODEC =
        Identifier.CODEC.xmap(
            AbilityResources::getAbilityResource,
            BI_MAP::get
        );

    public static final StreamCodec<? super RegistryFriendlyByteBuf, AbilityResource> STREAM_CODEC;

    private AbilityResources() {}

    public static void registerAbilityResources(final Identifier id, final AbilityResource resource) {
        if (!BI_MAP.isEmpty()) {
            throw new IllegalStateException("AbilityResource registry already initialized and frozen.");
        }

        REGISTRY.put(id, resource);
    }

    public static AbilityResource getAbilityResource(final Identifier id) {
        return REGISTRY.get(id);
    }

    public static Identifier getId(final AbilityResource abilityResources) {
        return BI_MAP.get(abilityResources);
    }

    public static Stream<Map.Entry<Identifier, AbilityResource>> entryStream() {
        return REGISTRY.entrySet().stream();
    }

    public static void bootStrap() {
        REGISTRY.put(Akythera.identifier("health"), new AbilityResource() {
            @Override
            public boolean hasResource(LivingEntityPatch<?> owner, float amount) {
                return owner.getOriginal().getHealth() > amount;
            }

            @Override
            public boolean consume(LivingEntityPatch<?> owner, float amount) {
                if (owner.getOriginal().getHealth() <= amount) {
                    return false;
                }

                owner.getOriginal().setHealth(owner.getOriginal().getHealth() - amount);

                return false;
            }
        });

        // Init BIMAP dereference
        REGISTRY.forEach((k, v) -> BI_MAP.put(v, k));
    }
}
