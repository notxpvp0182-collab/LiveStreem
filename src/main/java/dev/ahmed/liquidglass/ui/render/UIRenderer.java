package dev.ahmed.liquidglass.ui.render;

import dev.ahmed.liquidglass.ui.font.SmallCapsRenderer;
import dev.ahmed.liquidglass.ui.theme.GlassQuality;
import dev.ahmed.liquidglass.ui.theme.ThemeManager;
import dev.ahmed.liquidglass.ui.theme.ThemeTokens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;

public final class UIRenderer {
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

        // 1. Soft multi-layer ambient shadow
        if (q.shadowEnabled()) {
            drawShadow(ctx, x, y + t.shadowOffsetY(), w, h, radius, t.shadowRadius(), t.shadowColor());
        }

        // 2. Frosted dark glass fill
        drawRoundedRect(ctx, x, y, w, h, radius, surfaceColor);

        // 3. Mouse-responsive subtle radial sheen (HIGH/ULTRA)
        if (q.innerHighlightEnabled() && hoverProgress > 0.001f) {
            int hoverTint = withAlpha(t.accentPrimary(), (int) (18 * hoverProgress));
            drawRoundedRect(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(0, radius - 1), hoverTint);
        }

        // 4. 1px Top Specular Inner Highlight
        if (q.innerHighlightEnabled() && w > radius * 2) {
            int topRim = t.borderInner();
            ctx.fill(Math.round(x + radius * 0.6f), Math.round(y + 1),
                     Math.round(x + w - radius * 0.6f), Math.round(y + 2), topRim);
        }

        // 5. Restrained 1px anti-aliased rounded border
        int effectiveBorder = lerpColor(borderColor, t.borderActive(), hoverProgress);
        drawRoundedBorder(ctx, x, y, w, h, radius, t.borderWidth(), effectiveBorder);
    }

    public static void drawShadow(DrawContext ctx, float x, float y, float w, float h, float radius, float spread, int shadowColor) {
        int baseAlpha = (shadowColor >>> 24) & 0xFF;
        int rgb = shadowColor & 0x00FFFFFF;
        int steps = 4;
        for (int i = 1; i <= steps; i++) {
            float expand = (spread / steps) * i * 0.65f;
            float falloff = 1.0f - ((float) i / (steps + 1));
            int stepAlpha = Math.max(2, (int) (baseAlpha * falloff * 0.28f));
            drawRoundedRect(ctx, x - expand, y - expand, w + expand * 2, h + expand * 2, radius + expand, (stepAlpha << 24) | rgb);
        }
    }

    public static void drawRoundedRect(DrawContext ctx, float x, float y, float w, float h, float radius, int color) {
        int ix = Math.round(x);
        int iy = Math.round(y);
        int iw = Math.round(w);
        int ih = Math.round(h);
        int r = Math.min(Math.round(radius), Math.min(iw, ih) / 2);

        if (r <= 1) {
            ctx.fill(ix, iy, ix + iw, iy + ih, color);
            return;
        }

        // Center body + left/right columns
        ctx.fill(ix + r, iy, ix + iw - r, iy + ih, color);
        ctx.fill(ix, iy + r, ix + r, iy + ih - r, color);
        ctx.fill(ix + iw - r, iy + r, ix + iw, iy + ih - r, color);

        // Smooth scanline-feathered corners (SDF approximation on DrawContext)
        int baseAlpha = (color >>> 24) & 0xFF;
        int rgb = color & 0x00FFFFFF;
        for (int dy = 0; dy < r; dy++) {
            float py = r - dy - 0.5f;
            float dxExact = (float) Math.sqrt(Math.max(0.0f, r * r - py * py));
            int dxInt = (int) dxExact;
            float frac = dxExact - dxInt;

            // Solid scanline segment inside arc
            if (dxInt > 0) {
                ctx.fill(ix + r - dxInt, iy + dy, ix + r, iy + dy + 1, color);
                ctx.fill(ix + iw - r, iy + dy, ix + iw - r + dxInt, iy + dy + 1, color);
                ctx.fill(ix + r - dxInt, iy + ih - 1 - dy, ix + r, iy + ih - dy, color);
                ctx.fill(ix + iw - r, iy + ih - 1 - dy, ix + iw - r + dxInt, iy + ih - dy, color);
            }
            // Sub-pixel anti-aliased edge pixel
            int aaAlpha = (int) (baseAlpha * frac);
            if (aaAlpha > 4 && dxInt < r) {
                int aaColor = (aaAlpha << 24) | rgb;
                ctx.fill(ix + r - dxInt - 1, iy + dy, ix + r - dxInt, iy + dy + 1, aaColor);
                ctx.fill(ix + iw - r + dxInt, iy + dy, ix + iw - r + dxInt + 1, iy + dy + 1, aaColor);
                ctx.fill(ix + r - dxInt - 1, iy + ih - 1 - dy, ix + r - dxInt, iy + ih - dy, aaColor);
                ctx.fill(ix + iw - r + dxInt, iy + ih - 1 - dy, ix + iw - r + dxInt + 1, iy + ih - dy, aaColor);
            }
        }
    }

    public static void drawRoundedBorder(DrawContext ctx, float x, float y, float w, float h, float radius, float thickness, int color) {
        int ix = Math.round(x);
        int iy = Math.round(y);
        int iw = Math.round(w);
        int ih = Math.round(h);
        int r = Math.min(Math.round(radius), Math.min(iw, ih) / 2);

        // Straight edges
        ctx.fill(ix + r, iy, ix + iw - r, iy + 1, color);
        ctx.fill(ix + r, iy + ih - 1, ix + iw - r, iy + ih, color);
        ctx.fill(ix, iy + r, ix + 1, iy + ih - r, color);
        ctx.fill(ix + iw - 1, iy + r, ix + iw, iy + ih - r, color);

        // Corner arcs
        for (int i = 0; i < r; i++) {
            float py = r - i - 0.5f;
            int dx = Math.round((float) Math.sqrt(Math.max(0.0f, r * r - py * py)));
            ctx.fill(ix + r - dx, iy + i, ix + r - dx + 1, iy + i + 1, color);
            ctx.fill(ix + iw - r + dx - 1, iy + i, ix + iw - r + dx, iy + i + 1, color);
            ctx.fill(ix + r - dx, iy + ih - 1 - i, ix + r - dx + 1, iy + ih - i, color);
            ctx.fill(ix + iw - r + dx - 1, iy + ih - 1 - i, ix + iw - r + dx, iy + ih - i, color);
        }
    }

    public static void drawText(DrawContext ctx, String text, float x, float y, int color, float scale, boolean smallCaps, boolean shadow) {
        if (text == null || text.isEmpty()) return;
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        String rendered = smallCaps ? SmallCapsRenderer.toSmallCaps(text) : text;

        Matrix3x2fStack matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, y);
        if (Math.abs(scale - 1.0f) > 0.001f) {
            matrices.scale(scale, scale);
        }
        ctx.drawText(tr, rendered, 0, 0, color, shadow);
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
