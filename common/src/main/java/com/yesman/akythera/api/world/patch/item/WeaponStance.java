package com.yesman.akythera.api.world.patch.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.reflection.ScriptType;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.EnumerableDataBlockManager;
import net.minecraft.resources.Identifier;

/// Separation identifier of [ItemPatch]. Notifies how the holder handles the item by various
/// conditions like item in offhand, skill activation.
@ScriptType(autoRegister = false)
public interface WeaponStance extends EnumerableDataBlock {
	EnumerableDataBlockManager<WeaponStance> ENUM_MANAGER = new EnumerableDataBlockManager<>(
        "weapon_stance",
        RecordCodecBuilder.mapCodec(
            instance ->
                instance.group(
                    Codec.BOOL.fieldOf("offhand_free").forGetter(WeaponStance::isOffhandFree)
                )
                .apply(instance, offhandFree -> new WeaponStance() {
                    @Override
                    public int universalOrdinal() {
                        return ENUM_MANAGER.derefOrdinal(this);
                    }

                    @Override
                    public boolean isOffhandFree() {
                        return offhandFree;
                    }

                    @Override
                    public String toString() {
                        Identifier enumName = ENUM_MANAGER.derefIdentifier(this);
                        return enumName == null ? "WeaponStance" : enumName.toString();
                    }
                })
        )
    );

    @Override
    default EnumerableDataBlockManager<WeaponStance> getEnumManager() {
        return ENUM_MANAGER;
    }

    /// Returns whether the holder can use items in offhand while in the stance
	boolean isOffhandFree();
}
