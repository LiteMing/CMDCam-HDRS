package team.creative.cmdcam.common.command.argument;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.commands.SharedSuggestionProvider;
import team.creative.cmdcam.client.SceneException;
import team.creative.cmdcam.common.scene.tracking.CamPreset;
import team.creative.cmdcam.common.scene.tracking.TrackingOptions;

public class TrackingOptionsArgument implements ArgumentType<TrackingOptionsArgument.ParsedOptions> {
    
    private static final DynamicCommandExceptionType UNKNOWN_KEY = new DynamicCommandExceptionType(
        key -> new LiteralMessage("Unknown option: '" + key + "'"));
    private static final DynamicCommandExceptionType DUPLICATE_KEY = new DynamicCommandExceptionType(
        key -> new LiteralMessage("Duplicate option: '" + key + "'"));
    private static final SimpleCommandExceptionType EXPECTED_EQUALS = new SimpleCommandExceptionType(
        new LiteralMessage("Expected '=' after option key"));
    private static final SimpleCommandExceptionType INVALID_OPTION = new SimpleCommandExceptionType(
        new LiteralMessage("Invalid option"));
    
    private static final List<String> PRESET_KEYS = Arrays.asList(
        "duration", "distance", "fov", "damping", "pitch_follow", "yaw_follow"
    );
    private static final List<String> TRACKING_KEYS = Arrays.asList(
        "duration", "distance_scale", "fov", "damping", "pitch_follow", "yaw_follow"
    );
    
    public static class ParsedOptions {
        public final TrackingOptions options;
        public final long durationMs;
        
        public ParsedOptions(TrackingOptions options, long durationMs) {
            this.options = options;
            this.durationMs = durationMs;
        }
    }
    
    private final String modeId;
    private final boolean isPreset;
    
    private TrackingOptionsArgument(String modeId, boolean isPreset) {
        this.modeId = modeId;
        this.isPreset = isPreset;
    }
    
    public static TrackingOptionsArgument preset(String modeId) {
        return new TrackingOptionsArgument(modeId, true);
    }
    
    public static TrackingOptionsArgument tracking() {
        return new TrackingOptionsArgument(CamPreset.TRACKING, false);
    }
    
    public List<String> getAllowedKeys() {
        return isPreset ? PRESET_KEYS : TRACKING_KEYS;
    }
    
    @Override
    public ParsedOptions parse(StringReader reader) throws CommandSyntaxException {
        TrackingOptions options = new TrackingOptions(modeId);
        long durationMs = 0L;
        Set<String> seenKeys = new HashSet<>();
        List<String> allowedKeys = getAllowedKeys();
        
        while (reader.canRead()) {
            reader.skipWhitespace();
            if (!reader.canRead())
                break;
            
            int keyStart = reader.getCursor();
            while (reader.canRead() && reader.peek() != '=' && !Character.isWhitespace(reader.peek())) {
                reader.skip();
            }
            String key = reader.getString().substring(keyStart, reader.getCursor()).toLowerCase(Locale.ROOT);
            
            if (key.isEmpty())
                break;
            
            if (!allowedKeys.contains(key)) {
                reader.setCursor(keyStart);
                throw UNKNOWN_KEY.createWithContext(reader, key);
            }
            
            if (seenKeys.contains(key)) {
                reader.setCursor(keyStart);
                throw DUPLICATE_KEY.createWithContext(reader, key);
            }
            seenKeys.add(key);
            
            reader.skipWhitespace();
            if (!reader.canRead() || reader.peek() != '=') {
                throw EXPECTED_EQUALS.createWithContext(reader);
            }
            reader.skip(); // skip '='
            reader.skipWhitespace();
            
            int valStart = reader.getCursor();
            switch (key) {
                case "duration":
                    durationMs = DurationArgument.duration().parse(reader);
                    break;
                case "distance":
                    options.distance = reader.readDouble();
                    break;
                case "distance_scale":
                    options.distanceScale = reader.readDouble();
                    break;
                case "fov":
                    options.fov = reader.readDouble();
                    break;
                case "damping":
                    options.dampingMs = reader.readDouble();
                    break;
                case "pitch_follow":
                    options.pitchFollow = reader.readDouble();
                    break;
                case "yaw_follow":
                    options.yawFollow = reader.readDouble();
                    break;
                default:
                    reader.setCursor(valStart);
                    throw UNKNOWN_KEY.createWithContext(reader, key);
            }
            
            reader.skipWhitespace();
        }
        
        try {
            options.validate();
        } catch (SceneException e) {
            throw new CommandSyntaxException(INVALID_OPTION, e.getComponent());
        }
        
        return new ParsedOptions(options, durationMs);
    }
    
    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        
        int tokenStart = remaining.length();
        while (tokenStart > 0 && !Character.isWhitespace(remaining.charAt(tokenStart - 1))) {
            tokenStart--;
        }
        
