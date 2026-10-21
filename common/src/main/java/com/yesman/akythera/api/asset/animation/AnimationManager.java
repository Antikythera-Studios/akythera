package com.yesman.akythera.api.asset.animation;

import com.mojang.serialization.Codec;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.DynamicAssetManager;
import com.yesman.akythera.api.asset.SingletonAccessor;
import com.yesman.akythera.api.asset.armature.ArmatureManager;
import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.animation.keyframe.AnimationMirror;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.asset.loader.AnimationLoader;
import com.yesman.akythera.core.asset.loader.AssetLoader;
import com.yesman.akythera.core.asset.loader.ResourceLoadingFailureException;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public final class AnimationManager extends DynamicAssetManager<@NonNull AnimationClip> {
    public static final AssetAccessor<AnimationClip> EMPTY_ANIMATION_ACCESSOR = new SingletonAccessor<> (
        Identifier.fromNamespaceAndPath(Akythera.MODID, "empty"),
        AnimationClip.builder().build(),
        AssetAccessor.Type.ANIMATION
    );

    public static final String DIRECTORY = "animmodels/animations/";

    public static final Codec<AssetAccessor<AnimationClip>> CODEC =
        Identifier.CODEC.xmap(
            AnimationManager::accessor,
            accessor -> trimDirectory(accessor.identifier())
        );

    public static final AnimationManager INSTANCE = new AnimationManager();

    /// Identifiers whose clip is being read right now. An additive clip reads its reference pose
    /// mid-load, so the registry is re-entered while one entry is still being produced.
    private final Set<Identifier> loading = new HashSet<>();

    public static AnimationManager getInstance() {
        return INSTANCE;
    }

    private AnimationManager() {}

    /// An animation identifier as [DynamicAssetManager#issuedAccessor] keys it — the id with the
    /// asset directory trimmed off.
    public static Identifier trimDirectory(final Identifier identifier) {
        return Identifier.fromNamespaceAndPath(identifier.getNamespace(), identifier.getPath().replace(DIRECTORY, ""));
    }

    /// The mirrored twin of `animation`, under the source identifier plus
    /// [AnimationMirror#IDENTIFIER_SUFFIX]. Issued once and kept in
    /// [DynamicAssetManager#issuedAccessor], so the sidecar is read on the first call only.
    ///
    /// An animation whose `.akmeta` leaves `enable_mirroring` off, or names no vendor armature,
    /// registers `animation` itself under the mirrored identifier and is handed straight back. A
    /// mirror answers what it reflects.
    @SuppressWarnings("unchecked")
    public static AssetAccessor<AnimationClip> getMirrorAnimation(final AssetAccessor<AnimationClip> animation) {
        if (animation instanceof MirrorAnimationAccessor mirrored) {
            return mirrored.source();
        }

        Identifier sourceKey = trimDirectory(animation.identifier());
        Identifier mirrorKey = Identifier.fromNamespaceAndPath(
            sourceKey.getNamespace(), sourceKey.getPath() + AnimationMirror.IDENTIFIER_SUFFIX);
        AssetAccessor<? extends AnimationClip> issued = INSTANCE.issuedAccessor.get(mirrorKey);

        if (issued != null) {
            return (AssetAccessor<AnimationClip>)issued;
        }

        AnimationMirror.Setup setup = AnimationMirror.readSetup(
            animation.identifier(), ComponentsFactory.getImpl().getResourceManager());

        if (!setup.isMirroring()) {
            INSTANCE.issuedAccessor.put(mirrorKey, animation);

            return animation;
        }

        AssetAccessor<AnimationClip> mirror = new MirrorAnimationAccessor(
            Identifier.fromNamespaceAndPath(
                animation.identifier().getNamespace(),
                animation.identifier().getPath() + AnimationMirror.IDENTIFIER_SUFFIX
            ),
            animation,
            setup.vendor()
        );

        INSTANCE.issuedAccessor.put(mirrorKey, mirror);

        return mirror;
    }

    /// Returns an animation accessor by animation location
    @SuppressWarnings("unchecked")
    public static AssetAccessor<AnimationClip> accessor(Identifier path) throws IllegalArgumentException {
        // Hardcode empty animation accessor path
        if (path.equals(EMPTY_ANIMATION_ACCESSOR.identifier())) {
            return EMPTY_ANIMATION_ACCESSOR;
        }

        return (AssetAccessor<AnimationClip>)INSTANCE.issuedAccessor.computeIfAbsent(
            path,
            _ ->
                new AnimationAccessorImpl(
                    Identifier.fromNamespaceAndPath(path.getNamespace(), DIRECTORY + path.getPath()),
                    path.getPath().contains("/")
                        // issuedAccessor of ArmatureManager is keyed by the FULL prefixed id,
                        // unlike this manager's trimmed keys — look up with the prefix re-added
                        ? ArmatureManager.getInstance().getRegisteredAccessorById(
                            Identifier.fromNamespaceAndPath(
                                path.getNamespace(),
                                ArmatureManager.DIRECTORY + path.getPath().substring(0, path.getPath().indexOf("/"))
                            )
                        )
                        : null
                )
        );
    }

    /// Returns an animation accessor with explicit armature parameter that is used on generating the animation in Blender.
    @SuppressWarnings("unchecked")
    public static AssetAccessor<AnimationClip> accessor(String namespace, String animPath, AssetAccessor<? extends Armature> armature) {
        Identifier fullPath = Identifier.fromNamespaceAndPath(
            namespace,
            armature.identifier().getPath().replaceAll("armatures/", "animations/") + "/" + animPath
        );

        return (AssetAccessor<AnimationClip>)INSTANCE.issuedAccessor.computeIfAbsent(
            Identifier.fromNamespaceAndPath(fullPath.getNamespace(), fullPath.getPath().replaceAll(DIRECTORY, "")),
            _ -> new AnimationAccessorImpl(fullPath, armature)
        );
    }

    /// Accessor for animation data stored in [AnimationClip]
    ///
    /// Implements [PlayableAnimation] so that it can be used as
    /// a raw animation source with only default animation metadata.
    ///
    /// @param armature typically null for binary files, exist
    private record AnimationAccessorImpl(
        Identifier identifier,
        @Nullable AssetAccessor<? extends Armature> armature
    ) implements AssetAccessor<AnimationClip> {
        @Override
        public AnimationClip get() {
            // A cached null is a load that already failed — kept until the game reloads, so the
            // broken file is not re-read on every reference.
            if (INSTANCE.loadedObjectsRegistry.containsKey(identifier)) {
                return INSTANCE.loadedObjectsRegistry.get(identifier);
            }

            if (!INSTANCE.loading.add(identifier)) {
                throw new ResourceLoadingFailureException(
                    identifier, "Reference pose cycle: this animation reaches itself through 'ref_pose_provider'.");
            }

            try {
                AnimationClip clip = AnimationLoader
                    .getLoader(identifier, ComponentsFactory.getImpl().getResourceManager(), AssetLoader::readMetadata)
                    .loadAnimationClip(armature);

                INSTANCE.loadedObjectsRegistry.put(identifier, clip);

                return clip;
            } catch (ResourceLoadingFailureException e) {
                // leave null object for given identifier. live until the game is reloaded
                INSTANCE.loadedObjectsRegistry.put(identifier, null);
                throw e;
            } finally {
                INSTANCE.loading.remove(identifier);
            }
        }

        @Override
        public Type type() {
            return Type.ANIMATION;
        }

        @Override
        public @NonNull String toString() {
            return identifier.toString();
        }

        @Override
        public int hashCode() {
            return identifier.hashCode();
        }

        @SuppressWarnings("rawtypes")
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (obj instanceof AssetAccessor other) {
                return identifier.equals(other.identifier());
            } else if (obj instanceof Identifier rl) {
                return identifier.equals(rl);
            } else if (obj instanceof String name) {
                return identifier.toString().equals(name);
            } else {
                return false;
            }
        }
    }

    /// Accessor for the mirrored twin of another animation. The clip is baked on the first
    /// reference into [DynamicAssetManager#loadedObjectsRegistry], so a resource reload drops it
    /// like any other asset and the next reference re-bakes it.
    private record MirrorAnimationAccessor(
        Identifier identifier,
        AssetAccessor<AnimationClip> source,
        AssetAccessor<? extends Armature> vendor
    ) implements AssetAccessor<AnimationClip> {
        @Override
        public AnimationClip get() {
            return INSTANCE.loadedObjectsRegistry.computeIfAbsent(
                identifier, _ -> AnimationMirror.mirror(source.get(), vendor.get()));
        }

        @Override
        public Type type() {
            return Type.ANIMATION;
        }

        @Override
        public boolean isResourcePresent() {
            return source.isResourcePresent();
        }

        @Override
        public @NonNull String toString() {
            return identifier.toString();
        }

        @Override
        public int hashCode() {
            return identifier.hashCode();
        }

        @SuppressWarnings("rawtypes")
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (obj instanceof AssetAccessor other) {
                return identifier.equals(other.identifier());
            } else if (obj instanceof Identifier rl) {
                return identifier.equals(rl);
            } else if (obj instanceof String name) {
                return identifier.toString().equals(name);
            } else {
                return false;
            }
        }
    }
}
