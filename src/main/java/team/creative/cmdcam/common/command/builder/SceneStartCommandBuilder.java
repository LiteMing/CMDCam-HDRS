package team.creative.cmdcam.common.command.builder;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import team.creative.cmdcam.client.SceneException;
import team.creative.cmdcam.common.command.CamCommandProcessor;
import team.creative.cmdcam.common.command.argument.DurationArgument;
import team.creative.cmdcam.common.scene.CamScene;

public class SceneStartCommandBuilder {
    
    public static void start(ArgumentBuilder<CommandSourceStack, ?> origin, CamCommandProcessor processor) {
        boolean server = processor.requiresSceneName();
        ArgumentBuilder<CommandSourceStack, ?> startO = Commands.literal(server ? "play" : "start");
        ArgumentBuilder<CommandSourceStack, ?> start = startO;
        
        if (processor.requiresPlayer())
            start = Commands.argument("players", EntityArgument.players());
        else if (processor.requiresSceneName())
            start = Commands.argument("name", StringArgumentType.string());
        
        start.executes((x) -> {
            try {
                processor.start(x);
            } catch (SceneException e) {
                x.getSource().sendFailure(Component.translatable(e.getMessage()));
            }
            return 0;
        }).then(Commands.argument("duration", DurationArgument.duration()).executes((x) -> {
            try {
                long duration = DurationArgument.getDuration(x, "duration");
                if (duration > 0)
                    processor.getScene(x).duration = duration;
                processor.markDirty(x);
                processor.start(x);
            } catch (SceneException e) {
                x.getSource().sendFailure(Component.translatable(e.getMessage()));
            }
            return 0;
        }).then(Commands.argument("loop", IntegerArgumentType.integer(-1)).executes((x) -> {
            try {
                CamScene scene = processor.getScene(x);
                long duration = DurationArgument.getDuration(x, "duration");
                if (duration > 0)
                    scene.duration = duration;
                scene.loop = IntegerArgumentType.getInteger(x, "loop");
                processor.markDirty(x);
                processor.start(x);
            } catch (SceneException e) {
                x.getSource().sendFailure(Component.translatable(e.getMessage()));
            }
            return 0;
        })));
        
        if (processor.requiresSceneName())
            origin.then(startO.then(Commands.argument("name", StringArgumentType.string()).then(start)));
        else {
            if (processor.requiresPlayer())
                origin.then(startO.then(start));
            else
                origin.then(startO);
        }
        
        if (server && processor.supportsCloseup()) {
            ArgumentBuilder<CommandSourceStack, ?> closeupStart = Commands.literal("start")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("name", StringArgumentType.string())
                    .then(Commands.argument("target", EntityArgument.entity())
                        .then(Commands.argument("players", EntityArgument.players())
                            .executes((x) -> {
                                try {
                                    processor.startCloseup(x);
                                } catch (SceneException e) {
                                    x.getSource().sendFailure(Component.translatable(e.getMessage()));
                                }
                                return 0;
                            }))));
            origin.then(closeupStart);
        }
    }
    
    public static void quick(ArgumentBuilder<CommandSourceStack, ?> origin, CamCommandProcessor processor) {
        if (!processor.supportsCloseup())
            return;
        quickCommand(origin, processor, "closeup", "closeup");
        quickCommand(origin, processor, "shoulder", "shoulder");
    }
    
    private static void quickCommand(ArgumentBuilder<CommandSourceStack, ?> origin, CamCommandProcessor processor, String literal, String modeId) {
        ArgumentBuilder<CommandSourceStack, ?> cmd = Commands.literal(literal)
            .requires(source -> source.hasPermission(2))
            .then(Commands.argument("target", EntityArgument.entity())
                .then(Commands.argument("players", EntityArgument.players())
                    .executes(x -> executeQuick(x, processor, modeId, 8000L))
                    .then(Commands.argument("duration", DurationArgument.duration())
                        .executes(x -> executeQuick(x, processor, modeId, DurationArgument.getDuration(x, "duration"))))));
        origin.then(cmd);
    }
    
    private static int executeQuick(CommandContext<CommandSourceStack> x, CamCommandProcessor processor, String modeId, long duration) {
        try {
            processor.closeup(x, modeId, duration);
        } catch (SceneException e) {
            x.getSource().sendFailure(Component.translatable(e.getMessage()));
        }
        return 0;
    }
    
}
