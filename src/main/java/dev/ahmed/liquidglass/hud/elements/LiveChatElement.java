package dev.ahmed.liquidglass.hud.elements;

import dev.ahmed.liquidglass.hud.HudElement;
import dev.ahmed.liquidglass.stream.StreamManager;
import dev.ahmed.liquidglass.ui.animation.Animation;
import dev.ahmed.liquidglass.ui.render.UIRenderer;
import dev.ahmed.liquidglass.ui.theme.ThemeManager;
import dev.ahmed.liquidglass.ui.theme.ThemeTokens;
import dev.ahmed.liquidglass.youtube.ChatPopoutHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;

import java.awt.Color;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class LiveChatElement extends HudElement {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault());

    private static final int[] VIBRANT_PALETTE = {
        0xFFFF6B8B, 0xFF7AA2F7, 0xFF9ECE6A, 0xFFE0AF68,
        0xFFBB9AF7, 0xFF7DCFFF, 0xFFF7768E, 0xFF73DACA,
        0xFFFF9E64, 0xFFC0CAF5, 0xFF2AC3DE, 0xFFB4F9F8
    };

    public boolean showUsername = true;
    public boolean showTimestamp = true;
    public boolean enableEmojiFallback = true;
    public boolean coloredUsernames = true;
    public boolean coloredMessages = false;
    public boolean rgbMode = false;

    public int usernameColor = 0xFFD8DEE9;
    public int messageColor = 0xFFF4F5F7;
    public int timestampColor = 0xFF707581;
    public int maxVisibleMessages = 14;
    public float lineSpacing = 12.0f;
    public float entranceDurationSec = 0.22f;

    private static volatile int scrollLineOffset = 0;

    private record DisplayLine(
        StreamManager.ChatMessage msg,
        String timestampPart,
        String authorPart,
        String contentPart,
        boolean isFirstLine,
        int msgIndex
    ) {}

    public LiveChatElement() {
        super("live_chat", "Live Chat", 12, 160, 260, 145, true);
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
        ThemeTokens t = ThemeManager.get().tokens();
        Deque<StreamManager.ChatMessage> queue = StreamManager.get().chatMessages();

        ctx.enableScissor(
            Math.round(x() + 4), Math.round(y() + 4),
            Math.round(x() + width() - 4), Math.round(y() + height() - 4)
        );

        if (queue.isEmpty()) {
            UIRenderer.drawText(ctx, "Live chat waiting for messages...", x() + 8, y() + 10,
                t.textMuted(), style().scale, style().smallCaps, false);
            ctx.disableScissor();
            return;
        }

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int availWidth = Math.max(60, Math.round((width() - 16.0f) / Math.max(0.5f, style().scale)));

        List<StreamManager.ChatMessage> snapshot = new ArrayList<>(queue);
        List<DisplayLine> allWrappedLines = new ArrayList<>(snapshot.size() * 2);

        for (int mIdx = 0; mIdx < snapshot.size(); mIdx++) {
            StreamManager.ChatMessage msg = snapshot.get(mIdx);
            String ts = showTimestamp ? (TIME_FMT.format(Instant.ofEpochMilli(msg.timestampMs())) + " ") : "";
            String author = showUsername ? (msg.author() + ": ") : "";
            String body = ChatPopoutHelper.replaceShortcodes(msg.content());

            int prefixW = tr.getWidth(ts + author);
            int firstLineAvail = Math.max(30, availWidth - prefixW);

            List<String> wrapped = wrapTextToWidth(tr, body, firstLineAvail, Math.max(40, availWidth - 10));
            for (int l = 0; l < wrapped.size(); l++) {
                if (l == 0) {
                    allWrappedLines.add(new DisplayLine(msg, ts, author, wrapped.get(0), true, mIdx));
                } else {
                    allWrappedLines.add(new DisplayLine(msg, "", "  ", wrapped.get(l), false, mIdx));
                }
            }
        }

        int totalLines = allWrappedLines.size();
        int maxLinesInBox = Math.max(1, (int) ((height() - 16.0f) / (lineSpacing * style().scale)));
        int maxScroll = Math.max(0, totalLines - maxLinesInBox);
        if (scrollLineOffset > maxScroll) {
            scrollLineOffset = maxScroll;
        }

        int bottomLineIdx = totalLines - 1 - scrollLineOffset;
        long nowNs = System.nanoTime();
        float stepY = lineSpacing * style().scale;
        float drawY = y() + height() - 14.0f - (scrollLineOffset > 0 ? 12.0f : 0.0f);

        for (int i = bottomLineIdx; i >= 0; i--) {
            if (drawY < y() + 4) break;
            DisplayLine line = allWrappedLines.get(i);
            StreamManager.ChatMessage msg = line.msg();

            float ageSec = (nowNs - msg.receivedNanoTime()) / 1_000_000_000.0f;
            float anim = (style().animationEnabled && scrollLineOffset == 0)
                ? Animation.Easing.EASE_OUT_CUBIC.apply(Math.min(1.0f, ageSec / Math.max(0.05f, entranceDurationSec)))
                : 1.0f;

            float offsetY = (1.0f - anim) * 5.0f;
            int alpha = Math.max(15, Math.min(255, (int) (255 * anim)));

            Matrix3x2fStack matrices = ctx.getMatrices();
            matrices.pushMatrix();
            matrices.translate(x() + 8, drawY + offsetY);
            matrices.scale(style().scale, style().scale);

            float cursorX = 0.0f;
            if (!line.timestampPart().isEmpty()) {
                ctx.drawText(tr, line.timestampPart(), Math.round(cursorX), 0,
                    UIRenderer.withAlpha(timestampColor, alpha), false);
                cursorX += tr.getWidth(line.timestampPart());
            }
            if (!line.authorPart().isEmpty()) {
                int aCol = line.isFirstLine() ? resolveAuthorColor(msg, line.msgIndex()) : messageColor;
                ctx.drawText(tr, line.authorPart(), Math.round(cursorX), 0,
                    UIRenderer.withAlpha(aCol, alpha), false);
                cursorX += tr.getWidth(line.authorPart());
            }

            int mCol = resolveMessageColor(msg, line.msgIndex());
            ctx.drawText(tr, line.contentPart(), Math.round(cursorX), 0,
                UIRenderer.withAlpha(mCol, alpha), false);

            matrices.popMatrix();
            drawY -= stepY;
        }

        if (scrollLineOffset > 0) {
            float barY = y() + height() - 13.0f;
            UIRenderer.drawRoundedRect(ctx, x() + 6, barY - 1, width() - 12, 11, 3.0f, 0xCC1E2430);
            String histMsg = "History (-" + scrollLineOffset + " lines) | Hold PageDown 2s for Live";
            UIRenderer.drawText(ctx, histMsg, x() + 10, barY + 1, 0xFF7DCFFF, 0.85f, false, false);
        }

        ctx.disableScissor();
    }

    private int resolveAuthorColor(StreamManager.ChatMessage msg, int idx) {
        if (rgbMode) {
            float hue = ((System.currentTimeMillis() % 4000L) / 4000.0f + idx * 0.07f) % 1.0f;
            return 0xFF000000 | (Color.HSBtoRGB(hue, 0.72f, 1.0f) & 0x00FFFFFF);
        }
        if (msg.owner()) return 0xFFFFD866;
        if (msg.moderator()) return 0xFF8AB4F8;
        if (msg.member()) return 0xFF7FD6A0;
        if (coloredUsernames) {
            int hash = Math.abs(msg.author().hashCode());
            return VIBRANT_PALETTE[hash % VIBRANT_PALETTE.length];
        }
        return usernameColor;
    }

    private int resolveMessageColor(StreamManager.ChatMessage msg, int idx) {
        if (rgbMode) {
            float hue = ((System.currentTimeMillis() % 4000L) / 4000.0f + idx * 0.07f + 0.15f) % 1.0f;
            return 0xFF000000 | (Color.HSBtoRGB(hue, 0.55f, 1.0f) & 0x00FFFFFF);
        }
        if (coloredMessages) {
            int hash = Math.abs(msg.id().hashCode() + idx * 31);
            return VIBRANT_PALETTE[hash % VIBRANT_PALETTE.length];
        }
        return messageColor;
    }

    private static List<String> wrapTextToWidth(TextRenderer tr, String text, int firstMaxW, int nextMaxW) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }
        String remaining = text.trim();
        int currentMax = firstMaxW;

        while (!remaining.isEmpty()) {
            if (tr.getWidth(remaining) <= currentMax) {
                lines.add(remaining);
                break;
            }
            String trimmed = tr.trimToWidth(remaining, currentMax);
            if (trimmed.isEmpty()) {
                int step = Character.charCount(remaining.codePointAt(0));
                lines.add(remaining.substring(0, step));
                remaining = remaining.substring(step).trim();
            } else {
                int lastSpace = trimmed.lastIndexOf(' ');
                if (lastSpace > trimmed.length() / 3) {
                    lines.add(trimmed.substring(0, lastSpace));
                    remaining = remaining.substring(lastSpace + 1).trim();
                } else {
                    lines.add(trimmed);
                    remaining = remaining.substring(trimmed.length()).trim();
                }
            }
            currentMax = nextMaxW;
        }
        if (lines.isEmpty()) lines.add("");
        return lines;
    }
}
