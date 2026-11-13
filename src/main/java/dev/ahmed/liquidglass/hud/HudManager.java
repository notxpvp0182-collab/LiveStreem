package dev.ahmed.liquidglass.hud;

import dev.ahmed.liquidglass.hud.elements.LiveChatElement;
import dev.ahmed.liquidglass.stream.StreamManager;
import dev.ahmed.liquidglass.ui.debug.DebugOverlay;
import dev.ahmed.liquidglass.ui.render.UIRenderer;
import dev.ahmed.liquidglass.ui.theme.ThemeManager;
import dev.ahmed.liquidglass.ui.theme.ThemeTokens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class HudManager {
    private static final HudManager INSTANCE = new HudManager();
    private final List<HudElement> elements = new ArrayList<>();
    private String activeProfile = "Default";

    private HudManager() {}

    public static HudManager get() { return INSTANCE; }

    public void initDefaults() {
        if (!elements.isEmpty()) return;

        elements.add(new HudElement("live_indicator", "Live Indicator", 12, 12, 62, 20, true) {
            @Override
            protected void renderContent(DrawContext ctx) {
                ThemeTokens t = ThemeManager.get().tokens();
                var snap = StreamManager.get().snapshot();
                boolean isLive = snap.state() == StreamManager.ConnectionState.LIVE;
                float pulse = isLive ? (float) (0.65 + 0.35 * Math.sin(System.currentTimeMillis() / 260.0)) : 0.4f;
                int dotColor = isLive ? UIRenderer.withAlpha(0xFFFC8181, (int) (255 * pulse)) : t.textMuted();
                UIRenderer.drawRoundedRect(ctx, x() + 7, y() + 7, 6, 6, 3.0f, dotColor);
                UIRenderer.drawText(ctx, isLive ? "LIVE" : snap.state().name(),
                    x() + 18, y() + 6, style().textColor, style().scale, style().smallCaps, false);
            }
        });

        elements.add(metricElement("viewer_count", "Viewer Count", 78, 12, 86, 20, true, () -> {
            Long v = StreamManager.get().snapshot().viewerCount();
            return "Viewers: " + (v != null ? formatCompactNumber(v) : "--");
        }));

        elements.add(metricElement("like_count", "Like Count", 168, 12, 78, 20, true, () -> {
            Long l = StreamManager.get().snapshot().likeCount();
            return "Likes: " + (l != null ? formatCompactNumber(l) : "--");
        }));

        elements.add(metricElement("channel_name", "Channel Name", 12, 36, 128, 20, true, () ->
            "Channel: " + StreamManager.get().snapshot().channelName()));

        elements.add(metricElement("channel_handle", "Channel Handle", 144, 36, 102, 20, true, () ->
            "Handle: " + StreamManager.get().snapshot().channelHandle()));

        elements.add(metricElement("subscriber_count", "Subscriber Count", 12, 60, 118, 20, true, () -> {
            Long s = StreamManager.get().snapshot().subscriberCount();
            return "Subs: " + (s != null ? formatCompactNumber(s) : "--");
        }));

        elements.add(metricElement("stream_duration", "Stream Duration", 134, 60, 112, 20, true, () -> {
            var snap = StreamManager.get().snapshot();
            long start = snap.streamStartEpochMs();
            if (start <= 0L || snap.state() != StreamManager.ConnectionState.LIVE) return "Duration: --:--";
            long elapsedSec = Math.max(0L, (System.currentTimeMillis() - start) / 1000L);
            return String.format(Locale.ROOT, "Live: %02d:%02d:%02d",
                elapsedSec / 3600, (elapsedSec % 3600) / 60, elapsedSec % 60);
        }));

        elements.add(metricElement("stream_title", "Stream Title", 12, 84, 234, 20, true, () -> {
            String raw = StreamManager.get().snapshot().streamTitle();
            if (raw == null || raw.isEmpty()) return "Title: --";
            return raw.length() > 34 ? raw.substring(0, 31) + "..." : raw;
        }));

        elements.add(metricElement("connection_status", "Connection Status", 12, 108, 234, 20, false, () ->
            "Status: " + StreamManager.get().snapshot().statusMessage()));

        elements.add(new LiveChatElement());
    }

    private static HudElement metricElement(String id, String name, float x, float y, float w, float h,
                                            boolean enabled, java.util.function.Supplier<String> textSupplier) {
        return new HudElement(id, name, x, y, w, h, enabled) {
            @Override
            protected void renderContent(DrawContext ctx) {
                UIRenderer.drawText(ctx, textSupplier.get(), x() + 7, y() + (height() - 8) * 0.5f,
                    style().textColor, style().scale, style().smallCaps, false);
            }
        };
    }

    public static String formatCompactNumber(long count) {
        if (count < 1_000L) return String.valueOf(count);
        if (count < 1_000_000L) return String.format(Locale.ROOT, "%.1fK", count / 1_000.0);
        return String.format(Locale.ROOT, "%.1fM", count / 1_000_000.0);
    }

    public void renderHud(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden) return;
        if (mc.currentScreen instanceof HudEditorScreen) {
            return;
        }
        long startNs = System.nanoTime();
        for (HudElement el : elements) {
            if (el.isEnabled()) {
                el.renderElement(ctx, -1, -1, false);
            }
        }
        DebugOverlay.render(ctx, System.nanoTime() - startNs);
    }

    public void applyProfilePreset(String profileName) {
        this.activeProfile = profileName;
        for (HudElement el : elements) {
            switch (profileName) {
                case "Streaming" -> el.setEnabled(true);
                case "Minimal" -> el.setEnabled(
                    el.id().equals("live_indicator") || el.id().equals("viewer_count") || el.id().equals("live_chat")
                );
                case "Gaming" -> el.setEnabled(
                    el.id().equals("live_indicator") || el.id().equals("viewer_count")
                );
                default -> el.setEnabled(!el.id().equals("connection_status"));
            }
        }
    }

    public void setActiveProfileName(String name) {
        if (name != null && !name.isBlank()) this.activeProfile = name;
    }

    public boolean isLiveChatEnabled() { for (HudElement el : elements) { if (el instanceof LiveChatElement) return el.isEnabled(); } return false; }
    public List<HudElement> elements() { return Collections.unmodifiableList(elements); }
    public String activeProfile() { return activeProfile; }
}
