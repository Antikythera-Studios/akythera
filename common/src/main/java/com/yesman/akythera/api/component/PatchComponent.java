package com.yesman.akythera.api.component;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// A static, non-instantiated per owners component. Shared throughout blueprint instances.
public interface PatchComponent {
    /// Merges `overwrite` into this component, both being the same type. The default keeps nothing
    /// of this component and returns `overwrite`.
    default PatchComponent overwrite(final PatchComponent overwrite) {
        return overwrite;
    }

    /// What [#fold] pairs this component by across hosts. Null pairs on the name the host attaches
    /// it under; a [UniqueComponent] pairs on its own type, one of which a host may hold.
    default @Nullable Object mergeKey() {
        return this instanceof UniqueComponent ? getClass() : null;
    }

    /// Folds `layers` into one component map, a later layer laid over the earlier ones. Two
    /// components sharing a [#mergeKey] meet in the slot the earlier claimed and resolve through
    /// [#overwrite].
    ///
    /// A slot is claimed at most once per layer, so a layer declaring one merge key twice keeps both
    /// under their own names and reaches [ComponentAttachable#indexUnique] as the duplicate it is.
    static Map<String, PatchComponent> fold(final List<? extends Map<String, ? extends PatchComponent>> layers) {
        Map<String, PatchComponent> folded = new LinkedHashMap<>();
        Map<Object, String> slotsByMergeKey = new HashMap<>();

        for (Map<String, ? extends PatchComponent> layer : layers) {
            Set<String> claimedByLayer = new HashSet<>();

            layer.forEach((name, component) -> {
                Object mergeKey = component.mergeKey();
                String claimed = mergeKey == null ? null : slotsByMergeKey.get(mergeKey);
                String slot = claimed != null && !claimedByLayer.contains(claimed) ? claimed : name;

                if (mergeKey != null) {
                    slotsByMergeKey.putIfAbsent(mergeKey, slot);
                    claimedByLayer.add(slot);
                }

                folded.merge(slot, component, PatchComponent::overwrite);
            });
        }

        return folded;
    }
}
