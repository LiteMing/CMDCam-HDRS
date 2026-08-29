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
import team.creative.cmdcam.common.scene.tracking.CamTransitionStyle;
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
    private static final DynamicCommandExceptionType INVALID_STYLE = new DynamicCommandExceptionType(
        style -> new LiteralMessage("Invalid transition style: '" + style + "'. Expected: cut, smooth, fade"));
    private static final DynamicCommandExceptionType INVALID_COLOR = new DynamicCommandExceptionType(
        color -> new LiteralMessage("Invalid color: '" + color + "'. Expected: black, white, or #RRGGBB"));
    
    private static final List<String> PRESET_KEYS = Arrays.asList(
        "duration", "distance", "fov", "damping", "pitch_follow", "yaw_follow",
        "enter_style", "exit_style", "enter_duration", "exit_duration", "return_duration", "fade_color"
    );
    private static final List<String> TRACKING_KEYS = Arrays.asList(
        "duration", "distance_scale", "fov", "damping", "pitch_follow", "yaw_follow",
        "enter_style", "exit_style", "enter_duration", "exit_duration", "return_duration", "fade_color"
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
            
            // Map alias return_duration to exit_duration
            String canonicalKey = "return_duration".equals(key) ? "exit_duration" : key;
            if (seenKeys.contains(canonicalKey)) {
                reader.setCursor(keyStart);
                throw DUPLICATE_KEY.createWithContext(reader, key);
            }
            seenKeys.add(canonicalKey);
            
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
                case "enter_style": {
                    String styleStr = readToken(reader);
                    CamTransitionStyle style = CamTransitionStyle.fromString(styleStr);
                    if (style == null) {
                        reader.setCursor(valStart);
                        throw INVALID_STYLE.createWithContext(reader, styleStr);
                    }
                    options.enterStyle = style;
                    break;
                }
                case "exit_style": {
                    String styleStr = readToken(reader);
                    CamTransitionStyle style = CamTransitionStyle.fromString(styleStr);
                    if (style == null) {
                        reader.setCursor(valStart);
                        throw INVALID_STYLE.createWithContext(reader, styleStr);
                    }
                    options.exitStyle = style;
                    break;
                }
                case "enter_duration":
                    options.enterDurationMs = DurationArgument.duration().parse(reader);
                    break;
                case "exit_duration":
                case "return_duration":
                    options.returnDurationMs = DurationArgument.duration().parse(reader);
                    break;
                case "fade_color": {
                    String colorStr = readToken(reader);
                    options.fadeColor = parseColor(reader, valStart, colorStr);
                    break;
                }
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
    
    private static String readToken(StringReader reader) {
        int start = reader.getCursor();
        while (reader.canRead() && !Character.isWhitespace(reader.peek())) {
            reader.skip();
        }
        return reader.getString().substring(start, reader.getCursor());
    }
    
    private static int parseColor(StringReader reader, int valStart, String colorStr) throws CommandSyntaxException {
        if ("black".equalsIgnoreCase(colorStr))
            return 0x000000;
        if ("white".equalsIgnoreCase(colorStr))
            return 0xFFFFFF;
        if ("red".equalsIgnoreCase(colorStr))
            return 0xFF0000;
        if ("blue".equalsIgnoreCase(colorStr))
            return 0x0000FF;
        if ("green".equalsIgnoreCase(colorStr))
            return 0x00FF00;
        
        String hex = colorStr.startsWith("#") ? colorStr.substring(1) : colorStr;
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            reader.setCursor(valStart);
            throw INVALID_COLOR.createWithContext(reader, colorStr);
        }
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
                if (eq > 0) {
                    String k = part.substring(0, eq).toLowerCase(Locale.ROOT);
                    if ("return_duration".equals(k))
                        k = "exit_duration";
                    presentKeys.add(k);
                }
            }
        }
        
        SuggestionsBuilder currentBuilder = builder.createOffset(builder.getStart() + tokenStart);
        
        if (currentToken.contains("=")) {
            int eqIdx = currentToken.indexOf('=');
            String key = currentToken.substring(0, eqIdx).toLowerCase(Locale.ROOT);
            String prefix = currentToken.substring(0, eqIdx + 1);
            if ("duration".equals(key) || "enter_duration".equals(key) || "exit_duration".equals(key) || "return_duration".equals(key)) {
                List<String> examples = Arrays.asList(prefix + "160t", prefix + "750ms", prefix + "1s", prefix + "500ms");
                return SharedSuggestionProvider.suggest(examples, currentBuilder);
            } else if ("enter_style".equals(key) || "exit_style".equals(key)) {
                List<String> examples = Arrays.asList(prefix + "smooth", prefix + "cut", prefix + "fade");
                return SharedSuggestionProvider.suggest(examples, currentBuilder);
            } else if ("fade_color".equals(key)) {
                List<String> examples = Arrays.asList(prefix + "black", prefix + "white", prefix + "#000000", prefix + "#FFFFFF");
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
            String canonical = "return_duration".equals(k) ? "exit_duration" : k;
            if (!presentKeys.contains(canonical)) {
                candidates.add(k + "=");
            }
        }
        
        return SharedSuggestionProvider.suggest(candidates, currentBuilder);
    }
    
    @Override
    public Collection<String> getExamples() {
        return isPreset
            ? Arrays.asList("fov=50 enter_style=fade enter_duration=500ms", "exit_style=fade fade_color=black", "duration=160t damping=500 yaw_follow=0.75")
            : Arrays.asList("distance_scale=1.5 fov=60 enter_style=cut", "enter_style=fade exit_style=fade fade_color=white");
    }
}
