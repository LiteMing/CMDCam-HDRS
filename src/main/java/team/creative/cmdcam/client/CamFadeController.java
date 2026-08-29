package team.creative.cmdcam.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * High-precision, frame-rate independent full-screen color fade controller.
 * State updates happen on client ticks, while alpha evaluation and rendering happen smoothly every frame.
 */
@OnlyIn(Dist.CLIENT)
public class CamFadeController {
    
    private static final Minecraft MC = Minecraft.getInstance();
    
    public enum State {
        IDLE,
        FADING_OUT, // Screen fades to color (alpha 0 -> 1)
        FADING_IN   // Screen fades from color to game (alpha 1 -> 0)
    }
    
    private static State state = State.IDLE;
    private static long startTime = 0L;
    private static long duration = 0L;
    private static int colorRgb = 0x000000;
    private static Runnable onMidpointAction = null;
    private static Runnable onCompleteAction = null;
    
    public static boolean isActive() {
        return state != State.IDLE;
    }
    
    public static void reset() {
        state = State.IDLE;
        onMidpointAction = null;
        onCompleteAction = null;
    }
    
    /**
     * Starts a full two-phase fade transition:
     * 1. Fades to color in duration/2
     * 2. Runs onMidpoint (camera switch / setup)
     * 3. Fades from color in duration/2
     * 4. Runs onComplete
     */
    public static void startFullTransition(long totalDurationMs, int rgb, Runnable onMidpoint, Runnable onComplete) {
        if (totalDurationMs <= 0) {
            if (onMidpoint != null)
                onMidpoint.run();
            if (onComplete != null)
                onComplete.run();
            reset();
            return;
        }
        
        long half = Math.max(totalDurationMs / 2, 1L);
        colorRgb = rgb & 0xFFFFFF;
        duration = half;
        onMidpointAction = onMidpoint;
        onCompleteAction = onComplete;
        startTime = Util.getMillis();
        state = State.FADING_OUT;
    }
    
    /** Called on ClientTickEvent to drive phase transitions and callbacks. */
    public static void update() {
        if (state == State.IDLE)
            return;
        
        long elapsed = Util.getMillis() - startTime;
        if (state == State.FADING_OUT) {
            if (elapsed >= duration) {
                // Switch phase before running midpoint action so subsequent finish/start calls know we are fading in
                state = State.FADING_IN;
                startTime = Util.getMillis();
                if (onMidpointAction != null) {
                    Runnable action = onMidpointAction;
                    onMidpointAction = null;
                    action.run();
                }
            }
        } else if (state == State.FADING_IN) {
            if (elapsed >= duration) {
                state = State.IDLE;
                if (onCompleteAction != null) {
                    Runnable action = onCompleteAction;
                    onCompleteAction = null;
                    action.run();
                }
            }
        }
    }
    
    /** Calculates smooth per-frame alpha value using millisecond timestamp. */
    public static float getRenderAlpha() {
        if (state == State.IDLE)
            return 0.0F;
        
        long elapsed = Util.getMillis() - startTime;
        float progress = duration > 0 ? (float) elapsed / (float) duration : 1.0F;
        progress = Mth.clamp(progress, 0.0F, 1.0F);
        
        if (state == State.FADING_OUT)
            return progress;
        else if (state == State.FADING_IN)
            return 1.0F - progress;
        return 0.0F;
    }
    
    /** Renders overlay once per frame using vanilla GuiGraphics to avoid matrix/state corruption. */
    public static void renderOverlay(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        float alpha = getRenderAlpha();
        if (alpha <= 0.001F)
            return;
        
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        int argb = (a << 24) | colorRgb;
        
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        guiGraphics.fill(0, 0, screenWidth, screenHeight, argb);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}
