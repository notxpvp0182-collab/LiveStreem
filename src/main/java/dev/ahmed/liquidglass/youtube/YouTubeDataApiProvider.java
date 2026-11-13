package dev.ahmed.liquidglass.youtube;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ahmed.liquidglass.stream.ProviderException;
import dev.ahmed.liquidglass.stream.ProviderException.Kind;
import dev.ahmed.liquidglass.stream.StreamDataProvider;
import dev.ahmed.liquidglass.stream.StreamManager.ChatMessage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keyless, unlimited YouTube InnerTube provider.
 * Fetches live metadata and live chat directly from YouTube's web endpoints without any API key.
 */
public final class YouTubeDataApiProvider implements StreamDataProvider {
    private static final String WATCH_URL = "https://www.youtube.com/watch?v=";
    private static final String LIVE_CHAT_URL = "https://www.youtube.com/youtubei/v1/live_chat/get_live_chat?prettyPrint=false";
    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private static final long DEFAULT_POLL_MS = 3_500L;
    private static final Pattern NUMBER_PATTERN = Pattern.compile("([0-9]+(?:[.,][0-9]+)?)\\s*([KMBkmb])?");

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private volatile ChannelInfo cachedChannel;

    public YouTubeDataApiProvider() {}

    @Override
    public VideoInfo fetchVideo(String videoId) throws ProviderException {
        String html = fetchWatchHtml(videoId);

        JsonObject playerResp = extractJsonObject(html, "ytInitialPlayerResponse");
        JsonObject initialData = extractJsonObject(html, "ytInitialData");

        if (playerResp == null) {
            throw new ProviderException(Kind.NOT_FOUND, "Unable to read YouTube stream page. Check the link.");
        }

        JsonObject videoDetails = obj(playerResp, "videoDetails");
        if (videoDetails == null || videoDetails.isEmpty()) {
            throw new ProviderException(Kind.NOT_FOUND, "Stream not found or unavailable.");
        }

        String title = str(videoDetails, "title");
        String channelId = str(videoDetails, "channelId");
        String author = str(videoDetails, "author");
        boolean isLiveNow = bool(videoDetails, "isLive");
        boolean isUpcoming = bool(videoDetails, "isUpcoming");
        boolean isLiveContent = bool(videoDetails, "isLiveContent");

        JsonObject micro = obj(obj(playerResp, "microformat"), "playerMicroformatRenderer");
        JsonObject liveDetails = obj(micro, "liveBroadcastDetails");
        if (liveDetails != null && bool(liveDetails, "isLiveNow")) {
            isLiveNow = true;
        }
        long startMs = parseIsoTime(str(liveDetails, "startTimestamp"));
        String endIso = str(liveDetails, "endTimestamp");

        LiveStatus status;
        if (isLiveNow) status = LiveStatus.LIVE;
        else if (isUpcoming) status = LiveStatus.UPCOMING;
        else if (endIso != null || (isLiveContent && startMs > 0L)) status = LiveStatus.ENDED;
        else status = LiveStatus.NOT_LIVE;

        Long viewers = isLiveNow ? parseLongSafe(str(videoDetails, "viewCount")) : null;
        Long likes = null;
        String chatContinuation = null;

        if (initialData != null) {
            chatContinuation = extractChatContinuation(initialData);
            JsonObject primaryInfo = findObjectByKey(initialData, "videoPrimaryInfoRenderer", 0);
            if (primaryInfo != null) {
                if (viewers == null && isLiveNow) {
                    JsonObject vcRenderer = obj(obj(primaryInfo, "viewCount"), "videoViewCountRenderer");
                    viewers = parseCompactNumber(readText(obj(vcRenderer, "viewCount")));
                }
                likes = extractLikeCount(primaryInfo);
            }

            JsonObject ownerRenderer = findObjectByKey(initialData, "videoOwnerRenderer", 0);
            if (ownerRenderer != null) {
                String subText = readText(obj(ownerRenderer, "subscriberCountText"));
                Long subs = parseCompactNumber(subText);
                String handle = extractHandle(ownerRenderer);
                this.cachedChannel = new ChannelInfo(
                    channelId != null ? channelId : "",
                    author != null ? author : "--",
                    handle != null ? handle : "--",
                    subs,
                    null
                );
            }
        }

        if (this.cachedChannel == null) {
            this.cachedChannel = new ChannelInfo(
                channelId != null ? channelId : "",
                author != null ? author : "--",
                "--",
                null,
                null
            );
        }

        return new VideoInfo(
            videoId,
            title != null ? title : "--",
            channelId != null ? channelId : "",
            author != null ? author : "--",
            status,
            viewers,
            likes,
            startMs,
            chatContinuation
        );
    }

