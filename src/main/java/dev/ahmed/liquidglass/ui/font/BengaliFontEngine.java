package dev.ahmed.liquidglass.ui.font;

import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;

import java.io.DataInputStream;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BengaliFontEngine {
    private static final float HD_SCALE = 0.285f;
    public record AksharGlyph(int advanceSubPx, int[] spans) {}

    private static final Map<String, AksharGlyph> ATLAS = new HashMap<>(2048);
    private static boolean loaded = false;

    private BengaliFontEngine() {}

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try (InputStream in = BengaliFontEngine.class.getResourceAsStream("/assets/liquidglass/font/bengali_atlas.dat")) {
            if (in == null) return;
            DataInputStream dis = new DataInputStream(in);
            int count = dis.readInt();
            for (int i = 0; i < count; i++) {
                String key = dis.readUTF();
                int adv = dis.readUnsignedByte();
                int spanCount = dis.readUnsignedShort();
                int[] spans = new int[spanCount];
                for (int s = 0; s < spanCount; s++) {
                    spans[s] = dis.readInt();
                }
                ATLAS.put(key, new AksharGlyph(adv, spans));
            }
        } catch (Exception ignored) {}
    }

    public static boolean isBengaliChar(int cp) {
        return cp >= 0x0980 && cp <= 0x09FF;
    }

    public static String normalizeBengali(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        return Normalizer.normalize(raw, Normalizer.Form.NFC)
            .replace("\u09AF\u09BC", "\u09DF")
            .replace("\u09A1\u09BC", "\u09DC")
            .replace("\u09A2\u09BC", "\u09DD")
            .replace("\u09C7\u09BE", "\u09CB")
            .replace("\u09C7\u09D7", "\u09CC")
            .replace("\u200C", "")
            .replace("\u200D", "");
    }

    public static List<AksharGlyph> tokenizeToGlyphs(String bnWord) {
        ensureLoaded();
        String norm = normalizeBengali(bnWord);
        List<AksharGlyph> out = new ArrayList<>(norm.length());
        int n = norm.length();
        int i = 0;
        while (i < n) {
            AksharGlyph matched = null;
            int matchedLen = 0;
            int maxLen = Math.min(8, n - i);
            for (int len = maxLen; len >= 1; len--) {
                String sub = norm.substring(i, i + len);
                AksharGlyph g = ATLAS.get(sub);
                if (g != null) {
                    matched = g;
                    matchedLen = len;
                    break;
                }
            }
            if (matched != null) {
                out.add(matched);
                i += matchedLen;
            } else {
                i++;
            }
        }
        return out;
    }

    public static int measureBengaliWidth(String bnSegment) {
        List<AksharGlyph> glyphs = tokenizeToGlyphs(bnSegment);
        int subPx = 0;
        for (AksharGlyph g : glyphs) {
            subPx += g.advanceSubPx();
        }
        return Math.round(subPx * HD_SCALE);
    }

    public static float drawBengaliSegment(DrawContext ctx, String bnSegment, float x, float y, int color, boolean shadow) {
        List<AksharGlyph> glyphs = tokenizeToGlyphs(bnSegment);
        if (glyphs.isEmpty()) return 0.0f;

        int baseAlpha = (color >>> 24) & 0xFF;
        int rgb = color & 0x00FFFFFF;
        int shadowRgb = 0x080A0F;

        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y - 1.8f);
        m.scale(HD_SCALE, HD_SCALE);

        int cursorSubX = 0;
        for (AksharGlyph g : glyphs) {
            int[] spans = g.spans();
            if (shadow) {
                for (int sp : spans) {
                    int sy = (sp >>> 24) & 0xFF;
                    int x1 = ((sp >>> 16) & 0xFF) - 4;
                    int x2 = ((sp >>> 8) & 0xFF) - 4;
                    int sa = (sp & 0xFF);
                    int effA = (sa * baseAlpha) / 340;
                    if (effA > 10) {
                        ctx.fill(cursorSubX + x1 + 2, sy + 2, cursorSubX + x2 + 2, sy + 3, (effA << 24) | shadowRgb);
                    }
                }
            }
            for (int sp : spans) {
                int sy = (sp >>> 24) & 0xFF;
                int x1 = ((sp >>> 16) & 0xFF) - 4;
                int x2 = ((sp >>> 8) & 0xFF) - 4;
                int sa = (sp & 0xFF);
                int effA = (sa * baseAlpha) / 255;
                if (effA > 8) {
                    ctx.fill(cursorSubX + x1, sy, cursorSubX + x2, sy + 1, (effA << 24) | rgb);
                }
            }
            cursorSubX += g.advanceSubPx();
        }

        m.popMatrix();
        return cursorSubX * HD_SCALE;
    }
}
