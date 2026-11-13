package dev.ahmed.liquidglass.ui.components;

import dev.ahmed.liquidglass.ui.animation.Animation;
import dev.ahmed.liquidglass.ui.render.UIRenderer;
import dev.ahmed.liquidglass.ui.theme.ThemeManager;
import dev.ahmed.liquidglass.ui.theme.ThemeTokens;
import net.minecraft.client.gui.DrawContext;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class GlassToggle {
    private final String label;
    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;
    private final Animation knobAnim;
    private final Animation hoverAnim;

    public GlassToggle(String label, BooleanSupplier getter, Consumer<Boolean> setter) {
        this.label = label;
        this.getter = getter;
        this.setter = setter;
        boolean initial = getter.getAsBoolean();
        this.knobAnim = new Animation(initial ? 1.0f : 0.0f, 0.16f, Animation.Easing.EASE_OUT_CUBIC);
        this.hoverAnim = new Animation(0.0f, 0.14f, Animation.Easing.EASE_OUT_CUBIC);
    }

    public void render(DrawContext ctx, float x, float y, float w, float h, float mouseX, float mouseY, float dt) {
        ThemeTokens t = ThemeManager.get().tokens();
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        boolean state = getter.getAsBoolean();

        knobAnim.animateTo(state ? 1.0f : 0.0f);
        hoverAnim.animateTo(hovered ? 1.0f : 0.0f);
        float k = knobAnim.update(dt);
        float hv = hoverAnim.update(dt);

        // Row glass container
        int rowBg = UIRenderer.lerpColor(t.bgGlassWeak(), t.bgGlass(), hv);
        UIRenderer.drawRoundedRect(ctx, x, y, w, h, t.radiusSmall(), rowBg);
        UIRenderer.drawRoundedBorder(ctx, x, y, w, h, t.radiusSmall(), 1.0f,
            UIRenderer.lerpColor(t.borderBase(), t.borderActive(), hv * 0.6f));

        // Label
        UIRenderer.drawText(ctx, label, x + 10, y + (h - 8) * 0.5f,
            state ? t.textPrimary() : t.textSecondary(), 1.0f, false, false);

        // Pill switch on the right (matches reference screenshot 3)
        float pillW = 24.0f;
        float pillH = 12.0f;
        float pillX = x + w - pillW - 8.0f;
        float pillY = y + (h - pillH) * 0.5f;

        int trackColor = UIRenderer.lerpColor(0xFF1A1D26, t.accentPrimary(), k);
        UIRenderer.drawRoundedRect(ctx, pillX, pillY, pillW, pillH, pillH * 0.5f, trackColor);
        UIRenderer.drawRoundedBorder(ctx, pillX, pillY, pillW, pillH, pillH * 0.5f, 1.0f, t.borderBase());

        float knobSize = 8.0f;
        float knobX = pillX + 2.0f + (pillW - knobSize - 4.0f) * k;
        float knobY = pillY + 2.0f;
        int knobColor = UIRenderer.lerpColor(t.textMuted(), 0xFF0A0C10, k);
        UIRenderer.drawRoundedRect(ctx, knobX, knobY, knobSize, knobSize, knobSize * 0.5f, knobColor);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, float x, float y, float w, float h) {
        if (button == 0 && mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
            setter.accept(!getter.getAsBoolean());
            return true;
        }
        return false;
    }

    public String label() { return label; }
}
