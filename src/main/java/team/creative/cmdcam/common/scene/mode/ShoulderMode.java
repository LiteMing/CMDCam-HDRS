package team.creative.cmdcam.common.scene.mode;

import team.creative.cmdcam.common.scene.CamScene;
import team.creative.cmdcam.common.scene.tracking.CamPreset;

public class ShoulderMode extends TrackingMode {
    
    public ShoulderMode(CamScene scene) {
        super(scene, CamPreset.SHOULDER_PRESET);
    }
    
}
