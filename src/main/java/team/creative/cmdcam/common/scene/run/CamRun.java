package team.creative.cmdcam.common.scene.run;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import team.creative.cmdcam.CMDCam;
import team.creative.cmdcam.client.CMDCamClient;
import team.creative.cmdcam.client.SceneException;
import team.creative.cmdcam.common.math.interpolation.CamInterpolation;
import team.creative.cmdcam.common.math.interpolation.CamPitchMode;
import team.creative.cmdcam.common.math.point.CamPoint;
import team.creative.cmdcam.common.math.point.CamPoints;
import team.creative.cmdcam.common.mod.minema.MinemaAddon;
import team.creative.cmdcam.common.mod.minema.MinemaTimer;
import team.creative.cmdcam.common.scene.CamScene;
import team.creative.cmdcam.common.scene.attribute.CamAttribute;
import team.creative.cmdcam.common.scene.timer.RealTimeTimer;
import team.creative.cmdcam.common.scene.timer.RunTimer;
import team.creative.creativecore.common.util.mc.TickUtils;

@OnlyIn(Dist.CLIENT)
public class CamRun {
    
    private static Minecraft mc = Minecraft.getInstance();
    
    public static final CamAttribute[] PATH_ATTRIBUTES = new CamAttribute[] { CamAttribute.POSITION, CamAttribute.PITCH, CamAttribute.YAW, CamAttribute.ZOOM, CamAttribute.ROLL };
    
    public final CamScene scene;
    protected final List<CamRunStage> stages = new ArrayList<>();
    
    public double sizeOfIteration;
    
    private RunTimer timer;
    private boolean running;
    private int currentStage;
    private boolean finished;
    private int returnStageIndex = -1;
    private boolean returnRequested = false;
    private boolean returning = false;
    private int targetMissingTicks = 0;
    private CamStopReason stopReason;
    
    public CamRun(Level level, CamScene scene) {
        Entity camera = Minecraft.getInstance().player;
        this.scene = scene;
        
        boolean smoothEntry = scene.smoothBeginning;
        long enterDuration = 750L;
        if (scene.trackingOptions != null) {
            if (scene.trackingOptions.enterStyle == team.creative.cmdcam.common.scene.tracking.CamTransitionStyle.CUT)
                smoothEntry = false;
            enterDuration = scene.trackingOptions.enterDurationOrDefault(750L);
            if (enterDuration <= 0)
                smoothEntry = false;
        }
        
        if (smoothEntry) { // Smooth start from current player position
            CamPoints points = new CamPoints();
            CamPoint camPoint = CamPoint.create(camera);
            boolean smoothOk = true;
            try {
                CMDCamClient.PROCESSOR.makeRelative(scene, level, camPoint);
            } catch (SceneException e) {
                // Target not yet loaded: skip the smooth entry rather than inserting
                // an absolute world-coordinate point that would be misread as a local offset.
                CMDCam.LOGGER.warn("CMDCam: smooth start skipped because target pose is unavailable ({})", e.getMessage());
                smoothOk = false;
            }
            if (smoothOk) {
                points.add(camPoint);
                points.add(scene.points.get(0).copy());
                points.after(scene.points.get(0).copy());
                points.fixSpinning(CamPitchMode.FIX);
                long duration = scene.trackingOptions != null && scene.trackingOptions.enterDurationMs != null
                    ? enterDuration
                    : (long) Mth.clampedLerp(points.estimateLength() / 10, 1000, 20000);
                stages.add(new CamRunStage(this, CamInterpolation.HERMITE, duration, 0, points));
            }
        }
        
        { // First sequence
            CamPoints points = new CamPoints(scene.points);
            
            if (scene.loop != 0 && scene.points.size() > 1) {
                points.add(scene.points.get(0).copy());
                points.after(scene.points.get(1).copy());
            }
            
            points.fixSpinning(scene.pitchMode);
            
            stages.add(new CamRunStage(this, scene.interpolation, scene.duration, 0, points) {
                
                @Override
                public void start() {
                    super.start();
                    if (MinemaAddon.installed())
                        MinemaAddon.startCapture();
                }
                
            });
        }
        
        if (scene.loop != 0 && scene.loop != 1) { // actual loop
            CamPoints points = new CamPoints(scene.points);
            points.before(scene.points.get(scene.points.size() - 1).copy());
            
            points.add(scene.points.get(0).copy());
            points.after(scene.points.get(1).copy());
            
            points.fixSpinning(scene.pitchMode);
            
            stages.add(new CamRunStage(this, scene.interpolation, scene.duration, scene.loop > 0 ? scene.loop - 1 : scene.loop, points));
        }
        
        if (scene.loop > 0) { // end loop
            CamPoints points = new CamPoints(scene.points);
            points.before(scene.points.get(scene.points.size() - 1).copy());
            points.after(scene.points.get(scene.points.size() - 1).copy()); // For a slow stop
            
            points.fixSpinning(scene.pitchMode);
            
            stages.add(new CamRunStage(this, scene.interpolation, scene.duration, 0, points));
        }
        
        boolean smoothExit = scene.tracking && scene.targetReturnDuration > 0 && camera != null;
        if (scene.trackingOptions != null && scene.trackingOptions.exitStyle == team.creative.cmdcam.common.scene.tracking.CamTransitionStyle.CUT)
            smoothExit = false;
        if (smoothExit) {
            CamPoints points = new CamPoints();
            CamPoint p = CamPoint.create(camera);
            points.add(p);
            points.add(p.copy());
            points.fixSpinning(scene.pitchMode);
            this.returnStageIndex = stages.size();
            stages.add(new CamReturnStage(this, scene.targetReturnDuration, points));
        }
        
        this.currentStage = 0;
        this.timer = MinemaAddon.installed() ? new MinemaTimer() : new RealTimeTimer();
        this.finished = false;
        this.running = true;
    }
    
