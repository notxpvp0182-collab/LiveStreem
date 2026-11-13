package dev.ahmed.liquidglass.ui.font;

import java.text.Bidi;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reorders Bengali (বাংলা) and Devanagari pre-base vowel signs (ি, ে, ৈ, ো, ৌ)
 * and handles RTL Bidi so complex world scripts render naturally in Minecraft's font engine.
 */
public final class WorldLanguageShaper {
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>(512);

    private WorldLanguageShaper() {}

    public static String shape(String input) {
        if (input == null || input.isEmpty()) return "";
        if (CACHE.size() > 1024) CACHE.clear();
        return CACHE.computeIfAbsent(input, WorldLanguageShaper::shapeInternal);
    }

    private static String shapeInternal(String text) {
        boolean hasBengali = false;
        boolean hasDevanagari = false;
        boolean hasRtl = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u0980' && c <= '\u09FF') hasBengali = true;
            else if (c >= '\u0900' && c <= '\u097F') hasDevanagari = true;
            else if ((c >= '\u0590' && c <= '\u06FF') || (c >= '\u0750' && c <= '\u08FF')) hasRtl = true;
        }

        String out = text;
        if (hasBengali) out = shapeBengali(out);
        if (hasDevanagari) out = shapeDevanagari(out);
        if (hasRtl) out = shapeRtl(out);
        return out;
    }

    private static String shapeBengali(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 4);
        int n = s.length();
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (isBengaliConsonant(c)) {
                int clusterStart = i;
                int j = i + 1;
                while (j < n) {
                    char next = s.charAt(j);
                    if (next == '\u09BC') { // Nukta
                        j++;
                    } else if (next == '\u09CD' && j + 1 < n && isBengaliConsonant(s.charAt(j + 1))) { // Hasanta + Consonant conjunct
                        j += 2;
                    } else {
                        break;
                    }
                }
                String cluster = s.substring(clusterStart, j);
                if (j < n) {
                    char matra = s.charAt(j);
                    if (matra == '\u09BF' || matra == '\u09C7' || matra == '\u09C8') {
                        // ি, ে, ৈ move visually BEFORE the consonant cluster
                        sb.append(matra).append(cluster);
                        i = j + 1;
                        continue;
                    } else if (matra == '\u09CB') {
                        // ো splits into ে + cluster + া
                        sb.append('\u09C7').append(cluster).append('\u09BE');
                        i = j + 1;
                        continue;
                    } else if (matra == '\u09CC') {
                        // ৌ splits into ে + cluster + ৗ
                        sb.append('\u09C7').append(cluster).append('\u09D7');
                        i = j + 1;
                        continue;
                    }
                }
                sb.append(cluster);
                i = j;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    private static String shapeDevanagari(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        int n = s.length();
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (c >= '\u0915' && c <= '\u0939') {
                int start = i;
                int j = i + 1;
                while (j + 1 < n && s.charAt(j) == '\u094D' && s.charAt(j + 1) >= '\u0915' && s.charAt(j + 1) <= '\u0939') {
                    j += 2;
                }
                String cluster = s.substring(start, j);
                if (j < n && s.charAt(j) == '\u093F') { // Short 'i' matra ि
                    sb.append('\u093F').append(cluster);
                    i = j + 1;
                    continue;
                }
                sb.append(cluster);
                i = j;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    private static String shapeRtl(String s) {
        try {
            Bidi bidi = new Bidi(s, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
            if (!bidi.isLeftToRight()) {
                return new StringBuilder(s).reverse().toString();
            }
        } catch (Exception ignored) {}
        return s;
    }

    private static boolean isBengaliConsonant(char c) {
        return (c >= '\u0995' && c <= '\u09B9') || (c >= '\u09DC' && c <= '\u09DF') || c == '\u09CE';
    }
}
