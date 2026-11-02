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
/// @param threshold            ticks a [TriggeringType#HOLD_UNTIL] release is judged against. Absent means 0
/// @param holdStart            ticks a held type waits down before it triggers. Absent means [#DEFAULT_HOLD_START_TICKS]
/// @param priority             order among the actions bound to one key and triggering type. Higher runs first
/// @param suppressLowerActions while bound, the actions of a lower priority on the same key never trigger, and
///                             vanilla never reads the key
@ScriptType(autoRegister = false)
public record InputAction(
    String keyMapping,
    TriggeringType triggeringType,
    Optional<Integer> threshold,
    Optional<Integer> holdStart,
    int priority,
    boolean suppressLowerActions
) {
    public static final int DEFAULT_HOLD_START_TICKS = 2;

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
                    Codec.INT.optionalFieldOf("hold_start_ticks").forGetter(InputAction::holdStart),
                    Codec.INT.optionalFieldOf("priority", 0).forGetter(InputAction::priority),
                    Codec.BOOL.optionalFieldOf("suppress_lower_actions", false).forGetter(InputAction::suppressLowerActions)
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
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT),
            InputAction::holdStart,
            ByteBufCodecs.VAR_INT,
            InputAction::priority,
            ByteBufCodecs.BOOL,
            InputAction::suppressLowerActions,
            InputAction::new
        );

    public InputAction {
        if (threshold.isPresent() && !triggeringType.isHeld()) {
            throw new IllegalArgumentException(
                "Only a held triggering type reads a threshold, and " + triggeringType + " is not one."
            );
        }

        if (holdStart.isPresent() && !triggeringType.isHeld()) {
            throw new IllegalArgumentException(
                "Only a held triggering type reads hold start ticks, and " + triggeringType + " is not one."
            );
        }
    }

    /// The hold start in ticks, or [#DEFAULT_HOLD_START_TICKS] when this action names none.
    public int holdStartTicks() {
        return holdStart.orElse(DEFAULT_HOLD_START_TICKS);
    }

    /// Whether a hold of `heldTicks` has started.
    public boolean startedHold(final int heldTicks) {
        return heldTicks >= holdStartTicks();
    }

    /// The threshold in ticks, or 0 when this action names none.
    public int thresholdTicks() {
        return threshold.orElse(0);
    }

    /// Whether `heldTicks`, counted from the press, reached the threshold counted from the hold start.
    public boolean reachedThreshold(final int heldTicks) {
        return heldTicks - holdStartTicks() >= thresholdTicks();
    }
}
