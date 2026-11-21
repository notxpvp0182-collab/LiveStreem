package dev.ahmed.liquidglass.ui.render;

import dev.ahmed.liquidglass.ui.font.BengaliFontEngine;
import dev.ahmed.liquidglass.ui.font.SmallCapsRenderer;
import dev.ahmed.liquidglass.ui.font.WorldLanguageShaper;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

public final class EmojiRenderer {
    private EmojiRenderer() {}

    public static float drawYouTubeBadge(GuiGraphicsExtractor ctx, float x, float y, int alpha) {
        Matrix3x2fStack m = ctx.pose();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(0.5f, 0.5f);

        int red = UIRenderer.withAlpha(0xFFFF0033, alpha);
        int white = UIRenderer.withAlpha(0xFFFFFFFF, alpha);

        ctx.fill(3, 1, 19, 15, red);
        ctx.fill(1, 3, 21, 13, red);
        ctx.fill(2, 2, 20, 14, red);

        ctx.fill(8, 4, 10, 12, white);
        ctx.fill(10, 5, 12, 11, white);
        ctx.fill(12, 6, 14, 10, white);
        ctx.fill(14, 7, 15, 9, white);

        m.popMatrix();
        return 13.5f;
    }

    public static float drawRoleBadge(GuiGraphicsExtractor ctx, float x, float y, boolean owner, boolean mod, boolean member, int alpha) {
        if (!owner && !mod && !member) return 0.0f;
        Matrix3x2fStack m = ctx.pose();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(0.5f, 0.5f);

        int badgeCol = owner ? 0xFFFFD866 : (mod ? 0xFF5E97F6 : 0xFF2BA640);
        int col = UIRenderer.withAlpha(badgeCol, alpha);
        int inner = UIRenderer.withAlpha(0xFF0A0C10, alpha);

        ctx.fill(2, 1, 14, 15, col);
        ctx.fill(1, 2, 15, 14, col);
        ctx.fill(6, 4, 10, 12, inner);

        m.popMatrix();
        return 10.0f;
    }

    public static int measureRichTextWidth(Font tr, String raw, boolean smallCaps) {
        if (raw == null || raw.isEmpty()) return 0;
        String input = smallCaps ? SmallCapsRenderer.toSmallCaps(raw) : raw;
        int width = 0;
        int len = input.length();
        StringBuilder latinSeg = new StringBuilder();
        StringBuilder bnSeg = new StringBuilder();

        for (int i = 0; i < len; ) {
            int cp = input.codePointAt(i);
            int charCount = Character.charCount(cp);
            if (cp == 0xFE0F || cp == 0xFE0E) {
                i += charCount;
                continue;
            }
            if (BengaliFontEngine.isBengaliChar(cp)) {
                if (!latinSeg.isEmpty()) {
                    width += tr.width(WorldLanguageShaper.shape(latinSeg.toString()));
                    latinSeg.setLength(0);
                }
                bnSeg.appendCodePoint(cp);
                i += charCount;
                continue;
            } else if (!bnSeg.isEmpty()) {
                width += BengaliFontEngine.measureBengaliWidth(bnSeg.toString());
                bnSeg.setLength(0);
            }

            if (isRegionalIndicator(cp)) {
                if (!latinSeg.isEmpty()) {
                    width += tr.width(WorldLanguageShaper.shape(latinSeg.toString()));
                    latinSeg.setLength(0);
                }
                if (i + charCount < len && isRegionalIndicator(input.codePointAt(i + charCount))) {
                    i += charCount * 2;
                } else {
                    i += charCount;
                }
                width += 11;
                continue;
            }
            if (isColorEmoji(cp)) {
                if (!latinSeg.isEmpty()) {
                    width += tr.width(WorldLanguageShaper.shape(latinSeg.toString()));
                    latinSeg.setLength(0);
                }
                width += 10;
            } else {
                latinSeg.appendCodePoint(cp);
            }
            i += charCount;
        }
        if (!bnSeg.isEmpty()) {
            width += BengaliFontEngine.measureBengaliWidth(bnSeg.toString());
        }
        if (!latinSeg.isEmpty()) {
            width += tr.width(WorldLanguageShaper.shape(latinSeg.toString()));
        }
        return width;
    }

