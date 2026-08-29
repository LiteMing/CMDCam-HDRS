package team.creative.cmdcam.client;

import java.util.HashMap;
import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.network.NetworkConstants;
import team.creative.cmdcam.CMDCam;
import team.creative.cmdcam.common.command.argument.InterpolationArgument;
import team.creative.cmdcam.common.command.builder.PointArgumentBuilder;
import team.creative.cmdcam.common.command.builder.SceneCommandBuilder;
import team.creative.cmdcam.common.command.builder.SceneStartCommandBuilder;
import team.creative.cmdcam.common.math.interpolation.CamInterpolation;
import team.creative.cmdcam.common.math.point.CamPoint;
import team.creative.cmdcam.common.packet.GetPathPacket;
import team.creative.cmdcam.common.packet.SetPathPacket;
import team.creative.cmdcam.common.scene.CamScene;
import team.creative.cmdcam.common.scene.run.CamStopReason;
import team.creative.cmdcam.common.target.CamTargetPose;
import team.creative.creativecore.client.CreativeCoreClient;
import team.creative.creativecore.common.util.mc.TickUtils;

public class CMDCamClient {
    
    public final static Minecraft mc = Minecraft.getInstance();
    public static final CamCommandProcessorClient PROCESSOR = new CamCommandProcessorClient();
    public static final HashMap<String, CamScene> SCENES = new HashMap<>();
    
    private static final CamScene scene = CamScene.createDefault();
    private static CamScene playing;
    private static boolean serverAvailable = false;
    private static boolean hideGuiCache;
    private static boolean hasTargetMarker;
    private static CamPoint targetMarker;
    
    /**
     * A tracking camera start that is deferred until the target entity becomes available on the
     * client, or until the timeout expires.
     * <p>
     * This is the primary defence against the "local-coords-as-world-coords" bug: we refuse to
     * create a {@link team.creative.cmdcam.common.scene.run.CamRun} while the target pose is
     * invalid, because {@code CamRun}'s smooth-entry logic would otherwise produce a control point
     * at the entity-local offset interpreted as an absolute world position.
     */
    private static final class PendingTrackingStart {
        
        /** Maximum game ticks to wait for the target entity to become available. */
        static final int TIMEOUT_TICKS = 40; // 2 seconds at 20 TPS
        
        final CamScene scene;
        int remainingTicks;
        
        PendingTrackingStart(CamScene scene) {
            this.scene = scene;
            this.remainingTicks = TIMEOUT_TICKS;
        }
        
        /** Returns {@code true} when the timeout has been reached. */
        boolean tick() {
            return --remainingTicks <= 0;
        }
    }
    
    private static PendingTrackingStart pendingTrackingStart;
    
    public static void resetServerAvailability() {
        serverAvailable = false;
    }
    
    public static void setServerAvailability() {
        serverAvailable = true;
    }
    
