package dev.ahmed.liquidglass.hud;

public final class ElementStyle {
    public float scale = 1.0f;
    public float fontSize = 10.0f;
    public boolean smallCaps = false;
    public int textColor = 0xFFF4F5F7;

    public boolean backgroundEnabled = true;
    public int backgroundColor = 0xCC12141B;
    public float backgroundOpacity = 0.80f;
    public float backgroundBlur = 12.0f;

    public boolean borderEnabled = true;
    public int borderColor = 0x26FFFFFF;
    public float borderOpacity = 0.65f;
    public float borderWidth = 1.0f;
    public float cornerRadius = 8.5f;

    public boolean shadowEnabled = true;
    public float shadowOpacity = 0.45f;
    public float shadowBlur = 10.0f;

    public boolean animationEnabled = true;
    public float animationSpeed = 1.0f;

    public void copyFrom(ElementStyle o) {
        scale = o.scale; fontSize = o.fontSize; smallCaps = o.smallCaps; textColor = o.textColor;
        backgroundEnabled = o.backgroundEnabled; backgroundColor = o.backgroundColor;
        backgroundOpacity = o.backgroundOpacity; backgroundBlur = o.backgroundBlur;
        borderEnabled = o.borderEnabled; borderColor = o.borderColor; borderOpacity = o.borderOpacity;
        borderWidth = o.borderWidth; cornerRadius = o.cornerRadius;
        shadowEnabled = o.shadowEnabled; shadowOpacity = o.shadowOpacity; shadowBlur = o.shadowBlur;
        animationEnabled = o.animationEnabled; animationSpeed = o.animationSpeed;
    }
}