        String completedPart = remaining.substring(0, tokenStart).trim();
        String currentToken = remaining.substring(tokenStart);
        
        Set<String> presentKeys = new HashSet<>();
        if (!completedPart.isEmpty()) {
            for (String part : completedPart.split("\\s+")) {
                int eq = part.indexOf('=');
                if (eq > 0)
                    presentKeys.add(part.substring(0, eq).toLowerCase(Locale.ROOT));
            }
        }
        
        SuggestionsBuilder currentBuilder = builder.createOffset(builder.getStart() + tokenStart);
        
        if (currentToken.contains("=")) {
            int eqIdx = currentToken.indexOf('=');
            String key = currentToken.substring(0, eqIdx).toLowerCase(Locale.ROOT);
            String prefix = currentToken.substring(0, eqIdx + 1);
            if ("duration".equals(key)) {
                List<String> examples = Arrays.asList(prefix + "160t", prefix + "8s", prefix + "5000ms");
                return SharedSuggestionProvider.suggest(examples, currentBuilder);
            } else if ("fov".equals(key)) {
                List<String> examples = Arrays.asList(prefix + "30", prefix + "50", prefix + "70", prefix + "90");
                return SharedSuggestionProvider.suggest(examples, currentBuilder);
            } else if ("distance".equals(key) || "distance_scale".equals(key)) {
                List<String> examples = Arrays.asList(prefix + "1.5", prefix + "2.0", prefix + "3.0");
                return SharedSuggestionProvider.suggest(examples, currentBuilder);
            } else if ("damping".equals(key)) {
                List<String> examples = Arrays.asList(prefix + "0", prefix + "250", prefix + "500");
                return SharedSuggestionProvider.suggest(examples, currentBuilder);
            } else if ("pitch_follow".equals(key)) {
                List<String> examples = Arrays.asList(prefix + "0.0", prefix + "0.5", prefix + "1.0");
                return SharedSuggestionProvider.suggest(examples, currentBuilder);
            } else if ("yaw_follow".equals(key)) {
                List<String> examples = Arrays.asList(prefix + "0.0", prefix + "0.75", prefix + "1.0");
                return SharedSuggestionProvider.suggest(examples, currentBuilder);
            }
            return Suggestions.empty();
        }
        
        List<String> allowed = getAllowedKeys();
        List<String> candidates = new ArrayList<>();
        for (String k : allowed) {
            if (!presentKeys.contains(k)) {
                candidates.add(k + "=");
            }
        }
        
        return SharedSuggestionProvider.suggest(candidates, currentBuilder);
    }
    
    @Override
    public Collection<String> getExamples() {
        return isPreset
            ? Arrays.asList("fov=50", "distance=2.5 fov=60", "duration=160t damping=500", "yaw_follow=0.75")
            : Arrays.asList("fov=50", "distance_scale=1.5 fov=60", "duration=160t damping=500", "yaw_follow=1.0");
    }
}
