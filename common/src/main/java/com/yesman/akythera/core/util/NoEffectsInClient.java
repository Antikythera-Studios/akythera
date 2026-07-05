package com.yesman.akythera.core.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Marked for the fields or methods that doesn't have any effect in a client side,
/// typically used for a state variables that are not synced to clients or mob AI related
/// fields.
///
/// The annotation itself has no affection in operations. It is mainly for warn empty-call
/// of those fields & methods in client that clears use cases of them.
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
public @interface NoEffectsInClient {
}
