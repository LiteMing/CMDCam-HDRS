package team.creative.cmdcam.common.scene.run;

public enum CamStopReason {
    NATURAL_END(true),
    COMMAND_STOP(true),
    TARGET_LOST(true),
    OVERWRITE(false),
    DISCONNECT(false),
    WORLD_UNLOAD(false),
    DIMENSION_CHANGE(false),
    PLAYER_DEAD(false);
    
    public final boolean smoothReturn;
    
    CamStopReason(boolean smoothReturn) {
        this.smoothReturn = smoothReturn;
    }
}