    public static void init(FMLClientSetupEvent event) {
        MinecraftForge.EVENT_BUS.register(new CamEventHandlerClient());
        CreativeCoreClient.registerClientConfig(CMDCam.MODID);
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
            () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (a, b) -> true));
    }
    
    public static void load(IEventBus bus) {
        bus.addListener(CMDCamClient::init);
        MinecraftForge.EVENT_BUS.addListener(CMDCamClient::commands);
        bus.addListener(KeyHandler::registerKeys);
    }
    
    public static void commands(RegisterClientCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> cam = Commands.literal("cam");
        
        SceneStartCommandBuilder.start(cam, PROCESSOR);
        
        SceneCommandBuilder.scene(cam, PROCESSOR);
        
        event.getDispatcher().register(cam.then(Commands.literal("stop").executes(x -> {
            CMDCamClient.stop();
            return 0;
        })).then(Commands.literal("pause").executes(x -> {
            CMDCamClient.pause();
            return 0;
        })).then(Commands.literal("resume").executes(x -> {
            CMDCamClient.resume();
            return 0;
        })).then(Commands.literal("show").executes(x -> {
            CamEventHandlerClient.SHOW_ACTIVE_INTERPOLATION = true;
            x.getSource().sendSuccess(() -> Component.translatable("scene.interpolation.show_active"), false);
            return 0;
        }).then(Commands.argument("interpolation", InterpolationArgument.interpolationAll()).executes((x) -> {
            String interpolation = StringArgumentType.getString(x, "interpolation");
            if (!interpolation.equalsIgnoreCase("all")) {
                CamInterpolation.REGISTRY.get(interpolation).isRenderingEnabled = true;
                x.getSource().sendSuccess(() -> Component.translatable("scene.interpolation.show", interpolation), false);
            } else {
                for (CamInterpolation movement : CamInterpolation.REGISTRY.values())
                    movement.isRenderingEnabled = true;
                x.getSource().sendSuccess(() -> Component.translatable("scene.interpolation.show_all"), false);
            }
            return 0;
        }))).then(Commands.literal("hide").executes(x -> {
            CamEventHandlerClient.SHOW_ACTIVE_INTERPOLATION = false;
            x.getSource().sendSuccess(() -> Component.translatable("scene.interpolation.hide_active"), false);
            return 0;
        }).then(Commands.argument("interpolation", InterpolationArgument.interpolationAll()).executes((x) -> {
            String interpolation = StringArgumentType.getString(x, "interpolation");
            if (!interpolation.equalsIgnoreCase("all")) {
                CamInterpolation.REGISTRY.get(interpolation).isRenderingEnabled = false;
                x.getSource().sendSuccess(() -> Component.translatable("scene.interpolation.hide", interpolation), false);
            } else {
                for (CamInterpolation movement : CamInterpolation.REGISTRY.values())
                    movement.isRenderingEnabled = false;
                x.getSource().sendSuccess(() -> Component.translatable("scene.interpolation.hide_all"), false);
                CamEventHandlerClient.SHOW_ACTIVE_INTERPOLATION = false;
            }
            return 0;
        }))).then(Commands.literal("list").executes((x) -> {
            if (CMDCamClient.serverAvailable) {
                x.getSource().sendFailure(Component.translatable("scenes.list_fail"));
                return 0;
            }
            x.getSource().sendSuccess(() -> Component.translatable("scenes.list", SCENES.size(), String.join(", ", SCENES.keySet())), true);
            return 0;
        })).then(Commands.literal("load").then(Commands.argument("path", StringArgumentType.string()).executes((x) -> {
            String pathArg = StringArgumentType.getString(x, "path");
            if (CMDCamClient.serverAvailable)
                CMDCam.NETWORK.sendToServer(new GetPathPacket(pathArg));
            else {
                CamScene scene = CMDCamClient.SCENES.get(pathArg);
                if (scene != null) {
                    set(scene);
                    x.getSource().sendSuccess(() -> Component.translatable("scenes.load", pathArg), false);
                } else
                    x.getSource().sendFailure(Component.translatable("scenes.load_fail", pathArg));
            }
            return 0;
        }))).then(Commands.literal("save").then(Commands.argument("path", StringArgumentType.string()).executes((x) -> {
            String pathArg = StringArgumentType.getString(x, "path");
            try {
                CamScene scene = CMDCamClient.createScene();
                
                if (CMDCamClient.serverAvailable)
                    CMDCam.NETWORK.sendToServer(new SetPathPacket(pathArg, scene));
                else {
                    CMDCamClient.SCENES.put(pathArg, scene);
                    x.getSource().sendSuccess(() -> Component.translatable("scenes.save", pathArg), false);
                }
            } catch (SceneException e) {
                x.getSource().sendFailure(e.getComponent());
            }
            return 0;
        }))).then(new PointArgumentBuilder("follow_center", (x, y) -> targetMarker = y, PROCESSOR).executes(x -> {
            targetMarker = CamPoint.createLocal();
            return 0;
        })));
        
    }
    
    public static void renderBefore(RenderPlayerEvent.Pre event) {}
    
    public static CamScene getScene() {
        if (isPlaying())
            return playing;
        return scene;
    }
    
    public static CamScene getConfigScene() {
        return scene;
    }
    
    public static boolean isPlaying() {
        return playing != null;
    }
    
    public static List<CamPoint> getPoints() {
        return scene.points;
    }
    
    public static void set(CamScene scene) {
        CMDCamClient.scene.set(scene);
        checkTargetMarker();
    }
    
    public static void checkTargetMarker() {
        hasTargetMarker = scene.posTarget != null;
        if (hasTargetMarker && targetMarker == null)
            targetMarker = CamPoint.createLocal();
    }
    
    public static void start(CamScene scene) {
        if (scene.points.isEmpty())
            return;
        if (scene.points.size() == 1)
            scene.points.add(scene.points.get(0));
        playing = scene;
        playing.play();
    }
    
    /**
     * Entry point for server-initiated tracking cameras (closeup, shoulder, tracking template).
     * <p>
     * If the target entity is already present on the client, the camera starts immediately.
     * Otherwise the start is deferred for up to {@link PendingTrackingStart#TIMEOUT_TICKS} ticks
     * so that the target has time to arrive. This prevents a {@code CamRun} from being built
     * while the target pose is invalid, which would cause the smooth-entry control point to carry
     * an absolute world position misinterpreted as a local entity offset.
     */
    public static void startCloseup(CamScene scene) {
        if (playing != null)
            finishImmediately(CamStopReason.OVERWRITE);
        
        // Cancel any previous pending start that has not yet launched.
        pendingTrackingStart = null;
        
        if (scene.tracking && scene.posTarget != null && mc.level != null) {
            float partial = TickUtils.getFrameTime(mc.level);
            CamTargetPose pose = scene.posTarget.pose(mc.level, partial);
            if (!pose.valid) {
                // Target not yet loaded: queue and wait.
                pendingTrackingStart = new PendingTrackingStart(scene);
                return;
            }
        }
        
        // Target is available (or scene does not use tracking) – start immediately.
        start(scene);
    }
    
    public static void requestStop(CamStopReason reason) {
        if (playing == null)
            return;
        if (reason.smoothReturn && playing.tracking && playing.run != null)
            playing.run.requestReturn(reason);
        else
            finishImmediately(reason);
    }
    
    public static void finishImmediately(CamStopReason reason) {
        CamScene current = playing;
        if (current == null)
            return;
        
        boolean cleanupCompleted = false;
        try {
            current.finish(mc.level);
            cleanupCompleted = true;
        } catch (RuntimeException e) {
            CMDCam.LOGGER.error("Failed to clean up camera, reason={}", reason, e);
        } finally {
            playing = null;
            mc.options.hideGui = hideGuiCache;
            
            if (mc.player != null && mc.cameraEntity == null)
                mc.cameraEntity = mc.player;
            
            if (!cleanupCompleted) {
                CamEventHandlerClient.resetFOV();
                CamEventHandlerClient.resetRoll();
                if (mc.player != null)
                    mc.cameraEntity = mc.player;
            }
        }
    }
    
    public static void pause() {
        if (playing != null)
            playing.pause();
        mc.options.hideGui = hideGuiCache;
    }
    
    public static void resume() {
        if (playing != null)
            playing.resume();
    }
    
    public static void stop() {
        if (playing == null)
            return;
        if (playing.serverSynced())
            return;
        requestStop(CamStopReason.COMMAND_STOP);
    }
    
    public static void stopServer() {
        if (playing == null)
            return;
        requestStop(CamStopReason.COMMAND_STOP);
    }
    
    public static void noTickPath(Level level, float renderTickTime) {
        hideGuiCache = mc.options.hideGui;
    }
    
    public static void gameTickPath(Level level) {
        tickPendingStart(level);
        if (playing != null)
            playing.gameTick(level);
    }
    
    /**
     * Called every game tick to check whether a deferred tracking camera start can proceed.
     */
    private static void tickPendingStart(Level level) {
        if (pendingTrackingStart == null)
            return;
        
        CamScene pendingScene = pendingTrackingStart.scene;
        
        // Check if the target entity has appeared on the client yet.
        if (pendingScene.posTarget != null) {
            float partial = TickUtils.getFrameTime(level);
            CamTargetPose pose = pendingScene.posTarget.pose(level, partial);
            if (pose.valid) {
                pendingTrackingStart = null;
                start(pendingScene);
                return;
            }
        }
        
        // Still waiting – count down the timeout.
        if (pendingTrackingStart.tick()) {
            pendingTrackingStart = null;
            CMDCam.LOGGER.warn("CMDCam: tracking camera start timed out – target entity never appeared on client");
            if (mc.player != null)
                mc.player.sendSystemMessage(Component.translatable("scene.closeup.target_unavailable"));
        }
    }
    
    public static void renderTickPath(Level level, float renderTickTime) {
        if (playing == null)
            return;
        playing.renderTick(level, renderTickTime);
        if (playing == null || !playing.playing()) {
            mc.options.hideGui = hideGuiCache;
            playing = null;
        }
    }
    
    public static void resetTargetMarker() {
        targetMarker = null;
    }
    
    public static boolean hasTargetMarker() {
        return hasTargetMarker && targetMarker != null && scene.posTarget != null;
    }
    
    public static CamPoint getTargetMarker() {
        return targetMarker;
    }
    
    public static CamScene createScene() throws SceneException {
        if (scene.points.size() < 1)
            throw new SceneException("scene.create_fail");
        
        CamScene newScene = scene.copy();
        if (newScene.points.size() == 1)
            newScene.points.add(newScene.points.get(0));
        return newScene;
    }
    
    public static void teleportTo(CamPoint point) {
        Minecraft mc = Minecraft.getInstance();
        mc.player.getAbilities().flying = true;
        
        CamEventHandlerClient.roll((float) point.roll);
        CamEventHandlerClient.fov(point.zoom - CamEventHandlerClient.fovExactVanilla(mc.getPartialTick()));
        mc.player.absMoveTo(point.x, point.y, point.z, (float) point.rotationYaw, (float) point.rotationPitch);
        mc.player.absMoveTo(point.x, point.y - mc.player.getEyeHeight(), point.z, (float) point.rotationYaw, (float) point.rotationPitch);
    }
    
}
