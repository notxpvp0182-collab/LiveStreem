package dev.ahmed.liquidglass.youtube;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ahmed.liquidglass.stream.StreamDataProvider.ChatBatch;
import dev.ahmed.liquidglass.stream.StreamManager.ChatMessage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatPopoutHelper {
    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private static final Pattern API_KEY_PAT = Pattern.compile("\"INNERTUBE_API_KEY\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern CLIENT_VER_PAT = Pattern.compile("\"INNERTUBE_CLIENT_VERSION\"\\s*:\\s*\"([^\"]+)\"");

    private static volatile String cachedInnertubeKey = "";
    private static volatile String cachedClientVer = "2.20250214.01.00";
    private static volatile String lastVideoId = "";

    private ChatPopoutHelper() {}

    public static ChatBatch fetchLiveChat(HttpClient http, String videoIdOrToken, String pageToken) {
        if (videoIdOrToken != null && videoIdOrToken.length() == 11) {
            lastVideoId = videoIdOrToken;
        }
        try {
            if (pageToken != null && !pageToken.isEmpty() && !cachedInnertubeKey.isEmpty()) {
                ChatBatch postBatch = pollContinuation(http, pageToken);
                if (postBatch != null) return postBatch;
            }
            return fetchPopoutHtmlChat(http, lastVideoId.isEmpty() ? videoIdOrToken : lastVideoId);
        } catch (Exception e) {
            return new ChatBatch(List.of(), null, 4_000L);
        }
    }

    private static ChatBatch fetchPopoutHtmlChat(HttpClient http, String videoId) throws Exception {
        String url = "https://www.youtube.com/live_chat?v=" + videoId + "&is_popout=1&hl=en";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cookie", "CONSENT=YES+cb")
            .GET()
            .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) return new ChatBatch(List.of(), null, 4_000L);

        String html = resp.body();
        Matcher km = API_KEY_PAT.matcher(html);
        if (km.find()) cachedInnertubeKey = km.group(1);
        Matcher vm = CLIENT_VER_PAT.matcher(html);
        if (vm.find()) cachedClientVer = vm.group(1);

        JsonObject initialData = extractJson(html, "ytInitialData");
        if (initialData == null) return new ChatBatch(List.of(), null, 4_000L);

        JsonObject liveChat = obj(obj(initialData, "contents"), "liveChatRenderer");
        if (liveChat == null) return new ChatBatch(List.of(), null, 4_000L);

        List<ChatMessage> msgs = parseActions(array(liveChat, "actions"));
        String nextToken = extractNextContinuation(liveChat);
        return new ChatBatch(msgs, nextToken, 3_500L);
    }

    private static ChatBatch pollContinuation(HttpClient http, String token) throws Exception {
        String url = "https://www.youtube.com/youtubei/v1/live_chat/get_live_chat?prettyPrint=false&key=" + cachedInnertubeKey;
        String body = "{\"context\":{\"client\":{\"clientName\":\"WEB\",\"clientVersion\":\""
            + cachedClientVer + "\",\"hl\":\"en\",\"gl\":\"US\"}},\"continuation\":\""
            + token.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/json")
            .header("Origin", "https://www.youtube.com")
            .header("Referer", "https://www.youtube.com/live_chat?v=" + lastVideoId + "&is_popout=1")
            .header("Cookie", "CONSENT=YES+cb")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) return null;

        JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
        JsonObject cont = obj(obj(root, "continuationContents"), "liveChatContinuation");
        if (cont == null) return null;

        List<ChatMessage> msgs = parseActions(array(cont, "actions"));
        String nextToken = extractNextContinuation(cont);
        return new ChatBatch(msgs, nextToken, 3_500L);
    }

    private static String extractNextContinuation(JsonObject container) {
        JsonArray conts = array(container, "continuations");
        if (conts == null) return null;
        for (JsonElement el : conts) {
            JsonObject cObj = el.getAsJsonObject();
            for (String k : new String[]{"invalidationContinuationData", "timedContinuationData", "reloadContinuationData"}) {
                JsonObject d = obj(cObj, k);
                if (d != null && d.has("continuation")) {
                    return d.get("continuation").getAsString();
                }
            }
        }
        return null;
    }

    private static List<ChatMessage> parseActions(JsonArray actions) {
        List<ChatMessage> out = new ArrayList<>();
        if (actions == null) return out;
        long nowNs = System.nanoTime();
        for (JsonElement el : actions) {
            JsonObject item = obj(obj(el.getAsJsonObject(), "addChatItemAction"), "item");
            if (item == null) continue;
            JsonObject r = obj(item, "liveChatTextMessageRenderer");
            if (r == null) r = obj(item, "liveChatPaidMessageRenderer");
            if (r == null) continue;

            String id = r.has("id") ? r.get("id").getAsString() : null;
            String author = readRuns(obj(r, "authorName"));
            String text = readRuns(obj(r, "message"));
            if (id == null || text.isEmpty()) continue;

            long tsMs = System.currentTimeMillis();
            if (r.has("timestampUsec")) {
                try { tsMs = Long.parseLong(r.get("timestampUsec").getAsString()) / 1000L; } catch (Exception ignored) {}
            }

            boolean owner = false, mod = false, member = false;
            JsonArray badges = array(r, "authorBadges");
            if (badges != null) {
                for (JsonElement b : badges) {
                    JsonObject br = obj(b.getAsJsonObject(), "liveChatAuthorBadgeRenderer");
                    if (br == null) continue;
                    if (br.has("customThumbnail")) member = true;
                    JsonObject icon = obj(br, "icon");
                    String type = icon != null && icon.has("iconType") ? icon.get("iconType").getAsString() : "";
                    if ("OWNER".equals(type)) owner = true;
                    else if ("MODERATOR".equals(type)) mod = true;
                }
            }
            out.add(new ChatMessage(id, author.isEmpty() ? "Viewer" : author, text, tsMs, nowNs, owner, mod, member));
        }
        return out;
    }

    private static String readRuns(JsonObject obj) {
        if (obj == null) return "";
        if (obj.has("simpleText")) return obj.get("simpleText").getAsString();
        JsonArray runs = array(obj, "runs");
        if (runs == null) return "";
        StringBuilder sb = new StringBuilder();
        for (JsonElement e : runs) {
            JsonObject r = e.getAsJsonObject();
            if (r.has("text")) sb.append(r.get("text").getAsString());
            else if (r.has("emoji")) {
                JsonObject em = obj(r, "emoji");
                JsonArray sc = array(em, "shortcuts");
                if (sc != null && !sc.isEmpty()) sb.append(sc.get(0).getAsString());
            }
        }
        return sb.toString();
    }

    private static JsonObject extractJson(String html, String marker) {
        int idx = html.indexOf(marker);
        if (idx < 0) return null;
        int start = html.indexOf('{', idx + marker.length());
        if (start < 0) return null;
        int depth = 0;
        boolean inStr = false, esc = false;
        for (int i = start; i < html.length(); i++) {
            char c = html.charAt(i);
            if (esc) { esc = false; continue; }
            if (c == '\\' && inStr) { esc = true; continue; }
            if (c == '"') { inStr = !inStr; continue; }
            if (!inStr) {
                if (c == '{') depth++;
                else if (c == '}' && --depth == 0) {
                    try { return JsonParser.parseString(html.substring(start, i + 1)).getAsJsonObject(); }
                    catch (Exception e) { return null; }
                }
            }
        }
        return null;
    }

    private static JsonObject obj(JsonObject p, String k) {
        return (p != null && p.has(k) && p.get(k).isJsonObject()) ? p.getAsJsonObject(k) : null;
    }

    private static JsonArray array(JsonObject p, String k) {
        return (p != null && p.has(k) && p.get(k).isJsonArray()) ? p.getAsJsonArray(k) : null;
    }
}
