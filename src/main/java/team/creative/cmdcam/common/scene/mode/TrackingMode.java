package team.creative.cmdcam.common.scene.mode;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import team.creative.cmdcam.client.CamEventHandlerClient;
import team.creative.cmdcam.common.math.point.CamPoint;
import team.creative.cmdcam.common.scene.CamScene;
import team.creative.cmdcam.common.scene.run.CamRun;
import team.creative.cmdcam.common.target.CamTargetPose;
import team.creative.creativecore.common.util.math.vec.Vec3d;
import team.creative.creativecore.common.util.mc.TickUtils;

public abstract class TrackingMode extends OutsideMode {
    
    public Vec3d localOffset = new Vec3d(0, 0.6, 1.6);
    public double lookAhead = 0;
    public double lookHeight = 0;
    public float defaultFov = 70;
    public double defaultHeightFactor = 0.65;
    
    public Entity cameraEntityBefore;
    public CameraType cameraTypeBefore;
    public double fovOffsetBefore;
    public float rollBefore;
    
    public TrackingMode(CamScene scene) {
        super(scene);
    }
    
    @Override
    @OnlyIn(Dist.CLIENT)
    public void started(CamRun run) {
        super.started(run);
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
    
    @OnlyIn(Dist.CLIENT)
    public CamPoint calculate(CamScene scene, Level level, float partialTicks, CamPoint local) {
        if (scene.posTarget == null)
            return null;
        CamTargetPose pose = scene.posTarget.pose(level, partialTicks);
        if (!pose.valid)
            return null;
        Vec3d anchor = pose.anchor(scene.targetHeightFactor);
        if (anchor == null)
            return null;
        Vec3d offset = pose.localToWorld(local.x, local.z);
        Vec3d cameraPos = new Vec3d(anchor.x + offset.x, anchor.y + local.y, anchor.z + offset.z);
        Vec3d lookOffset = pose.localToWorld(0, -lookAhead);
        Vec3d lookPos = new Vec3d(anchor.x + lookOffset.x, anchor.y + lookHeight, anchor.z + lookOffset.z);
        double d0 = lookPos.x - cameraPos.x;
        double d1 = lookPos.y - cameraPos.y;
        double d2 = lookPos.z - cameraPos.z;
        double d3 = Math.sqrt(d0 * d0 + d2 * d2);
        double pitch = (-(Math.atan2(d1, d3) * 180.0D / Math.PI));
        double yaw = (Math.atan2(d2, d0) * 180.0D / Math.PI) - 90.0D;
        CamPoint result = local.copy();
        result.set(cameraPos);
        result.rotationYaw = yaw;
        result.rotationPitch = pitch;
        return result;
    }
}
