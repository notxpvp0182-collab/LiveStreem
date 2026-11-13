package dev.ahmed.liquidglass.ui.render;

import dev.ahmed.liquidglass.ui.font.SmallCapsRenderer;
import dev.ahmed.liquidglass.ui.font.WorldLanguageShaper;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Renders the authentic YouTube red play badge, role badges, and full-color 2D vector emojis
 * inline with text so emojis appear in vibrant colors instead of monochrome wireframe sketches.
 */
public final class EmojiRenderer {
    private EmojiRenderer() {}

    public static float drawYouTubeBadge(DrawContext ctx, float x, float y, int alpha) {
        int ix = Math.round(x);
        int iy = Math.round(y);
        int red = UIRenderer.withAlpha(0xFFFF0033, alpha);
        int white = UIRenderer.withAlpha(0xFFFFFFFF, alpha);

        // Rounded YouTube red pill (11x8)
        ctx.fill(ix + 1, iy, ix + 10, iy + 8, red);
        ctx.fill(ix, iy + 1, ix + 11, iy + 7, red);

        // Crisp white play triangle in center
        ctx.fill(ix + 4, iy + 2, ix + 5, iy + 6, white);
        ctx.fill(ix + 5, iy + 3, ix + 7, iy + 5, white);
        ctx.fill(ix + 7, iy + 3, ix + 8, iy + 5, white);
        return 14.0f;
    }

    public static float drawRoleBadge(DrawContext ctx, float x, float y, boolean owner, boolean mod, boolean member, int alpha) {
        if (!owner && !mod && !member) return 0.0f;
        int ix = Math.round(x);
        int iy = Math.round(y);
        int badgeCol = owner ? 0xFFFFD866 : (mod ? 0xFF5E97F6 : 0xFF2BA640);
        int col = UIRenderer.withAlpha(badgeCol, alpha);
        int inner = UIRenderer.withAlpha(0xFF0A0C10, alpha);

        ctx.fill(ix + 1, iy, ix + 7, iy + 8, col);
        ctx.fill(ix, iy + 1, ix + 8, iy + 7, col);
        ctx.fill(ix + 3, iy + 2, ix + 5, iy + 6, inner);
        return 11.0f;
    }

    public static int measureRichTextWidth(TextRenderer tr, String raw, boolean smallCaps) {
        if (raw == null || raw.isEmpty()) return 0;
        String shaped = WorldLanguageShaper.shape(smallCaps ? SmallCapsRenderer.toSmallCaps(raw) : raw);
        int width = 0;
        int len = shaped.length();
        StringBuilder seg = new StringBuilder();

        for (int i = 0; i < len; ) {
            int cp = shaped.codePointAt(i);
            int charCount = Character.charCount(cp);
            if (cp == 0xFE0F || cp == 0xFE0E || cp == 0x200D) {
                i += charCount;
                continue;
            }
            if (isColorEmoji(cp)) {
                if (!seg.isEmpty()) {
                    width += tr.getWidth(seg.toString());
                    seg.setLength(0);
                }
                width += 10;
            } else {
                seg.appendCodePoint(cp);
            }
            i += charCount;
        }
        if (!seg.isEmpty()) {
            width += tr.getWidth(seg.toString());
        }
        return width;
    }

    public static float drawRichText(DrawContext ctx, TextRenderer tr, String raw, float x, float y,
                                     int color, boolean smallCaps, boolean shadow) {
        if (raw == null || raw.isEmpty()) return 0.0f;
        String shaped = WorldLanguageShaper.shape(smallCaps ? SmallCapsRenderer.toSmallCaps(raw) : raw);
        int alpha = (color >>> 24) & 0xFF;
        float cursorX = x;
        int len = shaped.length();
        StringBuilder seg = new StringBuilder();

        for (int i = 0; i < len; ) {
            int cp = shaped.codePointAt(i);
            int charCount = Character.charCount(cp);
            if (cp == 0xFE0F || cp == 0xFE0E || cp == 0x200D) {
                i += charCount;
                continue;
            }
            if (isColorEmoji(cp)) {
                if (!seg.isEmpty()) {
                    String s = seg.toString();
                    ctx.drawText(tr, s, Math.round(cursorX), Math.round(y), color, shadow);
                    cursorX += tr.getWidth(s);
                    seg.setLength(0);
                }
                drawColorEmojiIcon(ctx, cp, Math.round(cursorX) + 1, Math.round(y), alpha);
                cursorX += 10.0f;
            } else {
                seg.appendCodePoint(cp);
            }
            i += charCount;
        }
        if (!seg.isEmpty()) {
            String s = seg.toString();
            ctx.drawText(tr, s, Math.round(cursorX), Math.round(y), color, shadow);
            cursorX += tr.getWidth(s);
        }
        return cursorX - x;
    }