    public static float drawRichText(GuiGraphicsExtractor ctx, Font tr, String raw, float x, float y,
                                     int color, boolean smallCaps, boolean shadow) {
        if (raw == null || raw.isEmpty()) return 0.0f;
        String input = smallCaps ? SmallCapsRenderer.toSmallCaps(raw) : raw;
        int alpha = (color >>> 24) & 0xFF;
        float cursorX = x;
        int len = input.length();
        StringBuilder latinSeg = new StringBuilder();
        StringBuilder bnSeg = new StringBuilder();

        for (int i = 0; i < len; ) {
            int cp = input.codePointAt(i);
            int charCount = Character.charCount(cp);
            if (cp == 0xFE0F || cp == 0xFE0E) {
                i += charCount;
                continue;
            }
            if (BengaliFontEngine.isBengaliChar(cp)) {
                if (!latinSeg.isEmpty()) {
                    String s = WorldLanguageShaper.shape(latinSeg.toString());
                    ctx.text(tr, s, Math.round(cursorX), Math.round(y), color, shadow);
                    cursorX += tr.width(s);
                    latinSeg.setLength(0);
                }
                bnSeg.appendCodePoint(cp);
                i += charCount;
                continue;
            } else if (!bnSeg.isEmpty()) {
                cursorX += BengaliFontEngine.drawBengaliSegment(ctx, bnSeg.toString(), cursorX, y, color, shadow);
                bnSeg.setLength(0);
            }

            if (isRegionalIndicator(cp)) {
                if (!latinSeg.isEmpty()) {
                    String s = WorldLanguageShaper.shape(latinSeg.toString());
                    ctx.text(tr, s, Math.round(cursorX), Math.round(y), color, shadow);
                    cursorX += tr.width(s);
                    latinSeg.setLength(0);
                }
                int secondCp = 0;
                if (i + charCount < len && isRegionalIndicator(input.codePointAt(i + charCount))) {
                    secondCp = input.codePointAt(i + charCount);
                    i += charCount * 2;
                } else {
                    i += charCount;
                }
                drawFlagEmoji(ctx, cp, secondCp, cursorX + 0.5f, y, alpha);
                cursorX += 11.0f;
                continue;
            }
            if (isColorEmoji(cp)) {
                if (!latinSeg.isEmpty()) {
                    String s = WorldLanguageShaper.shape(latinSeg.toString());
                    ctx.text(tr, s, Math.round(cursorX), Math.round(y), color, shadow);
                    cursorX += tr.width(s);
                    latinSeg.setLength(0);
                }
                drawColorEmojiIcon(ctx, cp, cursorX + 0.5f, y, alpha);
                cursorX += 10.0f;
            } else {
                latinSeg.appendCodePoint(cp);
            }
            i += charCount;
        }
        if (!bnSeg.isEmpty()) {
            cursorX += BengaliFontEngine.drawBengaliSegment(ctx, bnSeg.toString(), cursorX, y, color, shadow);
        }
        if (!latinSeg.isEmpty()) {
            String s = WorldLanguageShaper.shape(latinSeg.toString());
            ctx.text(tr, s, Math.round(cursorX), Math.round(y), color, shadow);
            cursorX += tr.width(s);
        }
        return cursorX - x;
    }

    private static boolean isRegionalIndicator(int cp) {
        return cp >= 0x1F1E6 && cp <= 0x1F1FF;
    }