    @Override
    public ChannelInfo fetchChannel(String channelId) {
        if (cachedChannel != null) {
            return cachedChannel;
        }
        return new ChannelInfo(channelId, "--", "--", null, null);
    }

    @Override
    public ChatBatch fetchChat(String liveChatContinuation, String pageToken) throws ProviderException {
        String token = (pageToken != null && !pageToken.isEmpty()) ? pageToken : liveChatContinuation;
        if (token == null || token.isEmpty()) {
            throw new ProviderException(Kind.CHAT_DISABLED, "Live chat token unavailable.");
        }

        String payload = "{\"context\":{\"client\":{\"clientName\":\"WEB\",\"clientVersion\":\"2.20241126.01.00\",\"hl\":\"en\",\"gl\":\"US\"}},\"continuation\":\""
            + escapeJson(token) + "\"}";

        HttpRequest req = HttpRequest.newBuilder(URI.create(LIVE_CHAT_URL))
            .timeout(Duration.ofSeconds(10))
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Cookie", "CONSENT=YES+cb")
            .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderException(Kind.CANCELLED, "Cancelled", e);
        } catch (IOException e) {
            throw new ProviderException(Kind.NETWORK, "Network error while reading live chat.", e);
        }

        if (resp.statusCode() != 200) {
            throw new ProviderException(Kind.SERVER, "YouTube chat HTTP " + resp.statusCode());
        }

        JsonObject root;
        try {
            root = JsonParser.parseString(resp.body()).getAsJsonObject();
        } catch (Exception e) {
            throw new ProviderException(Kind.MALFORMED, "Invalid chat JSON from YouTube.", e);
        }

        JsonObject liveChatCont = obj(obj(root, "continuationContents"), "liveChatContinuation");
        if (liveChatCont == null) {
            throw new ProviderException(Kind.CHAT_ENDED, "Live chat has ended.");
        }

        String nextToken = token;
        long timeoutMs = DEFAULT_POLL_MS;
        JsonArray continuations = array(liveChatCont, "continuations");
        if (continuations != null && !continuations.isEmpty()) {
            JsonObject contObj = continuations.get(0).getAsJsonObject();
            for (String key : new String[]{"invalidationContinuationData", "timedContinuationData", "reloadContinuationData"}) {
                JsonObject data = obj(contObj, key);
                if (data != null) {
                    String c = str(data, "continuation");
                    if (c != null && !c.isEmpty()) nextToken = c;
                    if (data.has("timeoutMs")) timeoutMs = data.get("timeoutMs").getAsLong();
                    break;
                }
            }
        }

