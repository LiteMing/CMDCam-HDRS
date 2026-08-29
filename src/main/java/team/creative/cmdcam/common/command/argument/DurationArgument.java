package team.creative.cmdcam.common.command.argument;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.network.chat.Component;

public class DurationArgument implements ArgumentType<Long> {
    
    /** One Minecraft game tick in milliseconds (20 TPS). */
    public static final long TICK_FACTOR = 50;
    public static final long SECOND_FACTOR = 1000;
    public static final long MINUTE_FACTOR = SECOND_FACTOR * 60;
    public static final long HOUR_FACTOR = MINUTE_FACTOR * 60;
    public static final long DAY_FACTOR = HOUR_FACTOR * 24;
    
    public static String printDuration(long duration) {
        StringBuilder output = new StringBuilder();
        long days = duration / DAY_FACTOR;
        if (days > 0) {
            output.append(" " + days + "d");
            duration -= days * DAY_FACTOR;
        }
        
        long hours = duration / HOUR_FACTOR;
        if (hours > 0) {
            output.append(" " + hours + "h");
            duration -= hours * HOUR_FACTOR;
        }
        
        long minutes = duration / MINUTE_FACTOR;
        if (minutes > 0) {
            output.append(" " + minutes + "m");
            duration -= minutes * MINUTE_FACTOR;
        }
        
        long seconds = duration / SECOND_FACTOR;
        if (seconds > 0) {
            output.append(" " + seconds + "s");
            duration -= seconds * SECOND_FACTOR;
        }
        
        if (duration > 0)
            output.append(" " + duration + "ms");
        
        return output.substring(1); // Remove first space
    }
    
    public static final List<String> EXAMPLES = Arrays.asList(new String[] { "20", "10s", "30s", "1m", "500ms" });
    
    public static DurationArgument duration() {
        return new DurationArgument();
    }
    
    public static long getDuration(final CommandContext<?> context, final String name) {
        return context.getArgument(name, long.class);
    }
    
    @Override
    public Long parse(StringReader reader) throws CommandSyntaxException {
        long time = reader.readLong();
        // Peek at the next characters to determine the unit suffix.
        // If none is present (end of input or non-letter char), default to ticks.
        if (!reader.canRead() || !Character.isLetter(reader.peek())) {
            // No suffix: treat the bare number as a tick count (1 tick = 50 ms at 20 TPS).
            return time * TICK_FACTOR;
        }
        String type = reader.readString();
        if (type.equalsIgnoreCase("t") || type.equalsIgnoreCase("tick") || type.equalsIgnoreCase("ticks"))
            return time * TICK_FACTOR;
        if (type.equalsIgnoreCase("ms"))
            return time;
        if (type.equalsIgnoreCase("s"))
            return time * SECOND_FACTOR;
        if (type.equalsIgnoreCase("m") || type.equalsIgnoreCase("min"))
            return time * MINUTE_FACTOR;
        if (type.equalsIgnoreCase("h"))
            return time * HOUR_FACTOR;
        if (type.equalsIgnoreCase("d"))
            return time * DAY_FACTOR;
        // Unknown suffix – reject with a helpful message.
        throw new CommandSyntaxException(
            new SimpleCommandExceptionType(new LiteralMessage("Invalid time format. Examples: 20 (20 ticks), 10s, 500ms, 1m")),
            Component.translatable("invalid_time_format"));
    }
    
    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
    
}
