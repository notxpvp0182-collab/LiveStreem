package dev.ahmed.liquidglass.ui.render;

import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;

public final class IconRenderer {
    public enum Icon {
        SETTINGS, CLOSE, BACK, SEARCH, EDIT, MOVE, LOCK, UNLOCK,
        EYE, EYE_OFF, RESET, ADD, REMOVE, CONNECT, DISCONNECT,
        LIVE, CHAT, STREAM, PROFILE, SERVER, THEME, DISPLAY, KEYBOARD, SAVE,
        SHIELD, SPARKLES, SLIDERS
    }

    private IconRenderer() {}

    public static void drawIcon(DrawContext ctx, Icon icon, float x, float y, float size, int color) {
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(0.5f, 0.5f);
        int s = Math.max(16, Math.round(size * 2.0f));
        int cx = s / 2;
        int cy = s / 2;

        switch (icon) {
            case BACK -> {
                ctx.fill(5, cy - 1, s - 4, cy + 1, color);
                for (int i = 0; i < 6; i++) {
                    ctx.fill(4 + i, cy - i, 6 + i, cy - i + 2, color);
                    ctx.fill(4 + i, cy + i - 1, 6 + i, cy + i + 1, color);
                }
            }
            case LIVE -> {
                UIRenderer.drawRoundedBorder(ctx, 1, 2, s / 2.0f - 2, s / 2.0f - 4, 3.5f, 1.0f, color);
                ctx.fill(cx - 3, cy - 4, cx - 1, cy + 4, color);
                ctx.fill(cx - 1, cy - 3, cx + 1, cy + 3, color);
                ctx.fill(cx + 1, cy - 2, cx + 3, cy + 2, color);
                ctx.fill(cx + 3, cy - 1, cx + 4, cy + 1, color);
            }
            case DISPLAY, STREAM -> {
                UIRenderer.drawRoundedBorder(ctx, 1, 1, s / 2.0f - 2, s / 2.0f - 4, 3.0f, 1.0f, color);
                ctx.fill(5, s - 5, s - 5, s - 3, color);
                ctx.fill(cx - 2, s - 7, cx + 2, s - 5, color);
            }
            case PROFILE -> {
                UIRenderer.drawRoundedRect(ctx, (cx - 4) * 0.5f, 1.5f, 4.0f, 4.0f, 2.0f, color);
                UIRenderer.drawRoundedRect(ctx, 2.5f, (cy + 2) * 0.5f, (s - 10) * 0.5f, 3.5f, 2.0f, color);
            }
            case CHAT -> {
                UIRenderer.drawRoundedBorder(ctx, 1, 1.5f, s / 2.0f - 2, s / 2.0f - 4.5f, 3.5f, 1.0f, color);
                ctx.fill(6, cy - 3, s - 6, cy - 1, color);
                ctx.fill(6, cy + 1, s - 9, cy + 3, color);
            }
            case SLIDERS, SETTINGS -> {
                ctx.fill(3, 5, s - 3, 7, color);
                ctx.fill(3, cy, s - 3, cy + 2, color);
                ctx.fill(3, s - 6, s - 3, s - 4, color);
                ctx.fill(7, 3, 11, 9, color);
                ctx.fill(s - 11, cy - 2, s - 7, cy + 4, color);
                ctx.fill(9, s - 8, 13, s - 2, color);
            }
            default -> UIRenderer.drawRoundedBorder(ctx, 2, 2, s / 2.0f - 4, s / 2.0f - 4, 3.0f, 1.0f, color);
        }
        m.popMatrix();
    }
}
