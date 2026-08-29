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
 * The control points are interpreted as offsets in the local space of the target, exactly the way {@code /cam-server <scene> follow <target>} authors them. The
 * authored rotation is kept, but the whole rig turns with the target: the camera rotation follows the delta between the current and the initial target yaw.
 */
public class FollowPathMode extends TrackingMode {
    
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
    }
    
    @Override
    @OnlyIn(Dist.CLIENT)
    protected void applyTemplateRotation(CamPoint result, CamTargetPose pose, float appliedPitch, TrackingOptions options) {
        if (!baseCaptured) {
            baseCaptured = true;
            baseYaw = pose.bodyYaw;
            basePitch = appliedPitch;
        }
        result.rotationYaw += Mth.wrapDegrees(pose.bodyYaw - baseYaw);
        result.rotationPitch = Mth.clamp(result.rotationPitch + (appliedPitch - basePitch), -90.0D, 90.0D);
    }
    
}
