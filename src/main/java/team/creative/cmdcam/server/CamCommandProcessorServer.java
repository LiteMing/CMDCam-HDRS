package team.creative.cmdcam.server;

import java.util.Collection;
import java.util.Collections;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import team.creative.cmdcam.CMDCam;
import team.creative.cmdcam.client.SceneException;
import team.creative.cmdcam.common.command.CamCommandProcessor;
import team.creative.cmdcam.common.math.point.CamPoint;
import team.creative.cmdcam.common.packet.StartCloseupPacket;
import team.creative.cmdcam.common.packet.StartPathPacket;
import team.creative.cmdcam.common.packet.TeleportPathPacket;
import team.creative.cmdcam.common.scene.CamScene;
import team.creative.cmdcam.common.scene.tracking.CamPreset;
import team.creative.cmdcam.common.scene.tracking.TrackingOptions;
import team.creative.creativecore.common.network.CreativePacket;

import static team.creative.cmdcam.common.command.builder.SceneStartCommandBuilder.DEFAULT_PRESET_DURATION;

public class CamCommandProcessorServer implements CamCommandProcessor {
    
    @Override
    public CamScene getScene(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        CamScene scene = CMDCamServer.get(context.getSource().getLevel(), name);
        return scene;
    }
    
    @Override
    public boolean canSelectTarget() {
        return false;
    }
    
    @Override
    public void selectTarget(CommandContext<CommandSourceStack> context, boolean look) {}
    
    @Override
    public boolean canCreatePoint(CommandContext<CommandSourceStack> context) {
        return context.getSource().getEntity() != null;
    }
    
    @Override
    public CamPoint createPoint(CommandContext<CommandSourceStack> context) {
        return CamPoint.create(context.getSource().getEntity());
    }
    
    @Override
    public boolean requiresSceneName() {
        return true;
    }
    
    @Override
    public boolean requiresPlayer() {
        return true;
    }
    
    @Override
    public boolean supportsCloseup() {
        return true;
    }
    
    @Override
    public void startTracking(CommandContext<CommandSourceStack> context, TrackingOptions options, long durationMs) throws SceneException {
        String name = StringArgumentType.getString(context, "name");
        CamScene stored = getScene(context);
        if (stored == null) {
            context.getSource().sendFailure(Component.translatable("scenes.load_fail", name));
            return;
        }
        if (stored.points.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("scene.create_fail"));
            return;
        }
        if (stored.posTarget == null) {
            context.getSource().sendFailure(Component.translatable("scene.tracking.absolute", name, name));
            return;
        }
        Entity target = resolveTarget(context);
        if (target == null)
            return;
        Collection<ServerPlayer> players = getPlayers(context);
        if (players.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("scene.closeup.no_players"));
            return;
        }
        
        CamScene runtime = stored.copy();
        if (durationMs > 0)
            runtime.duration = durationMs;
        
        TrackingOptions runtimeOptions = options.copy();
        runtimeOptions.modeId = CamPreset.TRACKING;
        
        CreativePacket packet = new StartCloseupPacket(runtime, target.getUUID(), CamPreset.TRACKING, runtimeOptions);
        for (ServerPlayer player : players)
            CMDCam.NETWORK.sendToClient(packet, player);
        final int count = players.size();
        context.getSource().sendSuccess(() -> Component.translatable("scene.tracking.started", name, count), true);
    }
    
    @Override
    public void startPreset(CommandContext<CommandSourceStack> context, TrackingOptions options, long durationMs) throws SceneException {
        CamPreset preset = CamPreset.get(options.modeId);
        Entity target = resolveTarget(context);
        if (target == null)
            return;
        Collection<ServerPlayer> players = getPlayers(context);
        if (players.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("scene.closeup.no_players"));
            return;
        }
        
        CamScene scene = CamScene.createDefault();
        scene.duration = durationMs > 0 ? durationMs : DEFAULT_PRESET_DURATION;
        scene.loop = 0;
        scene.points.add(new CamPoint(preset.offsetX, preset.offsetY, preset.offsetZ, 0, 0, 0, preset.fov));
        
        TrackingOptions runtimeOptions = options.copy();
        runtimeOptions.modeId = preset.id;
        
        CreativePacket packet = new StartCloseupPacket(scene, target.getUUID(), preset.id, runtimeOptions);
        for (ServerPlayer player : players)
            CMDCam.NETWORK.sendToClient(packet, player);
        final int count = players.size();
        context.getSource().sendSuccess(() -> Component.translatable("scene.closeup.started", preset.id, count), true);
    }
    
    private Entity resolveTarget(CommandContext<CommandSourceStack> context) {
        try {
            return EntityArgument.getEntity(context, "target");
        } catch (CommandSyntaxException e) {
            context.getSource().sendFailure(Component.translatable("scene.closeup.target_missing"));
            return null;
        }
    }
    
    public Collection<ServerPlayer> getPlayers(CommandContext<CommandSourceStack> context) {
        try {
            return EntityArgument.getPlayers(context, "players");
        } catch (CommandSyntaxException e) {
            return Collections.EMPTY_LIST;
        }
    }
    
    @Override
    public void start(CommandContext<CommandSourceStack> context) throws SceneException {
        CamScene scene = getScene(context);
        if (scene == null) {
            String name = StringArgumentType.getString(context, "name");
            context.getSource().sendFailure(Component.translatable("scenes.load_fail", name));
            return;
        }
        if (scene.points.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("scene.create_fail"));
            return;
        }
        CreativePacket packet = new StartPathPacket(scene);
        for (ServerPlayer player : getPlayers(context))
            CMDCam.NETWORK.sendToClient(packet, player);
    }
    
    @Override
    public void teleport(CommandContext<CommandSourceStack> context, int index) {
        CamScene scene = getScene(context);
        if (scene == null) {
            String name = StringArgumentType.getString(context, "name");
            context.getSource().sendFailure(Component.translatable("scenes.load_fail", name));
            return;
        }
        if (index < 0 || index >= scene.points.size())
            return;
        CreativePacket packet = new TeleportPathPacket(scene.points.get(index));
        for (ServerPlayer player : getPlayers(context))
            CMDCam.NETWORK.sendToClient(packet, player);
    }
    
    @Override
    public void markDirty(CommandContext<CommandSourceStack> context) {
        CMDCamServer.markDirty(context.getSource().getLevel());
    }
    
    @Override
    public Player getPlayer(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return EntityArgument.getPlayer(context, "player");
    }
    
    @Override
    public Entity getEntity(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return EntityArgument.getEntity(context, name);
    }
    
}
