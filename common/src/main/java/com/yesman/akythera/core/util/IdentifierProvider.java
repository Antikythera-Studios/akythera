package com.yesman.akythera.core.util;

import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.function.Function;

/// A utility interface to specify an owner of a module
///
/// Commonly used for attach/detach things in a runtime
public interface IdentifierProvider {
    Function<Identifier, IdentifierProvider> CONSTANT_IDENTIFIER_PROVIDERS = Util.memoize(identifier -> {
        return new IdentifierProvider() {
            final Identifier constIdentifier = identifier;

            @Override
            public Identifier getId() {
                return this.constIdentifier;
            }
        };
    });

    static IdentifierProvider constant(Identifier constantIdentifier) {
        return CONSTANT_IDENTIFIER_PROVIDERS.apply(constantIdentifier);
    }

    static IdentifierProvider constant(String constantIdentifier) {
        return constant(Identifier.parse(constantIdentifier));
    }

    IdentifierProvider PERMANENT_LISTENER = constant("permanent_subscriber");;

    static IdentifierProvider permanent() {
        return PERMANENT_LISTENER;
    }

    Identifier getId();

    default String getStringId() {
        return this.getId().toString();
    }
}
