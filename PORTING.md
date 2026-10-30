# Porting Guide

Breaking changes for addons and data packs, newest version first. New optional fields are listed when their defaults
keep old data unchanged.

## [Unreleased] - after 0.2.0-SNAPSHOT.5

### Play rate

| Before | After |
|---|---|
| `AnimationSequence` JSON `"rate_scale": 1.5` | `"play_rate": 1.5` |
| `AnimationSequence#rateScale()` | `AnimationSequence#playRate()` |
| `PlayableAnimation#rateScale()` (override) | `PlayableAnimation#playRate()` |

- The old `rate_scale` key is not read. A sequence that still has it plays at 1.0, without an error. Search your
  data for `rate_scale`.
- Akythera Labs saves `play_rate`. A sequence `.json` in an editor project that was saved with `rate_scale` opens at
  1.0; set Play Rate again in the Details panel.
- New, optional: `AnimationMontage` JSON `play_rate` (default 1.0), `AnimationMontage.Builder#playRate`.

### Move Vertical

| Before | After |
|---|---|
| `new AnimationSequence(notifyTrack, clip, fadeIn, fadeOut, loops, rootMotion, playRate, phaseOffset)` | `new AnimationSequence(notifyTrack, clip, fadeIn, fadeOut, loops, rootMotion, moveVertical, playRate, phaseOffset)` |
| `RootMotionComponent#setSource(transform, time)` | `RootMotionComponent#setSource(transform, time, playRate, moveVertical)` |

- Pass `true` as `moveVertical` to keep the old behavior. The short `AnimationSequence` constructors are unchanged.
- New, optional: `move_vertical` JSON key (default true) on `AnimationSequence` and `AnimationMontage`, and
  `AnimationMontage.Builder#moveVertical`. When false, root motion does not move the entity along the y axis. Only
  montages move the entity now, so the sequence value is stored for later use.
