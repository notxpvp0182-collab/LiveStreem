package dev.ahmed.liquidglass.hud.elements;

import dev.ahmed.liquidglass.hud.HudElement;
import dev.ahmed.liquidglass.stream.StreamManager;
import dev.ahmed.liquidglass.ui.animation.Animation;
import dev.ahmed.liquidglass.ui.font.WorldLanguageShaper;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class LiveChatElement extends HudElement {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault());

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

    public int usernameColor = 0xFFD8DEE9;
    public int messageColor = 0xFFFFFFFF;
    public int timestampColor = 0xFF8A909E;
    public int maxVisibleMessages = 14;
    public float lineSpacing = 12.5f;
    public float entranceDurationSec = 0.26f;

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
    private List<DisplayLine> cachedLines = List.of();
    private int cachedTotalReceived = -1;
    private int cachedAvailWidth = -1;
    private boolean cachedShowTs = false;
    private boolean cachedShowUser = true;
    private boolean cachedShowYt = true;
    private boolean cachedSmallCaps = false;

    public LiveChatElement() {
        super("live_chat", "Live Chat", 12, 145, 290, 155, true);
        style().backgroundEnabled = false;
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
        StreamManager.get().tickChatPacing();
        ThemeTokens t = ThemeManager.get().tokens();
        Deque<StreamManager.ChatMessage> queue = StreamManager.get().chatMessages();

        ctx.enableScissor(
            Math.round(x() + 2), Math.round(y() + 2),
            Math.round(x() + width() - 2), Math.round(y() + height() - 2)
        );

        if (queue.isEmpty()) {
            UIRenderer.drawText(ctx, "Live chat waiting for messages...", x() + 8, y() + 8,
                t.textMuted(), style().scale, style().smallCaps, textShadow);
            ctx.disableScissor();
            return;
        }

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int availWidth = Math.max(80, Math.round((width() - 14.0f) / Math.max(0.5f, style().scale)));
        int currentTotal = StreamManager.get().totalChatReceived();

        // Only rebuild display lines when a new message arrives or layout settings change
        if (currentTotal != cachedTotalReceived
            || availWidth != cachedAvailWidth
            || showTimestamp != cachedShowTs
            || showUsername != cachedShowUser
            || showYouTubeIcon != cachedShowYt
            || style().smallCaps != cachedSmallCaps) {

            if (availWidth != cachedAvailWidth || style().smallCaps != cachedSmallCaps) {
                wrapCache.clear();
            }
            cachedTotalReceived = currentTotal;
            cachedAvailWidth = availWidth;
            cachedShowTs = showTimestamp;
            cachedShowUser = showUsername;
            cachedShowYt = showYouTubeIcon;
            cachedSmallCaps = style().smallCaps;

            List<StreamManager.ChatMessage> snapshot = new ArrayList<>(queue);
            List<DisplayLine> built = new ArrayList<>(snapshot.size() * 2);

            for (int mIdx = 0; mIdx < snapshot.size(); mIdx++) {
                StreamManager.ChatMessage msg = snapshot.get(mIdx);
                String ts = showTimestamp ? (TIME_FMT.format(Instant.ofEpochMilli(msg.timestampMs())) + " ") : "";
                String author = showUsername ? formatAuthorColumn(msg.author()) : "";

                int badgeW = (showYouTubeIcon ? 14 : 0) + ((msg.owner() || msg.moderator() || msg.member()) ? 10 : 0);
                int prefixW = badgeW + tr.getWidth(ts + author);
                int lineAvail = Math.max(40, availWidth - prefixW);

                List<String> wrapped = wrapCache.computeIfAbsent(msg.id(), id -> {
                    String body = ChatPopoutHelper.replaceShortcodes(msg.content());
                    return wrapTextToWidth(tr, body, lineAvail, lineAvail);
                });
                if (wrapCache.size() > 600) wrapCache.clear();

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
            cachedLines = built;
        }

        List<DisplayLine> allWrappedLines = cachedLines;
        int totalLines = allWrappedLines.size();
        if (totalLines == 0) {
            ctx.disableScissor();
            return;
        }

        int maxLinesInBox = Math.max(1, (int) ((height() - 12.0f) / (lineSpacing * style().scale)));
        int maxScroll = Math.max(0, totalLines - maxLinesInBox);
        if (scrollLineOffset > maxScroll) {
            scrollLineOffset = maxScroll;
        }

        long nowNs = System.nanoTime();
        float stepY = lineSpacing * style().scale;

        if (!newestAtTop) {
            int bottomLineIdx = totalLines - 1 - scrollLineOffset;
            float drawY = y() + height() - 13.0f - (scrollLineOffset > 0 ? 12.0f : 0.0f);
            for (int i = bottomLineIdx; i >= 0; i--) {
                if (drawY < y() + 2) break;
                drawSingleDisplayLine(ctx, tr, allWrappedLines.get(i), drawY, nowNs);
                drawY -= stepY;
            }
        } else {
            float drawY = y() + 4.0f + (scrollLineOffset > 0 ? 12.0f : 0.0f);
            int maxMsgIdx = allWrappedLines.get(totalLines - 1).msgIndex();
            int skippedLines = 0;
            for (int m = maxMsgIdx; m >= 0; m--) {
                if (drawY > y() + height() - 12.0f) break;
                for (int i = 0; i < totalLines; i++) {
                    DisplayLine dl = allWrappedLines.get(i);
                    if (dl.msgIndex() == m) {
                        if (skippedLines < scrollLineOffset) {
                            skippedLines++;
                            continue;
                        }
                        if (drawY > y() + height() - 12.0f) break;
                        drawSingleDisplayLine(ctx, tr, dl, drawY, nowNs);
                        drawY += stepY;
                    }
                }
            }
        }

        if (scrollLineOffset > 0) {
            float barY = newestAtTop ? (y() + 2.0f) : (y() + height() - 13.0f);
            ctx.fill(Math.round(x() + 4), Math.round(barY), Math.round(x() + width() - 4), Math.round(barY + 11), 0xCC141822);
            String histMsg = "▲ History (-" + scrollLineOffset + ") | Hold Down 2s for Live";
            UIRenderer.drawText(ctx, histMsg, x() + 8, barY + 1.5f, 0xFF7DCFFF, 0.85f, false, true);
        }

        ctx.disableScissor();
    }

    private void drawSingleDisplayLine(DrawContext ctx, TextRenderer tr, DisplayLine line, float drawY, long nowNs) {
        StreamManager.ChatMessage msg = line.msg();
        float ageSec = (nowNs - msg.receivedNanoTime()) / 1_000_000_000.0f;
        float anim = (style().animationEnabled && scrollLineOffset == 0 && ageSec < entranceDurationSec)
            ? Animation.Easing.EASE_OUT_CUBIC.apply(Math.min(1.0f, ageSec / Math.max(0.05f, entranceDurationSec)))
            : 1.0f;

        float slideX = (1.0f - anim) * -10.0f;
        float slideY = (1.0f - anim) * (newestAtTop ? -4.0f : 4.0f);
        int alpha = Math.max(25, Math.min(255, (int) (255 * anim)));

        MatrixStack matrices = ctx.getMatrices();
        matrices.push();
        matrices.translate(x() + 6 + slideX, drawY + slideY, 0.0f);
        if (Math.abs(style().scale - 1.0f) > 0.001f) {
            matrices.scale(style().scale, style().scale, 1.0f);
        }

        int prefixW = line.prefixWidth();
        if (chatShadow) {
            int bgAlpha = Math.round(100 * (alpha / 255.0f));
            int lineStartX = line.isFirstLine() ? -2 : (prefixW - 2);
            int lineEndX = prefixW + line.contentWidth() + 4;
            ctx.fill(lineStartX, -1, lineEndX, 10, (bgAlpha << 24));
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
        return a + "  ";
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
