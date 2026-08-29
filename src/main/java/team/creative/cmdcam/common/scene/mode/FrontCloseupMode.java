package team.creative.cmdcam.common.scene.mode;

import team.creative.cmdcam.common.scene.CamScene;
import team.creative.cmdcam.common.scene.tracking.CamPreset;

public class FrontCloseupMode extends TrackingMode {
    
    public FrontCloseupMode(CamScene scene) {
        super(scene, CamPreset.CLOSEUP_PRESET);
    }
    
}
