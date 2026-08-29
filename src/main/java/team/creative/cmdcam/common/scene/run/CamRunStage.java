package team.creative.cmdcam.common.scene.run;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import team.creative.cmdcam.common.math.follow.CamFollow;
import team.creative.cmdcam.common.math.follow.CamFollowConfig;
import team.creative.cmdcam.common.math.interpolation.CamInterpolation;
import team.creative.cmdcam.common.math.point.CamPoint;
import team.creative.cmdcam.common.math.point.CamPoints;
import team.creative.cmdcam.common.scene.attribute.CamAttribute;
import team.creative.cmdcam.common.scene.mode.TrackingMode;
import team.creative.creativecore.common.util.math.interpolation.Interpolation;
import team.creative.creativecore.common.util.math.vec.Vec3d;
import team.creative.creativecore.common.util.math.vec.VecNd;

public class CamRunStage {
    
    public final CamRun run;
    public final long duration;
    public final int loops;
    public int looped = 0;
    private boolean started = false;
    private HashMap<CamAttribute, Interpolation> attributes = new HashMap<>();
    private HashMap<CamAttribute, CamFollow> followAttributes;
    private CamPoint lastTrackedPoint;
    
    public CamRunStage(CamRun run, CamInterpolation inter, long duration, int loops, CamPoints points) {
        this.run = run;
        this.duration = duration;
        this.loops = loops;
        
        double[] times = points.createTimes(run.scene);
        
        CamAttribute[] toStore = run.attributes();
        for (int i = 0; i < toStore.length; i++) {
            List vecs = new ArrayList(points.size());
            for (CamPoint point : points)
                vecs.add(toStore[i].get(point));
            attributes.put(toStore[i], points.interpolate(times, run.scene, inter, toStore[i]));
        }
        
    }
    
    public boolean hasStarted() {
        return started;
    }
    
    private <T extends VecNd> void addFollow(CamAttribute<T> attribute, CamFollowConfig<T> config, CamPoint point) {
        followAttributes.put(attribute, config.create(attribute.get(point)));
    }
    
    public void start() {
        followAttributes = new HashMap<>();
        CamPoint initial = CamPoint.create(run.scene.mode.getCamera());
        
        if (run.scene.lookTarget != null) {
            addFollow(CamAttribute.PITCH, run.scene.pitchFollowConfig, initial);
            addFollow(CamAttribute.YAW, run.scene.yawFollowConfig, initial);
        }
        
        if (run.scene.posTarget != null)
            addFollow(CamAttribute.POSITION, run.scene.posFollowConfig, initial);
        
        // Second-layer defence: seed lastTrackedPoint with the camera's current world position
        // so that if tracking.calculate() returns null on the first few frames, the camera stays
        // put rather than jumping to the local control-point coordinates (e.g. x=0, y=0.1, z=-1.5)
        // interpreted as world coordinates.
        if (run.scene.tracking) {
            Entity camera = run.scene.mode.getCamera();
            if (camera != null)
                lastTrackedPoint = CamPoint.create(camera);
        }
        
        started = true;
    }
    
    public CamPoint calculatePoint(Level level, long position, float partialTicks) {
        double progress = position / (double) duration;
        
        HashMap<CamAttribute, VecNd> generated = new HashMap<>();
        for (Entry<CamAttribute, Interpolation> entry : attributes.entrySet())
            generated.put(entry.getKey(), entry.getValue().valueAt(progress));
        
        CamPoint point = new CamPoint(generated);
        
        if (run.scene.tracking && run.scene.mode instanceof TrackingMode tracking) {
            CamPoint tracked = tracking.calculate(run.scene, level, partialTicks, point);
            if (tracked != null) {
                lastTrackedPoint = tracked.copy();
                return tracked;
            }
            if (lastTrackedPoint != null)
                return lastTrackedPoint.copy();
            return point;
        }
        
        CamPoint targetPoint = new CamPoint(0, 0, 0, 0, 0, 0, 0);

        if (run.scene.posTarget != null) {
            targetPoint.set(point);
            var newPos = run.scene.posTarget.position(level, partialTicks);
            if (newPos != null) {
                Vec3d vec = new Vec3d(newPos);
                run.scene.mode.correctTargetPosition(vec);
                targetPoint.add(vec);
            }
        }

        if (run.scene.lookTarget != null) {
            Vec3d vec = run.scene.lookTarget.position(level, partialTicks);

            if (vec != null) {
                run.scene.mode.correctTargetPosition(vec);

                double d0 = vec.x - targetPoint.x;
                double d1 = vec.y - targetPoint.y;
                double d2 = vec.z - targetPoint.z;

                double d3 = Math.sqrt(d0 * d0 + d2 * d2);
                targetPoint.rotationPitch = (-(Math.atan2(d1, d3) * 180.0D / Math.PI));
                targetPoint.rotationYaw = (Math.atan2(d2, d0) * 180.0D / Math.PI) - 90.0D;
            }
        }
        
        for (Entry<CamAttribute, CamFollow> entry : followAttributes.entrySet())
            entry.getKey().set(point, entry.getValue().follow(entry.getKey().get(targetPoint)));
        
        return point;
    }
    
    public boolean endless() {
        return loops < 0;
    }
    
}
