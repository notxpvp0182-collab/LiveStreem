package dev.ahmed.liquidglass.hud;

import dev.ahmed.liquidglass.hud.elements.LiveChatElement;
import dev.ahmed.liquidglass.ui.render.UIRenderer;
import net.minecraft.client.gui.DrawContext;

public abstract class HudElement {
    private final String id;
    private final String displayName;
    private boolean enabled;
    private boolean locked = false;
    private float x;
    private float y;
    private float width;
    private float height;
    private final float baseHeight;
    private final float minWidth;
    private final float minHeight;
    private final ElementStyle style = new ElementStyle();

    protected HudElement(String id, String displayName, float x, float y, float width, float height, boolean enabled) {
        this.id = id;
        this.displayName = displayName;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.baseHeight = Math.max(16.0f, height);
        this.minWidth = Math.max(36.0f, width * 0.45f);
        this.minHeight = Math.max(14.0f, height * 0.55f);
        this.enabled = enabled;
    }

    public final void renderElement(DrawContext ctx, float mouseX, float mouseY, boolean inEditor) {
        if (!enabled && !inEditor) return;

        if (style.backgroundEnabled) {
            int bg = UIRenderer.withAlpha(style.backgroundColor, (int) (style.backgroundOpacity * 255));
            int border = style.borderEnabled
                ? UIRenderer.withAlpha(style.borderColor, (int) (style.borderOpacity * 255))
                : 0x00000000;
            UIRenderer.drawGlassPanel(ctx, x, y, width, height, style.cornerRadius, bg, border, 0.0f, mouseX, mouseY);
        } else if (style.borderEnabled) {
            int border = UIRenderer.withAlpha(style.borderColor, (int) (style.borderOpacity * 255));
            UIRenderer.drawRoundedBorder(ctx, x, y, width, height, style.cornerRadius, style.borderWidth, border);
        }
        renderContent(ctx);
    }

    protected abstract void renderContent(DrawContext ctx);

    public void resize(float newW, float newH) {
        if (!locked) {
            this.width = Math.max(minWidth, newW);
            this.height = Math.max(minHeight, newH);
            // Automatically scale text size proportionally when resizing metric HUD elements
            if (!(this instanceof LiveChatElement)) {
                this.style.scale = Math.max(0.65f, Math.min(2.6f, this.height / this.baseHeight));
            }
        }
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }
    public float x() { return x; }
    public float y() { return y; }
    public void setPosition(float x, float y) { if (!locked) { this.x = x; this.y = y; } }
    public float width() { return width; }
    public float height() { return height; }
    public ElementStyle style() { return style; }
}
