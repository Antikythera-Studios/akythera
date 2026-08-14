package com.yesman.akythera.fabric.initializer;

import com.yesman.akythera.api.lifecycle.ComponentsFactory;

@FunctionalInterface
public interface ComponentFactoryInitializer {
    void onFactoryInitialized(ComponentsFactory factory);
}
