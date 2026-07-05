package com.yesman.akythera.core.util.helper;

import com.yesman.akythera.core.reflection.ScriptFunction;
import net.minecraft.world.phys.Vec3;
import org.joml.Math;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;

public final class VectorUtils {
    /// Transform float vector to a double vector
    public static Vec3 floatToDoubleVector(Vector3f fVector) {
        return new Vec3(fVector.x(), fVector.y(), fVector.z());
    }

    /// Transform double vector to a float vector
    public static Vector3f doubleToFloatVector(Vec3 dVector) {
        return new Vector3f((float)dVector.x(), (float)dVector.y(), (float)dVector.z());
    }

    /// Transform a joml library vector into a minecraft vector
    public static Vec3 jomlVectorToPhysicsVector(Vector3d src) {
        return new Vec3(src.x(), src.y(), src.z());
    }

    @ScriptFunction(pure = true, label = "Rotate Vector", desc = """
        Rotates a vector by a quaternion
    """)
    public static Vec3 rotateByQuaternion(Quaterniond rotator, Vec3 src) {
        return rotateByQuaternion(rotator, src.x(), src.y(), src.z());
    }

    public static Vec3 rotateByQuaternion(Quaterniond rotator, double x, double y, double z) {
        return jomlVectorToPhysicsVector(rotator.transform(x, y, z, new Vector3d()));
    }

    @ScriptFunction(pure = true, label = "Project Vector", desc = """
        Returns a projected vector from a to b
    """)
    public static Vec3 projectVector(Vec3 a, Vec3 b) {
        double dot = b.dot(a);
        double normalScale = 1.0D / ((b.x * b.x) + (b.y * b.y) + (b.z * b.z));

        return new Vec3(dot * b.x * normalScale, dot * b.y * normalScale, dot * b.z * normalScale);
    }

    @ScriptFunction(pure = true, label = "Get Vector X Rot", desc = """
        Returns pitch angle between given vector and unit vector (0, 0, 1)
    """)
    public static double getVectorXRot(Vec3 vec) {
        Vec3 normalized = vec.normalize();
        return -(Math.atan2(normalized.y, (float) Math.sqrt(normalized.x * normalized.x + normalized.z * normalized.z)) * (180D / Math.PI));
    }

    @ScriptFunction(pure = true, label = "Get Vector Y Rot", desc = """
        Returns yaw angle between given vector and unit vector (0, 0, 1)
    """)
    public static double getVectorYRot(Vec3 vec) {
        Vec3 normalized = vec.normalize();
        return Math.atan2(normalized.z, normalized.x) * (180D / Math.PI) - 90.0F;
    }

    @ScriptFunction(pure = true, label = "Get Angle Between Vectors", desc = """
        Returns an angle between two vectors
    """)
    public static double getAngleBetween(Vec3 a, Vec3 b, Vec3 up) {
        Vec3 normA = a.normalize();
        Vec3 normB = b.normalize();
        double dot = normA.dot(normB);
        double angle = Math.acos(Math.max(-1.0, Math.min(1.0, dot)));
        Vec3 cross = normA.cross(normB);

        if (up.dot(cross) < 0) {
            angle = -angle;
        }

        return Math.toDegrees(angle);
    }

    private VectorUtils() {}
}
