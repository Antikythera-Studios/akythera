package com.yesman.akythera.api.asset;

import net.minecraft.resources.Identifier;

/// Singleton pattern accessor
///
/// Useful for non synced and compile time assets, (empty or default)
public class SingletonAccessor<T> implements AssetAccessor<T> {
    /// Beware that it can't be used in syncing. Do not put singleton accessor
    /// where it has a chance to be called by network packet or codecs.
    protected final Identifier identifier;
    protected final T asset;

    private final Type type;

    public SingletonAccessor(final Identifier identifier, final T asset, final Type type) {
        this.identifier = identifier;
        this.asset = asset;
        this.type = type;
    }

    @Override
    public T get() {
        return asset;
    }

    @Override
    public Type type() {
        return type;
    }

    @Override
    public Identifier identifier() {
        return identifier;
    }
}
