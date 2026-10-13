package com.yesman.akythera.api.input;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.reflection.ScriptType;
import com.yesman.akythera.core.util.helper.ParseUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Locale;
import java.util.Optional;

/// One key binding an ability reads, as a data pack entry.
///
/// `keyMapping` is the registered name of a client side `KeyMapping`, which a data pack may name
/// but never create.
///
/// @param threshold ticks a [TriggeringType#HOLD_UNTIL] release is judged against. Absent means 0
/// @param priority  order among the actions bound to one key and triggering type. Higher runs first
@ScriptType(autoRegister = false)
public record InputAction(
    String keyMapping,
    TriggeringType triggeringType,
    Optional<Integer> threshold,
    int priority
) {
    private static final Codec<TriggeringType> TRIGGERING_TYPE_CODEC =
        Codec.STRING.comapFlatMap(
            name -> {
                TriggeringType value = ParseUtils.enumValueOfOrNull(TriggeringType.class, name);

                return value == null
                    ? DataResult.error(() -> "Unknown TriggeringType: " + name)
                    : DataResult.success(value);
            },
            value -> value.name().toLowerCase(Locale.ROOT)
        );

    public static final Codec<InputAction> CODEC =
        RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Codec.STRING.fieldOf("key_mapping").forGetter(InputAction::keyMapping),
                    TRIGGERING_TYPE_CODEC.fieldOf("triggering_type").forGetter(InputAction::triggeringType),
                    Codec.INT.optionalFieldOf("threshold").forGetter(InputAction::threshold),
                    Codec.INT.optionalFieldOf("priority", 0).forGetter(InputAction::priority)
                )
                .apply(instance, InputAction::new)
        );

    public static final StreamCodec<RegistryFriendlyByteBuf, InputAction> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            InputAction::keyMapping,
            ByteBufCodecs.idMapper(ordinal -> TriggeringType.values()[ordinal], TriggeringType::ordinal),
            InputAction::triggeringType,
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT),
            InputAction::threshold,
            ByteBufCodecs.VAR_INT,
            InputAction::priority,
            InputAction::new
        );

    public InputAction {
        if (threshold.isPresent() && !triggeringType.isHeld()) {
            throw new IllegalArgumentException(
                "Only a held triggering type reads a threshold, and " + triggeringType + " is not one."
            );
        }
    }

    /// The threshold in ticks, or 0 when this action names none.
    public int thresholdTicks() {
        return threshold.orElse(0);
    }

    /// Whether `heldTicks` reached the threshold.
    public boolean reachedThreshold(final int heldTicks) {
        return heldTicks >= thresholdTicks();
    }
}
