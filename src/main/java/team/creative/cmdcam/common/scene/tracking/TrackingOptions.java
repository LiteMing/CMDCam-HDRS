package team.creative.cmdcam.common.scene.tracking;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import team.creative.cmdcam.client.SceneException;

/**
 * Runtime configuration of an entity bound camera.
 * <p>
 * Every value is optional. A {@code null} value means "not provided", in which case the default of the used camera mode is applied. This distinction matters
 * because an omitted FOV must not override the zoom animation of a template path, while an explicitly given FOV must.
 * <p>
 * This class is side independent, it must never reference a camera mode or any client only class.
 */
public class TrackingOptions {
    
    public static final double MIN_DISTANCE = 0.2D;
    public static final double MAX_DISTANCE = 64.0D;
    public static final double MIN_DISTANCE_SCALE = 0.05D;
    public static final double MAX_DISTANCE_SCALE = 20.0D;
    public static final double MIN_FOV = 10.0D;
    public static final double MAX_FOV = 170.0D;
    public static final double MIN_DAMPING_MS = 0.0D;
    public static final double MAX_DAMPING_MS = 5000.0D;
    public static final double MIN_PITCH_FOLLOW = 0.0D;
    public static final double MAX_PITCH_FOLLOW = 1.0D;
    public static final double MIN_YAW_FOLLOW = 0.0D;
    public static final double MAX_YAW_FOLLOW = 1.0D;
    public static final long MIN_TRANSITION_DURATION_MS = 0L;
    public static final long MAX_TRANSITION_DURATION_MS = 10000L;
    public static final long DEFAULT_RETURN_DURATION_MS = 750L;
    public static final long DEFAULT_ENTER_DURATION_MS = 750L;
    public static final int DEFAULT_FADE_COLOR = 0x000000;
    
    /** id of the camera mode used to run the scene, e.g. {@code closeup}, {@code shoulder} or {@code tracking} */
    public String modeId;
    
    /** absolute distance between camera and target, only used by built in presets */
    public Double distance;
    
    /** scales the local X/Z of every control point, only used by template paths */
    public Double distanceScale;
    
    /** absolute field of view, {@code null} keeps whatever the path provides */
    public Double fov;
    
    /** half life of the pose smoothing in milliseconds, {@code 0} disables smoothing */
    public Double dampingMs;
    
    /** how much of the target pitch the camera follows, {@code 0..1} */
    public Double pitchFollow;
    
    /** how much the camera follows head/view yaw vs body yaw, {@code 0..1} (0=body, 1=head/view) */
    public Double yawFollow;
    
    /** height anchor on the target bounding box, {@code 0..1} */
    public Double targetHeightFactor;
    
    /** entry transition style */
    public CamTransitionStyle enterStyle = CamTransitionStyle.SMOOTH;
    
    /** exit transition style */
    public CamTransitionStyle exitStyle = CamTransitionStyle.SMOOTH;
    
    /** duration of the smooth enter or fade-in, in milliseconds */
    public Long enterDurationMs;
    
    /** duration of the smooth return to the player or fade-out, in milliseconds */
    public long returnDurationMs = DEFAULT_RETURN_DURATION_MS;
    
    /** color used for fade transition, RGB (default 0x000000 = black) */
    public Integer fadeColor;
    
    public TrackingOptions() {
        this(null);
    }
    
    public TrackingOptions(String modeId) {
        this.modeId = modeId;
    }
    
    public TrackingOptions copy() {
        TrackingOptions options = new TrackingOptions(modeId);
        options.distance = distance;
        options.distanceScale = distanceScale;
        options.fov = fov;
        options.dampingMs = dampingMs;
        options.pitchFollow = pitchFollow;
        options.yawFollow = yawFollow;
        options.targetHeightFactor = targetHeightFactor;
        options.enterStyle = enterStyle;
        options.exitStyle = exitStyle;
        options.enterDurationMs = enterDurationMs;
        options.returnDurationMs = returnDurationMs;
        options.fadeColor = fadeColor;
        return options;
    }
    
    public double distanceOrDefault(double fallback) {
        return distance != null ? clamp(distance, MIN_DISTANCE, MAX_DISTANCE) : fallback;
    }
    
    public double distanceScaleOrDefault(double fallback) {
        return distanceScale != null ? clamp(distanceScale, MIN_DISTANCE_SCALE, MAX_DISTANCE_SCALE) : fallback;
    }
    
    public double fovOrDefault(double fallback) {
        return fov != null ? clamp(fov, MIN_FOV, MAX_FOV) : fallback;
    }
    
    public double dampingOrDefault(double fallback) {
        return dampingMs != null ? clamp(dampingMs, MIN_DAMPING_MS, MAX_DAMPING_MS) : fallback;
    }
    
    public double pitchFollowOrDefault(double fallback) {
        return pitchFollow != null ? clamp(pitchFollow, MIN_PITCH_FOLLOW, MAX_PITCH_FOLLOW) : fallback;
    }
    
    public double yawFollowOrDefault(double fallback) {
        return yawFollow != null ? clamp(yawFollow, MIN_YAW_FOLLOW, MAX_YAW_FOLLOW) : fallback;
    }
    
    public double heightFactorOrDefault(double fallback) {
        return targetHeightFactor != null ? Mth.clamp(targetHeightFactor, 0.0D, 1.0D) : fallback;
    }
    
    public long enterDurationOrDefault(long fallback) {
        return enterDurationMs != null && enterDurationMs >= MIN_TRANSITION_DURATION_MS && enterDurationMs <= MAX_TRANSITION_DURATION_MS
                ? enterDurationMs : fallback;
    }
    
