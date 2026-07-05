package com.yesman.akythera.core.util.helper;

import com.yesman.akythera.core.reflection.ScriptField;
import com.yesman.akythera.core.reflection.ScriptFunction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Math;

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
	public static int getSign(double value) {
        if (value == 0.0D) return 0;
		return value > 0.0D ? 1 : -1;
	}

	@ScriptFunction(pure = true, label = "Get Direction", desc = "Get a direction vector by pitch and yaw rotations")
	public static Vec3 getDirectionVector(float pitch, float yaw) {
		float f = pitch * (float) Math.PI / 180F;
		float f1 = -yaw * (float) Math.PI / 180F;
		float f2 = Mth.cos(f1);
		float f3 = Mth.sin(f1);
		float f4 = Mth.cos(f);
		float f5 = Mth.sin(f);

		return new Vec3(f3 * f4, -f5, f2 * f4);
	}

	public static float findNearestRotation(float src, float rotation) {
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
	
	public static Vec3 getNearestVector(Vec3 from, Vec3... vectors) {
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
	
	public static Vec3 getNearestVector(Vec3 from, List<Vec3> vectors) {
		return getNearestVector(from, vectors.toArray(new Vec3[0]));
	}
	
	public static int greatest(int... iList) {
		int max = Integer.MIN_VALUE;
		
		for (int i : iList) {
			if (max < i) {
				max = i;
			}
		}
		
		return max;
	}
	
	public static int least(int... iList) {
		int min = Integer.MAX_VALUE;
		
		for (int i : iList) {
			if (min > i) {
				min = i;
			}
		}
		
		return min;
	}
	
	public static float greatest(float... fList) {
		float max = -1000000.0F;
		
		for (float f : fList) {
			if (max < f) {
				max = f;
			}
		}
		
		return max;
	}
	
	public static float least(float... fList) {
		float min = 1000000.0F;
		
		for (float f : fList) {
			if (min > f) {
				min = f;
			}
		}
		
		return min;
	}
	
	public static double greatest(double... dList) {
		double max = -1000000.0D;
		
		for (double d : dList) {
			if (max < d) {
				max = d;
			}
		}
		
		return max;
	}
	
	public static double least(double... dList) {
		double min = 1000000.0D;
		
		for (double d : dList) {
			if (min > d) {
				min = d;
			}
		}
		
		return min;
	}

	@ScriptFunction(pure = true, label = "Safe Division", desc = "Returns floating point quotient or fallback value when divisor is 0")
    public static float divideOrElse(final float dividend, final float divisor, final float fallbackValue) {
        return divisor == 0.0F ? fallbackValue : dividend / divisor;
    }

    /// Returns a compare result whether t1 is less than t2
	@ScriptFunction(pure = true, label = "Less (<)", category = "Math/Comparator", desc = "Returns t1 < t2")
    public static <T> boolean less(Comparator<T> comparator, T t1, T t2) {
        int result = comparator.compare(t1, t2);
        return result < 0;
    }

    /// Returns a compare result whether t1 is less than or equals to t2
	@ScriptFunction(pure = true, label = "Less or Equal (<=)", category = "Math/Comparator", desc = "Returns t1 <= t2")
    public static <T> boolean lessEqual(Comparator<T> comparator, T t1, T t2) {
        int result = comparator.compare(t1, t2);
        return result < 0 || result == 0;
    }

    /// Returns a compare result whether t1 is greater than t2
	@ScriptFunction(pure = true, label = "Greater (>)", category = "Math/Comparator", desc = "Returns t1 > t2")
    public static <T> boolean greater(Comparator<T> comparator, T t1, T t2) {
        int result = comparator.compare(t1, t2);
        return result > 0;
    }

    /// Returns a compare result whether t1 is greater than or equals to t2
	@ScriptFunction(pure = true, label = "Greater or Equal (>=)", category = "Math/Comparator", desc = "Returns t1 >= t2")
    public static <T> boolean greaterEqual(Comparator<T> comparator, T t1, T t2) {
        int result = comparator.compare(t1, t2);
        return result > 0 || result == 0;
    }

    /// Returns a compare result whether t1 equals t2
	@ScriptFunction(pure = true, label = "Equal (==)", category = "Math/Comparator", desc = "Returns t1 == t2")
    public static <T> boolean equal(Comparator<T> comparator, T t1, T t2) {
        return comparator.compare(t1, t2) == 0;
    }

    /// Returns a clamped value in both positive and negative
    public static float absoluteClamp(float value, float max) {
        return Math.clamp(value, -max, max);
    }

    /// Returns counterweight of given fraction
    ///
    /// @param d 0.0 ~ 1.0
    public static double oneMinus(double d) {
        return 1.0D - d;
    }

    /// Returns counterweight of given fraction
    ///
    /// @param f 0.0 ~ 1.0
    public static float oneMinus(float f) {
        return 1.0F - f;
    }

	private MathUtils() {}
}