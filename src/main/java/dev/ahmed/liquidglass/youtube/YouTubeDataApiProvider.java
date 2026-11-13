package dev.ahmed.liquidglass.youtube;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.ahmed.liquidglass.stream.ProviderException;
import dev.ahmed.liquidglass.stream.ProviderException.Kind;
import dev.ahmed.liquidglass.stream.StreamDataProvider;
import dev.ahmed.liquidglass.stream.StreamManager.ChatMessage;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Official YouTube Data API v3 implementation. The API key is user-supplied at runtime
 * (never bundled in the jar). Every call is blocking and must run off the render thread.
 */
public final class YouTubeDataApiProvider implements StreamDataProvider {
    private static final String BASE = "https://www.googleapis.com/youtube/v3/";
    private static final long DEFAULT_POLL_MS = 6_000L;

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .build();
    private final Supplier<String> apiKey;

    public YouTubeDataApiProvider(Supplier<String> apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public VideoInfo fetchVideo(String videoId) throws ProviderException {
        JsonObject root = get("videos", Map.of(
            "part", "snippet,liveStreamingDetails,statistics",
            "id", videoId));
        JsonArray items = array(root, "items");
        if (items == null || items.isEmpty()) {
            throw new ProviderException(Kind.NOT_FOUND,
                "Video not found. It may be private, deleted, or the link is wrong.");
        }
        JsonObject item = items.get(0).getAsJsonObject();
        JsonObject snippet = obj(item, "snippet");
        JsonObject live = obj(item, "liveStreamingDetails");
        JsonObject stats = obj(item, "statistics");

        String broadcast = str(snippet, "liveBroadcastContent");
        long start = parseTime(str(live, "actualStartTime"));
        String end = str(live, "actualEndTime");

        LiveStatus status;
        if ("live".equals(broadcast)) status = LiveStatus.LIVE;
        else if ("upcoming".equals(broadcast)) status = LiveStatus.UPCOMING;
        else if (end != null || start > 0L) status = LiveStatus.ENDED;
        else status = LiveStatus.NOT_LIVE;

        return new VideoInfo(
            videoId,
            str(snippet, "title"),
            str(snippet, "channelId"),
            str(snippet, "channelTitle"),
            status,
            number(live, "concurrentViewers"),
            number(stats, "likeCount"),
            start,
            str(live, "activeLiveChatId"));
    }

    @Override
    public ChannelInfo fetchChannel(String channelId) throws ProviderException {
        JsonObject root = get("channels", Map.of("part", "snippet,statistics", "id", channelId));
        JsonArray items = array(root, "items");
        if (items == null || items.isEmpty()) {
            throw new ProviderException(Kind.NOT_FOUND, "Channel information is unavailable.");
        }
        JsonObject item = items.get(0).getAsJsonObject();
        JsonObject snippet = obj(item, "snippet");
        JsonObject stats = obj(item, "statistics");
        boolean hidden = stats != null && stats.has("hiddenSubscriberCount")
            && stats.get("hiddenSubscriberCount").getAsBoolean();
        String avatar = null;
        JsonObject thumbs = obj(snippet, "thumbnails");
        JsonObject def = obj(thumbs, "default");
        if (def != null) avatar = str(def, "url");
        return new ChannelInfo(
            channelId,
            str(snippet, "title"),
            str(snippet, "customUrl"),
            hidden ? null : number(stats, "subscriberCount"),
            avatar);
    }

    @Override
    public ChatBatch fetchChat(String liveChatId, String pageToken) throws ProviderException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("liveChatId", liveChatId);
        params.put("part", "snippet,authorDetails");
        params.put("maxResults", "200");
        if (pageToken != null && !pageToken.isEmpty()) params.put("pageToken", pageToken);
        JsonObject root = get("liveChat/messages", params);

        List<ChatMessage> out = new ArrayList<>();
        JsonArray items = array(root, "items");
        long nowNs = System.nanoTime();
        if (items != null) {
            for (JsonElement el : items) {
                JsonObject item = el.getAsJsonObject();
                JsonObject snippet = obj(item, "snippet");
                JsonObject author = obj(item, "authorDetails");
                if (snippet == null || author == null) continue;
                if (snippet.has("hasDisplayContent") && !snippet.get("hasDisplayContent").getAsBoolean()) continue;
                String text = str(snippet, "displayMessage");
                String id = str(item, "id");
                if (id == null || text == null || text.isEmpty()) continue;
                out.add(new ChatMessage(
                    id,
                    orDefault(str(author, "displayName"), "?"),
                    text,
                    parseTime(str(snippet, "publishedAt")),
                    nowNs,
                    bool(author, "isChatOwner"),
                    bool(author, "isChatModerator"),
                    bool(author, "isChatSponsor")));
            }
        }
        long poll = root.has("pollingIntervalMillis") ? root.get("pollingIntervalMillis").getAsLong() : DEFAULT_POLL_MS;
        return new ChatBatch(out, str(root, "nextPageToken"), poll);
    }

