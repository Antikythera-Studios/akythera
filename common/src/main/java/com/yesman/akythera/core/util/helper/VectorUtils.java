package com.yesman.akythera.core.util.helper;

import com.yesman.akythera.core.reflection.ScriptFunction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Math;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;

public final class VectorUtils {
    /// Transform float vector to a double vector
    public static Vec3 floatToDoubleVector(final Vector3f fVector) {
        return new Vec3(fVector.x(), fVector.y(), fVector.z());
    }

    /// Transform double vector to a float vector
    public static Vector3f doubleToFloatVector(final Vec3 dVector) {
        return new Vector3f((float)dVector.x(), (float)dVector.y(), (float)dVector.z());
    }

    /// Transform a joml float vector into a minecraft vector
    public static Vec3 toVec3(final Vector3f vector) {
        return new Vec3(vector.x(), vector.y(), vector.z());
    }

    /// Transform a joml library vector into a minecraft vector
    public static Vec3 jomlVectorToPhysicsVector(final Vector3d src) {
        return new Vec3(src.x(), src.y(), src.z());
    }

    @ScriptFunction(pure = true, label = "Rotate Vector", desc = """
        Rotates a vector by a quaternion
    """)
    public static Vec3 rotateByQuaternion(final Quaterniond rotator, final Vec3 src) {
        return rotateByQuaternion(rotator, src.x(), src.y(), src.z());
    }

    public static Vec3 rotateByQuaternion(final Quaterniond rotator, final double x, final double y, final double z) {
        return jomlVectorToPhysicsVector(rotator.transform(x, y, z, new Vector3d()));
    }

    @ScriptFunction(pure = true, label = "Project Vector", desc = """
        Returns a projected vector from a to b
    """)
    public static Vec3 projectVector(final Vec3 a, final Vec3 b) {
        double dot = b.dot(a);
        double normalScale = 1.0D / ((b.x * b.x) + (b.y * b.y) + (b.z * b.z));

        return new Vec3(dot * b.x * normalScale, dot * b.y * normalScale, dot * b.z * normalScale);
    }

    @ScriptFunction(pure = true, label = "Get Vector X Rot", desc = """
        Returns pitch angle between given vector and unit vector (0, 0, 1)
    """)
    public static double getVectorXRot(final Vec3 vec) {
        Vec3 normalized = vec.normalize();
        return -(Math.atan2(normalized.y, (float) Math.sqrt(normalized.x * normalized.x + normalized.z * normalized.z)) * (180D / Math.PI));
    }

    @ScriptFunction(pure = true, label = "Get Vector Y Rot", desc = """
        Returns yaw angle between given vector and unit vector (0, 0, 1)
    """)
    public static double getVectorYRot(final Vec3 vec) {
        Vec3 normalized = vec.normalize();
        return Math.atan2(normalized.z, normalized.x) * (180D / Math.PI) - 90.0F;
    }

    @ScriptFunction(pure = true, label = "Get Angle Between Vectors", desc = """
        Returns an angle between two vectors in degree
    """)
    public static double getAngleBetween(final Vec3 a, final Vec3 b, final Vec3 up, final int precision) {
        Vec3 normA = getRoundedVector(a.normalize(), precision);
        Vec3 normB = getRoundedVector(b.normalize(), precision);
        double dot = normA.dot(normB);
        double angle = Math.acos(Math.max(-1.0, Math.min(1.0, dot)));
        Vec3 cross = normA.cross(normB);

        if (up.dot(cross) < 0) {
            angle = -angle;
        }

        return Math.toDegrees(angle);
    }

    @ScriptFunction(pure = true, label = "Get Angle Between Vectors in XZ Plane", desc = """
        Returns an angle between two vectors in degree
    """)
    public static double getHorizontalAngleBetween(final Vec3 a, final Vec3 b, final int precision) {
        return getAngleBetween(a.subtract(0, a.y(), 0), b.subtract(0, b.y(), 0), Vec3.Y_AXIS, precision);
    }

    public static Vec3 getRoundedVector(final Vec3 v, final int precision) {
        return new Vec3(
            MathUtils.roundAt(v.x(), precision),
            MathUtils.roundAt(v.y(), precision),
            MathUtils.roundAt(v.z(), precision)
        );
    }

    /// Below this a segment is a point and its direction is meaningless.
    private static final double DEGENERATE_SEGMENT = 1.0E-12D;

    /// Squared distance from `point` to the segment `from`-`to`.
    public static double pointToSegmentDistanceSqr(final Vec3 point, final Vec3 from, final Vec3 to) {
        Vec3 segment = to.subtract(from);
        double lengthSqr = segment.lengthSqr();

        if (lengthSqr <= DEGENERATE_SEGMENT) return point.distanceToSqr(from);

        double along = Mth.clamp(point.subtract(from).dot(segment) / lengthSqr, 0.0D, 1.0D);

        return point.distanceToSqr(from.add(segment.scale(along)));
    }

    /// Squared distance between the segments `a0`-`a1` and `b0`-`b1`.
    public static double segmentDistanceSqr(final Vec3 a0, final Vec3 a1, final Vec3 b0, final Vec3 b1) {
        Vec3 da = a1.subtract(a0);
        Vec3 db = b1.subtract(b0);
        Vec3 between = a0.subtract(b0);
        double aLengthSqr = da.lengthSqr();
        double bLengthSqr = db.lengthSqr();
        double bProjection = db.dot(between);
        double alongA;
        double alongB;

        if (aLengthSqr <= DEGENERATE_SEGMENT && bLengthSqr <= DEGENERATE_SEGMENT) {
            return between.lengthSqr();
        }

        if (aLengthSqr <= DEGENERATE_SEGMENT) {
            alongA = 0.0D;
            alongB = Mth.clamp(bProjection / bLengthSqr, 0.0D, 1.0D);
        } else {
            double aProjection = da.dot(between);

            if (bLengthSqr <= DEGENERATE_SEGMENT) {
                alongB = 0.0D;
                alongA = Mth.clamp(-aProjection / aLengthSqr, 0.0D, 1.0D);
            } else {
                double skew = da.dot(db);
                // Zero when the segments are parallel, where every point of one is equally near the
                // other and picking a0 is as good an answer as any.
                double denominator = aLengthSqr * bLengthSqr - skew * skew;

                alongA = denominator > DEGENERATE_SEGMENT
                    ? Mth.clamp((skew * bProjection - aProjection * bLengthSqr) / denominator, 0.0D, 1.0D)
                    : 0.0D;
                alongB = (skew * alongA + bProjection) / bLengthSqr;

                if (alongB < 0.0D) {
                    alongB = 0.0D;
                    alongA = Mth.clamp(-aProjection / aLengthSqr, 0.0D, 1.0D);
                } else if (alongB > 1.0D) {
                    alongB = 1.0D;
                    alongA = Mth.clamp((skew - aProjection) / aLengthSqr, 0.0D, 1.0D);
                }
            }
        }

        return a0.add(da.scale(alongA)).distanceToSqr(b0.add(db.scale(alongB)));
    }

    /// A unit vector perpendicular to `v`. Crossed against whichever world axis `v` leans on least, so
    /// the product never degenerates.
    public static Vec3 perpendicular(final Vec3 v) {
        Vec3 leastAligned = Math.abs(v.x()) < Math.abs(v.y())
            ? (Math.abs(v.x()) < Math.abs(v.z()) ? MathUtils.XP : MathUtils.ZP)
            : (Math.abs(v.y()) < Math.abs(v.z()) ? MathUtils.YP : MathUtils.ZP);

        return v.cross(leastAligned).normalize();
    }

    private VectorUtils() {}
}
