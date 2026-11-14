package com.yesman.akythera.client.api.gui.modal;

import com.yesman.akythera.client.api.gui.AkytheraEditBox;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// An interface to identify Modal widgets
///
/// Handled by [ModalAccessibleScreen#isModal] to prevent focusing
/// modal widgets in parent containers
public interface Modal {
    /// The box that filters this modal's list. Null when the modal has none.
    default @Nullable AkytheraEditBox<String> searchBox() {
        return null;
    }

    /// The rows [#searchBox] leaves that a pick can confirm, in list order, each by the text naming it.
    default List<String> results() {
        return List.of();
    }

    /// Picks the row of [#results] at `index`, the way a click that confirms it would. False when `index` is out
    /// of range, or when a pick here only selects.
    default boolean confirmResult(int index) {
        return false;
    }

    /// [#confirmResult] on the only row [#searchBox] left. False when it left any other number of rows.
    default boolean confirmSoleResult() {
        return results().size() == 1 && confirmResult(0);
    }
}
