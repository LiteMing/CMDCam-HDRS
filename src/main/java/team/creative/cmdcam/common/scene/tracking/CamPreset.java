package team.creative.cmdcam.common.scene.tracking;

/**
 * Side independent description of a built in entity bound camera.
 * <p>
 * The server needs these values to build the scene it sends to the client, while the client camera mode needs them as fallback defaults. Keeping them in one
 * place avoids duplicating magic numbers on both sides and keeps the dedicated server free of client only classes.
 */
public class CamPreset {
    
    public static final String CLOSEUP = "closeup";
    public static final String SHOULDER = "shoulder";
    public static final String TRACKING = "tracking";
    
    public static final CamPreset CLOSEUP_PRESET = new CamPreset(CLOSEUP, 0.0D, 0.1D, -1.5D, 0.0D, 0.0D, 50.0D, 1.5D, 0.78D, 250.0D, 0.65D);
    public static final CamPreset SHOULDER_PRESET = new CamPreset(SHOULDER, 0.8D, 0.4D, 1.8D, 4.0D, 0.1D, 75.0D, 1.8D, 0.65D, 350.0D, 0.35D);
    public static final CamPreset TRACKING_PRESET = new CamPreset(TRACKING, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 70.0D, 1.6D, 0.65D, 300.0D, 0.5D);
    
    private static final CamPreset[] PRESETS = new CamPreset[] { CLOSEUP_PRESET, SHOULDER_PRESET, TRACKING_PRESET };
    
    public static CamPreset get(String id) {
        if (id != null)
            for (CamPreset preset : PRESETS)
                if (preset.id.equals(id))
                    return preset;
        return TRACKING_PRESET;
    }
    
    public static boolean isPreset(String id) {
        if (id == null)
            return false;
        for (CamPreset preset : PRESETS)
            if (preset.id.equals(id))
                return true;
        return false;
    }
    
    /** registry id of the camera mode, also used as {@code modeId} of {@link TrackingOptions} */
    public final String id;
    
    /** local offset of the camera, X = right, Y = up, Z = behind, -Z = in front of the target */
    public final double offsetX;
    public final double offsetY;
    public final double offsetZ;
    
    /** distance in front of the target the camera looks at */
    public final double lookAhead;
    /** vertical offset of the look at position */
    public final double lookHeight;
    
    public final double fov;
    /** base distance the {@code offset} was authored for, used to scale it with the {@code distance} parameter */
    public final double distance;
    public final double heightFactor;
    /** pose smoothing half life in milliseconds */
    public final double dampingMs;
    /** how much of the target pitch the camera follows, {@code 0..1} */
    public final double pitchFollow;
    
    public CamPreset(String id, double offsetX, double offsetY, double offsetZ, double lookAhead, double lookHeight, double fov, double distance, double heightFactor,
            double dampingMs, double pitchFollow) {
        this.id = id;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.lookAhead = lookAhead;
        this.lookHeight = lookHeight;
        this.fov = fov;
        this.distance = distance;
        this.heightFactor = heightFactor;
        this.dampingMs = dampingMs;
        this.pitchFollow = pitchFollow;
    }
    
}