    public static boolean isColorEmoji(int cp) {
        return cp == 0x2764 || cp == 0x2665 || cp == 0x2728 || cp == 0x2B50 || cp == 0x26A1
            || cp == 0x2639 || cp == 0x263A || cp == 0x270C
            || (cp >= 0x1F300 && cp <= 0x1FAFF);
    }

    private static void drawColorEmojiIcon(DrawContext ctx, int cp, int x, int y, int alpha) {
        // 1. Hearts (❤, 💖, 💔, 🧡, 💚, 💙, 💜)
        if (cp == 0x2764 || cp == 0x2665 || (cp >= 0x1F493 && cp <= 0x1F49F) || cp == 0x1F9E1 || cp == 0x1F90D || cp == 0x1F5A4) {
            int hc = switch (cp) {
                case 0x1F9E1 -> 0xFFFF8800;
                case 0x1F49B -> 0xFFFFD83D;
                case 0x1F49A -> 0xFF34D399;
                case 0x1F499 -> 0xFF38BDF8;
                case 0x1F49C -> 0xFFA855F7;
                default -> 0xFFFF2A55;
            };
            int c = UIRenderer.withAlpha(hc, alpha);
            int hi = UIRenderer.withAlpha(0xFFFFB3C1, alpha);
            ctx.fill(x + 1, y + 1, x + 4, y + 4, c);
            ctx.fill(x + 5, y + 1, x + 8, y + 4, c);
            ctx.fill(x, y + 2, x + 9, y + 5, c);
            ctx.fill(x + 1, y + 5, x + 8, y + 6, c);
            ctx.fill(x + 2, y + 6, x + 7, y + 7, c);
            ctx.fill(x + 3, y + 7, x + 6, y + 8, c);
            ctx.fill(x + 4, y + 8, x + 5, y + 9, c);
            ctx.fill(x + 1, y + 2, x + 2, y + 3, hi);
            return;
        }

        // 2. Wilted Rose / Flower (🥀 0x1F940, 🌹 0x1F339)
        if (cp == 0x1F940 || cp == 0x1F339) {
            int petal = UIRenderer.withAlpha(0xFFE11D48, alpha);
            int darkPetal = UIRenderer.withAlpha(0xFF9F1239, alpha);
            int stem = UIRenderer.withAlpha(0xFF22C55E, alpha);
            ctx.fill(x + 3, y + 1, x + 7, y + 5, petal);
            ctx.fill(x + 4, y + 2, x + 6, y + 4, darkPetal);
            ctx.fill(x + 2, y + 3, x + 4, y + 6, petal);
            ctx.fill(x + 5, y + 4, x + 6, y + 9, stem);
            ctx.fill(x + 3, y + 6, x + 5, y + 7, stem);
            return;
        }

        // 3. Fire (🔥 0x1F525)
        if (cp == 0x1F525) {
            int outer = UIRenderer.withAlpha(0xFFFF5500, alpha);
            int mid = UIRenderer.withAlpha(0xFFFF9900, alpha);
            int core = UIRenderer.withAlpha(0xFFFFEE55, alpha);
            ctx.fill(x + 3, y, x + 6, y + 3, outer);
            ctx.fill(x + 2, y + 2, x + 7, y + 6, outer);
            ctx.fill(x + 1, y + 4, x + 8, y + 8, outer);
            ctx.fill(x + 2, y + 3, x + 7, y + 8, mid);
            ctx.fill(x + 3, y + 5, x + 6, y + 8, core);
            return;
        }

        // 4. Purple Devil (😈 0x1F608, 👿 0x1F47F)
        if (cp == 0x1F608 || cp == 0x1F47F) {
            int purple = UIRenderer.withAlpha(0xFF9333EA, alpha);
            int horn = UIRenderer.withAlpha(0xFF6B21A8, alpha);
            int eye = UIRenderer.withAlpha(0xFFFFFFFF, alpha);
            ctx.fill(x, y, x + 2, y + 2, horn);
            ctx.fill(x + 7, y, x + 9, y + 2, horn);
            drawRoundFace(ctx, x, y + 1, purple, UIRenderer.withAlpha(0xFFA855F7, alpha));
            ctx.fill(x + 2, y + 3, x + 4, y + 5, eye);
            ctx.fill(x + 5, y + 3, x + 7, y + 5, eye);
            ctx.fill(x + 2, y + 6, x + 7, y + 7, eye);
            return;
        }

        // 5. Green Alien (👽 0x1F47D)
        if (cp == 0x1F47D) {
            int green = UIRenderer.withAlpha(0xFF4ADE80, alpha);
            int eye = UIRenderer.withAlpha(0xFF090A0F, alpha);
            drawRoundFace(ctx, x, y + 1, green, UIRenderer.withAlpha(0xFF86EFAC, alpha));
            ctx.fill(x + 1, y + 3, x + 4, y + 5, eye);
            ctx.fill(x + 5, y + 3, x + 8, y + 5, eye);
            return;
        }

        // 6. Skull (💀 0x1F480)
        if (cp == 0x1F480) {
            int bone = UIRenderer.withAlpha(0xFFE5E7EB, alpha);
            int dark = UIRenderer.withAlpha(0xFF1F2937, alpha);
            drawRoundFace(ctx, x, y + 1, bone, UIRenderer.withAlpha(0xFFFFFFFF, alpha));
            ctx.fill(x + 2, y + 3, x + 4, y + 5, dark);
            ctx.fill(x + 5, y + 3, x + 7, y + 5, dark);
            ctx.fill(x + 3, y + 7, x + 4, y + 8, dark);
            ctx.fill(x + 5, y + 7, x + 6, y + 8, dark);
            return;
        }

        // 7. Standard Golden-Yellow Emoji Faces (😀, 😂, 🤣, 😭, 😍, 🤩, 😘, 🙃, 🤧, 😔, 😎)
        int faceBase = UIRenderer.withAlpha(0xFFFBBF24, alpha);
        int faceHi = UIRenderer.withAlpha(0xFFFDE047, alpha);
        int eye = UIRenderer.withAlpha(0xFF451A03, alpha);
        int tear = UIRenderer.withAlpha(0xFF38BDF8, alpha);
        int heart = UIRenderer.withAlpha(0xFFFF2A55, alpha);

        drawRoundFace(ctx, x, y + 1, faceBase, faceHi);

        if (cp == 0x1F62D || cp == 0x1F622) {
            // 😭 / 😢 Loudly Crying Face with cyan tear streams
            ctx.fill(x + 2, y + 3, x + 4, y + 4, eye);
            ctx.fill(x + 5, y + 3, x + 7, y + 4, eye);
            ctx.fill(x + 2, y + 4, x + 3, y + 8, tear);
            ctx.fill(x + 6, y + 4, x + 7, y + 8, tear);
            ctx.fill(x + 3, y + 6, x + 6, y + 8, eye);
        } else if (cp == 0x1F602 || cp == 0x1F923) {
            // 😂 / 🤣 Laughing with tears of joy
            ctx.fill(x + 2, y + 3, x + 4, y + 4, eye);
            ctx.fill(x + 5, y + 3, x + 7, y + 4, eye);
            ctx.fill(x, y + 4, x + 2, y + 6, tear);
            ctx.fill(x + 7, y + 4, x + 9, y + 6, tear);
            ctx.fill(x + 2, y + 6, x + 7, y + 8, UIRenderer.withAlpha(0xFFFFFFFF, alpha));
        } else if (cp == 0x1F60D || cp == 0x1F970 || cp == 0x1F618) {
            // 😍 / 🥰 / 😘 Heart Eyes / Kiss
            ctx.fill(x + 1, y + 3, x + 4, y + 5, heart);
            ctx.fill(x + 5, y + 3, x + 8, y + 5, heart);
            ctx.fill(x + 3, y + 6, x + 6, y + 7, eye);
        } else if (cp == 0x1F929) {
            // 🤩 Star-Struck
            int star = UIRenderer.withAlpha(0xFFFFFBEB, alpha);
            ctx.fill(x + 1, y + 2, x + 4, y + 5, star);
            ctx.fill(x + 5, y + 2, x + 8, y + 5, star);
            ctx.fill(x + 2, y + 6, x + 7, y + 8, eye);
        } else {
            // Classic Smiling / Expressive Emoji Face
            ctx.fill(x + 2, y + 3, x + 3, y + 5, eye);
            ctx.fill(x + 6, y + 3, x + 7, y + 5, eye);
            ctx.fill(x + 2, y + 6, x + 7, y + 7, eye);
        }
    }

    private static void drawRoundFace(DrawContext ctx, int x, int y, int baseCol, int hiCol) {
        ctx.fill(x + 2, y, x + 7, y + 8, baseCol);
        ctx.fill(x + 1, y + 1, x + 8, y + 7, baseCol);
        ctx.fill(x, y + 2, x + 9, y + 6, baseCol);
        ctx.fill(x + 2, y + 1, x + 6, y + 3, hiCol);
    }
}
