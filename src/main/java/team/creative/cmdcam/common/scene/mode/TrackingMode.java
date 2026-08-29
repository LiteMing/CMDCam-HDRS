package team.creative.cmdcam.common.scene.mode;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import team.creative.cmdcam.client.CamEventHandlerClient;
import team.creative.cmdcam.common.math.point.CamPoint;
import team.creative.cmdcam.common.scene.CamScene;
import team.creative.cmdcam.common.scene.run.CamRun;
import team.creative.cmdcam.common.scene.tracking.CamPreset;
import team.creative.cmdcam.common.scene.tracking.SmoothedTargetPose;
import team.creative.cmdcam.common.scene.tracking.TrackingOptions;
import team.creative.cmdcam.common.target.CamTargetPose;
import team.creative.creativecore.common.util.math.vec.Vec3d;
import team.creative.creativecore.common.util.mc.TickUtils;

/**
 * Base class of every camera which is bound to an entity.
 * <p>
 * The target pose is smoothed with a frame rate independent half life and the camera follows a configurable fraction of the target pitch. Subclasses either use
 * the control points of the scene as local offsets (template path) or orbit the target with a built in preset offset.
 */
public abstract class TrackingMode extends OutsideMode {
    
    /** the camera never fully copies the target pitch, otherwise quick head movement makes the viewer sick */
    public static final float MAX_APPLIED_PITCH = 75.0F;
    
    public double lookAhead = 0;
    public double lookHeight = 0;
    public double defaultFov = 70;
    public double defaultHeightFactor = 0.65;
    /** distance the authored local offset was made for, used to scale it with the {@code distance} parameter */
    public double defaultDistance = 1.6;
    public double defaultDampingMs = 300;
    public double defaultPitchFollow = 0.5;
    public double defaultYawFollow = 1.0;
    
    public Entity cameraEntityBefore;
    public CameraType cameraTypeBefore;
    public double fovOffsetBefore;
    public float rollBefore;
    
    private final SmoothedTargetPose smoother = new SmoothedTargetPose();
    
    public TrackingMode(CamScene scene) {
        this(scene, CamPreset.TRACKING_PRESET);
    }
    
    public TrackingMode(CamScene scene, CamPreset preset) {
        super(scene);
        this.lookAhead = preset.lookAhead;
        this.lookHeight = preset.lookHeight;
        this.defaultFov = preset.fov;
        this.defaultHeightFactor = preset.heightFactor;
        this.defaultDistance = preset.distance;
        this.defaultDampingMs = preset.dampingMs;
        this.defaultPitchFollow = preset.pitchFollow;
        this.defaultYawFollow = preset.yawFollow;
    }
    
    @Override
    @OnlyIn(Dist.CLIENT)
    public void started(CamRun run) {
        super.started(run);
        smoother.reset();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;
        float partial = TickUtils.getFrameTime(mc.level);
        this.cameraEntityBefore = mc.cameraEntity;
        this.cameraTypeBefore = mc.options.getCameraType();
        this.fovOffsetBefore = CamEventHandlerClient.fovExact(partial) - CamEventHandlerClient.fovExactVanilla(partial);
        this.rollBefore = CamEventHandlerClient.roll();
    }
    
