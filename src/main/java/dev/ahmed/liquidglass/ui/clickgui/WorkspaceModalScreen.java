package dev.ahmed.liquidglass.ui.clickgui;

import dev.ahmed.liquidglass.config.ConfigManager;
import dev.ahmed.liquidglass.hud.HudManager;
import dev.ahmed.liquidglass.stream.StreamManager;
import dev.ahmed.liquidglass.ui.components.GlassTextField;
import dev.ahmed.liquidglass.ui.render.IconRenderer;
import dev.ahmed.liquidglass.ui.render.UIRenderer;
import dev.ahmed.liquidglass.ui.theme.GlassQuality;
import dev.ahmed.liquidglass.ui.theme.ThemeManager;
import dev.ahmed.liquidglass.ui.theme.ThemeTokens;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class WorkspaceModalScreen extends Screen {
    private static final String[] PROFILES = {"Default", "Streaming", "Gaming", "Minimal"};
    private final Screen parent;
    private final GlassTextField streamUrlField = new GlassTextField("Paste YouTube Live URL (youtube.com/watch?v=... or /live/...)");
    private long lastFrameNs = System.nanoTime();

    public WorkspaceModalScreen(Screen parent) {
        super(Text.literal("LiveStream Workspace"));
        this.parent = parent;
        ConfigManager cfg = ConfigManager.get();
        String url = StreamManager.get().lastUrl().isEmpty() ? cfg.youtubeUrl() : StreamManager.get().lastUrl();
        streamUrlField.setText(url);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float deltaTicks) {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastFrameNs) / 1_000_000_000.0f);
        lastFrameNs = now;

        ThemeTokens t = ThemeManager.get().tokens();
        ctx.fill(0, 0, this.width, this.height, t.bgOverlay());

        float modalW = Math.min(520.0f, this.width - 40.0f);
        float modalH = Math.min(310.0f, this.height - 40.0f);
        float mx = (this.width - modalW) * 0.5f;
        float my = (this.height - modalH) * 0.5f;

        UIRenderer.drawGlassPanel(ctx, mx, my, modalW, modalH, t.radiusLarge(),
            t.bgGlassStrong(), t.borderBase(), 0.1f, mouseX, mouseY);

        IconRenderer.drawIcon(ctx, IconRenderer.Icon.BACK, mx + 12, my + 12, 10, t.textSecondary());
        UIRenderer.drawText(ctx, "Back", mx + 26, my + 13, t.textSecondary(), 1.0f, false, false);
        UIRenderer.drawText(ctx, "LiveStream Connection (No API Key Needed)", mx + 74, my + 13, t.textPrimary(), 1.0f, false, false);

        var snap = StreamManager.get().snapshot();
        String stateText = snap.state().name();
        int badgeW = this.textRenderer.getWidth(stateText) + 16;
        UIRenderer.drawRoundedRect(ctx, mx + modalW - badgeW - 12, my + 10, badgeW, 16, 8.0f, t.bgElevated());
        UIRenderer.drawText(ctx, stateText, mx + modalW - badgeW - 4, my + 14, t.accentPrimary(), 1.0f, false, false);

        float sideX = mx + 12;
        float sideY = my + 36;
        float sideW = 125;
        float sideH = modalH - 48;
        UIRenderer.drawRoundedRect(ctx, sideX, sideY, sideW, sideH, t.radiusMedium(), t.bgElevated());
        UIRenderer.drawRoundedBorder(ctx, sideX, sideY, sideW, sideH, t.radiusMedium(), 1.0f, t.borderBase());

        UIRenderer.drawText(ctx, "HUD PROFILES", sideX + 10, sideY + 10, t.textMuted(), 0.9f, true, false);
        for (int i = 0; i < PROFILES.length; i++) {
            float py = sideY + 26 + i * 24;
            boolean active = PROFILES[i].equals(HudManager.get().activeProfile());
            UIRenderer.drawRoundedRect(ctx, sideX + 6, py, sideW - 12, 20, t.radiusSmall(),
                active ? t.bgGlassStrong() : t.bgGlassWeak());
            UIRenderer.drawRoundedBorder(ctx, sideX + 6, py, sideW - 12, 20, t.radiusSmall(), 1.0f,
                active ? t.borderActive() : t.borderBase());
            UIRenderer.drawText(ctx, PROFILES[i], sideX + 14, py + 6,
                active ? t.textPrimary() : t.textSecondary(), 1.0f, false, false);
        }

        UIRenderer.drawText(ctx, "GLASS QUALITY", sideX + 10, sideY + 132, t.textMuted(), 0.9f, true, false);
        GlassQuality[] qualities = GlassQuality.values();
        for (int i = 0; i < qualities.length; i++) {
            float qy = sideY + 148 + i * 22;
            boolean active = qualities[i] == ThemeManager.get().quality();
            UIRenderer.drawRoundedRect(ctx, sideX + 6, qy, sideW - 12, 18, t.radiusSmall(),
                active ? t.bgGlassStrong() : t.bgGlassWeak());
            UIRenderer.drawText(ctx, qualities[i].name(), sideX + 14, qy + 5,
                active ? t.accentPrimary() : t.textSecondary(), 1.0f, false, false);
        }

        float mainX = sideX + sideW + 10;
        float mainY = sideY;
        float mainW = modalW - sideW - 34;
        float mainH = sideH;
        UIRenderer.drawRoundedRect(ctx, mainX, mainY, mainW, mainH, t.radiusMedium(), t.bgElevated());
        UIRenderer.drawRoundedBorder(ctx, mainX, mainY, mainW, mainH, t.radiusMedium(), 1.0f, t.borderBase());

        UIRenderer.drawText(ctx, "YouTube Live URL (100% Free & Unlimited)", mainX + 12, mainY + 14, t.textPrimary(), 1.0f, false, false);
        float fieldW = mainW - 24;
        streamUrlField.render(ctx, mainX + 12, mainY + 32, fieldW, 24, mouseX, mouseY, dt);

        float bw = (fieldW - 12) / 3.0f;
        drawButton(ctx, mainX + 12, mainY + 66, bw, 22, "Connect", mouseX, mouseY);
        drawButton(ctx, mainX + 18 + bw, mainY + 66, bw, 22, "Disconnect", mouseX, mouseY);
        drawButton(ctx, mainX + 24 + bw * 2, mainY + 66, bw, 22, "Refresh", mouseX, mouseY);

        String status = snap.statusMessage();
        int maxStatusW = (int) fieldW;
        if (this.textRenderer.getWidth(status) > maxStatusW) {
            status = this.textRenderer.trimToWidth(status, maxStatusW - 8) + "...";
        }
        UIRenderer.drawText(ctx, status, mainX + 12, mainY + 100, t.textSecondary(), 1.0f, false, false);

        float cardW = (mainW - 32) * 0.5f;
        drawTelemetryCard(ctx, mainX + 12, mainY + 120, cardW, 42, "Channel", snap.channelName());
        drawTelemetryCard(ctx, mainX + 20 + cardW, mainY + 120, cardW, 42,
            "Subscribers", snap.subscriberCount() != null ? HudManager.formatCompactNumber(snap.subscriberCount()) : "--");
        drawTelemetryCard(ctx, mainX + 12, mainY + 170, cardW, 42,
            "Live Viewers", snap.viewerCount() != null ? HudManager.formatCompactNumber(snap.viewerCount()) : "--");
        drawTelemetryCard(ctx, mainX + 20 + cardW, mainY + 170, cardW, 42,
            "Likes", snap.likeCount() != null ? HudManager.formatCompactNumber(snap.likeCount()) : "--");
    }

    private void drawButton(DrawContext ctx, float x, float y, float w, float h, String label, int mouseX, int mouseY) {
        ThemeTokens t = ThemeManager.get().tokens();
        boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        UIRenderer.drawRoundedRect(ctx, x, y, w, h, t.radiusSmall(), hover ? t.bgGlassStrong() : t.bgGlassWeak());
        UIRenderer.drawRoundedBorder(ctx, x, y, w, h, t.radiusSmall(), 1.0f, hover ? t.borderActive() : t.borderBase());
        float tw = this.textRenderer.getWidth(label);
        UIRenderer.drawText(ctx, label, x + (w - tw) * 0.5f, y + (h - 8) * 0.5f, t.textPrimary(), 1.0f, false, false);
    }

    private void drawTelemetryCard(DrawContext ctx, float x, float y, float w, float h, String label, String value) {
        ThemeTokens t = ThemeManager.get().tokens();
        UIRenderer.drawRoundedRect(ctx, x, y, w, h, t.radiusSmall(), t.bgGlassWeak());
        UIRenderer.drawRoundedBorder(ctx, x, y, w, h, t.radiusSmall(), 1.0f, t.borderBase());
        UIRenderer.drawText(ctx, label, x + 8, y + 8, t.textMuted(), 1.0f, true, false);
        UIRenderer.drawText(ctx, value, x + 8, y + 24, t.textPrimary(), 1.0f, false, false);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double cx = click.x();
        double cy = click.y();
        int btn = click.button();

        float modalW = Math.min(520.0f, this.width - 40.0f);
        float modalH = Math.min(310.0f, this.height - 40.0f);
        float mx = (this.width - modalW) * 0.5f;
        float my = (this.height - modalH) * 0.5f;

        if (btn == 0 && cx >= mx + 10 && cx <= mx + 60 && cy >= my + 10 && cy <= my + 28) {
            if (this.client != null) this.client.setScreen(parent);
            return true;
        }

        float sideX = mx + 12;
        float sideY = my + 36;
        float sideW = 125;

        for (int i = 0; i < PROFILES.length; i++) {
            float py = sideY + 26 + i * 24;
            if (btn == 0 && cx >= sideX + 6 && cx <= sideX + sideW - 6 && cy >= py && cy <= py + 20) {
                HudManager.get().applyProfilePreset(PROFILES[i]);
                ConfigManager.get().save();
                return true;
            }
        }

        GlassQuality[] qualities = GlassQuality.values();
        for (int i = 0; i < qualities.length; i++) {
            float qy = sideY + 148 + i * 22;
            if (btn == 0 && cx >= sideX + 6 && cx <= sideX + sideW - 6 && cy >= qy && cy <= qy + 18) {
                ThemeManager.get().setQuality(qualities[i]);
                ConfigManager.get().save();
                return true;
            }
        }

        float mainX = sideX + sideW + 10;
        float mainY = sideY;
        float mainW = modalW - sideW - 34;

        float fieldW = mainW - 24;
        if (streamUrlField.mouseClicked(cx, cy, btn, mainX + 12, mainY + 32, fieldW, 24)) {
            return true;
        }

        float bw = (fieldW - 12) / 3.0f;
        float by = mainY + 66;
        if (btn == 0 && cy >= by && cy <= by + 22) {
            if (cx >= mainX + 12 && cx <= mainX + 12 + bw) {
                ConfigManager.get().setStreamSettings(streamUrlField.getText(), "");
                ConfigManager.get().save();
                StreamManager.get().connect(streamUrlField.getText());
                return true;
            }
            if (cx >= mainX + 18 + bw && cx <= mainX + 18 + bw * 2) {
                StreamManager.get().disconnect();
                return true;
            }
            if (cx >= mainX + 24 + bw * 2 && cx <= mainX + 24 + bw * 3) {
                StreamManager.get().refresh();
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    @Override public boolean mouseDragged(Click click, double ox, double oy) { if (streamUrlField.mouseDragged(click.x())) return true; return super.mouseDragged(click, ox, oy); }
    @Override public boolean mouseReleased(Click click) { streamUrlField.mouseReleased(); return super.mouseReleased(click); }
    public boolean keyPressed(KeyInput input) {
        if (streamUrlField.keyPressed(input)) return true;
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (this.client != null) this.client.setScreen(parent);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (streamUrlField.charTyped(input)) return true;
        return super.charTyped(input);
    }

    @Override
    public void removed() {
        ConfigManager.get().setStreamSettings(streamUrlField.getText(), "");
        ConfigManager.get().save();
    }

    @Override
    public boolean shouldPause() { return false; }
}
