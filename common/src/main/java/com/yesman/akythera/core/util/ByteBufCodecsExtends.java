package com.yesman.akythera.core.util;

import com.mojang.datafixers.util.Function7;
import com.mojang.datafixers.util.Function8;
import com.mojang.datafixers.util.Function9;
import com.yesman.akythera.api.asset.AssetAccessor;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Util;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/// A helper interface for more stream codec in networking, not specified in [ByteBufCodecs]
@NullMarked
public interface ByteBufCodecsExtends {
    /// Returns a stream codec for any type of [List]
    static <B extends ByteBuf, T> StreamCodec<B, List<T>> listOf(StreamCodec<B, T> elementCodec) {
        return new StreamCodec<> () {
            @Override
            public void encode(B buffer, List<T> obj) {
                buffer.writeInt(obj.size());
                obj.forEach(val -> elementCodec.encode(buffer, val));
            }

            @Override
            public List<T> decode(B buffer) {
                int size = buffer.readInt();
                List<T> list = new ArrayList<> (size);

                for (int i = 0; i < size; i++) {
                    T obj = elementCodec.decode(buffer);
                    list.add(obj);
                }

                return list;
            }
        };
    }

    /// Returns a stream codec for any type of [AssetAccessor]
    static <B extends FriendlyByteBuf, T> StreamCodec<B, AssetAccessor<T>> assetAccessor(
        StreamCodec<B, T> elementCodec,
        Function<Identifier, AssetAccessor<T>> accessorProvider
    ) {
        return new StreamCodec<> () {
            @Override
            public void encode(B buffer, AssetAccessor<T> obj) {
                buffer.writeIdentifier(obj.identifier());
            }

            @Override
            public AssetAccessor<T> decode(B buffer) {
                return accessorProvider.apply(buffer.readIdentifier());
            }
        };
    }

    /// Returns a stream codec that handles null pointers from original stream codec.
    ///
    /// @param codec A non-primitive object codec
    @SuppressWarnings("unchecked")
    static <T> StreamCodec<ByteBuf, T> ofNullable(StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        return new StreamCodec<> () {
            @Override @Nullable
            public T decode(ByteBuf buffer) {
                boolean isNull = buffer.readBoolean();

                if (isNull) {
                    return null;
                } else {
                    return ((StreamCodec<@NonNull ByteBuf, T>)codec).decode(buffer);
                }
            }

            @Override
            public void encode(ByteBuf buffer, @Nullable T object) {
                if (object == null) {
                    buffer.writeBoolean(true);
                } else {
                    buffer.writeBoolean(false);
                    ((StreamCodec<@NonNull ByteBuf, T>)codec).encode(buffer, object);
                }
            }
        };
    }

    /// Returns a stream codec for any type of [TagKey]
    static <T> StreamCodec<FriendlyByteBuf, TagKey<T>> tagKey() {
        return new StreamCodec<> () {
            @Override
            public TagKey<T> decode(FriendlyByteBuf buffer) {
                Identifier registry = buffer.readIdentifier();
                Identifier tagName = buffer.readIdentifier();
                return TagKey.create(ResourceKey.createRegistryKey(registry), tagName);
            }

            @Override
            public void encode(FriendlyByteBuf buffer, TagKey<T> tagKey) {
                buffer.writeIdentifier(tagKey.registry().identifier());
                buffer.writeIdentifier(tagKey.location());
            }
        };
    }

    /// Stream codec for any type of [ResourceKey]
    static <T> StreamCodec<RegistryFriendlyByteBuf, ResourceKey<T>> getResourceKey(ResourceKey<? extends Registry<T>> registryKey) {
        return new StreamCodec<> () {
            @Override
            public void encode(RegistryFriendlyByteBuf buffer, ResourceKey<T> obj) {
                buffer.writeResourceKey(obj);
            }

            @Override
            public ResourceKey<T> decode(RegistryFriendlyByteBuf buffer) {
                return buffer.readResourceKey(registryKey);
            }
        };
    }

    /// Stream codec for [Vec3]
    StreamCodec<ByteBuf, Vec3> VEC3 = new StreamCodec<> () {
        @Override
        public void encode(ByteBuf buffer, Vec3 obj) {
            buffer.writeDouble(obj.x);
            buffer.writeDouble(obj.y);
            buffer.writeDouble(obj.z);
        }

        @Override
        public Vec3 decode(ByteBuf buffer) {
            return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        }
    };