    private static void drawFlagEmoji(GuiGraphicsExtractor ctx, int firstCp, int secondCp, float x, float y, int alpha) {
        Matrix3x2fStack m = ctx.pose();
        m.pushMatrix();
        m.translate(x, y + 0.5f);
        m.scale(0.5f, 0.5f);

        char c1 = (char) ('A' + (firstCp - 0x1F1E6));
        char c2 = secondCp > 0 ? (char) ('A' + (secondCp - 0x1F1E6)) : ' ';
        String code = "" + c1 + c2;

        if ("BD".equals(code)) {
            ctx.fill(0, 1, 18, 13, UIRenderer.withAlpha(0xFF006A4E, alpha));
            ctx.fill(6, 4, 11, 10, UIRenderer.withAlpha(0xFFF42A41, alpha));
            ctx.fill(5, 5, 12, 9, UIRenderer.withAlpha(0xFFF42A41, alpha));
        } else if ("IN".equals(code)) {
            ctx.fill(0, 1, 18, 5, UIRenderer.withAlpha(0xFFFF9933, alpha));
            ctx.fill(0, 5, 18, 9, UIRenderer.withAlpha(0xFFFFFFFF, alpha));
            ctx.fill(0, 9, 18, 13, UIRenderer.withAlpha(0xFF138808, alpha));
            ctx.fill(8, 6, 10, 8, UIRenderer.withAlpha(0xFF000080, alpha));
        } else {
            ctx.fill(0, 1, 18, 7, UIRenderer.withAlpha(0xFF22C55E, alpha));
            ctx.fill(0, 7, 18, 13, UIRenderer.withAlpha(0xFFFACC15, alpha));
            ctx.fill(5, 4, 13, 10, UIRenderer.withAlpha(0xFF3B82F6, alpha));
        }
        m.popMatrix();
    }

    public static boolean isColorEmoji(int cp) {
        return cp == 0x2764 || cp == 0x2665 || cp == 0x2728 || cp == 0x2B50 || cp == 0x26A1
            || cp == 0x2639 || cp == 0x263A || cp == 0x270C
            || (cp >= 0x1F300 && cp <= 0x1FAFF);
    }

    private static void drawColorEmojiIcon(GuiGraphicsExtractor ctx, int cp, float fx, float fy, int alpha) {
        Matrix3x2fStack m = ctx.pose();
        m.pushMatrix();
        m.translate(fx, fy);
        m.scale(0.5f, 0.5f);

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
            ctx.fill(2, 2, 8, 8, c);
            ctx.fill(10, 2, 16, 8, c);
            ctx.fill(1, 4, 17, 10, c);
            ctx.fill(2, 10, 16, 12, c);
            ctx.fill(4, 12, 14, 14, c);
            ctx.fill(6, 14, 12, 16, c);
            ctx.fill(8, 16, 10, 17, c);
            ctx.fill(3, 3, 5, 5, hi);
            m.popMatrix();
            return;
        }

        if (cp == 0x1F940 || cp == 0x1F339) {
            int petal = UIRenderer.withAlpha(0xFFE11D48, alpha);
            int darkPetal = UIRenderer.withAlpha(0xFF9F1239, alpha);
            int stem = UIRenderer.withAlpha(0xFF22C55E, alpha);
            ctx.fill(6, 2, 14, 10, petal);
            ctx.fill(8, 4, 12, 8, darkPetal);
            ctx.fill(4, 6, 8, 11, petal);
            ctx.fill(10, 8, 12, 17, stem);
            ctx.fill(6, 12, 10, 14, stem);
            m.popMatrix();
            return;
        }

        if (cp == 0x1F525) {
            int outer = UIRenderer.withAlpha(0xFFFF5500, alpha);
            int mid = UIRenderer.withAlpha(0xFFFF9900, alpha);
            int core = UIRenderer.withAlpha(0xFFFFEE55, alpha);
            ctx.fill(6, 1, 12, 6, outer);
            ctx.fill(4, 4, 14, 12, outer);
            ctx.fill(2, 8, 16, 16, outer);
            ctx.fill(4, 6, 14, 15, mid);
            ctx.fill(6, 10, 12, 16, core);
            m.popMatrix();
            return;
        }

