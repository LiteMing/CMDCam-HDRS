package team.creative.cmdcam.common.scene.mode;

import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import team.creative.cmdcam.common.math.point.CamPoint;
import team.creative.cmdcam.common.scene.CamScene;
import team.creative.cmdcam.common.scene.run.CamRun;
import team.creative.cmdcam.common.scene.tracking.CamPreset;
import team.creative.cmdcam.common.scene.tracking.TrackingOptions;
import team.creative.cmdcam.common.target.CamTargetPose;

/**
 * Binds a user created path to an entity.
 * <p>
 * The control points are interpreted as offsets in the local space of the target, exactly the way
 * {@code /cam-server start tracking <scene> <target>} authors them. The authored rotation is kept,
 * but the whole rig turns with the target.
 *
 * <h3>Rotation space</h3>
 * <p>
 * Two modes exist, selected automatically based on {@link CamScene#trackingReferenceYaw}:
 * <ul>
 *   <li><b>Local-space (preferred)</b>: {@code trackingReferenceYaw != NaN}.  Control-point yaws
 *       were authored relative to the target's body yaw at record time. On playback the authored
 *       yaw is rotated by {@code currentBodyYaw - trackingReferenceYaw}, so the camera looks in
 *       the correct direction from the very first frame, regardless of which way the target is
 *       facing.</li>
 *   <li><b>Legacy / absolute</b>: {@code trackingReferenceYaw == NaN}.  Fallback for scenes
 *       authored before this field existed. The old delta-from-first-frame behaviour is
 *       preserved; rotation is correct only when the target faces approximately the same way as
 *       at authoring time.</li>
 * </ul>
 */
public class FollowPathMode extends TrackingMode {
    
    /** Used only in legacy mode: the target's yaw on the first valid frame. */
    private boolean baseCaptured;
    private float baseYaw;
    private float basePitch;
    
    public FollowPathMode(CamScene scene) {
        super(scene, CamPreset.TRACKING_PRESET);
    }
    
    @Override
    protected boolean usesTemplatePath() {
        return true;
    }
    
    @Override
    @OnlyIn(Dist.CLIENT)
    public void started(CamRun run) {
        super.started(run);
        baseCaptured = false;
        baseYaw = 0;
        basePitch = 0;
    }
    
    @Override
    @OnlyIn(Dist.CLIENT)
    protected void applyTemplateRotation(CamPoint result, CamTargetPose pose, float appliedPitch, TrackingOptions options) {
        float refYaw = scene.trackingReferenceYaw;
        
        if (!Float.isNaN(refYaw)) {
            // Local-space mode: authored yaws are relative to refYaw.
            // Rotate them by the delta between the target's current yaw and the authoring yaw.
            float delta = Mth.wrapDegrees(pose.bodyYaw - refYaw);
            result.rotationYaw = Mth.wrapDegrees(result.rotationYaw + delta);
            // Pitch offset: authored pitch was relative to basePitch captured at start().
            // We don't have a reference pitch in local-space mode, so we use 0 (no offset),
            // meaning the authored pitch is purely the camera-to-target elevation.
            result.rotationPitch = Mth.clamp(result.rotationPitch + appliedPitch, -90.0D, 90.0D);
        } else {
            // Legacy mode: capture the target's pose on the first valid frame and accumulate
            // the delta from there. This is unchanged from the original behaviour.
            if (!baseCaptured) {
                baseCaptured = true;
                baseYaw = pose.bodyYaw;
                basePitch = appliedPitch;
            }
            result.rotationYaw += Mth.wrapDegrees(pose.bodyYaw - baseYaw);
            result.rotationPitch = Mth.clamp(result.rotationPitch + (appliedPitch - basePitch), -90.0D, 90.0D);
        }
    }
    
}