    /// Stream codec for [GameRule]
    /*
    StreamCodec<ByteBuf, EpicFightGameRules.KeyValuePair> GAME_RULE = new StreamCodec<> () {
        @Override
        public void encode(ByteBuf buffer, EpicFightGameRules.KeyValuePair obj) {
            Utf8String.write(buffer, obj.gamerule().getRuleName(), 32767);
            obj.gamerule().getRuleType().codec().encode(buffer, obj.value());
        }

        @SuppressWarnings("unchecked")
        @Override
        public EpicFightGameRules.KeyValuePair decode(ByteBuf buffer) {
            String ruleName = Utf8String.read(buffer, 32767);
            var gameRule = (EpicFightGameRules.ConfigurableGameRule<Object, ?, ?>)EpicFightGameRules.GAME_RULES.get(ruleName);
            Object value = gameRule.getRuleType().codec().decode(buffer);
            return new EpicFightGameRules.KeyValuePair(gameRule, value);
        }
    };
    */

    /// Stream codecs by enums. Avoided using [Enum#values] since it returns newly allocated arrays in each call
    ///
    /// Use [#enums] to get a type-casted stream codec
    Function<Class<? extends Enum<?>>, StreamCodec<? extends ByteBuf, ? extends Enum<?>>> ENUM = Util.memoize(
        enumCls -> {
            try {
                // Made an outer codec to declare constructor
                return new EnumCodec<> (enumCls);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException | InvocationTargetException e) {
                throw new IllegalArgumentException("Not a valid enum class " + enumCls.getSimpleName(), e);
            }
        });

    /// A stream codec that stores enum array and encode/decode enums by ordinal.
    class EnumCodec<B extends ByteBuf, T extends Enum<T>> implements StreamCodec<B, T> {
        private final T[] enumValues;

        @SuppressWarnings("unchecked")
        private EnumCodec(Class<? extends Enum<?>> enumType)
            throws NoSuchMethodException, SecurityException, IllegalAccessException, InvocationTargetException
        {
            Method valuesInvoker = enumType.getMethod("values");
            enumValues = (T[]) valuesInvoker.invoke(null);
        }

        @Override
        public void encode(B buffer, T value) {
            buffer.writeInt(value.ordinal());
        }

        @Override
        public T decode(B buffer) {
            return enumValues[buffer.readInt()];
        }
    }

    /// Returns a typed enum codec for a given enum class
    @SuppressWarnings("unchecked")
    static <B extends ByteBuf, T extends Enum<T>> StreamCodec<B, T> enums(Class<T> enumCls) {
        return (StreamCodec<B, T>)ENUM.apply(enumCls);
    }

    /// Stream codecs for any [EnumerableDataBlock]. Distinguished by [EnumerableDataBlockManager] that are declared by each enum interface
    ///
    /// Use [#enumerableDataBlock] for typed stream codec.
    Function<EnumerableDataBlockManager<? extends EnumerableDataBlock>, StreamCodec<? extends ByteBuf, ? extends EnumerableDataBlock>> ENUMERABLE_DATA_BLOCK =
        Util.memoize(enumManager ->
            new StreamCodec<> () {
                @Override
                public void encode(ByteBuf buffer, EnumerableDataBlock obj) {
                    buffer.writeInt(obj.universalOrdinal());
                }

                @Override
                public EnumerableDataBlock decode(ByteBuf buffer) {
                    return enumManager.getOrThrow(buffer.readInt());
                }
            }
        );

    @SuppressWarnings("unchecked")
    static <B extends ByteBuf, T extends EnumerableDataBlock> StreamCodec<B, T> enumerableDataBlock(EnumerableDataBlockManager<T> dataBlockManager) {
        return (StreamCodec<B, T>) ENUMERABLE_DATA_BLOCK.apply(dataBlockManager);
    }