    public void renderTick(Level level, float deltaTime) {
        if (returnRequested && returnStageIndex >= 0) {
            returnRequested = false;
            returning = true;
            timer.stageCompleted();
            currentStage = returnStageIndex;
        }
        
        CamRunStage stage = stages.get(currentStage);
        
        if (!stage.hasStarted())
            stage.start();
        
        long time = position(deltaTime);
        if (!stage.endless() && time >= stage.duration) {
            
            timer.stageCompleted();
            if (stage.looped < stage.loops || stage.loops < 0)
                stage.looped++;
            else {
                currentStage++;
                if (currentStage < stages.size()) {
                    if (currentStage == returnStageIndex && stopReason == null) {
                        stopReason = CamStopReason.NATURAL_END;
                        returning = true;
                    }
                    stage = stages.get(currentStage);
                    stage.start();
                    time = 0;
                } else {
                    // All stages (including the return stage) have finished.
                    if (stopReason == null)
                        stopReason = CamStopReason.NATURAL_END;
                    scene.finish(level);
                    return;
                }
            }
        }

        
        mc.options.hideGui = true;
        scene.mode.process(stage.calculatePoint(level, time, deltaTime));
    }
    
    public void gameTick(Level level) {
        timer.tick(running);
        if (scene.tracking && !isReturning() && returnStageIndex >= 0 && scene.posTarget != null) {
            float partial = TickUtils.getFrameTime(level);
            if (!scene.posTarget.pose(level, partial).valid) {
                if (++targetMissingTicks >= 15)
                    requestReturn(CamStopReason.TARGET_LOST);
            } else
                targetMissingTicks = 0;
        }
    }
    
    public boolean isReturning() {
        return returning || returnRequested || currentStage == returnStageIndex;
    }
    
    public void requestReturn(CamStopReason reason) {
        if (returnStageIndex < 0 || isReturning())
            return;
        
        this.stopReason = reason;
        this.returnRequested = true;
        
        if (!running) {
            running = true;
            timer.resume();
        }
    }
    
    public CamStopReason stopReason() {
        return stopReason;
    }
    
    public CamAttribute[] attributes() {
        return PATH_ATTRIBUTES;
    }
    
    public void finish() {
        if (MinemaAddon.installed())
            MinemaAddon.stopCapture();
    }
    
    public long position(float partialTick) {
        return timer.position(running, partialTick);
    }
    
    public boolean playing() {
        return running;
    }
    
    public boolean done() {
        return finished;
    }
    
    public void pause() {
        running = false;
        timer.pause();
    }
    
    public void resume() {
        running = true;
        timer.resume();
    }
    
    public void stop() {
        finished = true;
        running = false;
        if (MinemaAddon.installed())
            MinemaAddon.stopCapture();
    }
    
}
