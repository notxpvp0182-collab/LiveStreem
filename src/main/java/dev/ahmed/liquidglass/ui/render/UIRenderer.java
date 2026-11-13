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

        if (q.shadowEnabled() && ((surfaceColor >>> 24) & 0xFF) > 10) {
            drawShadow(ctx, x, y + 2.0f, w, h, radius, 4.0f, t.shadowColor());
        }

        drawRoundedRect(ctx, x, y, w, h, radius, surfaceColor);

        if (q.innerHighlightEnabled() && hoverProgress > 0.01f) {
            int hoverTint = withAlpha(t.accentPrimary(), (int) (18 * hoverProgress));
            drawRoundedRect(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(0, radius - 1), hoverTint);
        }

        if (q.innerHighlightEnabled() && w > 8) {
            ctx.fill(Math.round(x + 3), Math.round(y + 1), Math.round(x + w - 3), Math.round(y + 2), t.borderInner());
        }

        if (((borderColor >>> 24) & 0xFF) > 2) {
            int effectiveBorder = lerpColor(borderColor, t.borderActive(), hoverProgress);
            drawRoundedBorder(ctx, x, y, w, h, radius, 1.0f, effectiveBorder);
        }
    }

    public static void drawShadow(DrawContext ctx, float x, float y, float w, float h, float radius, float spread, int shadowColor) {
        int baseAlpha = (shadowColor >>> 24) & 0xFF;
        if (baseAlpha <= 4) return;
        int rgb = shadowColor & 0x00FFFFFF;
        int c1 = (Math.max(4, baseAlpha / 4) << 24) | rgb;
        int c2 = (Math.max(2, baseAlpha / 8) << 24) | rgb;
        drawRoundedRect(ctx, x - 1.5f, y - 1.0f, w + 3.0f, h + 3.0f, radius + 1.0f, c1);
        drawRoundedRect(ctx, x - 3.0f, y - 2.0f, w + 6.0f, h + 5.0f, radius + 2.0f, c2);
    }

    public static void drawRoundedRect(DrawContext ctx, float x, float y, float w, float h, float radius, int color) {
        if (((color >>> 24) & 0xFF) <= 1) return;
        int ix = Math.round(x);
        int iy = Math.round(y);
        int iw = Math.round(w);
        int ih = Math.round(h);
        if (iw <= 0 || ih <= 0) return;

        int r = Math.min(Math.round(radius), Math.min(iw, ih) / 2);
        if (r <= 1) {
            ctx.fill(ix, iy, ix + iw, iy + ih, color);
            return;
        }

        int b = Math.min(3, r);
        ctx.fill(ix + b, iy, ix + iw - b, iy + ih, color);
        ctx.fill(ix, iy + b, ix + b, iy + ih - b, color);
        ctx.fill(ix + iw - b, iy + b, ix + iw, iy + ih - b, color);
        if (b >= 2) {
            ctx.fill(ix + 1, iy + 1, ix + b, iy + b, color);
            ctx.fill(ix + iw - b, iy + 1, ix + iw - 1, iy + b, color);
            ctx.fill(ix + 1, iy + ih - b, ix + b, iy + ih - 1, color);
            ctx.fill(ix + iw - b, iy + ih - b, ix + iw - 1, iy + ih - 1, color);
        }
    }

    public static void drawRoundedBorder(DrawContext ctx, float x, float y, float w, float h, float radius, float thickness, int color) {
        if (((color >>> 24) & 0xFF) <= 2) return;
        int ix = Math.round(x);
        int iy = Math.round(y);
        int iw = Math.round(w);
        int ih = Math.round(h);
        if (iw <= 2 || ih <= 2) return;

        int b = radius > 1.5f ? 2 : 0;
        ctx.fill(ix + b, iy, ix + iw - b, iy + 1, color);
        ctx.fill(ix + b, iy + ih - 1, ix + iw - b, iy + ih, color);
        ctx.fill(ix, iy + b, ix + 1, iy + ih - b, color);
        ctx.fill(ix + iw - 1, iy + b, ix + iw, iy + ih - b, color);

        if (b > 0) {
            ctx.fill(ix + 1, iy + 1, ix + 2, iy + 2, color);
            ctx.fill(ix + iw - 2, iy + 1, ix + iw - 1, iy + 2, color);
            ctx.fill(ix + 1, iy + ih - 2, ix + 2, iy + ih - 1, color);
            ctx.fill(ix + iw - 2, iy + ih - 2, ix + iw - 1, iy + ih - 1, color);
        }
    }

    public static void drawText(DrawContext ctx, String text, float x, float y, int color, float scale, boolean smallCaps, boolean shadow) {
        if (text == null || text.isEmpty()) return;
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        String shaped = WorldLanguageShaper.shape(smallCaps ? SmallCapsRenderer.toSmallCaps(text) : text);

        Matrix3x2fStack matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, y);
        if (Math.abs(scale - 1.0f) > 0.001f) {
            matrices.scale(scale, scale);
        }
        ctx.drawText(tr, shaped, 0, 0, color, shadow);
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
