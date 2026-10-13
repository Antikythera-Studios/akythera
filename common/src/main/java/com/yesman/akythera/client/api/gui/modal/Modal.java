package com.yesman.akythera.client.api.gui.modal;

import com.yesman.akythera.client.api.gui.AkytheraEditBox;
import org.jspecify.annotations.Nullable;

/// An interface to identify Modal widgets
///
/// Handled by [ModalAccessibleScreen#isModal] to prevent focusing
/// modal widgets in parent containers
public interface Modal {
    /// The box that filters this modal's list. Null when the modal has none.
    default @Nullable AkytheraEditBox<String> searchBox() {
        return null;
    }

    /// Picks the only row [#searchBox] left, the way a click that confirms it would. True when that
    /// confirmed a pick; false when the search left any other number of rows, or when a pick here only
    /// selects.
    default boolean confirmSoleResult() {
        return false;
    }
}
