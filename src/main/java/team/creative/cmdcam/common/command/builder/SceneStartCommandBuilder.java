package team.creative.cmdcam.common.command.builder;

import java.util.function.Predicate;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import team.creative.cmdcam.client.SceneException;
import team.creative.cmdcam.common.command.CamCommandProcessor;
import team.creative.cmdcam.common.command.argument.DurationArgument;
import team.creative.cmdcam.common.scene.tracking.CamPreset;
import team.creative.cmdcam.common.scene.tracking.TrackingOptions;

/**
 * Builds the unified {@code start} command tree.
 * <p>
 * Mental model: {@code start = play}, {@code path | tracking | preset = what to play}, {@code closeup | shoulder = which built in preset}.
 */
public class SceneStartCommandBuilder {
    
    public static final long DEFAULT_PRESET_DURATION = 8000L;
    
    private static final Predicate<CommandSourceStack> PERMISSION_2 = source -> source.hasPermission(2);
    private static final String[] PRESETS = new String[] { CamPreset.CLOSEUP, CamPreset.SHOULDER };
    
    public static void start(ArgumentBuilder<CommandSourceStack, ?> origin, CamCommandProcessor processor) {
        if (!processor.requiresSceneName()) { // /cam start [duration] [loop]
            origin.then(durationLoop(Commands.literal("start"), processor, false, x -> processor.start(x)));
            return;
        }
        
        LiteralArgumentBuilder<CommandSourceStack> start = Commands.literal("start");
        
        // start path <scene> <players> [duration] [loop]
        ArgumentBuilder<CommandSourceStack, ?> pathName = Commands.argument("name", StringArgumentType.string());
        pathName.then(durationLoop(Commands.argument("players", EntityArgument.players()), processor, false, x -> processor.startPath(x, optional(x, "duration", long.class), optional(x, "loop", Integer.class))));
        start.then(Commands.literal("path").then(pathName));
        
        // start tracking <scene> <target> <players> [duration] [distance_scale] [fov] [damping] [pitch_follow]
        ArgumentBuilder<CommandSourceStack, ?> trackingTarget = Commands.argument("target", EntityArgument.entity());
        trackingTarget.then(options(Commands.argument("players", EntityArgument.players()), processor, CamPreset.TRACKING, false));
        start.then(Commands.literal("tracking").requires(PERMISSION_2).then(Commands.argument("name", StringArgumentType.string()).then(trackingTarget)));
        
        // start preset <closeup|shoulder> <target> <players> [duration] [distance] [fov] [damping] [pitch_follow]
        LiteralArgumentBuilder<CommandSourceStack> preset = Commands.literal("preset");
        for (String id : PRESETS) {
            ArgumentBuilder<CommandSourceStack, ?> presetTarget = Commands.argument("target", EntityArgument.entity());
            presetTarget.then(options(Commands.argument("players", EntityArgument.players()), processor, id, true));
            preset.then(Commands.literal(id).requires(PERMISSION_2).then(presetTarget));
        }
        start.then(preset);
        
        // deprecated: start <scene> <target> <players>, replaced by start tracking
        if (processor.supportsCloseup()) {
            ArgumentBuilder<CommandSourceStack, ?> legacyTarget = Commands.argument("target", EntityArgument.entity());
            legacyTarget.then(Commands.argument("players", EntityArgument.players()).executes(x -> {
                try {
                    warnDeprecated(x);
                    processor.startTracking(x, new TrackingOptions(CamPreset.TRACKING), 0L);
                } catch (SceneException e) {
                    x.getSource().sendFailure(e.getComponent());
                }
                return 0;
            }));
            start.then(Commands.argument("name", StringArgumentType.string()).requires(PERMISSION_2).then(legacyTarget));
        }
        
        origin.then(start);
        
        // deprecated: play <scene> <players> [duration] [loop], replaced by start path
        ArgumentBuilder<CommandSourceStack, ?> playName = Commands.argument("name", StringArgumentType.string());
        playName.then(durationLoop(Commands.argument("players", EntityArgument.players()), processor, true,
            x -> processor.startPath(x, optional(x, "duration", long.class), optional(x, "loop", Integer.class))));
        origin.then(Commands.literal("play").then(playName));
    }
    
    /**
     * Shortcut top-level commands for the two built in presets.
     * Supports the full optional parameter chain: [duration] [distance] [fov] [damping] [pitch_follow]
     */
    public static void quick(ArgumentBuilder<CommandSourceStack, ?> origin, CamCommandProcessor processor) {
        if (!processor.supportsCloseup())
            return;
        for (String id : PRESETS) {
            ArgumentBuilder<CommandSourceStack, ?> quickTarget = Commands.argument("target", EntityArgument.entity());
            quickTarget.then(options(Commands.argument("players", EntityArgument.players()), processor, id, true));
            origin.then(Commands.literal(id).requires(PERMISSION_2).then(quickTarget));
        }
    }
    
