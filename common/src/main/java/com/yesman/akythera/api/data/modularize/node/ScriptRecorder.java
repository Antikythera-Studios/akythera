package com.yesman.akythera.api.data.modularize.node;

import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/// Where every node tree the engine runs gets its [ScriptNode.ExecutionContext], and where a throw out
/// of one is reported.
///
/// A run site asks [#newContext] (a run whose record replaces the last one) or [#liveContext] (a
/// deferred sample adding to its canvas' record) for a context, runs the tree, and hands a throw to
/// [#recordFailure]:
///
/// ```java
/// ScriptNode.ExecutionContext context = ScriptRecorder.newContext(ScriptCaller.of(this, UPDATE), variables);
///
/// try {
///     graph.execute(this, context);
/// } catch (Throwable throwable) {
///     ScriptRecorder.recordFailure(context, throwable);
/// }
/// ```
///
/// Recording is off until a tool sets a watch filter ([#watch]). A context whose owner passes it fills an
/// [ExecutionRecord] as the tree runs, kept by blueprint and site. Failures are kept whether or not
/// anything is watched: the first per blueprint and site is logged, kept until [#removeFailures], and
/// handed to the failure listener.
public final class ScriptRecorder {
    /// A site inside a blueprint.
    private record Site(@Nullable ResourceKey<?> blueprint, String site) {
        private static Site of(final ScriptCaller caller) {
            return new Site(caller.blueprint(), caller.site());
        }
    }

    /// Which owners record. Null while nothing is watched.
    private static volatile @Nullable Predicate<@Nullable Object> watchFilter;

    private static volatile @Nullable Consumer<ScriptFailure> failureListener;

    /// The last run of each entry point of the watched owners.
    private static final Map<Site, ExecutionRecord> RECORDS = new ConcurrentHashMap<>();

    /// Everything the watched owners' deferred sub-trees sampled since [#clearLiveRecords], by canvas.
    private static final Map<Site, ExecutionRecord> LIVE_RECORDS = new ConcurrentHashMap<>();

    private static final Map<Site, ScriptFailure> FAILURES = new ConcurrentHashMap<>();

    private ScriptRecorder() {}

    /// A context for one run by `caller`. While `caller`'s owner is watched it records, and its record
    /// replaces the last one of the same site.
    public static ScriptNode.ExecutionContext newContext(final ScriptCaller caller, final ScriptVariables locals) {
        ExecutionRecord record = null;

        if (isWatched(caller)) {
            record = new ExecutionRecord();
            RECORDS.put(Site.of(caller), record);
        }

        return new ScriptNode.ExecutionContext(caller, locals, record);
    }

    /// A context for one sample of a deferred sub-tree. While `caller`'s owner is watched it records
    /// into its canvas' record, after the samples before it.
    public static ScriptNode.ExecutionContext liveContext(final ScriptCaller caller, final ScriptVariables locals) {
        ExecutionRecord record = isWatched(caller)
            ? LIVE_RECORDS.computeIfAbsent(Site.of(caller), _ -> new ExecutionRecord())
            : null;

        return new ScriptNode.ExecutionContext(caller, locals, record);
    }

    /// Reports `cause`, thrown out of the run `context` belongs to, and closes the run's open nodes.
    public static ScriptFailure recordFailure(final ScriptNode.ExecutionContext context, final Throwable cause) {
        ScriptFailure failure = context.fail(cause);
        ScriptCaller caller = failure.caller();

        if (FAILURES.putIfAbsent(Site.of(caller), failure) != null) {
            return failure;
        }

        Akythera.LOGGER.error(
            "Error while executing \"{}; {};\" in {}",
            caller.blueprint() == null ? "?" : caller.blueprint().identifier(), caller.site(), failure.node(), cause
        );

        Consumer<ScriptFailure> listener = failureListener;

        if (listener != null) {
            listener.accept(failure);
        }

        return failure;
    }

    /// Records the runs of every owner `filter` accepts, or of none when `filter` is null. Drops every
    /// record kept so far.
    public static void watch(final @Nullable Predicate<@Nullable Object> filter) {
        watchFilter = filter;

        RECORDS.clear();
        LIVE_RECORDS.clear();
    }

    /// The last recorded run of `blueprint` at `site`, or null.
    public static @Nullable ExecutionRecord record(final ResourceKey<?> blueprint, final String site) {
        return RECORDS.get(new Site(blueprint, site));
    }

    /// The last recorded run of each site of `blueprint`.
    public static Map<String, ExecutionRecord> records(final ResourceKey<?> blueprint) {
        return bySite(RECORDS, blueprint);
    }

    /// What `blueprint`'s deferred sub-trees sampled since [#clearLiveRecords], by canvas.
    public static Map<String, ExecutionRecord> liveRecords(final ResourceKey<?> blueprint) {
        return bySite(LIVE_RECORDS, blueprint);
    }

    public static void clearLiveRecords() {
        LIVE_RECORDS.clear();
    }

    /// The failures kept for `blueprint`, by site.
    public static Map<String, ScriptFailure> failures(final ResourceKey<?> blueprint) {
        return bySite(FAILURES, blueprint);
    }

    /// Forgets `blueprint`'s failures, so the next throw at each site is reported again.
    public static void removeFailures(final ResourceKey<?> blueprint) {
        FAILURES.keySet().removeIf(site -> blueprint.equals(site.blueprint()));
    }

    /// Receives each failure [#recordFailure] reports, on the thread the tree ran on.
    public static void setFailureListener(final @Nullable Consumer<ScriptFailure> listener) {
        failureListener = listener;
    }

    private static boolean isWatched(final ScriptCaller caller) {
        Predicate<@Nullable Object> filter = watchFilter;

        return filter != null && filter.test(caller.owner());
    }

    private static <V> Map<String, V> bySite(final Map<Site, V> kept, final ResourceKey<?> blueprint) {
        Map<String, V> out = new HashMap<>();

        kept.forEach((site, value) -> {
            if (Objects.equals(blueprint, site.blueprint())) out.put(site.site(), value);
        });

        return out;
    }
}
