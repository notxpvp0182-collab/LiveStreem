package dev.ahmed.liquidglass.ui.render;

import dev.ahmed.liquidglass.ui.font.SmallCapsRenderer;
import dev.ahmed.liquidglass.ui.font.WorldLanguageShaper;
import dev.ahmed.liquidglass.ui.theme.GlassQuality;
import dev.ahmed.liquidglass.ui.theme.ThemeManager;
import dev.ahmed.liquidglass.ui.theme.ThemeTokens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;

public final class UIRenderer {
    // Pre-computed circle arc table for radii up to 36 half-pixels (zero runtime Math.sqrt overhead)
    private static final int MAX_R = 36;
    private static final int[][] ARC_INSET = new int[MAX_R + 1][];

    static {
        for (int r = 1; r <= MAX_R; r++) {
            ARC_INSET[r] = new int[r];
            for (int dy = 0; dy < r; dy++) {
                double py = r - dy - 0.5;
                int dx = (int) Math.round(Math.sqrt(Math.max(0.0, r * r - py * py)));
                ARC_INSET[r][dy] = Math.max(0, r - dx);
            }
        }
    }

    private UIRenderer() {}

    public static void drawGlassPanel(
        DrawContext ctx,
        float x, float y, float w, float h,
        float radius,
        int surfaceColor,
        int borderColor,
        float hoverProgress,
        float mouseX, float mouseY
    ) {
        ThemeTokens t = ThemeManager.get().tokens();
        GlassQuality q = ThemeManager.get().quality();

        float smoothRadius = Math.max(radius, 7.0f);

        if (q.shadowEnabled() && ((surfaceColor >>> 24) & 0xFF) > 10) {
            drawShadow(ctx, x, y + 2.0f, w, h, smoothRadius, 4.0f, t.shadowColor());
        }

        drawRoundedRect(ctx, x, y, w, h, smoothRadius, surfaceColor);

        if (q.innerHighlightEnabled() && hoverProgress > 0.01f) {
            int hoverTint = withAlpha(t.accentPrimary(), (int) (18 * hoverProgress));
            drawRoundedRect(ctx, x + 1.0f, y + 1.0f, w - 2.0f, h - 2.0f, Math.max(2.0f, smoothRadius - 1.0f), hoverTint);
        }

        if (((borderColor >>> 24) & 0xFF) > 2) {
            int effectiveBorder = lerpColor(borderColor, t.borderActive(), hoverProgress);
            drawRoundedBorder(ctx, x, y, w, h, smoothRadius, 1.0f, effectiveBorder);
        }
    }

    public static void drawShadow(DrawContext ctx, float x, float y, float w, float h, float radius, float spread, int shadowColor) {
        int baseAlpha = (shadowColor >>> 24) & 0xFF;
        if (baseAlpha <= 4) return;
        int rgb = shadowColor & 0x00FFFFFF;
        int c1 = (Math.max(4, baseAlpha / 4) << 24) | rgb;
        int c2 = (Math.max(2, baseAlpha / 8) << 24) | rgb;
        drawRoundedRect(ctx, x - 1.5f, y - 0.5f, w + 3.0f, h + 2.5f, radius + 1.5f, c1);
        drawRoundedRect(ctx, x - 3.0f, y - 1.0f, w + 6.0f, h + 4.5f, radius + 3.0f, c2);
    }

    /**
     * Renders at 0.5f sub-pixel scale using pre-computed circular arcs so corners are
     * genuinely round and non-pixelated while keeping high FPS.
     */
    public static void drawRoundedRect(DrawContext ctx, float x, float y, float w, float h, float radius, int color) {
        if (((color >>> 24) & 0xFF) <= 1 || w <= 0 || h <= 0) return;

        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(0.5f, 0.5f);

        int iw = Math.round(w * 2.0f);
        int ih = Math.round(h * 2.0f);
        int r = Math.min(MAX_R, Math.min(Math.round(radius * 2.0f), Math.min(iw, ih) / 2));

        if (r <= 1) {
            ctx.fill(0, 0, iw, ih, color);
            m.popMatrix();
            return;
        }

        int[] insets = ARC_INSET[r];
        for (int dy = 0; dy < r; dy++) {
            int in = insets[dy];
            if (iw - in > in) {
                ctx.fill(in, dy, iw - in, dy + 1, color);
                ctx.fill(in, ih - 1 - dy, iw - in, ih - dy, color);
            }
        }
        if (ih - r > r) {
            ctx.fill(0, r, iw, ih - r, color);
        }
        m.popMatrix();
    }

    public static void drawRoundedBorder(DrawContext ctx, float x, float y, float w, float h, float radius, float thickness, int color) {
        if (((color >>> 24) & 0xFF) <= 2 || w <= 2 || h <= 2) return;

        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(0.5f, 0.5f);

        int iw = Math.round(w * 2.0f);
        int ih = Math.round(h * 2.0f);
        int r = Math.min(MAX_R, Math.min(Math.round(radius * 2.0f), Math.min(iw, ih) / 2));

        if (r <= 1) {
            ctx.fill(0, 0, iw, 2, color);
            ctx.fill(0, ih - 2, iw, ih, color);
            ctx.fill(0, 2, 2, ih - 2, color);
            ctx.fill(iw - 2, 2, iw, ih - 2, color);
            m.popMatrix();
            return;
        }

        int[] insets = ARC_INSET[r];
        // Top and bottom straight border segments
        ctx.fill(r, 0, iw - r, 2, color);
        ctx.fill(r, ih - 2, iw - r, ih, color);
        // Left and right straight border segments
        ctx.fill(0, r, 2, ih - r, color);
        ctx.fill(iw - 2, r, iw, ih - r, color);

        // Smooth 2-subpixel corner arc strokes
        for (int dy = 0; dy < r; dy++) {
            int in = insets[dy];
            int nextIn = (dy + 1 < r) ? insets[dy + 1] : 0;
            int strokeW = Math.max(2, in - nextIn + 1);
            ctx.fill(in, dy, in + strokeW, dy + 1, color);
            ctx.fill(iw - in - strokeW, dy, iw - in, dy + 1, color);
            ctx.fill(in, ih - 1 - dy, in + strokeW, ih - dy, color);
            ctx.fill(iw - in - strokeW, ih - 1 - dy, iw - in, ih - dy, color);
        }

        m.popMatrix();
    }

    public static void drawText(DrawContext ctx, String text, float x, float y, int color, float scale, boolean smallCaps, boolean shadow) {
        if (text == null || text.isEmpty()) return;
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        Matrix3x2fStack matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, y);
        if (Math.abs(scale - 1.0f) > 0.001f) {
            matrices.scale(scale, scale);
        }
        EmojiRenderer.drawRichText(ctx, tr, text, 0, 0, color, smallCaps, shadow);
        matrices.popMatrix();
    }

    public static int withAlpha(int argb, int alpha255) {
        int clamped = Math.max(0, Math.min(255, alpha255));
        return (clamped << 24) | (argb & 0x00FFFFFF);
    }

    public static int lerpColor(int c1, int c2, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        int a = (int) (((c1 >>> 24) & 0xFF) + (((c2 >>> 24) & 0xFF) - ((c1 >>> 24) & 0xFF)) * t);
        int r = (int) (((c1 >>> 16) & 0xFF) + (((c2 >>> 16) & 0xFF) - ((c1 >>> 16) & 0xFF)) * t);
        int g = (int) (((c1 >>> 8) & 0xFF) + (((c2 >>> 8) & 0xFF) - ((c1 >>> 8) & 0xFF)) * t);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
