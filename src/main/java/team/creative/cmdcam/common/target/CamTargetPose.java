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
    
    public Vec3d localToWorld(double localX, double localZ) {
        double rad = Math.toRadians(Mth.wrapDegrees(bodyYaw));
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        return new Vec3d(-localX * cos + localZ * sin, 0, -localX * sin - localZ * cos);
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
