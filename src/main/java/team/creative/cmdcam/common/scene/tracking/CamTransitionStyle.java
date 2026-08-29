package team.creative.cmdcam.common.scene.tracking;

import java.util.Locale;

public enum CamTransitionStyle {
    SMOOTH,
    CUT,
    FADE;
    
    public static CamTransitionStyle fromString(String name) {
        if (name == null)
            return null;
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    public String getId() {
        return name().toLowerCase(Locale.ROOT);
    }
}
