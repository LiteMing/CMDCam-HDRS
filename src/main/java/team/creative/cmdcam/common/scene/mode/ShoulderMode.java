package team.creative.cmdcam.common.scene.mode;

import team.creative.cmdcam.common.scene.CamScene;
import team.creative.creativecore.common.util.math.vec.Vec3d;

public class ShoulderMode extends TrackingMode {
    
    public ShoulderMode(CamScene scene) {
        super(scene);
        this.localOffset = new Vec3d(0.8, 0.4, 1.3);
        this.lookAhead = 4.0;
        this.lookHeight = 0.1;
        this.defaultFov = 75;
        this.defaultHeightFactor = 0.65;
    }
}