    static <B, C, T1, T2, T3, T4, T5, T6, T7> StreamCodec<B, C> composite7(
        final StreamCodec<? super B, T1> codec1,
        final Function<C, T1> getter1,
        final StreamCodec<? super B, T2> codec2,
        final Function<C, T2> getter2,
        final StreamCodec<? super B, T3> codec3,
        final Function<C, T3> getter3,
        final StreamCodec<? super B, T4> codec4,
        final Function<C, T4> getter4,
        final StreamCodec<? super B, T5> codec5,
        final Function<C, T5> getter5,
        final StreamCodec<? super B, T6> codec6,
        final Function<C, T6> getter6,
        final StreamCodec<? super B, T7> codec7,
        final Function<C, T7> getter7,
        final Function7<T1, T2, T3, T4, T5, T6, T7, C> factory
    ) {
        return new StreamCodec<>() {
            @Override
            public C decode(B p_330310_) {
                T1 t1 = codec1.decode(p_330310_);
                T2 t2 = codec2.decode(p_330310_);
                T3 t3 = codec3.decode(p_330310_);
                T4 t4 = codec4.decode(p_330310_);
                T5 t5 = codec5.decode(p_330310_);
                T6 t6 = codec6.decode(p_330310_);
                T7 t7 = codec7.decode(p_330310_);
                return factory.apply(t1, t2, t3, t4, t5, t6, t7);
            }

            @Override
            public void encode(B p_332052_, C p_331912_) {
                codec1.encode(p_332052_, getter1.apply(p_331912_));
                codec2.encode(p_332052_, getter2.apply(p_331912_));
                codec3.encode(p_332052_, getter3.apply(p_331912_));
                codec4.encode(p_332052_, getter4.apply(p_331912_));
                codec5.encode(p_332052_, getter5.apply(p_331912_));
                codec6.encode(p_332052_, getter6.apply(p_331912_));
                codec7.encode(p_332052_, getter7.apply(p_331912_));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5, T6, T7, T8> StreamCodec<B, C> composite8(
        final StreamCodec<? super B, T1> codec1,
        final Function<C, T1> getter1,
        final StreamCodec<? super B, T2> codec2,
        final Function<C, T2> getter2,
        final StreamCodec<? super B, T3> codec3,
        final Function<C, T3> getter3,
        final StreamCodec<? super B, T4> codec4,
        final Function<C, T4> getter4,
        final StreamCodec<? super B, T5> codec5,
        final Function<C, T5> getter5,
        final StreamCodec<? super B, T6> codec6,
        final Function<C, T6> getter6,
        final StreamCodec<? super B, T7> codec7,
        final Function<C, T7> getter7,
        final StreamCodec<? super B, T8> codec8,
        final Function<C, T8> getter8,
        final Function8<T1, T2, T3, T4, T5, T6, T7, T8, C> factory
    ) {
        return new StreamCodec<>() {
            @Override
            public C decode(B p_330310_) {
                T1 t1 = codec1.decode(p_330310_);
                T2 t2 = codec2.decode(p_330310_);
                T3 t3 = codec3.decode(p_330310_);
                T4 t4 = codec4.decode(p_330310_);
                T5 t5 = codec5.decode(p_330310_);
                T6 t6 = codec6.decode(p_330310_);
                T7 t7 = codec7.decode(p_330310_);
                T8 t8 = codec8.decode(p_330310_);
                return factory.apply(t1, t2, t3, t4, t5, t6, t7, t8);
            }

            @Override
            public void encode(B p_332052_, C p_331912_) {
                codec1.encode(p_332052_, getter1.apply(p_331912_));
                codec2.encode(p_332052_, getter2.apply(p_331912_));
                codec3.encode(p_332052_, getter3.apply(p_331912_));
                codec4.encode(p_332052_, getter4.apply(p_331912_));
                codec5.encode(p_332052_, getter5.apply(p_331912_));
                codec6.encode(p_332052_, getter6.apply(p_331912_));
                codec7.encode(p_332052_, getter7.apply(p_331912_));
                codec8.encode(p_332052_, getter8.apply(p_331912_));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5, T6, T7, T8, T9> StreamCodec<B, C> composite9(
        final StreamCodec<? super B, T1> codec1,
        final Function<C, T1> getter1,
        final StreamCodec<? super B, T2> codec2,
        final Function<C, T2> getter2,
        final StreamCodec<? super B, T3> codec3,
        final Function<C, T3> getter3,
        final StreamCodec<? super B, T4> codec4,
        final Function<C, T4> getter4,
        final StreamCodec<? super B, T5> codec5,
        final Function<C, T5> getter5,
        final StreamCodec<? super B, T6> codec6,
        final Function<C, T6> getter6,
        final StreamCodec<? super B, T7> codec7,
        final Function<C, T7> getter7,
        final StreamCodec<? super B, T8> codec8,
        final Function<C, T8> getter8,
        final StreamCodec<? super B, T9> codec9,
        final Function<C, T9> getter9,
        final Function9<T1, T2, T3, T4, T5, T6, T7, T8, T9, C> factory
    ) {
        return new StreamCodec<>() {
            @Override
            public C decode(B p_330310_) {
                T1 t1 = codec1.decode(p_330310_);
                T2 t2 = codec2.decode(p_330310_);
                T3 t3 = codec3.decode(p_330310_);
                T4 t4 = codec4.decode(p_330310_);
                T5 t5 = codec5.decode(p_330310_);
                T6 t6 = codec6.decode(p_330310_);
                T7 t7 = codec7.decode(p_330310_);
                T8 t8 = codec8.decode(p_330310_);
                T9 t9 = codec9.decode(p_330310_);
                return factory.apply(t1, t2, t3, t4, t5, t6, t7, t8, t9);
            }

            @Override
            public void encode(B p_332052_, C p_331912_) {
                codec1.encode(p_332052_, getter1.apply(p_331912_));
                codec2.encode(p_332052_, getter2.apply(p_331912_));
                codec3.encode(p_332052_, getter3.apply(p_331912_));
                codec4.encode(p_332052_, getter4.apply(p_331912_));
                codec5.encode(p_332052_, getter5.apply(p_331912_));
                codec6.encode(p_332052_, getter6.apply(p_331912_));
                codec7.encode(p_332052_, getter7.apply(p_331912_));
                codec8.encode(p_332052_, getter8.apply(p_331912_));
                codec9.encode(p_332052_, getter9.apply(p_331912_));
            }
        };
    }
}