        List<ChatMessage> messages = new ArrayList<>();
        JsonArray actions = array(liveChatCont, "actions");
        long nowNs = System.nanoTime();
        if (actions != null) {
            for (JsonElement el : actions) {
                JsonObject item = obj(obj(el.getAsJsonObject(), "addChatItemAction"), "item");
                if (item == null) continue;
                JsonObject renderer = obj(item, "liveChatTextMessageRenderer");
                if (renderer == null) renderer = obj(item, "liveChatPaidMessageRenderer");
                if (renderer == null) continue;

                String id = str(renderer, "id");
                String author = readText(obj(renderer, "authorName"));
                String content = readMessageRuns(obj(renderer, "message"));
                if (id == null || content.isEmpty()) continue;

                long tsMs = System.currentTimeMillis();
                String usec = str(renderer, "timestampUsec");
                if (usec != null) {
                    try { tsMs = Long.parseLong(usec) / 1000L; } catch (NumberFormatException ignored) {}
                }

                boolean isOwner = false;
                boolean isMod = false;
                boolean isMember = false;
                JsonArray badges = array(renderer, "authorBadges");
                if (badges != null) {
                    for (JsonElement bEl : badges) {
                        JsonObject br = obj(bEl.getAsJsonObject(), "liveChatAuthorBadgeRenderer");
                        if (br == null) continue;
                        if (br.has("customThumbnail")) isMember = true;
                        String iconType = str(obj(br, "icon"), "iconType");
                        if ("OWNER".equals(iconType)) isOwner = true;
                        else if ("MODERATOR".equals(iconType)) isMod = true;
                    }
                }

                messages.add(new ChatMessage(
                    id,
                    author.isEmpty() ? "Viewer" : author,
                    content,
                    tsMs,
                    nowNs,
                    isOwner,
                    isMod,
                    isMember
                ));
            }
        }

