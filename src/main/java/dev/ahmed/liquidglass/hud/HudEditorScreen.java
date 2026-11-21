package dev.ahmed.liquidglass.hud;

import dev.ahmed.liquidglass.config.ConfigManager;
import dev.ahmed.liquidglass.ui.render.UIRenderer;
import dev.ahmed.liquidglass.ui.theme.ThemeManager;
import dev.ahmed.liquidglass.ui.theme.ThemeTokens;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public final class HudEditorScreen extends Screen {
    private final Screen parent;
    private HudElement selectedElement = null;
    private HudElement inspectorElement = null;
    private boolean dragging = false;
    private boolean resizing = false;
    private float dragOffsetX, dragOffsetY;

    public HudEditorScreen(Screen parent) {
        super(Text.literal("LiveStream HUD Editor"));
        this.parent = parent;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float deltaTicks) {
        ThemeTokens t = ThemeManager.get().tokens();

        if (dragging && selectedElement != null) {
            ctx.fill(this.width / 2, 0, this.width / 2 + 1, this.height, 0x22FFFFFF);
            ctx.fill(0, this.height / 2, this.width, this.height / 2 + 1, 0x22FFFFFF);
        }

        for (HudElement el : HudManager.get().elements()) {
            el.renderElement(ctx, mouseX, mouseY, true);

            boolean isSel = (el == selectedElement || el == inspectorElement);
            if (isSel) {
                UIRenderer.drawRoundedBorder(ctx, el.x() - 1, el.y() - 1, el.width() + 2, el.height() + 2,
                    el.style().cornerRadius + 1, 1.0f, t.borderActive());
                if (!el.isLocked()) {
                    UIRenderer.drawRoundedRect(ctx, el.x() + el.width() - 5, el.y() + el.height() - 5, 6, 6, 2, t.accentPrimary());
                }
            }
        }

        if (inspectorElement != null) {
            float dw = 152.0f;
            float dh = 148.0f;
            float ix = Math.min(this.width - dw - 8, inspectorElement.x() + inspectorElement.width() + 8);
            if (ix < 8) ix = 8;
            float iy = Math.max(8, Math.min(this.height - dh - 8, inspectorElement.y()));

            UIRenderer.drawGlassPanel(ctx, ix, iy, dw, dh, t.radiusMedium(),
                t.bgGlassStrong(), t.borderActive(), 0.25f, mouseX, mouseY);

            UIRenderer.drawText(ctx, inspectorElement.displayName(), ix + 8, iy + 7, t.textPrimary(), 0.95f, false, true);

            float bw = (dw - 18) * 0.5f;
            drawMiniPill(ctx, ix + 6, iy + 22, bw, 16, "Show: " + (inspectorElement.isEnabled() ? "ON" : "OFF"),
                inspectorElement.isEnabled(), mouseX, mouseY);
            drawMiniPill(ctx, ix + 12 + bw, iy + 22, bw, 16, "Lock: " + (inspectorElement.isLocked() ? "ON" : "OFF"),
                inspectorElement.isLocked(), mouseX, mouseY);

            drawMiniPill(ctx, ix + 6, iy + 42, bw, 16, "Bg: " + (inspectorElement.style().backgroundEnabled ? "ON" : "OFF"),
                inspectorElement.style().backgroundEnabled, mouseX, mouseY);
            drawMiniPill(ctx, ix + 12 + bw, iy + 42, bw, 16, "Border: " + (inspectorElement.style().borderEnabled ? "ON" : "OFF"),
                inspectorElement.style().borderEnabled, mouseX, mouseY);

            drawMiniPill(ctx, ix + 6, iy + 62, bw, 16, "Caps: " + (inspectorElement.style().smallCaps ? "ON" : "OFF"),
                inspectorElement.style().smallCaps, mouseX, mouseY);
            drawMiniPill(ctx, ix + 12 + bw, iy + 62, bw, 16, "Anim: " + (inspectorElement.style().animationEnabled ? "ON" : "OFF"),
                inspectorElement.style().animationEnabled, mouseX, mouseY);

            String scaleLabel = String.format(Locale.ROOT, "Text Size: %.1fx", inspectorElement.style().scale);
            UIRenderer.drawText(ctx, scaleLabel, ix + 8, iy + 86, t.textSecondary(), 0.85f, false, false);
            drawMiniPill(ctx, ix + dw - 44, iy + 83, 16, 14, "-", false, mouseX, mouseY);
            drawMiniPill(ctx, ix + dw - 24, iy + 83, 16, 14, "+", false, mouseX, mouseY);

            String radLabel = "Corner: " + Math.round(inspectorElement.style().cornerRadius) + "px";
            UIRenderer.drawText(ctx, radLabel, ix + 8, iy + 106, t.textSecondary(), 0.85f, false, false);
            drawMiniPill(ctx, ix + dw - 44, iy + 103, 16, 14, "-", false, mouseX, mouseY);
            drawMiniPill(ctx, ix + dw - 24, iy + 103, 16, 14, "+", false, mouseX, mouseY);

            drawMiniPill(ctx, ix + 6, iy + 124, dw - 12, 16, "Reset Scale (1.0x)", false, mouseX, mouseY);
        }
    }

    private void drawMiniPill(DrawContext ctx, float x, float y, float w, float h, String text, boolean active, int mx, int my) {
        ThemeTokens t = ThemeManager.get().tokens();
        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + h;
        int fill = active ? UIRenderer.withAlpha(t.accentPrimary(), 55) : (hover ? t.bgGlassStrong() : t.bgElevated());
        int border = active ? t.borderActive() : (hover ? t.borderActive() : t.borderBase());
        UIRenderer.drawRoundedRect(ctx, x, y, w, h, 3.5f, fill);
        UIRenderer.drawRoundedBorder(ctx, x, y, w, h, 3.5f, 1.0f, border);
        float tw = this.textRenderer.getWidth(text) * 0.85f;
        UIRenderer.drawText(ctx, text, x + (w - tw) * 0.5f, y + (h - 7) * 0.5f,
            active ? t.textPrimary() : t.textSecondary(), 0.85f, false, false);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x();
        double my = click.y();
        int btn = click.button();

        if (inspectorElement != null && btn == 0) {
            float dw = 152.0f;
            float dh = 148.0f;
            float ix = Math.min(this.width - dw - 8, inspectorElement.x() + inspectorElement.width() + 8);
            if (ix < 8) ix = 8;
            float iy = Math.max(8, Math.min(this.height - dh - 8, inspectorElement.y()));
            float bw = (dw - 18) * 0.5f;

            if (mx >= ix && mx <= ix + dw && my >= iy && my <= iy + dh) {
                if (hit(mx, my, ix + 6, iy + 22, bw, 16)) inspectorElement.setEnabled(!inspectorElement.isEnabled());
                else if (hit(mx, my, ix + 12 + bw, iy + 22, bw, 16)) inspectorElement.setLocked(!inspectorElement.isLocked());
                else if (hit(mx, my, ix + 6, iy + 42, bw, 16)) inspectorElement.style().backgroundEnabled = !inspectorElement.style().backgroundEnabled;
                else if (hit(mx, my, ix + 12 + bw, iy + 42, bw, 16)) inspectorElement.style().borderEnabled = !inspectorElement.style().borderEnabled;
                else if (hit(mx, my, ix + 6, iy + 62, bw, 16)) inspectorElement.style().smallCaps = !inspectorElement.style().smallCaps;
                else if (hit(mx, my, ix + 12 + bw, iy + 62, bw, 16)) inspectorElement.style().animationEnabled = !inspectorElement.style().animationEnabled;
                else if (hit(mx, my, ix + dw - 44, iy + 83, 16, 14)) inspectorElement.style().scale = Math.max(0.6f, inspectorElement.style().scale - 0.1f);
                else if (hit(mx, my, ix + dw - 24, iy + 83, 16, 14)) inspectorElement.style().scale = Math.min(2.5f, inspectorElement.style().scale + 0.1f);
                else if (hit(mx, my, ix + dw - 44, iy + 103, 16, 14)) inspectorElement.style().cornerRadius = Math.max(0.0f, inspectorElement.style().cornerRadius - 1.0f);
                else if (hit(mx, my, ix + dw - 24, iy + 103, 16, 14)) inspectorElement.style().cornerRadius = Math.min(18.0f, inspectorElement.style().cornerRadius + 1.0f);
                else if (hit(mx, my, ix + 6, iy + 124, dw - 12, 16)) inspectorElement.style().scale = 1.0f;
                return true;
            }
        }

        for (int i = HudManager.get().elements().size() - 1; i >= 0; i--) {
            HudElement el = HudManager.get().elements().get(i);
            if (mx >= el.x() && mx <= el.x() + el.width() && my >= el.y() && my <= el.y() + el.height()) {
                selectedElement = el;
                if (btn == 1) {
                    inspectorElement = el;
                    return true;
                }
                if (btn == 0 && !el.isLocked()) {
                    if (mx >= el.x() + el.width() - 8 && my >= el.y() + el.height() - 8) {
                        resizing = true;
                    } else {
                        dragging = true;
                        dragOffsetX = (float) mx - el.x();
                        dragOffsetY = (float) my - el.y();
                    }
                    return true;
                }
            }
        }
        inspectorElement = null;
        return super.mouseClicked(click, doubled);
    }

    private static boolean hit(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (selectedElement != null && !selectedElement.isLocked()) {
            if (resizing) {
                selectedElement.resize((float) click.x() - selectedElement.x(), (float) click.y() - selectedElement.y());
                return true;
            }
            if (dragging) {
                float nx = (float) click.x() - dragOffsetX;
                float ny = (float) click.y() - dragOffsetY;
                float cx = (this.width - selectedElement.width()) * 0.5f;
                if (Math.abs(nx - cx) < 5.0f) nx = cx;
                selectedElement.setPosition(
                    Math.max(2, Math.min(this.width - selectedElement.width() - 2, nx)),
                    Math.max(2, Math.min(this.height - selectedElement.height() - 2, ny))
                );
                return true;
            }
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragging = false;
        resizing = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        HudElement target = null; for (int i = HudManager.get().elements().size() - 1; i >= 0; i--) { HudElement el = HudManager.get().elements().get(i); if (mouseX >= el.x() && mouseX <= el.x() + el.width() && mouseY >= el.y() && mouseY <= el.y() + el.height()) { target = el; break; } } if (target == null) target = selectedElement != null ? selectedElement : inspectorElement; if (target != null) {
            float next = target.style().scale + (float) verticalAmount * 0.08f;
            target.style().scale = Math.max(0.5f, Math.min(2.6f, next));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (this.client != null) this.client.setScreen(parent);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void removed() {
        ConfigManager.get().save();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