    private JsonObject get(String path, Map<String, String> params) throws ProviderException {
        String key = apiKey.get();
        if (key == null || key.isBlank()) {
            throw new ProviderException(Kind.MISSING_KEY, "No YouTube API key set. Add one in the Stream Connection panel.");
        }
        StringBuilder url = new StringBuilder(BASE).append(path).append('?');
        for (Map.Entry<String, String> e : params.entrySet()) {
            url.append(enc(e.getKey())).append('=').append(enc(e.getValue())).append('&');
        }
        url.append("key=").append(enc(key.trim()));

        HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString()))
            .timeout(Duration.ofSeconds(12))
            .header("Accept", "application/json")
            .GET()
            .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderException(Kind.CANCELLED, "Request cancelled.", e);
        } catch (IOException e) {
            throw new ProviderException(Kind.NETWORK, "Network problem: " + e.getClass().getSimpleName(), e);
        }

        int code = response.statusCode();
        JsonObject json;
        try {
            JsonElement parsed = JsonParser.parseString(response.body());
            json = parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (JsonParseException e) {
            if (code >= 500) throw new ProviderException(Kind.SERVER, "YouTube server error (" + code + ").", e);
            throw new ProviderException(Kind.MALFORMED, "Unreadable response from YouTube.", e);
        }
        if (code >= 200 && code < 300) return json;
        throw mapError(code, json);
    }

    private static ProviderException mapError(int code, JsonObject json) {
        JsonObject error = obj(json, "error");
        String message = orDefault(str(error, "message"), "HTTP " + code);
        String reason = null;
        JsonArray errors = array(error, "errors");
        if (errors != null && !errors.isEmpty()) reason = str(errors.get(0).getAsJsonObject(), "reason");
        if (reason == null) reason = "";

        return switch (reason) {
            case "quotaExceeded", "dailyLimitExceeded" ->
                new ProviderException(Kind.QUOTA_EXCEEDED, "YouTube API daily quota is used up. Try again tomorrow or use another key.");
            case "rateLimitExceeded", "userRateLimitExceeded" ->
                new ProviderException(Kind.RATE_LIMITED, "YouTube is rate limiting requests. Slowing down.");
            case "keyInvalid", "badRequest", "keyExpired" ->
                new ProviderException(Kind.INVALID_KEY, "YouTube rejected the API key: " + message);
            case "liveChatEnded", "liveChatNotFound" ->
                new ProviderException(Kind.CHAT_ENDED, "Live chat has ended.");
            case "liveChatDisabled" ->
                new ProviderException(Kind.CHAT_DISABLED, "Live chat is disabled for this stream.");
            case "forbidden", "accessNotConfigured", "ipRefererBlocked" ->
                new ProviderException(Kind.FORBIDDEN, "Access denied: " + message);
            default -> {
                if (code == 404) yield new ProviderException(Kind.NOT_FOUND, "Not found: " + message);
                if (code == 429) yield new ProviderException(Kind.RATE_LIMITED, "Too many requests. Slowing down.");
                if (code == 401 || code == 403) yield new ProviderException(Kind.FORBIDDEN, "Access denied: " + message);
                if (code >= 500) yield new ProviderException(Kind.SERVER, "YouTube server error (" + code + ").");
                yield new ProviderException(Kind.MALFORMED, "Unexpected YouTube response: " + message);
            }
        };
    }

    private static String enc(String v) { return URLEncoder.encode(v, StandardCharsets.UTF_8); }

    private static JsonObject obj(JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || !parent.get(key).isJsonObject()) return null;
        return parent.getAsJsonObject(key);
    }

    private static JsonArray array(JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || !parent.get(key).isJsonArray()) return null;
        return parent.getAsJsonArray(key);
    }

    private static String str(JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || parent.get(key).isJsonNull()) return null;
        return parent.get(key).getAsString();
    }

    private static boolean bool(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).getAsBoolean();
    }

    /** YouTube returns counts as strings; null means the value is hidden or not provided. */
    private static Long number(JsonObject parent, String key) {
        String raw = str(parent, key);
        if (raw == null) return null;
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static long parseTime(String iso) {
        if (iso == null) return 0L;
        try {
            return Instant.parse(iso).toEpochMilli();
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    private static String orDefault(String v, String fallback) { return v == null || v.isEmpty() ? fallback : v; }
}
