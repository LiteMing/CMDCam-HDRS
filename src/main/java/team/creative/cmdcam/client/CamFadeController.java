package team.creative.cmdcam.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/**
 * Renders full-screen solid color overlays for smooth camera transitions.
 * Independent of HUD hideGui and title rendering.
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
    private static float currentAlpha = 0.0F;
    private static Runnable onMidpointAction = null;
    private static Runnable onCompleteAction = null;
    
    public static boolean isActive() {
        return state != State.IDLE || currentAlpha > 0.001F;
    }
    
    public static void reset() {
        state = State.IDLE;
        currentAlpha = 0.0F;
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
        colorRgb = rgb;
        duration = half;
        onMidpointAction = onMidpoint;
        onCompleteAction = onComplete;
        startTime = Util.getMillis();
        state = State.FADING_OUT;
        currentAlpha = 0.0F;
    }
    
    public static void update() {
        if (state == State.IDLE)
            return;
        
        long elapsed = Util.getMillis() - startTime;
        float progress = duration > 0 ? (float) elapsed / (float) duration : 1.0F;
        progress = Mth.clamp(progress, 0.0F, 1.0F);
        
        if (state == State.FADING_OUT) {
            currentAlpha = progress;
            if (progress >= 1.0F) {
                currentAlpha = 1.0F;
                if (onMidpointAction != null) {
                    Runnable action = onMidpointAction;
                    onMidpointAction = null;
                    action.run();
                }
                // Transition to fade-in
                state = State.FADING_IN;
                startTime = Util.getMillis();
            }
        } else if (state == State.FADING_IN) {
            currentAlpha = 1.0F - progress;
            if (progress >= 1.0F) {
                currentAlpha = 0.0F;
                state = State.IDLE;
                if (onCompleteAction != null) {
                    Runnable action = onCompleteAction;
                    onCompleteAction = null;
                    action.run();
                }
            }
        }
    }
    
    public static void renderOverlay(int screenWidth, int screenHeight) {
        if (currentAlpha <= 0.001F)
            return;
        
        float r = ((colorRgb >> 16) & 0xFF) / 255.0F;
        float g = ((colorRgb >> 8) & 0xFF) / 255.0F;
        float b = (colorRgb & 0xFF) / 255.0F;
        float a = Mth.clamp(currentAlpha, 0.0F, 1.0F);
        
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        
        Matrix4f matrix = new Matrix4f().ortho(0, screenWidth, screenHeight, 0, -1000.0F, 3000.0F);
        RenderSystem.setProjectionMatrix(matrix, com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.getModelViewStack().pushPose();
        RenderSystem.getModelViewStack().setIdentity();
        RenderSystem.applyModelViewMatrix();
        
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder buffer = tessellator.getBuilder();
        buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(0, screenHeight, 0).color(r, g, b, a).endVertex();
        buffer.vertex(screenWidth, screenHeight, 0).color(r, g, b, a).endVertex();
        buffer.vertex(screenWidth, 0, 0).color(r, g, b, a).endVertex();
        buffer.vertex(0, 0, 0).color(r, g, b, a).endVertex();
        tessellator.end();
        
        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
