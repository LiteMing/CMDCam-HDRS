package team.creative.cmdcam.common.scene.tracking;

import net.minecraft.Util;
import net.minecraft.util.Mth;
import team.creative.cmdcam.common.target.CamTargetPose;
import team.creative.creativecore.common.util.math.vec.Vec3d;

/**
 * Exponential smoothing of the target pose with a frame rate independent half life.
 * <p>
 * The smoothing is driven by wall clock time, so the perceived response is identical at 30, 60 or 144 FPS. Angles are interpolated along the shortest arc, which
 * keeps a target crossing the 179/-179 degree boundary from spinning the camera around.
 */
public class SmoothedTargetPose {
    
    /** guards against a huge catch up step after the game was paused or the target was lost for a while */
    public static final double MAX_DELTA_SECONDS = 0.25D;
    
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    
    private boolean initialized;
    private long lastTimeMs = -1L;
    
    public void reset() {
        initialized = false;
        lastTimeMs = -1L;
    }
    
    public boolean initialized() {
        return initialized;
    }
    
    /** Consumes the elapsed time without touching the filter, used while the target is temporarily unavailable. */
    public void skipFrame() {
        lastTimeMs = Util.getMillis();
    }
    
    /** Time in seconds since the previous frame, clamped so a stall never turns into a jump. */
    public double deltaSeconds() {
        long now = Util.getMillis();
        if (lastTimeMs < 0L) {
            lastTimeMs = now;
            return 0.0D;
        }
        double delta = (now - lastTimeMs) / 1000.0D;
        lastTimeMs = now;
        if (delta < 0.0D)
            delta = 0.0D;
        return Math.min(delta, MAX_DELTA_SECONDS);
    }
    
    public CamTargetPose update(CamTargetPose target, double dampingMs) {
        return update(target, deltaSeconds(), dampingMs);
    }
    
    public CamTargetPose update(CamTargetPose target, double deltaSeconds, double dampingMs) {
        if (target == null || !target.valid)
            return null;
        
        double delta = Mth.clamp(deltaSeconds, 0.0D, MAX_DELTA_SECONDS);
        
        // The very first valid pose is adopted as is, the camera must not travel there from the world origin.
        if (!initialized || dampingMs <= 0.0D) {
            initialized = true;
            x = target.eyePosition.x;
            y = target.eyePosition.y;
            z = target.eyePosition.z;
            yaw = target.bodyYaw;
            pitch = target.pitch;
            return current(target);
        }
        
        double halfLife = dampingMs / 1000.0D;
        double alpha = 1.0D - Math.pow(0.5D, delta / halfLife);
        alpha = Mth.clamp(alpha, 0.0D, 1.0D);
        
        x = Mth.lerp(alpha, x, target.eyePosition.x);
        y = Mth.lerp(alpha, y, target.eyePosition.y);
        z = Mth.lerp(alpha, z, target.eyePosition.z);
        yaw = Mth.rotLerp((float) alpha, yaw, target.bodyYaw);
        pitch = Mth.rotLerp((float) alpha, pitch, target.pitch);
        
        return current(target);
    }
    
    private CamTargetPose current(CamTargetPose sample) {
        return new CamTargetPose(new Vec3d(x, y, z), sample.bbHeight, sample.eyeHeight, yaw, pitch);
    }
    
}
