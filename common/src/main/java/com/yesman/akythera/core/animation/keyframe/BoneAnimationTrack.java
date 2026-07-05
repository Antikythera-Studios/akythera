package com.yesman.akythera.core.animation.keyframe;

import com.yesman.akythera.core.math.Transform;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.function.BiConsumer;

public record BoneAnimationTrack(List<Keyframe> keyframes) {
	public BoneAnimationTrack() {
		this(List.of());
	}

	public BoneAnimationTrack(Keyframe[] keyframes) {
		this(List.of(keyframes));
	}

    public int length() {
        return keyframes.size();
    }

    public float lastTime() {
        return keyframes().getLast().timeStamp();
    }

	public BoneAnimationTrack deepCopy() {
		return slice(0, keyframes.size());
	}

    public void forEach(BiConsumer<Integer, Keyframe> task) {
        forEach(task, 0, keyframes.size());
    }

    public void forEach(BiConsumer<Integer, Keyframe> task, int start, int end) {
        end = Math.min(end, keyframes.size());

        for (int i = start; i < end; i++) {
            task.accept(i, keyframes.get(i));
        }
    }

    /// Returns a transform sheet with keyframes between @param start and @param end - 1
	public BoneAnimationTrack slice(int start, int end) {
		int len = end - start;
		List<Keyframe> newKeyframes = new ArrayList<>(len);

		for (int i = 0; i < len; i++) {
			Keyframe kf = keyframes.get(i + start);
            newKeyframes.add(Keyframe.deepCopy(kf));
		}

		return new BoneAnimationTrack(Collections.unmodifiableList(newKeyframes));
	}

    /// Returns an interpolated transform in a specific time
    public Transform getTransformAt(float time) {
        if (keyframes.isEmpty()) {
            return Transform.identity();
        }

        // Clamp the argument
        time = Mth.clamp(time, 0.0F, lastTime());

        // Binary search
        int begin = 0, end = length() - 1;

        while (end - begin > 1) {
            int i = begin + (end - begin) / 2;

            if (keyframes.get(i).timeStamp() <= time && keyframes.get(i+1).timeStamp() > time) {
                begin = i;
                end = i+1;
                break;
            } else {
                if (keyframes.get(i).timeStamp() > time) {
                    end = i;
                } else if (keyframes.get(i+1).timeStamp() <= time) {
                    begin = i;
                }
            }
        }

        return getTransformAt(begin, end, time);
    }

    /// Returns transform by specifying keyframe and delta. Used on getting baked transforms
    public Transform getTransformAt(int keyframeStart, int keyframeEnd, float time) {
        Keyframe beginKf = keyframes.get(keyframeStart);
        Keyframe endKf = keyframes.get(keyframeEnd);
        float delta = Mth.clamp((time - beginKf.timeStamp()) / (endKf.timeStamp() - beginKf.timeStamp()), 0.0F, 1.0F);

        return Transform.lerp(beginKf.transform(), endKf.transform(), delta);
    }

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		int idx = 0;
		
		for (Keyframe kf : keyframes) {
			sb.append(kf);
			
			if (++idx < keyframes.size()) {
				sb.append("\n");
			}
		}
		
		return sb.toString();
	}

    /// Returns a new transform sheet filled with timestamps from @param source
    ///
    /// @param timeStamps gathers timestamps from animation clip ordered by ascending
    public static BoneAnimationTrack bakeKeyframes(BoneAnimationTrack source, TreeSet<Float> timeStamps) {
        List<Keyframe> keyframes = new ArrayList<> (timeStamps.size());

        for (float v : timeStamps) {
            // Add the last transform when timestamp exceeds max time
            keyframes.add(new Keyframe(v, v > source.lastTime() ? source.keyframes.getLast().transform() : source.getTransformAt(v)));
        }

        return new BoneAnimationTrack(Collections.unmodifiableList(keyframes));
    }
}