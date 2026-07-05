/// Contains core drivers, algorithms, mechanisms that are not meant
/// to be exposed as an API. Developers should rather depend on modules
/// in `api` package, which are the implementations of the core package.
///
/// The modules are still stable and less changed but Akythera studio
/// doesn't ensure stability and follow-up supports for future changes.
///
/// Any custom inheritances or implementations of modules in 'core' package
/// might not work with data serialization system. (A Representative example
/// is `PlayableAnimation`. Any custom classes that implements the interface
/// won't be supported by Minecraft's DFU system.
///
/// @see com.yesman.akythera.core.animation.playable.PlayableAnimation#tagOf

package com.yesman.akythera.core;