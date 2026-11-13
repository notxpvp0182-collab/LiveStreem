package dev.ahmed.liquidglass.hud.elements;

import dev.ahmed.liquidglass.hud.HudElement;
import dev.ahmed.liquidglass.stream.StreamManager;
import dev.ahmed.liquidglass.ui.animation.Animation;
import dev.ahmed.liquidglass.ui.render.UIRenderer;
import dev.ahmed.liquidglass.ui.theme.ThemeManager;
import dev.ahmed.liquidglass.ui.theme.ThemeTokens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;

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

    public boolean showUsername = true;
    public boolean showTimestamp = true;
    public boolean enableEmojiFallback = true;
    public int usernameColor = 0xFFD8DEE9;
    public int messageColor = 0xFFF4F5F7;
    public int timestampColor = 0xFF707581;
    public int maxVisibleMessages = 10;
    public float lineSpacing = 12.0f;
    public float entranceDurationSec = 0.22f;

    private record CachedLayout(String sanitizedLine, int pixelWidth) {}
    private final Map<String, CachedLayout> layoutCache = new ConcurrentHashMap<>(128);

    public LiveChatElement() {
        super("live_chat", "Live Chat", 12, 160, 230, 145, true);
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

        List<StreamManager.ChatMessage> snapshot = new ArrayList<>(queue);
        int startIdx = Math.max(0, snapshot.size() - maxVisibleMessages);
        long nowNs = System.nanoTime();
        float drawY = y() + height() - 16;

        for (int i = snapshot.size() - 1; i >= startIdx; i--) {
            if (drawY < y() + 4) break;
            StreamManager.ChatMessage msg = snapshot.get(i);

            // Entrance animation for newly arrived messages (opacity 0->1, translateY +6->0, scale 98%->100%)
            float ageSec = (nowNs - msg.receivedNanoTime()) / 1_000_000_000.0f;
            float anim = style().animationEnabled
                ? Animation.Easing.EASE_OUT_CUBIC.apply(Math.min(1.0f, ageSec / Math.max(0.05f, entranceDurationSec)))
                : 1.0f;

            float offsetY = (1.0f - anim) * 6.0f;
            float scale = 0.98f + 0.02f * anim;
            int alpha = Math.max(10, Math.min(255, (int) (255 * anim)));

            CachedLayout cached = getOrBuildLayout(msg);

            Matrix3x2fStack matrices = ctx.getMatrices();
            matrices.pushMatrix();
            matrices.translate(x() + 8, drawY + offsetY);
            matrices.scale(scale * style().scale, scale * style().scale);

            float cursorX = 0.0f;
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;

            if (showTimestamp) {
                String ts = TIME_FMT.format(Instant.ofEpochMilli(msg.timestampMs())) + " ";
                ctx.drawText(tr, ts, Math.round(cursorX), 0, UIRenderer.withAlpha(timestampColor, alpha), false);
                cursorX += tr.getWidth(ts);
            }
            if (showUsername) {
                String author = msg.author() + ": ";
                ctx.drawText(tr, author, Math.round(cursorX), 0, UIRenderer.withAlpha(authorColor(msg), alpha), false);
                cursorX += tr.getWidth(author);
            }
            ctx.drawText(tr, cached.sanitizedLine(), Math.round(cursorX), 0,
                UIRenderer.withAlpha(messageColor, alpha), false);

            matrices.popMatrix();
            drawY -= lineSpacing;
        }

        ctx.disableScissor();
    }

    private int authorColor(StreamManager.ChatMessage msg) {
        if (msg.owner()) return 0xFFFFD866;
        if (msg.moderator()) return 0xFF8AB4F8;
        if (msg.member()) return 0xFF7FD6A0;
        return usernameColor;
    }

    private CachedLayout getOrBuildLayout(StreamManager.ChatMessage msg) {
        if (layoutCache.size() > 256) layoutCache.clear();
        return layoutCache.computeIfAbsent(msg.id(), id -> {
            String safeText = enableEmojiFallback ? sanitizeUnsupportedEmoji(msg.content()) : msg.content();
            int w = MinecraftClient.getInstance().textRenderer.getWidth(safeText);
            return new CachedLayout(safeText, w);
        });
    }

    private static String sanitizeUnsupportedEmoji(String input) {
        if (input == null || input.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); ) {
            int cp = input.codePointAt(i);
            i += Character.charCount(cp);
            if (cp >= 0x1F600 && cp <= 0x1F64F) {
                sb.append("[:)]");
            } else if (cp >= 0x1F300 && cp <= 0x1FAFF) {
                sb.append("[*]");
            } else {
                sb.appendCodePoint(cp);
            }
        }
        return sb.toString();
    }
}
