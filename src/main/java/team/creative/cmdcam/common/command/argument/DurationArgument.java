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
    
    private static final SimpleCommandExceptionType INVALID_FORMAT = new SimpleCommandExceptionType(
        new LiteralMessage("Invalid time format. Examples: 20 (20 ticks), 10s, 500ms, 1m"));
    
    /** One Minecraft game tick in milliseconds (20 TPS). */
    public static final long TICK_FACTOR   = 50;
    public static final long SECOND_FACTOR = 1_000;
    public static final long MINUTE_FACTOR = SECOND_FACTOR * 60;
    public static final long HOUR_FACTOR   = MINUTE_FACTOR * 60;
    public static final long DAY_FACTOR    = HOUR_FACTOR   * 24;
    
    /**
     * Formats a millisecond duration back to a human-readable string.
     * Returns {@code "0ms"} for zero or negative inputs instead of throwing.
     */
    public static String printDuration(long duration) {
        if (duration <= 0)
            return "0ms";
        
        StringBuilder output = new StringBuilder();
        long days = duration / DAY_FACTOR;
        if (days > 0) {
            output.append(' ').append(days).append('d');
            duration -= days * DAY_FACTOR;
        }
        
        long hours = duration / HOUR_FACTOR;
        if (hours > 0) {
            output.append(' ').append(hours).append('h');
            duration -= hours * HOUR_FACTOR;
        }
        
        long minutes = duration / MINUTE_FACTOR;
        if (minutes > 0) {
            output.append(' ').append(minutes).append('m');
            duration -= minutes * MINUTE_FACTOR;
        }
        
        long seconds = duration / SECOND_FACTOR;
        if (seconds > 0) {
            output.append(' ').append(seconds).append('s');
            duration -= seconds * SECOND_FACTOR;
        }
        
        if (duration > 0)
            output.append(' ').append(duration).append("ms");
        
        // output always has at least one entry because the early-return handled duration<=0.
        return output.substring(1); // strip leading space
    }
    
    public static final List<String> EXAMPLES = Arrays.asList("20", "10s", "30s", "1m", "500ms");
    
    public static DurationArgument duration() {
        return new DurationArgument();
    }
    
    public static long getDuration(final CommandContext<?> context, final String name) {
        return context.getArgument(name, long.class);
    }
    
    @Override
    public Long parse(StringReader reader) throws CommandSyntaxException {
        final int start = reader.getCursor();
        long time = reader.readLong();
        
        // Reject negative durations; callers treat <=0 as "use default" which can mask bugs.
        if (time < 0) {
            reader.setCursor(start);
            throw INVALID_FORMAT.createWithContext(reader);
        }
        
        // Peek at the next characters to determine the unit suffix.
        // If none is present (end of input or non-letter char), default to ticks.
        if (!reader.canRead() || !Character.isLetter(reader.peek())) {
            // No suffix: treat the bare number as a tick count (1 tick = 50 ms at 20 TPS).
            return checkedMultiply(reader, start, time, TICK_FACTOR);
        }
        
        String type = reader.readString();
        if (type.equalsIgnoreCase("t") || type.equalsIgnoreCase("tick") || type.equalsIgnoreCase("ticks"))
            return checkedMultiply(reader, start, time, TICK_FACTOR);
        if (type.equalsIgnoreCase("ms"))
            return time;
        if (type.equalsIgnoreCase("s"))
            return checkedMultiply(reader, start, time, SECOND_FACTOR);
        if (type.equalsIgnoreCase("m") || type.equalsIgnoreCase("min"))
            return checkedMultiply(reader, start, time, MINUTE_FACTOR);
        if (type.equalsIgnoreCase("h"))
            return checkedMultiply(reader, start, time, HOUR_FACTOR);
        if (type.equalsIgnoreCase("d"))
            return checkedMultiply(reader, start, time, DAY_FACTOR);
        
        // Unknown suffix -- reject with a helpful message.
        reader.setCursor(start);
        throw new CommandSyntaxException(INVALID_FORMAT, Component.translatable("invalid_time_format"));
    }
    
    /**
     * Multiplies {@code time} by {@code factor}, throwing a {@link CommandSyntaxException}
     * (instead of silently wrapping) if the result would overflow {@code long}.
     */
    private static long checkedMultiply(StringReader reader, int start, long time, long factor) throws CommandSyntaxException {
        try {
            return Math.multiplyExact(time, factor);
        } catch (ArithmeticException e) {
            reader.setCursor(start);
            throw INVALID_FORMAT.createWithContext(reader);
        }
    }
    
    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
    
}
