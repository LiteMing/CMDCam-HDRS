package team.creative.cmdcam.common.scene;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import team.creative.cmdcam.common.math.follow.CamFollowConfig;
import team.creative.cmdcam.common.math.interpolation.CamInterpolation;
import team.creative.cmdcam.common.math.interpolation.CamPitchMode;
import team.creative.cmdcam.common.math.point.CamPoint;
import team.creative.cmdcam.common.scene.attribute.CamAttribute;
import team.creative.cmdcam.common.scene.mode.CamMode;
import team.creative.cmdcam.common.scene.mode.DefaultMode;
import team.creative.cmdcam.common.scene.run.CamRun;
import team.creative.cmdcam.common.scene.tracking.TrackingOptions;
import team.creative.cmdcam.common.target.CamTarget;
import team.creative.creativecore.common.util.math.vec.Vec1d;
import team.creative.creativecore.common.util.math.vec.Vec3d;
import team.creative.creativecore.common.util.math.vec.VecNd;
import team.creative.creativecore.common.util.registry.exception.RegistryException;

public class CamScene {
    
    public static CamScene createDefault() {
        return new CamScene(10000, 0, "default", new ArrayList<>(), CamInterpolation.HERMITE);
    }
    
    private boolean started = false;
    
    private boolean serverSynced = false;
    
    public long duration;
    public int loop = 0;
    
    public CamMode mode;
    public CamInterpolation interpolation;
    
    public CamTarget lookTarget;
    public CamFollowConfig<Vec1d> pitchFollowConfig = new CamFollowConfig<>(CamAttribute.PITCH, 10);
    public CamFollowConfig<Vec1d> yawFollowConfig = new CamFollowConfig<>(CamAttribute.YAW, 10);
    
    /** if null it will be the same as the lookTarget */
    public CamTarget posTarget;
    public CamFollowConfig<Vec3d> posFollowConfig = new CamFollowConfig<>(CamAttribute.POSITION, 2);
    
    //public boolean targetBodyRotation = false;
    //public boolean targetHeadRotation = false;
    
    public List<CamPoint> points;
    
    public boolean smoothBeginning = true;
    public CamPitchMode pitchMode = CamPitchMode.FIX_KEEP_DIRECTION;
    public boolean distanceBasedTiming = false;
    
    public boolean tracking = false;
    public double targetHeightFactor = 0.65D;
    public long targetReturnDuration = 750L;
    /** runtime configuration of the entity bound camera, only present while {@link #tracking} is true */
    public TrackingOptions trackingOptions;
    /**
     * The target entity's body yaw (degrees) at the time the tracking path was authored.
     * Used by {@code FollowPathMode} to rotate the authored control-point angles into the
     * target's local space on playback, so the camera looks in the correct direction even
     * when the target is facing a different way than when the path was recorded.
     *
     * <p>{@code Float.NaN} means "not set" (legacy scene authored before this field
     * existed). In that case {@code FollowPathMode} falls back to the old delta-from-
     * first-frame behaviour, which is correct for scenes authored against their original
     * target but wrong for reuse against a differently-facing entity.
     */
    public float trackingReferenceYaw = Float.NaN;
    
    @OnlyIn(Dist.CLIENT)
    public CamRun run;
    
    public CamScene(long duration, int loop, String mode, List<CamPoint> points, CamInterpolation interpolation) {
        this.duration = duration;
        setMode(mode);
        this.points = points;
        this.interpolation = interpolation;
    }
    
    public CamScene(CompoundTag nbt) throws RegistryException {
        this.duration = nbt.getLong("duration");
        this.loop = nbt.getInt("loop");
        
        setMode(nbt.getString("mode"));
        this.interpolation = CamInterpolation.REGISTRY.get(nbt.getString("inter"));
        
        this.lookTarget = nbt.contains("look_target") ? CamTarget.load(nbt.getCompound("look_target")) : null;
        this.pitchFollowConfig.load(nbt.getCompound("pitch"));
        this.yawFollowConfig.load(nbt.getCompound("yaw"));
        
        this.posTarget = nbt.contains("pos_target") ? CamTarget.load(nbt.getCompound("pos_target")) : null;
        this.posFollowConfig.load(nbt.getCompound("pos"));
        
        ListTag list = nbt.getList("points", 10);
        this.points = new ArrayList<>();
        for (Tag point : list)
            points.add(new CamPoint((CompoundTag) point));
        
        this.smoothBeginning = nbt.getBoolean("smooth_start");
        this.pitchMode = CamPitchMode.values()[nbt.getInt("pitch_mode")];
        this.distanceBasedTiming = nbt.getBoolean("d_timing");
        this.trackingReferenceYaw = nbt.contains("tracking_ref_yaw")
            ? nbt.getFloat("tracking_ref_yaw") : Float.NaN;
    }
    
    public void setServerSynced() {
        serverSynced = true;
    }
    
    public CamScene bindTracking(UUID uuid, double heightFactor, long returnDuration) {
        TrackingOptions options = new TrackingOptions();
        options.targetHeightFactor = heightFactor;
        options.returnDurationMs = returnDuration;
        return bindTracking(uuid, options);
    }
    