    public long returnDurationOrDefault(long fallback) {
        return returnDurationMs >= MIN_TRANSITION_DURATION_MS && returnDurationMs <= MAX_TRANSITION_DURATION_MS ? returnDurationMs : fallback;
    }
    
    public int fadeColorOrDefault(int fallback) {
        return fadeColor != null ? fadeColor : fallback;
    }
    
    private static boolean invalid(Double value, double min, double max) {
        return value != null && (!Double.isFinite(value) || value < min || value > max);
    }
    
    /** Rejects out of range values or non-finite numbers (NaN, Infinity) instead of silently clamping them. */
    public void validate() throws SceneException {
        if (invalid(distance, MIN_DISTANCE, MAX_DISTANCE))
            throw new SceneException("scene.tracking.invalid_distance", MIN_DISTANCE, MAX_DISTANCE);
        if (invalid(distanceScale, MIN_DISTANCE_SCALE, MAX_DISTANCE_SCALE))
            throw new SceneException("scene.tracking.invalid_distance_scale", MIN_DISTANCE_SCALE, MAX_DISTANCE_SCALE);
        if (invalid(fov, MIN_FOV, MAX_FOV))
            throw new SceneException("scene.tracking.invalid_fov", MIN_FOV, MAX_FOV);
        if (invalid(dampingMs, MIN_DAMPING_MS, MAX_DAMPING_MS))
            throw new SceneException("scene.tracking.invalid_damping", MIN_DAMPING_MS, MAX_DAMPING_MS);
        if (invalid(pitchFollow, MIN_PITCH_FOLLOW, MAX_PITCH_FOLLOW))
            throw new SceneException("scene.tracking.invalid_pitch_follow", MIN_PITCH_FOLLOW, MAX_PITCH_FOLLOW);
        if (invalid(yawFollow, MIN_YAW_FOLLOW, MAX_YAW_FOLLOW))
            throw new SceneException("scene.tracking.invalid_yaw_follow", MIN_YAW_FOLLOW, MAX_YAW_FOLLOW);
        if (invalid(targetHeightFactor, 0.0D, 1.0D))
            throw new SceneException("scene.tracking.invalid_height_factor", 0.0D, 1.0D);
        if (enterDurationMs != null && (enterDurationMs < MIN_TRANSITION_DURATION_MS || enterDurationMs > MAX_TRANSITION_DURATION_MS))
            throw new SceneException("scene.tracking.invalid_enter_duration", MIN_TRANSITION_DURATION_MS, MAX_TRANSITION_DURATION_MS);
        if (returnDurationMs < MIN_TRANSITION_DURATION_MS || returnDurationMs > MAX_TRANSITION_DURATION_MS)
            throw new SceneException("scene.tracking.invalid_exit_duration", MIN_TRANSITION_DURATION_MS, MAX_TRANSITION_DURATION_MS);
    }
    
    public CompoundTag save(CompoundTag nbt) {
        if (modeId != null)
            nbt.putString("mode", modeId);
        if (distance != null)
            nbt.putDouble("distance", distance);
        if (distanceScale != null)
            nbt.putDouble("distance_scale", distanceScale);
        if (fov != null)
            nbt.putDouble("fov", fov);
        if (dampingMs != null)
            nbt.putDouble("damping", dampingMs);
        if (pitchFollow != null)
            nbt.putDouble("pitch_follow", pitchFollow);
        if (yawFollow != null)
            nbt.putDouble("yaw_follow", yawFollow);
        if (targetHeightFactor != null)
            nbt.putDouble("height_factor", targetHeightFactor);
        if (enterStyle != null)
            nbt.putString("enter_style", enterStyle.getId());
        if (exitStyle != null)
            nbt.putString("exit_style", exitStyle.getId());
        if (enterDurationMs != null)
            nbt.putLong("enter_duration", enterDurationMs);
        nbt.putLong("return_duration", returnDurationMs);
        if (fadeColor != null)
            nbt.putInt("fade_color", fadeColor);
        return nbt;
    }
    
    public static TrackingOptions load(CompoundTag nbt) {
        if (nbt == null || nbt.isEmpty())
            return new TrackingOptions();
        TrackingOptions options = new TrackingOptions();
        options.modeId = nbt.contains("mode") ? nbt.getString("mode") : null;
        options.distance = nbt.contains("distance") ? nbt.getDouble("distance") : null;
        options.distanceScale = nbt.contains("distance_scale") ? nbt.getDouble("distance_scale") : null;
        options.fov = nbt.contains("fov") ? nbt.getDouble("fov") : null;
        options.dampingMs = nbt.contains("damping") ? nbt.getDouble("damping") : null;
        options.pitchFollow = nbt.contains("pitch_follow") ? nbt.getDouble("pitch_follow") : null;
        options.yawFollow = nbt.contains("yaw_follow") ? nbt.getDouble("yaw_follow") : null;
        options.targetHeightFactor = nbt.contains("height_factor") ? nbt.getDouble("height_factor") : null;
        if (nbt.contains("enter_style"))
            options.enterStyle = CamTransitionStyle.fromString(nbt.getString("enter_style"));
        if (nbt.contains("exit_style"))
            options.exitStyle = CamTransitionStyle.fromString(nbt.getString("exit_style"));
        if (nbt.contains("enter_duration"))
            options.enterDurationMs = nbt.getLong("enter_duration");
        options.returnDurationMs = nbt.contains("return_duration") ? nbt.getLong("return_duration") : DEFAULT_RETURN_DURATION_MS;
        if (nbt.contains("fade_color"))
            options.fadeColor = nbt.getInt("fade_color");
        return options;
    }
    
    private static double clamp(double value, double min, double max) {
        return Mth.clamp(value, min, max);
    }
    
}