    private static ArgumentBuilder<CommandSourceStack, ?> durationLoop(ArgumentBuilder<CommandSourceStack, ?> node, CamCommandProcessor processor,
            boolean deprecated, SceneStarter starter) {
        node.executes(x -> runDurationLoop(x, deprecated, starter));
        node.then(Commands.argument("duration", DurationArgument.duration())
            .executes(x -> runDurationLoop(x, deprecated, starter))
            .then(Commands.argument("loop", IntegerArgumentType.integer(-1))
                .executes(x -> runDurationLoop(x, deprecated, starter))));
        return node;
    }
    
    private static int runDurationLoop(CommandContext<CommandSourceStack> x, boolean deprecated, SceneStarter starter) {
        try {
            if (deprecated)
                warnDeprecated(x);
            // Overrides are passed to the processor; the processor must apply them to a *copy*
            // of the saved scene so the stored template is never mutated by a play command.
            starter.start(x);
        } catch (SceneException e) {
            x.getSource().sendFailure(e.getComponent());
        }
        return 0;
    }
    
    /** Appends the optional camera parameters in a fixed order, every level accepts the same command so any suffix can be left out. */
    private static ArgumentBuilder<CommandSourceStack, ?> options(ArgumentBuilder<CommandSourceStack, ?> node, CamCommandProcessor processor, String modeId,
            boolean absoluteDistance) {
        OptionStarter starter = CamPreset.TRACKING.equals(modeId) ? processor::startTracking : processor::startPreset;
        Command<CommandSourceStack> command = x -> runOptions(x, modeId, absoluteDistance, starter);
        
        node.executes(command);
        ArgumentBuilder<CommandSourceStack, ?> current = node;
        current = append(current, "duration", DurationArgument.duration(), command);
        current = append(current, absoluteDistance ? "distance" : "distance_scale", DoubleArgumentType.doubleArg(), command);
        current = append(current, "fov", DoubleArgumentType.doubleArg(), command);
        current = append(current, "damping", DoubleArgumentType.doubleArg(), command);
        current = append(current, "pitch_follow", DoubleArgumentType.doubleArg(), command);
        return node;
    }
    
    private static int runOptions(CommandContext<CommandSourceStack> x, String modeId, boolean absoluteDistance, OptionStarter starter) {
        try {
            TrackingOptions options = readOptions(x, modeId, absoluteDistance);
            options.validate();
            starter.start(x, options, durationOr(x, 0L));
        } catch (SceneException e) {
            x.getSource().sendFailure(e.getComponent());
        }
        return 0;
    }
    
    private static TrackingOptions readOptions(CommandContext<CommandSourceStack> x, String modeId, boolean absoluteDistance) {
        TrackingOptions options = new TrackingOptions(modeId);
        if (absoluteDistance)
            options.distance = optional(x, "distance", Double.class);
        else
            options.distanceScale = optional(x, "distance_scale", Double.class);
        options.fov = optional(x, "fov", Double.class);
        options.dampingMs = optional(x, "damping", Double.class);
        options.pitchFollow = optional(x, "pitch_follow", Double.class);
        return options;
    }
    
    private static ArgumentBuilder<CommandSourceStack, ?> append(ArgumentBuilder<CommandSourceStack, ?> parent, String name, ArgumentType<?> type,
            Command<CommandSourceStack> command) {
        ArgumentBuilder<CommandSourceStack, ?> argument = Commands.argument(name, type);
        argument.executes(command);
        parent.then(argument);
        return argument;
    }
    
    private static void warnDeprecated(CommandContext<CommandSourceStack> x) {
        x.getSource().sendSystemMessage(Component.translatable("scene.start.deprecated"));
    }
    
    private static long durationOr(CommandContext<CommandSourceStack> x, long fallback) {
        Long duration = optional(x, "duration", long.class);
        return duration != null && duration > 0 ? duration : fallback;
    }
    
    /** Reads an optional argument, {@code null} when the node was not part of the parsed command. */
    private static <T> T optional(CommandContext<CommandSourceStack> x, String name, Class<T> type) {
        try {
            T value = x.getArgument(name, type);
            return value;
        } catch (IllegalArgumentException | ClassCastException e) {
            return null;
        }
    }
    
    @FunctionalInterface
    private interface SceneStarter {
        
        void start(CommandContext<CommandSourceStack> context) throws SceneException;
        
    }
    
    @FunctionalInterface
    private interface OptionStarter {
        
        void start(CommandContext<CommandSourceStack> context, TrackingOptions options, long durationMs) throws SceneException;
        
    }
    
}
