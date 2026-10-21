package com.yesman.akythera.core.util.helper;

import com.yesman.akythera.core.reflection.ScriptField;
import com.yesman.akythera.core.reflection.ScriptFunction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Math;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

public final class MathUtils {
	@ScriptField(category = "Math/Vector")
	public static final Vec3 ZERO = new Vec3(0.0D, 0.0D, 0.0D);

    /// Normalized directional vector to positive X
	@ScriptField(category = "Math/Vector")
	public static final Vec3 XP = new Vec3(1.0D, 0.0D, 0.0D);

    /// Normalized directional vector to negative X
	@ScriptField(category = "Math/Vector")
	public static final Vec3 XN = new Vec3(-1.0D, 0.0D, 0.0D);

    /// Normalized directional vector to positive Y
	@ScriptField(category = "Math/Vector")
	public static final Vec3 YP = new Vec3(0.0D, 1.0D, 0.0D);

    /// Normalized directional vector to negative Y
	@ScriptField(category = "Math/Vector")
	public static final Vec3 YN = new Vec3(0.0D, -1.0D, 0.0D);

    /// Normalized directional vector to positive Z
	@ScriptField(category = "Math/Vector")
	public static final Vec3 ZP = new Vec3(0.0D, 0.0D, 1.0D);

    /// Normalized directional vector to negative Z
	@ScriptField(category = "Math/Vector")
	public static final Vec3 ZN = new Vec3(0.0D, 0.0D, -1.0D);

    /// Used for correcting floating point error
    public static final float EPSILON = 1e-5f;

	@ScriptFunction(pure = true, label = "Get Sign", desc = "Get plus or minus sign of the given value")
	public static int getSign(final double value) {
        if (value == 0.0D) return 0;

		return value > 0.0D ? 1 : -1;
	}

	@ScriptFunction(pure = true, label = "Get Direction", desc = "Get a direction vector by pitch and yaw rotations")
	public static Vec3 getDirectionVector(final float pitch, final float yaw) {
		float f = pitch * (float) Math.PI / 180F;
		float f1 = -yaw * (float) Math.PI / 180F;
		float f2 = Mth.cos(f1);
		float f3 = Mth.sin(f1);
		float f4 = Mth.cos(f);
		float f5 = Mth.sin(f);

		return new Vec3(f3 * f4, -f5, f2 * f4);
	}

	public static float findNearestRotation(final float src, final float rotation) {
		float diff = Math.abs(src - rotation);
		float idealRotation = rotation;
		int sign = Mth.sign(src - rotation);
		
		if (sign == 0) {
			return rotation;
		}
		
		while (true) {
			float next = idealRotation + sign * 360.0F;
			
			if (Math.abs(src - next) > diff) {
				return idealRotation;
			}
			
			idealRotation = next;
			diff = Math.abs(src - next);
		}
	}
	
	public static Vec3 getNearestVector(final Vec3 from, final Vec3... vectors) {
		double minLength = 1000000.0D;
		int index = 0;
		
		for (int i = 0; i < vectors.length; i++) {
			if (vectors[i] == null) {
				continue;
			}
			
			double distSqr = from.distanceToSqr(vectors[i]);
			
			if (distSqr < minLength) {
				minLength = distSqr;
				index = i;
			}
		}
		
		return vectors[index];
	}
	
	public static Vec3 getNearestVector(final Vec3 from, final List<Vec3> vectors) {
		return getNearestVector(from, vectors.toArray(new Vec3[0]));
	}
	
	public static int greatest(final int... iList) {
		int max = Integer.MIN_VALUE;
		
		for (int i : iList) {
			if (max < i) {
				max = i;
			}
		}
		
		return max;
	}
	
	public static int least(final int... iList) {
		int min = Integer.MAX_VALUE;
		
		for (int i : iList) {
			if (min > i) {
				min = i;
			}
		}
		
		return min;
	}
	
	public static float greatest(final float... fList) {
		float max = -1000000.0F;
		
		for (float f : fList) {
			if (max < f) {
				max = f;
			}
		}
		
		return max;
	}
	
	public static float least(final float... fList) {
		float min = 1000000.0F;
		
		for (float f : fList) {
			if (min > f) {
				min = f;
			}
		}
		
		return min;
	}
	
	public static double greatest(final double... dList) {
		double max = -1000000.0D;
		
		for (double d : dList) {
			if (max < d) {
				max = d;
			}
		}
		
		return max;
	}
	
