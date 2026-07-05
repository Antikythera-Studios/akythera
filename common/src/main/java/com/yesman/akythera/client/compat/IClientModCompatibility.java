package com.yesman.akythera.client.compat;

import com.yesman.akythera.compat.IModCompatibility;

/// Each module for mod compatibility code is managed in independent packages
/// to avoid code crash and keep coherence
///
/// For common side code (especially logical server side) should implement [IModCompatibility]
public interface IClientModCompatibility {
    void onInitializeClient();
}