        if (cp == 0x1F608 || cp == 0x1F47F) {
            int purple = UIRenderer.withAlpha(0xFF9333EA, alpha);
            int horn = UIRenderer.withAlpha(0xFF6B21A8, alpha);
            int eye = UIRenderer.withAlpha(0xFFFFFFFF, alpha);
            ctx.fill(1, 0, 4, 4, horn);
            ctx.fill(14, 0, 17, 4, horn);
            drawSmoothCircleFace(ctx, purple, UIRenderer.withAlpha(0xFFA855F7, alpha));
            ctx.fill(4, 6, 7, 9, eye);
            ctx.fill(11, 6, 14, 9, eye);
            ctx.fill(5, 12, 13, 14, eye);
            m.popMatrix();
            return;
        }

        if (cp == 0x1F47D) {
            int green = UIRenderer.withAlpha(0xFF4ADE80, alpha);
            int eye = UIRenderer.withAlpha(0xFF090A0F, alpha);
            drawSmoothCircleFace(ctx, green, UIRenderer.withAlpha(0xFF86EFAC, alpha));
            ctx.fill(3, 6, 7, 10, eye);
            ctx.fill(11, 6, 15, 10, eye);
            m.popMatrix();
            return;
        }

        int faceBase = UIRenderer.withAlpha(0xFFFBBF24, alpha);
        int faceHi = UIRenderer.withAlpha(0xFFFDE047, alpha);
        int eye = UIRenderer.withAlpha(0xFF451A03, alpha);
        int tear = UIRenderer.withAlpha(0xFF38BDF8, alpha);
        int heart = UIRenderer.withAlpha(0xFFFF2A55, alpha);

        drawSmoothCircleFace(ctx, faceBase, faceHi);

        if (cp == 0x1F62D || cp == 0x1F622) {
            ctx.fill(4, 6, 7, 8, eye);
            ctx.fill(11, 6, 14, 8, eye);
            ctx.fill(4, 8, 6, 16, tear);
            ctx.fill(12, 8, 14, 16, tear);
            ctx.fill(6, 11, 12, 15, eye);
        } else if (cp == 0x1F602 || cp == 0x1F923) {
            ctx.fill(4, 6, 7, 8, eye);
            ctx.fill(11, 6, 14, 8, eye);
            ctx.fill(1, 8, 4, 12, tear);
            ctx.fill(14, 8, 17, 12, tear);
            ctx.fill(4, 11, 14, 15, UIRenderer.withAlpha(0xFFFFFFFF, alpha));
        } else if (cp == 0x1F60D || cp == 0x1F970 || cp == 0x1F618) {
            ctx.fill(3, 5, 8, 9, heart);
            ctx.fill(10, 5, 15, 9, heart);
            ctx.fill(6, 12, 12, 14, eye);
        } else if (cp == 0x1F929) {
            int star = UIRenderer.withAlpha(0xFFFFFBEB, alpha);
            ctx.fill(3, 4, 8, 9, star);
            ctx.fill(10, 4, 15, 9, star);
            ctx.fill(5, 11, 13, 15, eye);
        } else {
            ctx.fill(4, 6, 6, 9, eye);
            ctx.fill(12, 6, 14, 9, eye);
            ctx.fill(5, 12, 13, 14, eye);
        }
        m.popMatrix();
    }

    private static void drawSmoothCircleFace(GuiGraphicsExtractor ctx, int baseCol, int hiCol) {
        ctx.fill(5, 1, 13, 17, baseCol);
        ctx.fill(3, 2, 15, 16, baseCol);
        ctx.fill(2, 3, 16, 15, baseCol);
        ctx.fill(1, 5, 17, 13, baseCol);
        ctx.fill(4, 2, 12, 6, hiCol);
    }
}
