package dev.ahmed.liquidglass.youtube;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ahmed.liquidglass.stream.ProviderException;
import dev.ahmed.liquidglass.stream.ProviderException.Kind;
import dev.ahmed.liquidglass.stream.StreamDataProvider;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class YouTubeDataApiProvider implements StreamDataProvider {
    private static final String WATCH_URL = "https://www.youtube.com/watch?v=";
    private static final String NEXT_API_URL = "https://www.youtube.com/youtubei/v1/next?prettyPrint=false";
    private static final String UPDATED_META_URL = "https://www.youtube.com/youtubei/v1/updated_metadata?prettyPrint=false";
    private static final String RYD_VOTES_URL = "https://returnyoutubedislikeapi.com/votes?videoId=";
    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private static final Pattern NUMBER_PATTERN = Pattern.compile("([0-9]+(?:[.,][0-9]+)?)\\s*([KMBkmb])?");
    private static final Pattern EXACT_INT_PATTERN = Pattern.compile("([0-9][0-9,]*)");

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(6))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private volatile ChannelInfo cachedChannel;
    private volatile VideoInfo lastVideoInfo;
    private volatile String trackedVideoId = "";
    private volatile String metaContinuation = null;
    private volatile long cumulativeTotalViews = -1L;
    private volatile long lastWatchingCount = -1L;
    private volatile long apiReportedTotalViews = -1L;
    private volatile int pollTick = 0;

    public YouTubeDataApiProvider() {}

    @Override
    public VideoInfo fetchVideo(String videoId) throws ProviderException {
        if (!videoId.equals(trackedVideoId)) {
            trackedVideoId = videoId;
            cumulativeTotalViews = -1L;
            lastWatchingCount = -1L;
            apiReportedTotalViews = -1L;
            metaContinuation = null;
            pollTick = 0;
        }
        int tick = pollTick++;
        if (tick % 4 == 0) {
            CompletableFuture.runAsync(() -> refreshRydTotalViews(videoId));
        }
        if (lastVideoInfo == null || tick % 5 == 0) {
            return fetchFullWatchPage(videoId);
        }
        try {
            return fetchFastRealtimeTelemetry(videoId);
        } catch (Exception e) {
            return fetchFullWatchPage(videoId);
        }
    }

    private void refreshRydTotalViews(String videoId) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(RYD_VOTES_URL + videoId))
                .timeout(Duration.ofSeconds(4))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET()
                .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) {
                JsonObject obj = JsonParser.parseString(resp.body()).getAsJsonObject();
                if (obj.has("viewCount") && !obj.get("viewCount").isJsonNull()) {
                    long vc = obj.get("viewCount").getAsLong();
                    if (vc > apiReportedTotalViews) {
                        apiReportedTotalViews = vc;
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private VideoInfo fetchFastRealtimeTelemetry(String videoId) throws Exception {
        String webBody = "{\"context\":{\"client\":{\"clientName\":\"WEB\",\"clientVersion\":\"2.20250214.01.00\",\"hl\":\"en\",\"gl\":\"US\"}},\"videoId\":\"" + videoId + "\"}";
        String metaBody = (metaContinuation != null && !metaContinuation.isEmpty())
            ? "{\"context\":{\"client\":{\"clientName\":\"WEB\",\"clientVersion\":\"2.20250214.01.00\",\"hl\":\"en\",\"gl\":\"US\"}},\"continuation\":\"" + metaContinuation + "\"}"
            : webBody;

        CompletableFuture<JsonObject> nextFut = CompletableFuture.supplyAsync(() -> postJson(NEXT_API_URL, webBody, videoId));
        CompletableFuture<JsonObject> metaFut = CompletableFuture.supplyAsync(() -> postJson(UPDATED_META_URL, metaBody, videoId));

        JsonObject nextResp = nextFut.get();
        JsonObject metaResp = metaFut.get();

        if (nextResp == null && metaResp == null) {
            return fetchFullWatchPage(videoId);
        }
        return parseCombinedTelemetry(videoId, null, nextResp, metaResp);
    }

    private VideoInfo fetchFullWatchPage(String videoId) throws ProviderException {
        String html = fetchWatchHtml(videoId);
        JsonObject playerResp = extractJsonObject(html, "ytInitialPlayerResponse");
        JsonObject initialData = extractJsonObject(html, "ytInitialData");
        if (playerResp == null) {
            throw new ProviderException(Kind.NOT_FOUND, "Unable to read YouTube stream page. Check the link.");
        }
        return parseCombinedTelemetry(videoId, playerResp, initialData, null);
    }

    private synchronized VideoInfo parseCombinedTelemetry(String videoId, JsonObject playerResp, JsonObject initialData, JsonObject metaResp) throws ProviderException {
        VideoInfo prev = this.lastVideoInfo;
        String title = prev != null ? prev.title() : "--";
        String channelId = prev != null ? prev.channelId() : "";
        String author = prev != null ? prev.channelTitle() : "--";
        LiveStatus status = prev != null ? prev.status() : LiveStatus.LIVE;
        long startMs = prev != null ? prev.actualStartEpochMs() : 0L;
        Long detailsViews = null;
        Long microViews = null;

        if (playerResp != null) {
            JsonObject videoDetails = obj(playerResp, "videoDetails");
            if (videoDetails != null && !videoDetails.isEmpty()) {
                if (str(videoDetails, "title") != null) title = str(videoDetails, "title");
                if (str(videoDetails, "channelId") != null) channelId = str(videoDetails, "channelId");
                if (str(videoDetails, "author") != null) author = str(videoDetails, "author");
                boolean isLiveNow = bool(videoDetails, "isLive");
                boolean isUpcoming = bool(videoDetails, "isUpcoming");
                boolean isLiveContent = bool(videoDetails, "isLiveContent");
                detailsViews = parseLongSafe(str(videoDetails, "viewCount"));

                JsonObject micro = obj(obj(playerResp, "microformat"), "playerMicroformatRenderer");
                if (micro != null) {
                    microViews = parseLongSafe(str(micro, "viewCount"));
                }
                JsonObject liveDetails = obj(micro, "liveBroadcastDetails");
                if (liveDetails != null && bool(liveDetails, "isLiveNow")) isLiveNow = true;
                long parsedStart = parseIsoTime(str(liveDetails, "startTimestamp"));
                if (parsedStart > 0L) startMs = parsedStart;
                String endIso = str(liveDetails, "endTimestamp");

                if (isLiveNow) status = LiveStatus.LIVE;
                else if (isUpcoming) status = LiveStatus.UPCOMING;
                else if (endIso != null || (isLiveContent && startMs > 0L)) status = LiveStatus.ENDED;
                else status = LiveStatus.NOT_LIVE;
            } else if (prev == null) {
                throw new ProviderException(Kind.NOT_FOUND, "Stream not found or unavailable.");
            }
        }

        Long watching = null;
        Long likes = prev != null ? prev.likeCount() : null;
        Long descTotalViews = null;

        if (metaResp != null) {
            JsonObject timedCont = findObjectByKey(metaResp, "timedContinuationData", 0);
            if (timedCont != null && str(timedCont, "continuation") != null) {
                metaContinuation = str(timedCont, "continuation");
            }
            watching = extractWatchingFromNode(metaResp);
            Long metaLikes = extractExactLikes(metaResp);
            if (metaLikes != null) likes = metaLikes;
        }

        if (initialData != null) {
            JsonObject updEndpoint = findObjectByKey(initialData, "updatedMetadataEndpoint", 0);
            if (updEndpoint != null && str(updEndpoint, "continuation") != null) {
                metaContinuation = str(updEndpoint, "continuation");
            }
            if (watching == null) {
                watching = extractWatchingFromNode(initialData);
            }
            Long nextLikes = extractExactLikes(initialData);
            if (nextLikes != null) likes = nextLikes;

            JsonObject descHeader = findObjectByKey(initialData, "videoDescriptionHeaderRenderer", 0);
            if (descHeader != null) {
                String vTxt = readText(obj(descHeader, "views"));
                if (vTxt != null && !vTxt.toLowerCase(Locale.ROOT).contains("watching")) {
                    descTotalViews = parseExactIntFromText(vTxt);
                }
            }
            JsonObject ownerRenderer = findObjectByKey(initialData, "videoOwnerRenderer", 0);
            if (ownerRenderer != null) {
                JsonObject subObj = obj(ownerRenderer, "subscriberCountText");
                Long subs = parseCompactNumber(readAccessibilityOrText(subObj));
                String handle = extractHandle(ownerRenderer);
                this.cachedChannel = new ChannelInfo(channelId, author, handle != null ? handle : "--", subs, null);
            }
        }

        if (watching == null && status == LiveStatus.LIVE) {
            watching = prev != null && prev.concurrentViewers() != null ? prev.concurrentViewers() : detailsViews;
        }

        // Update baseline Total Views from all available cumulative view sources
        long candidateApiTotal = apiReportedTotalViews;
        if (microViews != null && microViews > candidateApiTotal && (watching == null || !microViews.equals(watching))) {
            candidateApiTotal = microViews;
        }
        if (detailsViews != null && detailsViews > candidateApiTotal && (watching == null || !detailsViews.equals(watching))) {
            candidateApiTotal = detailsViews;
        }
        if (descTotalViews != null && descTotalViews > candidateApiTotal) {
            candidateApiTotal = descTotalViews;
        }
        if (candidateApiTotal > cumulativeTotalViews) {
            cumulativeTotalViews = candidateApiTotal;
        }

        // Accumulate newly arriving viewers into cumulativeTotalViews so Total Views always grows and never drops with Watching
        if (watching != null) {
            if (cumulativeTotalViews < 0) {
                cumulativeTotalViews = Math.max(watching, candidateApiTotal);
            } else if (lastWatchingCount >= 0 && watching > lastWatchingCount) {
                cumulativeTotalViews += (watching - lastWatchingCount);
            }
            if (watching > cumulativeTotalViews) {
                cumulativeTotalViews = watching;
            }
            lastWatchingCount = watching;
        }

        Long finalTotalViews = cumulativeTotalViews >= 0 ? cumulativeTotalViews : (detailsViews != null ? detailsViews : watching);

        if (this.cachedChannel == null) {
            this.cachedChannel = new ChannelInfo(channelId, author, "--", null, null);
        }

        VideoInfo info = new VideoInfo(
            videoId, title, channelId, author, status,
            watching, finalTotalViews, likes, startMs,
            status == LiveStatus.LIVE ? videoId : null
        );
        this.lastVideoInfo = info;
        return info;
    }

    private static Long extractWatchingFromNode(JsonObject root) {
        JsonObject vcEntity = findObjectByKey(root, "viewCountEntity", 0);
        if (vcEntity != null) {
            Long n = parseExactIntFromText(str(vcEntity, "viewCountNumber"));
            if (n != null) return n;
            JsonObject vcObj = obj(vcEntity, "viewCount");
            if (vcObj != null) {
                Long c = parseExactIntFromText(str(vcObj, "content"));
                if (c != null) return c;
            }
        }
        JsonObject vcr = findObjectByKey(root, "videoViewCountRenderer", 0);
        if (vcr != null) {
            Long orig = parseLongSafe(str(vcr, "originalViewCount"));
            if (orig != null && orig > 0) return orig;
            Long txt = parseExactIntFromText(readText(obj(vcr, "viewCount")));
            if (txt != null) return txt;
        }
        JsonObject vcFactoid = findObjectByKey(root, "viewCountFactoidRenderer", 0);
        if (vcFactoid != null) {
            JsonObject factoid = obj(obj(vcFactoid, "factoid"), "factoidRenderer");
            if (factoid != null) {
                Long acc = parseExactIntFromText(str(factoid, "accessibilityText"));
                if (acc != null) return acc;
                Long val = parseCompactNumber(readText(obj(factoid, "value")));
                if (val != null) return val;
            }
        }
        return null;
    }

    @Override
    public ChannelInfo fetchChannel(String channelId) {
        return cachedChannel != null ? cachedChannel : new ChannelInfo(channelId, "--", "--", null, null);
    }

    @Override
    public ChatBatch fetchChat(String liveChatContinuation, String pageToken) {
        return ChatPopoutHelper.fetchLiveChat(this.http, liveChatContinuation, pageToken);
    }

    private JsonObject postJson(String url, String jsonBody, String videoId) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(4))
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/json")
                .header("Origin", "https://www.youtube.com")
                .header("Referer", "https://www.youtube.com/watch?v=" + videoId)
                .header("X-YouTube-Client-Name", "1")
                .header("X-YouTube-Client-Version", "2.20250214.01.00")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cookie", "CONSENT=YES+cb")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) {
                return JsonParser.parseString(resp.body()).getAsJsonObject();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String fetchWatchHtml(String videoId) throws ProviderException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(WATCH_URL + videoId + "&hl=en"))
            .timeout(Duration.ofSeconds(8))
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

    private static Long extractExactLikes(JsonObject root) {
        JsonObject lcEntity = findObjectByKey(root, "likeCountEntity", 0);
        if (lcEntity != null) {
            Long n = parseExactIntFromText(str(lcEntity, "likeCountIfIndifferentNumber"));
            if (n != null) return n;
            JsonObject exp = obj(lcEntity, "expandedLikeCountIfIndifferent");
            if (exp != null) {
                Long c = parseCompactNumber(str(exp, "content"));
                if (c != null) return c;
            }
        }
        JsonObject likeModel = findObjectByKey(root, "likeButtonViewModel", 0);
        if (likeModel != null) {
            JsonObject btnModel = findObjectByKey(likeModel, "buttonViewModel", 0);
            if (btnModel != null) {
                Long exact = parseExactIntFromText(str(btnModel, "accessibilityText"));
                if (exact != null) return exact;
                Long compact = parseCompactNumber(str(btnModel, "title"));
                if (compact != null) return compact;
            }
        }
        JsonObject toggleAct = findObjectByKey(root, "updateToggleButtonTextAction", 0);
        if (toggleAct != null) {
            Long exact = parseExactIntFromText(readAccessibilityOrText(obj(toggleAct, "defaultText")));
            if (exact != null) return exact;
        }
        JsonObject toggleBtn = findObjectByKey(root, "toggleButtonRenderer", 0);
        if (toggleBtn != null) {
            Long exact = parseExactIntFromText(readAccessibilityOrText(obj(toggleBtn, "defaultText")));
            if (exact != null) return exact;
        }
        return null;
    }

    private static String readAccessibilityOrText(JsonObject container) {
        if (container == null) return "";
        JsonObject accData = obj(obj(container, "accessibility"), "accessibilityData");
        if (accData != null && str(accData, "label") != null) {
            return str(accData, "label");
        }
        return readText(container);
    }

    private static Long parseExactIntFromText(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher m = EXACT_INT_PATTERN.matcher(text);
        Long best = null;
        while (m.find()) {
            try {
                long val = Long.parseLong(m.group(1).replace(",", ""));
                if (best == null || val > best) best = val;
            } catch (NumberFormatException ignored) {}
        }
        return best;
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
        if (element == null || depth > 16 || !element.isJsonObject()) return null;
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
        boolean inString = false, escaped = false;
        for (int i = startBrace; i < html.length(); i++) {
            char c = html.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\' && inString) { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (!inString) {
                if (c == '{') depth++;
                else if (c == '}' && --depth == 0) {
                    try { return JsonParser.parseString(html.substring(startBrace, i + 1)).getAsJsonObject(); }
                    catch (Exception e) { return null; }
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
        try { return Long.parseLong(raw.trim().replace(",", "")); } catch (NumberFormatException e) { return null; }
    }

    private static long parseIsoTime(String iso) {
        if (iso == null || iso.isEmpty()) return 0L;
        try { return OffsetDateTime.parse(iso).toInstant().toEpochMilli(); } catch (Exception e) { return 0L; }
    }

    private static JsonObject obj(JsonObject parent, String key) {
        return (parent != null && parent.has(key) && parent.get(key).isJsonObject()) ? parent.getAsJsonObject(key) : null;
    }

    private static JsonArray array(JsonObject parent, String key) {
        return (parent != null && parent.has(key) && parent.get(key).isJsonArray()) ? parent.getAsJsonArray(key) : null;
    }

    private static String str(JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || parent.get(key).isJsonNull()) return null;
        try { return parent.get(key).getAsString(); } catch (Exception e) { return null; }
    }

    private static boolean bool(JsonObject parent, String key) {
        try { return parent != null && parent.has(key) && parent.get(key).getAsBoolean(); } catch (Exception e) { return false; }
    }
}
