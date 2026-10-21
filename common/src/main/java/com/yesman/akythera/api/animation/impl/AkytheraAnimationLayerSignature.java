package com.yesman.akythera.api.animation.impl;

import com.yesman.akythera.core.animation.AnimationLayerSignature;

public enum AkytheraAnimationLayerSignature implements AnimationLayerSignature {
    // Base layer covers all bones
    BASE,
    // Extra layer that controls upper body bones. Typically used for item use animations
    UPPER_BODY,
    ;

    final int id;

    AkytheraAnimationLayerSignature() {
        this.id = AnimationLayerSignature.ENUM_MANAGER.assign(this);
    }

    @Override
    public int universalOrdinal() {
        return this.id;
    }
}
