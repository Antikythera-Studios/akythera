package com.yesman.akythera.client.gui.widget.common;

import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.EnumerableDataBlockManager;

/// Enum interface for widget styles
public interface WidgetTheme extends EnumerableDataBlock {
    EnumerableDataBlockManager<WidgetTheme> ENUM_MANAGER = new EnumerableDataBlockManager<>("widget_theme");

    @Override
    default EnumerableDataBlockManager<? extends EnumerableDataBlock> getEnumManager() {
        return ENUM_MANAGER;
    }
}
