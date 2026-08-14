/// Backend source code of API—drivers, algorithms, mechanisms that are
/// not meant to be exposed to addons that depend on Akythera. Developers
/// rather use source code in `api` package—the implementations of `core`
/// package.
///
/// The modules are still expected to be stable and unchanged, but not
/// granted—Use it at your own risk.
///
/// Any custom inheritances or implementations of modules in 'core' package
/// are not subject of data serialization that Akythera grants.
/// [com.yesman.akythera.core.animation.playable.PlayableAnimation] for example,
/// any custom class that implements the interface should implement custom
/// [com.mojang.serialization.Codec].

package com.yesman.akythera.client.core;