package team.creative.cmdcam.common.scene.run;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import team.creative.cmdcam.client.CamEventHandlerClient;
import team.creative.cmdcam.common.math.interpolation.CamInterpolation;
import team.creative.cmdcam.common.math.point.CamPoint;
import team.creative.cmdcam.common.math.point.CamPoints;
import team.creative.cmdcam.common.scene.mode.TrackingMode;
import team.creative.creativecore.common.util.math.vec.Vec3d;
import team.creative.creativecore.common.util.mc.TickUtils;

@OnlyIn(Dist.CLIENT)
public class CamReturnStage extends CamRunStage {
    
    private boolean captured = false;
    private double startX, startY, startZ;
    private float startYaw, startPitch;
    private double startZoom, startRoll;
    
    public CamReturnStage(CamRun run, long duration, CamPoints points) {
        super(run, CamInterpolation.HERMITE, duration, 0, points);
    }
    
    @Override
    public void start() {
        super.start();
        capture();
    }
    
    private void capture() {
        if (captured)
            return;
        captured = true;
        Minecraft mc = Minecraft.getInstance();
        Entity camera = run.scene.mode.getCamera();
        if (camera == null)
            camera = mc.player;
        if (camera == null)
            return;
        float partial = TickUtils.getFrameTime(mc.level);
        Vec3 pos = camera.getEyePosition(partial);
        this.startX = pos.x;
        this.startY = pos.y;
        this.startZ = pos.z;
        this.startYaw = camera.getViewYRot(partial);
        this.startPitch = camera.getViewXRot(partial);
        this.startZoom = CamEventHandlerClient.fovExact(partial);
        this.startRoll = CamEventHandlerClient.roll();
    }
    
    @Override
    public CamPoint calculatePoint(Level level, long position, float partialTicks) {
        capture();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return new CamPoint(startX, startY, startZ, startYaw, startPitch, startRoll, startZoom);
        
        double t = duration > 0 ? position / (double) duration : 1.0;
        t = Mth.clamp(t, 0.0, 1.0);
        t = t * t * (3.0 - 2.0 * t);
        float tf = (float) t;
        
        Vec3 eye = mc.player.getEyePosition(partialTicks);
        double x = Mth.lerp(t, startX, eye.x);
        double y = Mth.lerp(t, startY, eye.y);
        double z = Mth.lerp(t, startZ, eye.z);
        
        float targetYaw = mc.player.getViewYRot(partialTicks);
        float targetPitch = mc.player.getViewXRot(partialTicks);
        double yaw = Mth.rotLerp(tf, startYaw, targetYaw);
        double pitch = Mth.rotLerp(tf, startPitch, targetPitch);
        
        double targetZoom = CamEventHandlerClient.fovExactVanilla(partialTicks);
        double targetRoll = 0;
        if (run.scene.mode instanceof TrackingMode tracking) {
            targetZoom += tracking.fovOffsetBefore;
            targetRoll = tracking.rollBefore;
        }
        double zoom = Mth.lerp(t, startZoom, targetZoom);
        double roll = Mth.lerp(t, startRoll, targetRoll);
        
        return new CamPoint(x, y, z, yaw, pitch, roll, zoom);
    }
}
