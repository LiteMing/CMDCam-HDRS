package team.creative.cmdcam.common.scene.mode;

import team.creative.cmdcam.common.scene.CamScene;
import team.creative.creativecore.common.util.math.vec.Vec3d;

public class FrontCloseupMode extends TrackingMode {
    
    public FrontCloseupMode(CamScene scene) {
        super(scene);
        this.localOffset = new Vec3d(0, 0.1, -1.5);
        this.lookAhead = 0;
        this.lookHeight = 0;
        this.defaultFov = 50;
        this.defaultHeightFactor = 0.78;
    }
}
