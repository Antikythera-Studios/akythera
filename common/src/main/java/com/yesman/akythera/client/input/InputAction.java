package com.yesman.akythera.client.input;

import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.EnumerableDataBlockManager;
import net.minecraft.client.KeyMapping;
import org.jspecify.annotations.Nullable;

public interface InputAction extends EnumerableDataBlock {
    EnumerableDataBlockManager<InputAction> ENUM_MANAGER = new EnumerableDataBlockManager<>("input_action");

    @Override
    default EnumerableDataBlockManager<? extends EnumerableDataBlock> getEnumManager() {
        return ENUM_MANAGER;
    }

    /// Input activation type
    InputActivation inputActivation();

    /// Mapped key on this input action
    KeyMapping keyMapping();

    /// Threshold time to judge input finished/canceled
    @Nullable Integer threshold();
}
