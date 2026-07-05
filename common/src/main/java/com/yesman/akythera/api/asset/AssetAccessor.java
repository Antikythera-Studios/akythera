package com.yesman.akythera.api.asset;

import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.core.asset.loader.ResourceLoadingFailureException;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// A referencer of assets distinguished by [Identifier]. But the loading time is
/// when the first referencing is occurred.
///
/// Typically, the cached assets should be reset when resource reloading (F3+T)
/// is triggered. The newly created assets are returned by [#get]
///
/// Useful for heavy binary assets like meshes, armatures, and animations
public interface AssetAccessor<T> extends Supplier<T> {
    /// Returns a referencing asset and if the asset is not loaded, it tries to
    /// load an asset.
    ///
    /// Consider deeply where to call this method. If it's called on the asset
    /// loading stage all assets are allocated to memory at the beginning, losing
    /// the accessor's innate meaning of delegating asset loading on demand.
    ///
    /// @return nullable, but we do not ensure
    ///
    /// @exception ResourceLoadingFailureException An exception thrown while trying
    ///                                            to read asset from specified path.
    ///                                            Needs proper handler or use [#getOrHandleException]
    T get() throws ResourceLoadingFailureException;

    /// Returns which type of asset this accessor holds
    Type type();

    /// Safe asset getter
    default @Nullable T getOrHandleException(Consumer<ResourceLoadingFailureException> exceptionHandler) {
        try {
            return get();
        } catch (ResourceLoadingFailureException e) {
            exceptionHandler.accept(e);
            return null;
        }
    }

    Identifier identifier();

    /// Checks whether the resource file is present
    ///
    /// This won't check the validity of the resource file. Even if the resource
    /// throws an error when loading, this method returns true. Only a case that
    /// file doesn't exist, it throws false.
    default boolean isResourcePresent() {
        return ComponentsFactory.getImpl().getResourceManager().getResource(identifier()).isPresent();
    }

    /// Checks the referencing asset exists
    ///
    /// Null when some exceptions occurred while reading asset file.
    ///
    /// It also tries referencing the asset, so you can't use this method
    /// just for "checking" if the asset is loaded or not.
    default boolean isPresent() {
        return get() != null;
    }

    /// Checks if the referencing asset doesn't exist
    ///
    /// Has same effect as [#isPresent]
    default boolean isEmpty() {
        return !isPresent();
    }

    /// Checks the type compatibility of the accessor without loading assets
    default boolean checkType(final Type type) {
        return type() == type;
    }

    /// Returns the asset of @param whenNull if it's empty
    default T orElse(T whenNull) {
        return isPresent() ? get() : whenNull;
    }

    /// Do action if the asset exists
    default void ifPresent(Consumer<T> action) {
        if (isPresent()) {
            action.accept(get());
        }
    }

    /// Do action if the asset exists, or execute @param whenNull if it's null
    default void ifPresentOrElse(Consumer<T> action, Runnable whenNull) {
        if (isPresent()) {
            action.accept(get());
        } else {
            whenNull.run();
        }
    }

    /// Try accessing the asset and throw exception if it's null. If not, execute the given task
    default void doOrThrow(Consumer<T> action) throws NoSuchElementException {
        checkNotNull();
        action.accept(get());
    }

    /// Try accessing the asset and throw exception if it's null
    default void checkNotNull() throws NoSuchElementException {
        if (!isPresent()) {
            throw new NoSuchElementException("[AssetAccessor] The referencing asset " + identifier() + " is null.");
        }
    }

    enum Type {
        STATIC_MESH, SKELETAL_MESH, ARMATURE, ANIMATION
    }
}