    public CamScene bindTracking(UUID uuid, TrackingOptions options) {
        this.tracking = true;
        this.trackingOptions = options != null ? options : new TrackingOptions();
        this.targetHeightFactor = this.trackingOptions.heightFactorOrDefault(targetHeightFactor);
        this.targetReturnDuration = this.trackingOptions.returnDurationOrDefault(targetReturnDuration);
        this.lookTarget = new CamTarget.EntityTarget(uuid);
        this.posTarget = new CamTarget.EntityTarget(uuid);
        return this;
    }
    
    public CompoundTag save(CompoundTag nbt) {
        nbt.putLong("duration", duration);
        nbt.putInt("loop", loop);
        
        nbt.putString("mode", CamMode.REGISTRY.getId(mode));
        nbt.putString("inter", CamInterpolation.REGISTRY.getId(interpolation));
        
        if (lookTarget != null)
            nbt.put("look_target", lookTarget.save(new CompoundTag()));
        nbt.put("pitch", pitchFollowConfig.save(new CompoundTag()));
        nbt.put("yaw", yawFollowConfig.save(new CompoundTag()));
        
        if (posTarget != null)
            nbt.put("pos_target", posTarget.save(new CompoundTag()));
        nbt.put("pos", posFollowConfig.save(new CompoundTag()));
        
        ListTag list = new ListTag();
        for (CamPoint point : points)
            list.add(point.save(new CompoundTag()));
        nbt.put("points", list);
        
        nbt.putBoolean("smooth_start", smoothBeginning);
        nbt.putInt("pitch_mode", pitchMode.ordinal());
        nbt.putBoolean("d_timing", distanceBasedTiming);
        if (!Float.isNaN(trackingReferenceYaw))
            nbt.putFloat("tracking_ref_yaw", trackingReferenceYaw);
        
        return nbt;
    }
    
    public boolean endless() {
        return loop < 0;
    }
    
    public boolean serverSynced() {
        return serverSynced;
    }
    
    public void play() {
        started = true;
    }
    
    public boolean paused() {
        return run == null || !run.playing();
    }
    
    public void togglePause() {
        if (playing())
            if (paused())
                resume();
            else
                pause();
    }
    
    public void pause() {
        if (run != null)
            run.pause();
    }
    
    public void resume() {
        if (run != null)
            run.resume();
    }
    
    public void stop() {
        if (run != null)
            run.stop();
    }
    
    public boolean playing() {
        return run != null;
    }
    
    protected void started(Level level) {
        if (lookTarget != null)
            lookTarget.start(level);
        if (posTarget != null)
            posTarget.start(level);
        
        if (level.isClientSide) {
            run = new CamRun(level, this);
            mode.started(run);
        }
    }
    
    public void finish(Level level) {
        if (lookTarget != null)
            lookTarget.finish();
        if (posTarget != null)
            posTarget.finish();
        
        stop();
        if (run != null) {
            CamRun currentRun = run;
            try {
                if (mode != null)
                    mode.finished(currentRun);
            } finally {
                try {
                    currentRun.finish();
                } finally {
                    run = null;
                }
            }
        }
        
        started = false;
    }
    
    public void renderTick(Level level, float deltaTime) {
        if (started) {
            started = false;
            started(level);
        }
        
        if (run != null)
            run.renderTick(level, deltaTime);
    }
    
    public void gameTick(Level level) {
        if (started) {
            started = false;
            started(level);
        }
        
        if (run != null)
            run.gameTick(level);
    }
    
    public void set(CamScene scene) {
        this.duration = scene.duration;
        this.loop = scene.loop;
        setMode(CamMode.REGISTRY.getId(scene.mode));
        this.points = scene.copyPoints();
        this.interpolation = scene.interpolation;
        this.serverSynced = scene.serverSynced;
        this.lookTarget = scene.lookTarget;
        this.pitchFollowConfig = scene.pitchFollowConfig;
        this.yawFollowConfig = scene.yawFollowConfig;
        this.posTarget = scene.posTarget;
        this.posFollowConfig = scene.posFollowConfig;
        this.smoothBeginning = scene.smoothBeginning;
        this.pitchMode = scene.pitchMode;
        this.distanceBasedTiming = scene.distanceBasedTiming;
        this.tracking = scene.tracking;
        this.targetHeightFactor = scene.targetHeightFactor;
        this.targetReturnDuration = scene.targetReturnDuration;
        this.trackingOptions = scene.trackingOptions;
        this.trackingReferenceYaw = scene.trackingReferenceYaw;
    }
    
    public void setMode(String mode) {
        this.mode = CamMode.REGISTRY.createSafe(DefaultMode.class, mode, this);
    }
    
    private List<CamPoint> copyPoints() {
        List<CamPoint> newPoints = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++)
            newPoints.add(points.get(i).copy());
        return newPoints;
    }
    
    public CamScene copy() {
        CamScene scene = new CamScene(duration, loop, CamMode.REGISTRY.getId(mode), copyPoints(), interpolation);
        scene.set(this);
        return scene;
    }
    
    public <T extends VecNd> CamFollowConfig<T> getConfig(CamAttribute<T> attribute) {
        if (attribute == CamAttribute.POSITION)
            return (CamFollowConfig<T>) posFollowConfig;
        if (attribute == CamAttribute.PITCH)
            return (CamFollowConfig<T>) pitchFollowConfig;
        if (attribute == CamAttribute.YAW)
            return (CamFollowConfig<T>) yawFollowConfig;
        return null;
    }
    
}