    @Override
    @OnlyIn(Dist.CLIENT)
    public void finished(CamRun run) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            Entity cam = (cameraEntityBefore != null && !cameraEntityBefore.isRemoved() && cameraEntityBefore.level() == mc.player.level())
                    ? cameraEntityBefore : mc.player;
            mc.cameraEntity = cam;
        } else {
            mc.cameraEntity = null;
        }
        
        if (cameraTypeBefore != null)
            mc.options.setCameraType(cameraTypeBefore);
        CamEventHandlerClient.fov(fovOffsetBefore);
        CamEventHandlerClient.roll(rollBefore);
        
        camPlayer = null;
    }
    
    /** Whether the control points of the scene are used as local offsets instead of a fixed preset offset. */
    public boolean usesTemplatePath() {
        return false;
    }
    
    public void compensateSmoothEntryStart(CamPoint point, TrackingOptions options) {
        if (options == null)
            return;
        double scale = usesTemplatePath()
            ? options.distanceScaleOrDefault(1.0D)
            : options.distance != null
                ? options.distanceOrDefault(defaultDistance) / Math.max(defaultDistance, 0.0001D)
                : 1.0D;
        point.x = (point.x - options.offsetXOrZero()) / Math.max(scale, 0.0001D);
        point.y -= options.offsetYOrZero();
        point.z = (point.z - options.offsetZOrZero()) / Math.max(scale, 0.0001D);
    }
    
    protected TrackingOptions options() {
        TrackingOptions options = scene.trackingOptions;
        return options != null ? options : new TrackingOptions();
    }
    
    @OnlyIn(Dist.CLIENT)
    public CamPoint calculate(CamScene scene, Level level, float partialTicks, CamPoint local) {
        if (scene.posTarget == null)
            return null;
        CamTargetPose raw = scene.posTarget.pose(level, partialTicks);
        if (!raw.valid) {
            smoother.skipFrame();
            return null;
        }
        
        TrackingOptions options = options();
        CamTargetPose pose = smoother.update(raw, options.dampingOrDefault(defaultDampingMs));
        if (pose == null)
            return null;
        Vec3d anchor = pose.anchor(options.heightFactorOrDefault(defaultHeightFactor));
        if (anchor == null)
            return null;
        
        float appliedPitch = Mth.clamp((float) (pose.pitch * options.pitchFollowOrDefault(defaultPitchFollow)), -MAX_APPLIED_PITCH, MAX_APPLIED_PITCH);
        float appliedYaw = pose.blendYaw(options.yawFollowOrDefault(defaultYawFollow));
        CamPoint result = local.copy();
        
        if (usesTemplatePath()) {
            double scale = options.distanceScaleOrDefault(1.0D);
            double localX = local.x * scale + options.offsetXOrZero();
            double localY = local.y + options.offsetYOrZero();
            double localZ = local.z * scale + options.offsetZOrZero();
            Vec3d offset = pose.localToWorld(localX, localY, localZ, appliedYaw, appliedPitch);
            result.x = anchor.x + offset.x;
            result.y = anchor.y + offset.y;
            result.z = anchor.z + offset.z;
            applyTemplateRotation(result, pose, appliedYaw, appliedPitch, options);
            if (options.fov != null)
                result.zoom = options.fovOrDefault(defaultFov);
            return result;
        }
        
        // Scale the authored offset instead of replacing it, this way a smooth start still travels from the player to the preset position.
        double scale = options.distance != null ? options.distanceOrDefault(defaultDistance) / Math.max(defaultDistance, 0.0001D) : 1.0D;
        double localX = local.x * scale + options.offsetXOrZero();
        double localY = local.y + options.offsetYOrZero();
        double localZ = local.z * scale + options.offsetZOrZero();
        Vec3d cameraOffset = pose.localToWorld(localX, localY, localZ, appliedYaw, appliedPitch);
        Vec3d cameraPos = new Vec3d(anchor.x + cameraOffset.x, anchor.y + cameraOffset.y, anchor.z + cameraOffset.z);
        
        Vec3d lookOffset = pose.localToWorld(0, lookHeight, -lookAhead, appliedYaw, appliedPitch);
        Vec3d lookPos = new Vec3d(anchor.x + lookOffset.x, anchor.y + lookOffset.y, anchor.z + lookOffset.z);
        
        double d0 = lookPos.x - cameraPos.x;
        double d1 = lookPos.y - cameraPos.y;
        double d2 = lookPos.z - cameraPos.z;
        double d3 = Math.sqrt(d0 * d0 + d2 * d2);
        
        result.set(cameraPos);
        // Always look at the target again, the camera must never inherit the raw target rotation.
        result.rotationYaw = (Math.atan2(d2, d0) * 180.0D / Math.PI) - 90.0D;
        result.rotationPitch = -(Math.atan2(d1, d3) * 180.0D / Math.PI);
        result.zoom = options.fovOrDefault(defaultFov);
        return result;
    }
    
    /**
     * Called for template paths after the position has been resolved. The default keeps the authored rotation, subclasses may rotate the whole rig with the
     * target.
     */
    @OnlyIn(Dist.CLIENT)
    protected void applyTemplateRotation(CamPoint result, CamTargetPose pose, float appliedYaw, float appliedPitch, TrackingOptions options) {}
    
}
