package dev.ahmed.liquidglass.hud.elements;

import dev.ahmed.liquidglass.hud.HudEditorScreen;
import dev.ahmed.liquidglass.hud.HudElement;
import dev.ahmed.liquidglass.stream.StreamManager;
import dev.ahmed.liquidglass.ui.render.EmojiRenderer;
import dev.ahmed.liquidglass.ui.render.UIRenderer;
import dev.ahmed.liquidglass.ui.theme.ThemeManager;
import dev.ahmed.liquidglass.ui.theme.ThemeTokens;
import dev.ahmed.liquidglass.youtube.ChatPopoutHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

import java.awt.Color;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class LiveChatElement extends HudElement {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault());

    private static final String[] ANIM_IDS = {
        "SLIDE_UP", "LIQUID_POP", "SLIDE_SIDE", "BOUNCE", "CASCADE", "SMOOTH_FADE"
    };
    private static final String[] ANIM_NAMES = {
        "Left Slide", "Left Pop", "Left Spring", "Left Bounce", "Left Wave", "Left Fade"
    };

    private static final int[] VIBRANT_PALETTE = {
        0xFFFFB84D, 0xFFFF6B8B, 0xFF5CE1E6, 0xFF9ECE6A,
        0xFFBB9AF7, 0xFFFF757F, 0xFF7DCFFF, 0xFFFFD166,
        0xFF73DACA, 0xFFFF9E64, 0xFF8AB4F8, 0xFFF472B6
    };

    public boolean showYouTubeIcon = true;
    public boolean showUsername = true;
    public boolean showTimestamp = false;
    public boolean enableEmojiFallback = true;
    public boolean coloredUsernames = true;
    public boolean coloredMessages = false;
    public boolean rgbMode = false;
    public boolean newestAtTop = false;
    public boolean chatShadow = false;
    public boolean textShadow = true;

    public String chatAnimStyle = "SLIDE_UP";
    public float chatAnimSpeed = 1.0f;

    public int usernameColor = 0xFFD8DEE9;
    public int messageColor = 0xFFFFFFFF;
    public int timestampColor = 0xFF8A909E;
    public int maxVisibleMessages = 14;
    public float lineSpacing = 13.0f;
    public float entranceDurationSec = 0.52f;

    private static volatile int scrollLineOffset = 0;

    private record DisplayLine(
        StreamManager.ChatMessage msg,
        String timestampPart,
        String authorPart,
        String contentPart,
        boolean isFirstLine,
        int msgIndex,
        int prefixWidth,
        int contentWidth
    ) {}

    private final Map<String, List<String>> wrapCache = new ConcurrentHashMap<>(512);
    private final Map<String, Long> firstRenderNsMap = new ConcurrentHashMap<>(512);
    private List<DisplayLine> cachedLines = List.of();
    private int cachedTotalReceived = -1;
    private int cachedAvailWidth = -1;
    private float cachedScale = -1.0f;
    private boolean cachedShowTs = false;
    private boolean cachedShowUser = true;
    private boolean cachedShowYt = true;
    private boolean cachedSmallCaps = false;

    private float scrollPushPx = 0.0f;
    private long lastRenderNs = System.nanoTime();
    private String lastTopMsgId = "";

    public LiveChatElement() {
        super("live_chat", "Live Chat", 12, 145, 290, 155, true);
        style().backgroundEnabled = false;
    }

    public static String styleNameFor(int index1To6) {
        int i = Math.max(0, Math.min(ANIM_NAMES.length - 1, index1To6 - 1));
        return ANIM_NAMES[i];
    }

    public int getAnimStyleIndex() {
        for (int i = 0; i < ANIM_IDS.length; i++) {
            if (ANIM_IDS[i].equalsIgnoreCase(chatAnimStyle)) return i + 1;
        }
        return 1;
    }

    public void setAnimStyleIndex(int index1To6) {
        int i = Math.max(0, Math.min(ANIM_IDS.length - 1, index1To6 - 1));
        this.chatAnimStyle = ANIM_IDS[i];
        if (!lastTopMsgId.isEmpty()) {
            firstRenderNsMap.put(lastTopMsgId, System.nanoTime());
        }
    }

    public static void scrollUp(int lines) {
        scrollLineOffset = Math.min(2000, scrollLineOffset + lines);
    }

    public static void scrollDown(int lines) {
        scrollLineOffset = Math.max(0, scrollLineOffset - lines);
    }

    public static void jumpToLiveBottom() {
        scrollLineOffset = 0;
    }

    @Override
    protected void renderContent(DrawContext ctx) {
        long nowNs = System.nanoTime();
        float dt = Math.min(0.05f, Math.max(0.001f, (nowNs - lastRenderNs) / 1_000_000_000.0f));
        lastRenderNs = nowNs;

        StreamManager.get().tickChatPacing();
        ThemeTokens t = ThemeManager.get().tokens();
        Deque<StreamManager.ChatMessage> queue = StreamManager.get().chatMessages();
        boolean inHudEditor = MinecraftClient.getInstance().currentScreen instanceof HudEditorScreen;

        ctx.enableScissor(
            Math.round(x() + 2), Math.round(y() + 2),
            Math.round(x() + width() - 2), Math.round(y() + height() - 2)
        );

        List<StreamManager.ChatMessage> snapshot;
        if (queue.isEmpty()) {
            if (!inHudEditor) {
                UIRenderer.drawText(ctx, "Live chat waiting for messages...", x() + 8, y() + 8,
                    t.textMuted(), style().scale, style().smallCaps, textShadow);
                ctx.disableScissor();
                return;
            }
            long cycleId = System.currentTimeMillis() / 2200L;
            snapshot = List.of(
                new StreamManager.ChatMessage("p1", "Viewer_01", "Scroll here to resize chat text! 🔥", System.currentTimeMillis(), nowNs, false, false, false),
                new StreamManager.ChatMessage("p2", "Moderator", "Left-side smooth chat animation ✨", System.currentTimeMillis(), nowNs, false, true, false),
                new StreamManager.ChatMessage("p3_" + cycleId, "Streamer", String.format(Locale.ROOT, "Text Size: %.2fx (Anim Preview)", style().scale), System.currentTimeMillis(), nowNs, true, false, false)
            );
        } else {
            snapshot = new ArrayList<>(queue);
        }

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int availWidth = Math.max(60, Math.round((width() - 14.0f) / Math.max(0.45f, style().scale)));
        int currentTotal = queue.isEmpty() ? (int) (System.currentTimeMillis() / 2200L) : StreamManager.get().totalChatReceived();
        float stepY = lineSpacing * style().scale;

        if (currentTotal != cachedTotalReceived
            || availWidth != cachedAvailWidth
            || Math.abs(style().scale - cachedScale) > 0.001f
            || showTimestamp != cachedShowTs
            || showUsername != cachedShowUser
            || showYouTubeIcon != cachedShowYt
            || style().smallCaps != cachedSmallCaps) {

            if (availWidth != cachedAvailWidth || Math.abs(style().scale - cachedScale) > 0.001f || style().smallCaps != cachedSmallCaps) {
                wrapCache.clear();
            }
            cachedTotalReceived = currentTotal;
            cachedAvailWidth = availWidth;
            cachedScale = style().scale;
            cachedShowTs = showTimestamp;
            cachedShowUser = showUsername;
            cachedShowYt = showYouTubeIcon;
            cachedSmallCaps = style().smallCaps;

            List<DisplayLine> built = new ArrayList<>(snapshot.size() * 2);
            int latestMsgLineCount = 0;

            for (int mIdx = 0; mIdx < snapshot.size(); mIdx++) {
                StreamManager.ChatMessage msg = snapshot.get(mIdx);
                if (mIdx < snapshot.size() - 1) {
                    firstRenderNsMap.putIfAbsent(msg.id(), nowNs - 10_000_000_000L);
                } else {
                    firstRenderNsMap.putIfAbsent(msg.id(), nowNs);
                }

                String ts = showTimestamp ? (TIME_FMT.format(Instant.ofEpochMilli(msg.timestampMs())) + " ") : "";
                String author = showUsername ? formatAuthorColumn(msg.author()) : "";

                int badgeW = (showYouTubeIcon ? 14 : 0) + ((msg.owner() || msg.moderator() || msg.member()) ? 10 : 0);
                int prefixW = badgeW + tr.getWidth(ts + author);
                int lineAvail = Math.max(36, availWidth - prefixW);

                List<String> wrapped = wrapCache.computeIfAbsent(msg.id() + "_" + lineAvail, id -> {
                    String body = ChatPopoutHelper.replaceShortcodes(msg.content());
                    return wrapTextToWidth(tr, body, lineAvail, lineAvail);
                });
                if (wrapCache.size() > 600) wrapCache.clear();
                if (firstRenderNsMap.size() > 800) firstRenderNsMap.clear();

                if (mIdx == snapshot.size() - 1) {
                    latestMsgLineCount = wrapped.size();
                }

                for (int l = 0; l < wrapped.size(); l++) {
                    String part = wrapped.get(l);
                    int cw = EmojiRenderer.measureRichTextWidth(tr, part, style().smallCaps);
                    if (l == 0) {
                        built.add(new DisplayLine(msg, ts, author, part, true, mIdx, prefixW, cw));
                    } else {
                        built.add(new DisplayLine(msg, "", "", part, false, mIdx, prefixW, cw));
                    }
                }
            }

            if (!snapshot.isEmpty()) {
                String newestId = snapshot.get(snapshot.size() - 1).id();
                if (!newestId.equals(lastTopMsgId)) {
                    firstRenderNsMap.put(newestId, nowNs);
                    if (!lastTopMsgId.isEmpty() && style().animationEnabled && scrollLineOffset == 0) {
                        scrollPushPx = Math.min(height() * 0.4f, scrollPushPx + Math.max(1, latestMsgLineCount) * stepY);
                    }
                    lastTopMsgId = newestId;
                }
            }
            cachedLines = built;
        }

        if (scrollPushPx > 0.05f) {
            float decayRate = 14.0f * Math.max(0.5f, chatAnimSpeed);
            scrollPushPx *= (float) Math.exp(-dt * decayRate);
            if (scrollPushPx < 0.1f) scrollPushPx = 0.0f;
        } else {
            scrollPushPx = 0.0f;
        }

        List<DisplayLine> allWrappedLines = cachedLines;
        int totalLines = allWrappedLines.size();
        if (totalLines == 0) {
            ctx.disableScissor();
            return;
        }

        int newestMsgIdx = allWrappedLines.get(totalLines - 1).msgIndex();
        int maxLinesInBox = Math.max(1, (int) ((height() - 12.0f) / stepY));
        int maxScroll = Math.max(0, totalLines - maxLinesInBox);
        if (scrollLineOffset > maxScroll) scrollLineOffset = maxScroll;

        if (!newestAtTop) {
            int bottomLineIdx = totalLines - 1 - scrollLineOffset;
            float baseDrawY = y() + height() - 13.0f - (scrollLineOffset > 0 ? 12.0f : 0.0f);
            for (int i = bottomLineIdx; i >= 0; i--) {
                DisplayLine dl = allWrappedLines.get(i);
                // Only apply scrollPushPx to older messages so the newest message stays inside the bottom scissor bound
                float lineY = baseDrawY + (dl.msgIndex() < newestMsgIdx ? scrollPushPx : 0.0f);
                if (lineY < y() - stepY) break;
                drawSingleDisplayLine(ctx, tr, dl, lineY, nowNs);
                baseDrawY -= stepY;
            }
        } else {
            float baseDrawY = y() + 4.0f + (scrollLineOffset > 0 ? 12.0f : 0.0f);
            int skippedLines = 0;
            for (int m = newestMsgIdx; m >= 0; m--) {
                if (baseDrawY > y() + height() + stepY) break;
                for (int i = 0; i < totalLines; i++) {
                    DisplayLine dl = allWrappedLines.get(i);
                    if (dl.msgIndex() == m) {
                        if (skippedLines < scrollLineOffset) {
                            skippedLines++;
                            continue;
                        }
                        float lineY = baseDrawY - (dl.msgIndex() < newestMsgIdx ? scrollPushPx : 0.0f);
                        if (lineY > y() + height() + stepY) break;
                        drawSingleDisplayLine(ctx, tr, dl, lineY, nowNs);
                        baseDrawY += stepY;
                    }
                }
            }
        }

        if (scrollLineOffset > 0) {
            float barY = newestAtTop ? (y() + 2.0f) : (y() + height() - 13.0f);
            UIRenderer.drawRoundedRect(ctx, x() + 4, barY, width() - 8, 11, 4.0f, 0xD910141D);
            String histMsg = "▲ History (-" + scrollLineOffset + ") | Hold Down 2s for Live";
            UIRenderer.drawText(ctx, histMsg, x() + 8, barY + 1.5f, 0xFF7DCFFF, 0.85f, false, true);
        }

        ctx.disableScissor();
    }

    private void drawSingleDisplayLine(DrawContext ctx, TextRenderer tr, DisplayLine line, float drawY, long nowNs) {
        StreamManager.ChatMessage msg = line.msg();
        long startNs = firstRenderNsMap.computeIfAbsent(msg.id(), k -> nowNs);
        float duration = Math.max(0.20f, entranceDurationSec / Math.max(0.5f, chatAnimSpeed));
        float ageSec = Math.max(0.0f, (nowNs - startNs) / 1_000_000_000.0f);
        float rawT = (style().animationEnabled && scrollLineOffset == 0 && ageSec < duration)
            ? Math.max(0.0f, Math.min(1.0f, ageSec / duration))
            : 1.0f;

        float easeCubic = 1.0f - (float) Math.pow(1.0f - rawT, 3);
        float easeQuart = 1.0f - (float) Math.pow(1.0f - rawT, 4);
        float easeBack = 1.0f + 1.9f * (float) Math.pow(rawT - 1.0f, 3) + 0.9f * (float) Math.pow(rawT - 1.0f, 2);

        float slideX = 0.0f;
        float slideY = 0.0f;
        float popScale = 1.0f;

        if (rawT < 1.0f) {
            switch (chatAnimStyle) {
                case "LIQUID_POP" -> {
                    slideX = (1.0f - easeCubic) * -90.0f;
                    popScale = 0.78f + 0.22f * easeBack;
                }
                case "SLIDE_SIDE" -> {
                    slideX = (1.0f - easeBack) * -125.0f;
                }
                case "BOUNCE" -> {
                    slideX = (1.0f - easeBack) * -110.0f;
                    slideY = -(float) Math.sin(rawT * Math.PI) * 3.5f;
                }
                case "CASCADE" -> {
                    slideX = (1.0f - easeQuart) * -130.0f;
                    slideY = (1.0f - easeCubic) * -4.0f;
                    popScale = 0.88f + 0.12f * easeCubic;
                }
                case "SMOOTH_FADE" -> {
                    slideX = (1.0f - easeCubic) * -55.0f;
                }
                default -> { // SLIDE_UP -> Smooth Left-to-Right Slide
                    slideX = (1.0f - easeCubic) * -115.0f;
                }
            }
        }

        int alpha = Math.max(25, Math.min(255, Math.round(255.0f * easeCubic)));
        float finalScale = style().scale * popScale;

        MatrixStack matrices = ctx.getMatrices();
        matrices.push();
        matrices.translate(x() + 6.0f + slideX, drawY + slideY, 0.0f);
        if (Math.abs(finalScale - 1.0f) > 0.001f) {
            matrices.scale(finalScale, finalScale, 1.0f);
        }

        int prefixW = line.prefixWidth();
        if (chatShadow) {
            int bgAlpha = Math.round(115 * (alpha / 255.0f));
            float lineStartX = line.isFirstLine() ? -2.5f : (prefixW - 2.5f);
            float pillW = (prefixW + line.contentWidth() + 5.0f) - lineStartX;
            UIRenderer.drawRoundedRect(ctx, lineStartX, -1.5f, Math.max(12.0f, pillW), 11.0f, 3.5f, (bgAlpha << 24) | 0x0B0E14);
        }

        float cursorX = 0.0f;
        if (line.isFirstLine()) {
            if (showYouTubeIcon) {
                cursorX += EmojiRenderer.drawYouTubeBadge(ctx, cursorX, 0, alpha);
            }
            if (msg.owner() || msg.moderator() || msg.member()) {
                cursorX += EmojiRenderer.drawRoleBadge(ctx, cursorX, 0, msg.owner(), msg.moderator(), msg.member(), alpha);
            }
            if (!line.timestampPart().isEmpty()) {
                ctx.drawText(tr, line.timestampPart(), Math.round(cursorX), 0,
                    UIRenderer.withAlpha(timestampColor, alpha), textShadow);
                cursorX += tr.getWidth(line.timestampPart());
            }
            if (!line.authorPart().isEmpty()) {
                int aCol = resolveAuthorColor(msg, line.msgIndex());
                ctx.drawText(tr, line.authorPart(), Math.round(cursorX), 0,
                    UIRenderer.withAlpha(aCol, alpha), textShadow);
            }
        }

        int mCol = UIRenderer.withAlpha(resolveMessageColor(msg, line.msgIndex()), alpha);
        EmojiRenderer.drawRichText(ctx, tr, line.contentPart(), prefixW, 0, mCol, style().smallCaps, textShadow);

        matrices.pop();
    }

    private String formatAuthorColumn(String author) {
        String a = author.startsWith("@") ? author : ("@" + author);
        if (a.length() > 15) a = a.substring(0, 12) + "...";
        return a + ": ";
    }

    private int resolveAuthorColor(StreamManager.ChatMessage msg, int idx) {
        if (rgbMode) {
            float hue = ((System.currentTimeMillis() % 3500L) / 3500.0f + idx * 0.07f) % 1.0f;
            return 0xFF000000 | (Color.HSBtoRGB(hue, 0.75f, 1.0f) & 0x00FFFFFF);
        }
        if (msg.owner()) return 0xFFFFD866;
        if (msg.moderator()) return 0xFF5E97F6;
        if (msg.member()) return 0xFF4ADE80;
        if (coloredUsernames) {
            int hash = Math.abs(msg.author().hashCode());
            return VIBRANT_PALETTE[hash % VIBRANT_PALETTE.length];
        }
        return usernameColor;
    }

    private int resolveMessageColor(StreamManager.ChatMessage msg, int idx) {
        if (rgbMode) {
            float hue = ((System.currentTimeMillis() % 3500L) / 3500.0f + idx * 0.07f + 0.18f) % 1.0f;
            return 0xFF000000 | (Color.HSBtoRGB(hue, 0.55f, 1.0f) & 0x00FFFFFF);
        }
        if (coloredMessages) {
            int hash = Math.abs(msg.id().hashCode() + idx * 31);
            return VIBRANT_PALETTE[hash % VIBRANT_PALETTE.length];
        }
        return messageColor;
    }

    private List<String> wrapTextToWidth(TextRenderer tr, String text, int firstMaxW, int nextMaxW) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }
        String remaining = text.trim();
        int currentMax = firstMaxW;

        while (!remaining.isEmpty()) {
            if (EmojiRenderer.measureRichTextWidth(tr, remaining, style().smallCaps) <= currentMax) {
                lines.add(remaining);
                break;
            }
            int cut = findWrapCutIndex(tr, remaining, currentMax);
            String part = remaining.substring(0, cut).trim();
            if (part.isEmpty()) {
                int step = Character.charCount(remaining.codePointAt(0));
                lines.add(remaining.substring(0, step));
                remaining = remaining.substring(step).trim();
            } else {
                lines.add(part);
                remaining = remaining.substring(cut).trim();
            }
            currentMax = nextMaxW;
        }
        if (lines.isEmpty()) lines.add("");
        return lines;
    }

    private int findWrapCutIndex(TextRenderer tr, String str, int maxW) {
        int lastSpace = -1;
        int idx = 0;
        while (idx < str.length()) {
            int cp = str.codePointAt(idx);
            int nextIdx = idx + Character.charCount(cp);
            if (cp == ' ') lastSpace = idx;
            int w = EmojiRenderer.measureRichTextWidth(tr, str.substring(0, nextIdx), style().smallCaps);
            if (w > maxW) {
                if (lastSpace > idx / 3) return lastSpace;
                return Math.max(Character.charCount(str.codePointAt(0)), idx);
            }
            idx = nextIdx;
        }
        return str.length();
    }
}
