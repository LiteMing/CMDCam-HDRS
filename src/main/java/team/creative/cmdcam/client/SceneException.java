package team.creative.cmdcam.client;

import net.minecraft.network.chat.Component;

public class SceneException extends Exception {
    
    private static final Object[] NO_ARGS = new Object[0];
    
    private final Object[] args;
    
    public SceneException(String msg) {
        super(msg);
        this.args = NO_ARGS;
    }
    
    public SceneException(String msg, Object... args) {
        super(msg);
        this.args = args != null ? args : NO_ARGS;
    }
    
    public Component getComponent() {
        return Component.translatable(getMessage(), args);
    }
    
}