	public static double least(final double... dList) {
		double min = 1000000.0D;
		
		for (double d : dList) {
			if (min > d) {
				min = d;
			}
		}
		
		return min;
	}

	@ScriptFunction(pure = true, label = "Safe Division", desc = "Returns floating point quotient or fallback value when divisor is 0")
    public static float safeDivision(final float dividend, final float divisor, final float fallbackValue) {
        return divisor == 0.0F ? fallbackValue : dividend / divisor;
    }

    public static float approachWrapped(final float current, final float goal, final float maxStep, final float min, final float max) {
        float period = max - min;
        float delta = Mth.positiveModulo(goal - current + period * 0.5F, period) - period * 0.5F;

        return min + Mth.positiveModulo(current + absoluteClamp(delta, maxStep) - min, period);
    }

	@ScriptFunction(pure = true, label = "Speed Match Rate", desc = "Playback rate that moves an animation's authored ground speed to the real speed, clamped")
    public static float speedMatchRate(final float speed, final float groundSpeed, final float minRate, final float maxRate) {
        return Mth.clamp(safeDivision(speed, groundSpeed, 1.0F), minRate, maxRate);
    }

    /// Returns a quotient, or 0 when the divisor is too close to 0 to divide by.
    ///
    /// A divisor within [#EPSILON] of 0 yields 0 rather than an infinity: one degenerate value must
    /// not poison every later arithmetic it takes part in.
	@ScriptFunction(pure = true, label = "Divide or Zero", desc = "Returns quotient or 0 when divisor is 0")
    public static double divideOrZero(final double dividend, final double divisor) {
        return Math.abs(divisor) < EPSILON ? 0.0D : dividend / divisor;
    }

    /// Returns a compare result whether t1 is less than t2
	@ScriptFunction(pure = true, label = "Less (<)", category = "Math/Comparator", desc = "Returns t1 < t2")
    public static <T> boolean less(final Comparator<T> comparator, final T t1, final T t2) {
        int result = comparator.compare(t1, t2);
        return result < 0;
    }

    /// Returns a compare result whether t1 is less than or equals to t2
	@ScriptFunction(pure = true, label = "Less or Equal (<=)", category = "Math/Comparator", desc = "Returns t1 <= t2")
    public static <T> boolean lessEqual(final Comparator<T> comparator, final T t1, final T t2) {
        int result = comparator.compare(t1, t2);
        return result < 0 || result == 0;
    }

    /// Returns a compare result whether t1 is greater than t2
	@ScriptFunction(pure = true, label = "Greater (>)", category = "Math/Comparator", desc = "Returns t1 > t2")
    public static <T> boolean greater(final Comparator<T> comparator, final T t1, final T t2) {
        int result = comparator.compare(t1, t2);
        return result > 0;
    }

    /// Returns a compare result whether t1 is greater than or equals to t2
	@ScriptFunction(pure = true, label = "Greater or Equal (>=)", category = "Math/Comparator", desc = "Returns t1 >= t2")
    public static <T> boolean greaterEqual(final Comparator<T> comparator, final T t1, final T t2) {
        int result = comparator.compare(t1, t2);
        return result > 0 || result == 0;
    }

    /// Returns a compare result whether t1 equals t2
	@ScriptFunction(pure = true, label = "Equal (==)", category = "Math/Comparator", desc = "Returns t1 == t2")
    public static <T> boolean equal(final Comparator<T> comparator, final T t1, final T t2) {
        return comparator.compare(t1, t2) == 0;
    }

    /// Returns a clamped value in both positive and negative
    public static float absoluteClamp(final float value, final float max) {
        return Math.clamp(value, -max, max);
    }

    /// Returns counterweight of given fraction
    ///
    /// @param d 0.0 ~ 1.0
    public static double oneMinus(final double d) {
        return 1.0D - d;
    }

    /// Returns counterweight of given fraction
    ///
    /// @param f 0.0 ~ 1.0
    public static float oneMinus(final float f) {
        return 1.0F - f;
    }

	/// Returns a rounded floating-point value to specific position
	///
	/// For negative `position`, abandon all floating point values.
	public static double roundAt(final double value, final int position) {
		if (position < 0) return Math.round(value);

		BigDecimal bd = BigDecimal.valueOf(value);
		bd = bd.setScale(position, RoundingMode.HALF_UP);

		return bd.doubleValue();
	}

	private MathUtils() {}
}