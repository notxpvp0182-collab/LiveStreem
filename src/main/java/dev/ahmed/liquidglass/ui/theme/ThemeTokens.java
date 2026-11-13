package dev.ahmed.liquidglass.ui.theme;

public record ThemeTokens(
    String id,
    String displayName,
    // Backgrounds (ARGB)
    int bgBase,
    int bgElevated,
    int bgGlass,
    int bgGlassStrong,
    int bgGlassWeak,
    int bgOverlay,
    // Glass Material Parameters
    float glassOpacity,
    float glassBlurRadius,
    float glassSaturation,
    float glassBrightness,
    float glassRefractionStrength,
    float glassEdgeSoftness,
    float glassHighlightIntensity,
    float glassNoiseIntensity,
    // Borders (ARGB)
    int borderBase,
    int borderInner,
    int borderOuter,
    int borderActive,
    float borderWidth,
    // Shadows
    int shadowColor,
    float shadowRadius,
    float shadowOffsetX,
    float shadowOffsetY,
    // Radii
    float radiusSmall,
    float radiusMedium,
    float radiusLarge,
    float radiusPill,
    // Text Colors (ARGB)
    int textPrimary,
    int textSecondary,
    int textMuted,
    int textDisabled,
    int textAccent,
    // Typography Sizes (px)
    float fontTitle,
    float fontHeading,
    float fontBody,
    float fontSmall,
    float fontTiny,
    // Accents & Status (ARGB)
    int accentPrimary,
    int accentSecondary,
    int accentHover,
    int accentActive,
    int statusSuccess,
    int statusWarning,
    int statusError,
    int statusInfo
) {
    public static ThemeTokens darkLiquidGlass() {
        return new ThemeTokens(
            "dark_liquid_glass",
            "Dark Liquid Glass",
            0xFF08090C, // bgBase
            0xFF101218, // bgElevated
            0xCC12141B, // bgGlass (80% alpha)
            0xE6141720, // bgGlassStrong (90% alpha)
            0x99101218, // bgGlassWeak (60% alpha)
            0xB3060709, // bgOverlay
            0.82f,      // glassOpacity
            14.0f,      // glassBlurRadius
            1.08f,      // glassSaturation
            0.94f,      // glassBrightness
            0.012f,     // glassRefractionStrength (subtle edge-only)
            1.25f,      // glassEdgeSoftness
            0.18f,      // glassHighlightIntensity
            0.015f,     // glassNoiseIntensity
            0x26FFFFFF, // borderBase (15% white)
            0x1AFFFFFF, // borderInner (10% top specular rim)
            0x4D000000, // borderOuter
            0x73D8DEE9, // borderActive (restrained ice-white rim)
            1.0f,       // borderWidth
            0x80000000, // shadowColor
            12.0f,      // shadowRadius
            0.0f,       // shadowOffsetX
            4.0f,       // shadowOffsetY
            5.0f,       // radiusSmall
            8.0f,       // radiusMedium
            12.0f,      // radiusLarge
            999.0f,     // radiusPill
            0xFFF4F5F7, // textPrimary
            0xFFA7ABB5, // textSecondary
            0xFF707581, // textMuted
            0xFF4B4F58, // textDisabled
            0xFFE5E9F0, // textAccent
            18.0f,      // fontTitle
            13.0f,      // fontHeading
            10.0f,      // fontBody
            8.5f,       // fontSmall
            7.5f,       // fontTiny
            0xFFD8DEE9, // accentPrimary (restrained frost silver)
            0xFF88C0D0, // accentSecondary
            0xFFECEFF4, // accentHover
            0xFFFFFFFF, // accentActive
            0xFF68D391, // statusSuccess
            0xFFF6AD55, // statusWarning
            0xFFFC8181, // statusError
            0xFF63B3ED  // statusInfo
        );
    }

    public static ThemeTokens amoledGlass() {
        return new ThemeTokens(
            "amoled_glass",
            "AMOLED Obsidian",
            0xFF030304, 0xFF090A0D, 0xD90A0B0F, 0xF00C0D12, 0x8C08090C, 0xCC020203,
            0.86f, 16.0f, 1.04f, 0.90f, 0.010f, 1.25f, 0.14f, 0.010f,
            0x1FFFFFFF, 0x14FFFFFF, 0x66000000, 0x66E5E9F0, 1.0f,
            0x99000000, 14.0f, 0.0f, 5.0f,
            5.0f, 8.0f, 12.0f, 999.0f,
            0xFFF8F9FA, 0xFF9CA3AF, 0xFF6B7280, 0xFF374151, 0xFFF3F4F6,
            18.0f, 13.0f, 10.0f, 8.5f, 7.5f,
            0xFFE5E7EB, 0xFF93C5FD, 0xFFFFFFFF, 0xFFFFFFFF,
            0xFF34D399, 0xFFFBBF24, 0xFFF87171, 0xFF60A5FA
        );
    }
}
