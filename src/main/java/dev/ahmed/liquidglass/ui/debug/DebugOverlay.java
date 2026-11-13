package dev.ahmed.liquidglass.ui.debug;

import dev.ahmed.liquidglass.hud.HudElement;
import dev.ahmed.liquidglass.hud.HudManager;
import dev.ahmed.liquidglass.stream.StreamManager;
import dev.ahmed.liquidglass.ui.render.UIRenderer;
import dev.ahmed.liquidglass.ui.theme.ThemeManager;
import dev.ahmed.liquidglass.ui.theme.ThemeTokens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.Locale;

public final class DebugOverlay {
    private static boolean metricsEnabled = false;
    private static boolean visualBoundsEnabled = false;

    private DebugOverlay() {}

    public static void render(DrawContext ctx, long uiRenderTimeNs) {
        if (!metricsEnabled && !visualBoundsEnabled) return;
        ThemeTokens t = ThemeManager.get().tokens();

        if (visualBoundsEnabled) {
            for (HudElement el : HudManager.get().elements()) {
                if (!el.isEnabled()) continue;
                UIRenderer.drawRoundedBorder(ctx, el.x(), el.y(), el.width(), el.height(), 0, 1.0f, 0xAA63B3ED);
                UIRenderer.drawText(ctx, el.id() + " [" + (int) el.x() + "," + (int) el.y() + "]",
                    el.x() + 2, el.y() - 9, 0xFF63B3ED, 0.8f, false, true);
            }
        }

        if (metricsEnabled) {
            float x = ctx.getScaledWindowWidth() - 168.0f;
            float y = 10.0f;
            UIRenderer.drawGlassPanel(ctx, x, y, 158, 86, t.radiusSmall(), t.bgGlassStrong(), t.borderBase(), 0.0f, -1, -1);

            double uiMs = uiRenderTimeNs / 1_000_000.0;
            var snap = StreamManager.get().snapshot();

            UIRenderer.drawText(ctx, "DEV TELEMETRY", x + 8, y + 6, t.accentPrimary(), 0.9f, true, false);
            UIRenderer.drawText(ctx, String.format(Locale.ROOT, "FPS: %d | UI: %.2f ms",
                MinecraftClient.getInstance().getCurrentFps(), uiMs), x + 8, y + 20, t.textSecondary(), 0.9f, false, false);
            UIRenderer.drawText(ctx, "State: " + snap.state().name(), x + 8, y + 34, t.textSecondary(), 0.9f, false, false);
            UIRenderer.drawText(ctx, "HUD Elements: " + HudManager.get().elements().size(),
                x + 8, y + 48, t.textSecondary(), 0.9f, false, false);
            UIRenderer.drawText(ctx, "Chat Queue: " + StreamManager.get().chatMessages().size() + "/" + StreamManager.get().maxChatHistory(),
                x + 8, y + 62, t.textSecondary(), 0.9f, false, false);
        }
    }

    public static boolean isMetricsEnabled() { return metricsEnabled; }
    public static void setMetricsEnabled(boolean enabled) { metricsEnabled = enabled; }
    public static boolean isVisualBoundsEnabled() { return visualBoundsEnabled; }
    public static void setVisualBoundsEnabled(boolean enabled) { visualBoundsEnabled = enabled; }
}
