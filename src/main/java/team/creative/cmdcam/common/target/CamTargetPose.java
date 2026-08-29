package team.creative.cmdcam.common.target;

import net.minecraft.util.Mth;
import team.creative.creativecore.common.util.math.vec.Vec3d;

public class CamTargetPose {
    
    public final Vec3d eyePosition;
    public final double bbHeight;
    public final double eyeHeight;
    public final float bodyYaw;
    public final float pitch;
    public final boolean valid;
    
    private CamTargetPose() {
        this.eyePosition = null;
        this.bbHeight = 0;
        this.eyeHeight = 0;
        this.bodyYaw = 0;
        this.pitch = 0;
        this.valid = false;
    }
    
    public CamTargetPose(Vec3d eyePosition, double bbHeight, double eyeHeight, float bodyYaw, float pitch) {
        this.eyePosition = eyePosition;
        this.bbHeight = bbHeight;
        this.eyeHeight = eyeHeight;
        this.bodyYaw = bodyYaw;
        this.pitch = pitch;
        this.valid = true;
    }
    
    public static CamTargetPose invalid() {
        return new CamTargetPose();
    }
    
    public Vec3d anchor(double heightFactor) {
        if (!valid)
            return null;
        double feetY = eyePosition.y - eyeHeight;
        return new Vec3d(eyePosition.x, feetY + bbHeight * heightFactor, eyePosition.z);
    }
    
    /**
     * Transforms a local offset into world space using only the horizontal facing. Kept for compatibility, the Y component is always zero.
     * <p>
     * Local axis convention: {@code X = right}, {@code Y = up}, {@code Z = behind}, {@code -Z = in front} of the target.
     */
    public Vec3d localToWorld(double localX, double localZ) {
        return localToWorld(localX, 0, localZ, bodyYaw, 0);
    }
    
    public Vec3d localToWorld(double localX, double localY, double localZ) {
        return localToWorld(localX, localY, localZ, bodyYaw, pitch);
    }
    
    /**
     * Applies pitch first (around the local X axis) and afterwards yaw (around the world Y axis).
     * <p>
     * Pitch uses the Minecraft convention where a negative value means looking up. A target looking up therefore moves a point behind it down and a point in
     * front of it up, which is exactly the way the camera should orbit around the head.
     */
    public Vec3d localToWorld(double localX, double localY, double localZ, float yaw, float pitch) {
        double pitchRad = Math.toRadians(Mth.wrapDegrees(pitch));
        double pitchCos = Math.cos(pitchRad);
        double pitchSin = Math.sin(pitchRad);
        
        double pitchedY = localY * pitchCos + localZ * pitchSin;
        double pitchedZ = -localY * pitchSin + localZ * pitchCos;
        
        return localToWorldYaw(localX, pitchedY, pitchedZ, yaw);
    }
    
    public Vec3d localToWorldYaw(double localX, double localY, double localZ, float yaw) {
        double rad = Math.toRadians(Mth.wrapDegrees(yaw));
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        return new Vec3d(-localX * cos + localZ * sin, localY, -localX * sin - localZ * cos);
    }
    
    public void worldToLocal(Vec3d offset) {
        double rad = Math.toRadians(Mth.wrapDegrees(bodyYaw));
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double ox = offset.x;
        double oz = offset.z;
        offset.x = -cos * ox - sin * oz;
        offset.z = sin * ox - cos * oz;
    }
}