        return new ChatBatch(messages, nextToken, Math.max(2_500L, Math.min(8_000L, timeoutMs)));
    }

    private String fetchWatchHtml(String videoId) throws ProviderException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(WATCH_URL + videoId + "&hl=en"))
            .timeout(Duration.ofSeconds(10))
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cookie", "CONSENT=YES+cb")
            .GET()
            .build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                throw new ProviderException(Kind.SERVER, "YouTube returned HTTP " + resp.statusCode());
            }
            return resp.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderException(Kind.CANCELLED, "Cancelled", e);
        } catch (IOException e) {
            throw new ProviderException(Kind.NETWORK, "Cannot reach YouTube. Check internet connection.", e);
        }
    }

    private static String extractChatContinuation(JsonObject initialData) {
        JsonObject liveChatRenderer = findObjectByKey(initialData, "liveChatRenderer", 0);
        if (liveChatRenderer == null) return null;

        // Prefer unfiltered "Live chat" (all messages) sub-menu item if available
        JsonObject sortMenu = findObjectByKey(liveChatRenderer, "sortFilterSubMenuRenderer", 0);
        JsonArray subItems = array(sortMenu, "subMenuItems");
        if (subItems != null && subItems.size() > 1) {
            JsonObject item1 = subItems.get(1).getAsJsonObject();
            String c = str(obj(obj(item1, "continuation"), "reloadContinuationData"), "continuation");
            if (c != null && !c.isEmpty()) return c;
        }

        JsonArray conts = array(liveChatRenderer, "continuations");
        if (conts != null && !conts.isEmpty()) {
            JsonObject first = conts.get(0).getAsJsonObject();
            for (String k : new String[]{"reloadContinuationData", "invalidationContinuationData", "timedContinuationData"}) {
                String c = str(obj(first, k), "continuation");
                if (c != null && !c.isEmpty()) return c;
            }
        }
        return null;
    }

    private static Long extractLikeCount(JsonObject primaryInfo) {
        JsonObject likeModel = findObjectByKey(primaryInfo, "likeButtonViewModel", 0);
        if (likeModel != null) {
            JsonObject btnModel = findObjectByKey(likeModel, "buttonViewModel", 0);
            if (btnModel != null) {
                Long parsed = parseCompactNumber(str(btnModel, "title"));
                if (parsed != null) return parsed;
            }
        }
        return null;
    }

    private static String extractHandle(JsonObject ownerRenderer) {
        JsonObject webCmd = findObjectByKey(ownerRenderer, "webCommandMetadata", 0);
        String url = str(webCmd, "url");
        if (url != null && url.contains("/@")) {
            return url.substring(url.indexOf("/@") + 1);
        }
        return "--";
    }

    private static JsonObject findObjectByKey(JsonElement element, String targetKey, int depth) {
        if (element == null || depth > 14 || !element.isJsonObject()) return null;
        JsonObject obj = element.getAsJsonObject();
        if (obj.has(targetKey) && obj.get(targetKey).isJsonObject()) {
            return obj.getAsJsonObject(targetKey);
        }
        for (String key : obj.keySet()) {
            JsonElement child = obj.get(key);
            if (child.isJsonObject()) {
                JsonObject found = findObjectByKey(child, targetKey, depth + 1);
                if (found != null) return found;
            } else if (child.isJsonArray()) {
                for (JsonElement item : child.getAsJsonArray()) {
                    if (item.isJsonObject()) {
                        JsonObject found = findObjectByKey(item, targetKey, depth + 1);
                        if (found != null) return found;
                    }
                }
            }
        }
        return null;
    }

    private static JsonObject extractJsonObject(String html, String varName) {
        int idx = html.indexOf(varName);
        if (idx < 0) return null;
        int startBrace = html.indexOf('{', idx + varName.length());
        if (startBrace < 0) return null;

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = startBrace; i < html.length(); i++) {
            char c = html.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (!inString) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        try {
                            return JsonParser.parseString(html.substring(startBrace, i + 1)).getAsJsonObject();
                        } catch (Exception e) {
                            return null;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static String readText(JsonObject container) {
        if (container == null) return "";
        String simple = str(container, "simpleText");
        if (simple != null) return simple;
        JsonArray runs = array(container, "runs");
        if (runs != null) {
            StringBuilder sb = new StringBuilder();
            for (JsonElement el : runs) {
                String t = str(el.getAsJsonObject(), "text");
                if (t != null) sb.append(t);
            }
            return sb.toString();
        }
        return "";
    }

    private static String readMessageRuns(JsonObject msgObj) {
        if (msgObj == null) return "";
        JsonArray runs = array(msgObj, "runs");
        if (runs == null) return readText(msgObj);
        StringBuilder sb = new StringBuilder();
        for (JsonElement el : runs) {
            JsonObject run = el.getAsJsonObject();
            String t = str(run, "text");
            if (t != null) {
                sb.append(t);
            } else if (run.has("emoji")) {
                JsonObject emoji = obj(run, "emoji");
                JsonArray shortcuts = array(emoji, "shortcuts");
                if (shortcuts != null && !shortcuts.isEmpty()) {
                    sb.append(shortcuts.get(0).getAsString());
                } else {
                    String eid = str(emoji, "emojiId");
                    if (eid != null) sb.append(eid);
                }
            }
        }
        return sb.toString();
    }

    private static Long parseCompactNumber(String text) {
        if (text == null || text.isBlank()) return null;
        String cleaned = text.replace(",", "");
        Matcher m = NUMBER_PATTERN.matcher(cleaned);
        if (!m.find()) return null;
        try {
            double val = Double.parseDouble(m.group(1));
            String suffix = m.group(2);
            if (suffix != null) {
                switch (suffix.toUpperCase(Locale.ROOT)) {
                    case "K" -> val *= 1_000.0;
                    case "M" -> val *= 1_000_000.0;
                    case "B" -> val *= 1_000_000_000.0;
                }
            }
            return Math.round(val);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long parseLongSafe(String raw) {
        if (raw == null) return null;
        try { return Long.parseLong(raw.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static long parseIsoTime(String iso) {
        if (iso == null || iso.isEmpty()) return 0L;
        try {
            return OffsetDateTime.parse(iso).toInstant().toEpochMilli();
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

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
        try { return parent.get(key).getAsString(); } catch (Exception e) { return null; }
    }

    private static boolean bool(JsonObject parent, String key) {
        try {
            return parent != null && parent.has(key) && parent.get(key).getAsBoolean();
        } catch (Exception e) {
            return false;
        }
    }
}
