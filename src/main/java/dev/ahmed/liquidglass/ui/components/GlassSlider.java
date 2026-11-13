package dev.ahmed.liquidglass.ui.components;

import dev.ahmed.liquidglass.ui.animation.Animation;
import dev.ahmed.liquidglass.ui.render.UIRenderer;
import dev.ahmed.liquidglass.ui.theme.ThemeManager;
import dev.ahmed.liquidglass.ui.theme.ThemeTokens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public final class GlassSlider {
    private final String label;
    private final double min;
    private final double max;
    private final double step;
    private final String suffix;
    private final DoubleSupplier getter;
    private final DoubleConsumer setter;
    private final Animation fillAnim;
    private final Animation hoverAnim;
    private boolean dragging = false;

    public GlassSlider(String label, double min, double max, double step, String suffix,
                       DoubleSupplier getter, DoubleConsumer setter) {
        this.label = label;
        this.min = min;
        this.max = max;
        this.step = step;
        this.suffix = suffix;
        this.getter = getter;
        this.setter = setter;
        float initialNorm = (float) ((getter.getAsDouble() - min) / Math.max(0.0001, max - min));
        this.fillAnim = new Animation(initialNorm, 0.10f, Animation.Easing.EASE_OUT_CUBIC);
        this.hoverAnim = new Animation(0.0f, 0.14f, Animation.Easing.EASE_OUT_CUBIC);
    }

    public void render(DrawContext ctx, float x, float y, float w, float h, float mouseX, float mouseY, float dt) {
        ThemeTokens t = ThemeManager.get().tokens();
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        double currentVal = getter.getAsDouble();
        float norm = (float) Math.max(0.0, Math.min(1.0, (currentVal - min) / Math.max(0.0001, max - min)));

        fillAnim.animateTo(norm);
        hoverAnim.animateTo((hovered || dragging) ? 1.0f : 0.0f);
        float fill = fillAnim.update(dt);
        float hv = hoverAnim.update(dt);

        UIRenderer.drawRoundedRect(ctx, x, y, w, h, t.radiusSmall(), UIRenderer.lerpColor(t.bgGlassWeak(), t.bgGlass(), hv));
        UIRenderer.drawRoundedBorder(ctx, x, y, w, h, t.radiusSmall(), 1.0f,
            UIRenderer.lerpColor(t.borderBase(), t.borderActive(), hv * 0.7f));

        // Label & formatted numeric readout
        UIRenderer.drawText(ctx, label, x + 8, y + 4, t.textSecondary(), 1.0f, false, false);
        String formatted = (step >= 1.0 ? String.valueOf(Math.round(currentVal)) : String.format(Locale.ROOT, "%.1f", currentVal)) + suffix;
        int valW = MinecraftClient.getInstance().textRenderer.getWidth(formatted);
        UIRenderer.drawText(ctx, formatted, x + w - valW - 8, y + 4, t.textPrimary(), 1.0f, false, false);

        // Track + active bar + rounded thumb
        float trackX = x + 8;
        float trackY = y + h - 7;
        float trackW = w - 16;
        UIRenderer.drawRoundedRect(ctx, trackX, trackY, trackW, 3, 1.5f, 0xFF181B24);
        UIRenderer.drawRoundedRect(ctx, trackX, trackY, Math.max(3.0f, trackW * fill), 3, 1.5f, t.accentPrimary());

        float thumbX = trackX + trackW * fill - 3.5f;
        UIRenderer.drawRoundedRect(ctx, thumbX, trackY - 2.0f, 7.0f, 7.0f, 3.5f, t.accentActive());
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, float x, float y, float w, float h) {
        if (button == 0 && mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
            dragging = true;
            updateFromMouse(mouseX, x + 8, w - 16);
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, float x, float w) {
        if (dragging) {
            updateFromMouse(mouseX, x + 8, w - 16);
            return true;
        }
        return false;
    }

    public void mouseReleased() {
        dragging = false;
    }

    private void updateFromMouse(double mouseX, float trackX, float trackW) {
        double ratio = Math.max(0.0, Math.min(1.0, (mouseX - trackX) / Math.max(1.0, trackW)));
        double raw = min + ratio * (max - min);
        if (step > 0) {
            raw = Math.round(raw / step) * step;
        }
        setter.accept(Math.max(min, Math.min(max, raw)));
    }
}
