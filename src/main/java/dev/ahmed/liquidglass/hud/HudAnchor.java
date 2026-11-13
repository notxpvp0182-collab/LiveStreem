package dev.ahmed.liquidglass.hud;

public enum HudAnchor {
    TOP_LEFT(0.0f, 0.0f),
    TOP_CENTER(0.5f, 0.0f),
    TOP_RIGHT(1.0f, 0.0f),
    CENTER_LEFT(0.0f, 0.5f),
    CENTER(0.5f, 0.5f),
    CENTER_RIGHT(1.0f, 0.5f),
    BOTTOM_LEFT(0.0f, 1.0f),
    BOTTOM_CENTER(0.5f, 1.0f),
    BOTTOM_RIGHT(1.0f, 1.0f);

    private final float pivotX;
    private final float pivotY;

    HudAnchor(float pivotX, float pivotY) {
        this.pivotX = pivotX;
        this.pivotY = pivotY;
    }

    public float toScreenX(float offsetX, float elementWidth, int screenWidth) {
        float anchorPointX = (screenWidth - elementWidth) * pivotX;
        return Math.max(2.0f, Math.min(screenWidth - elementWidth - 2.0f, anchorPointX + offsetX));
    }

    public float toScreenY(float offsetY, float elementHeight, int screenHeight) {
        float anchorPointY = (screenHeight - elementHeight) * pivotY;
        return Math.max(2.0f, Math.min(screenHeight - elementHeight - 2.0f, anchorPointY + offsetY));
    }

    public float toOffsetX(float screenX, float elementWidth, int screenWidth) {
        return screenX - (screenWidth - elementWidth) * pivotX;
    }

    public float toOffsetY(float screenY, float elementHeight, int screenHeight) {
        return screenY - (screenHeight - elementHeight) * pivotY;
    }

    public static HudAnchor closestAnchor(float x, float y, float w, float h, int screenW, int screenH) {
        float cx = x + w * 0.5f;
        float cy = y + h * 0.5f;
        int col = cx < screenW * 0.33f ? 0 : (cx > screenW * 0.66f ? 2 : 1);
        int row = cy < screenH * 0.33f ? 0 : (cy > screenH * 0.66f ? 2 : 1);
        return values()[row * 3 + col];
    }

    public float pivotX() { return pivotX; }
    public float pivotY() { return pivotY; }
}
