package dev.ahmed.liquidglass.ui.render;

import net.minecraft.client.gui.DrawContext;

public final class IconRenderer {
    public enum Icon {
        SETTINGS, CLOSE, BACK, SEARCH, EDIT, MOVE, LOCK, UNLOCK,
        EYE, EYE_OFF, RESET, ADD, REMOVE, CONNECT, DISCONNECT,
        LIVE, CHAT, STREAM, PROFILE, SERVER, THEME, DISPLAY, KEYBOARD, SAVE,
        SHIELD,SPARKLES, SLIDERS
    }

    private IconRenderer() {}

    public static void drawIcon(DrawContext ctx, Icon icon, float x, float y, float size, int color) {
        int ix = Math.round(x);
        int iy = Math.round(y);
        int s = Math.max(8, Math.round(size));
        int cx = ix + s / 2;
        int cy = iy + s / 2;

        switch (icon) {
            case CLOSE -> {
                for (int i = 2; i < s - 2; i++) {
                    ctx.fill(ix + i, iy + i, ix + i + 2, iy + i + 1, color);
                    ctx.fill(ix + s - 1 - i, iy + i, ix + s - i + 1, iy + i + 1, color);
                }
            }
            case BACK -> {
                ctx.fill(ix + 2, cy, ix + s - 2, cy + 1, color);
                for (int i = 1; i <= 3; i++) {
                    ctx.fill(ix + 2 + i, cy - i, ix + 3 + i, cy - i + 1, color);
                    ctx.fill(ix + 2 + i, cy + i, ix + 3 + i, cy + i + 1, color);
                }
            }
            case SEARCH -> {
                UIRenderer.drawRoundedBorder(ctx, ix + 1, iy + 1, s - 4, s - 4, (s - 4) * 0.5f, 1.0f, color);
                for (int i = 0; i < 3; i++) {
                    ctx.fill(ix + s - 4 + i, iy + s - 4 + i, ix + s - 3 + i, iy + s - 3 + i, color);
                }
            }
            case ADD -> {
                ctx.fill(cx, iy + 2, cx + 1, iy + s - 2, color);
                ctx.fill(ix + 2, cy, ix + s - 2, cy + 1, color);
            }
            case REMOVE -> ctx.fill(ix + 2, cy, ix + s - 2, cy + 1, color);
            case LOCK -> {
                UIRenderer.drawRoundedBorder(ctx, ix + 2, cy - 1, s - 4, s / 2 + 1, 2.0f, 1.0f, color);
                UIRenderer.drawRoundedBorder(ctx, ix + 3, iy + 1, s - 6, s / 2, 2.0f, 1.0f, color);
            }
            case UNLOCK -> {
                UIRenderer.drawRoundedBorder(ctx, ix + 2, cy - 1, s - 4, s / 2 + 1, 2.0f, 1.0f, color);
                ctx.fill(ix + 3, iy + 1, ix + s - 3, iy + 2, color);
                ctx.fill(ix + 3, iy + 2, ix + 4, cy - 1, color);
            }
            case LIVE -> {
                UIRenderer.drawRoundedRect(ctx, cx - 2, cy - 2, 4, 4, 2.0f, color);
                UIRenderer.drawRoundedBorder(ctx, ix + 1, iy + 1, s - 2, s - 2, (s - 2) * 0.5f, 1.0f,
                    UIRenderer.withAlpha(color, 110));
            }
            case CHAT -> {
                UIRenderer.drawRoundedBorder(ctx, ix + 1, iy + 2, s - 2, s - 5, 2.5f, 1.0f, color);
                ctx.fill(ix + 3, iy + s - 3, ix + 5, iy + s - 1, color);
            }
            case PROFILE -> {
                UIRenderer.drawRoundedBorder(ctx, cx - 2, iy + 1, 4, 4, 2.0f, 1.0f, color);
                UIRenderer.drawRoundedBorder(ctx, ix + 2, cy + 1, s - 4, s / 2 - 1, 2.5f, 1.0f, color);
            }
            case DISPLAY, STREAM -> {
                UIRenderer.drawRoundedBorder(ctx, ix + 1, iy + 2, s - 2, s - 5, 2.0f, 1.0f, color);
                ctx.fill(cx - 2, iy + s - 2, cx + 2, iy + s - 1, color);
            }
            case SLIDERS, SETTINGS -> {
                ctx.fill(ix + 2, iy + 3, ix + s - 2, iy + 4, color);
                ctx.fill(ix + 2, cy, ix + s - 2, cy + 1, color);
                ctx.fill(ix + 2, iy + s - 4, ix + s - 2, iy + s - 3, color);
                ctx.fill(ix + 4, iy + 2, ix + 6, iy + 5, color);
                ctx.fill(ix + s - 6, cy - 1, ix + s - 4, cy + 2, color);
                ctx.fill(ix + 5, iy + s - 5, ix + 7, iy + s - 2, color);
            }
            default -> UIRenderer.drawRoundedBorder(ctx, ix + 2, iy + 2, s - 4, s - 4, 2.0f, 1.0f, color);
        }
    }
}
