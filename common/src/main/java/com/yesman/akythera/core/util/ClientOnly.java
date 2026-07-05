package com.yesman.akythera.core.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Marked to fields and methods that have potential issue if they're called
/// in a dedicated server, to avoid an indirect referencing of vanilla classes
/// that server side accessing is prevented by each mod loader.
///
/// Neoforge's "@OnlyIn", and Fabric's "@Environment" marks all client side vanilla
/// modules and when classloader find them in opposite side, the game crashes with
/// invalid dist exception.
///
/// This annotation doesn't have any direct effect. it's only for marking the danger
/// fields or methods from each side to ease debugging when developers actually meet
/// such crashes.
///
/// This shouldn't necessarily be marked to the fields and methods that belong to
/// classes under `*.client.*` packages since they already ensure the class is only
/// referenced on the client side.
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
public @interface ClientOnly {
}
