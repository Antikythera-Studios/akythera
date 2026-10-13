package com.yesman.akythera.client.api.event.gui;

import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.core.event.Event;
import net.minecraft.client.gui.components.AbstractWidget;

/// Fired by [ModalManager] when a modal joins or leaves the modal stack.
public abstract class ModalEvent extends Event {
    private final AbstractWidget modal;

    public ModalEvent(final AbstractWidget modal) {
        this.modal = modal;
    }

    public final AbstractWidget getModal() {
        return modal;
    }

    /// After the modal is pushed on top of the stack.
    public static final class Opened extends ModalEvent {
        public Opened(final AbstractWidget modal) {
            super(modal);
        }
    }

    /// After the modal is popped off the stack and deactivated.
    public static final class Closed extends ModalEvent {
        public Closed(final AbstractWidget modal) {
            super(modal);
        }
    }
}
