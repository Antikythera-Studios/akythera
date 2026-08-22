package com.yesman.akythera.core.animation.playable;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.math.TimeWindow;
import org.jspecify.annotations.Nullable;

import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

/// Definition of section playback order, loops and control the playback time
public final class SectionInfo {
    public static final Codec<SectionInfo> CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                Codec.unboundedMap(Codec.STRING, Codec.FLOAT).fieldOf("sections").forGetter(SectionInfo::sections),
                Codec.STRING.listOf().listOf().fieldOf("playing_orders").forGetter(SectionInfo::playingOrders)
            )
            .apply(instance, SectionInfo::new)
    );

    private final Map<String, TimeWindow> sections;
    private final Map<String, String> nextSections;

    /// Shares same info with [#nextSections], but saved for serialization
    private final List<List<String>> playingOrders;

    public SectionInfo(final Map<String, Float> sectionsAndStartTimes, final List<List<String>> playingOrders) {
        if (sectionsAndStartTimes.containsKey(AnimationMontage.GENERIC_SECTION_NAME)) {
            throw new IllegalArgumentException(
                MessageFormat.format(
                    "Cannot use {0} for montage section name.",
                    AnimationMontage.GENERIC_SECTION_NAME
                )
            );
        }

        Map<String, TimeWindow> sectionsByPlayingOrder = new LinkedHashMap<>();

        // Defer the calculation of max animation time to avoid loading assets in registry loading stage.
        if (sectionsAndStartTimes.isEmpty()) {
            sectionsByPlayingOrder.put(AnimationMontage.GENERIC_SECTION_NAME, TimeWindow.create(0.0F, Float.MAX_VALUE));
        } else {
            List<Map.Entry<String, Float>> sectionsByOrder = sectionsAndStartTimes.entrySet().stream().sorted(Map.Entry.comparingByValue()).toList();

            for (int i = 1; i < sectionsByOrder.size(); i++) {
                float prevTime = sectionsByOrder.get(i - 1).getValue();
                float nextTime = sectionsByOrder.get(i).getValue();
                sectionsByPlayingOrder.put(sectionsByOrder.get(i - 1).getKey(), TimeWindow.create(prevTime, nextTime));
            }

            sectionsByPlayingOrder.put(sectionsByOrder.getLast().getKey(), TimeWindow.create(sectionsByOrder.getLast().getValue(), Float.MAX_VALUE));
        }

        // Derive the next-section map from ALL sequences. Core rule: one transition per section.
        // The same transition (A->B) repeated is fine; a different one (A->B and A->C) is ambiguous.
        Map<String, String> nextSections = new HashMap<>();

        for (List<String> sequence : playingOrders) {
            for (int i = 1; i < sequence.size(); i++) {
                String from = sequence.get(i - 1);
                String to = sequence.get(i);
                String existing = nextSections.get(from);

                if (existing != null && !existing.equals(to)) {
                    throw new IllegalStateException(
                        MessageFormat.format(
                            "Montage Section error: ambiguous transition from {0} — already {0}->{1}, also got {0}->{2}.",
                            from,
                            existing,
                            to
                        )
                    );
                }

                nextSections.put(from, to);
            }
        }

        this.sections = Collections.unmodifiableMap(sectionsByPlayingOrder);
        this.nextSections = Collections.unmodifiableMap(nextSections);
        // Defensive deep copy so callers can't mutate our stored sequences.
        this.playingOrders = playingOrders.stream().map(List::copyOf).toList();
    }

    public @Nullable String getSectionAt(final float time) {
        for (Map.Entry<String, TimeWindow> e : sections.entrySet()) {
            if (e.getValue().isIn(time)) return e.getKey();
        }

        return null;
    }

    public @Nullable TimeWindow getSectionTimeWindow(final String sectionName) {
        return sections.get(sectionName);
    }

    public @Nullable String getNextSection(final String currentSection) {
        return nextSections.get(currentSection);
    }

    /// For serialization
    private Map<String, Float> sections() {
        return sections.entrySet().stream()
            .filter(entry -> !entry.getKey().equals(AnimationMontage.GENERIC_SECTION_NAME))
            .map(entry -> Pair.of(entry.getKey(), entry.getValue().from()))
            .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
    }

    /// The authored play sequences. Used both for serialization and by the editor.
    /// Loops/branches all preserved (round-trips exactly).
    public List<List<String>> playingOrders() {
        return playingOrders;
    }
}
