package dev.ahmed.liquidglass.hud;

import dev.ahmed.liquidglass.config.ConfigManager;
import dev.ahmed.liquidglass.ui.render.UIRenderer;
import dev.ahmed.liquidglass.ui.theme.ThemeManager;
import dev.ahmed.liquidglass.ui.theme.ThemeTokens;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

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
            int outline = isSel ? t.borderActive() : 0x33FFFFFF;
            UIRenderer.drawRoundedBorder(ctx, el.x() - 1, el.y() - 1, el.width() + 2, el.height() + 2,
                el.style().cornerRadius + 1, 1.0f, outline);

            if (isSel && !el.isLocked()) {
                UIRenderer.drawRoundedRect(ctx, el.x() + el.width() - 4, el.y() + el.height() - 4, 6, 6, 2, t.accentPrimary());
            }
        }

        if (inspectorElement != null) {
            float ix = Math.min(this.width - 175, inspectorElement.x() + inspectorElement.width() + 8);
            float iy = Math.max(10, Math.min(this.height - 150, inspectorElement.y()));
            UIRenderer.drawGlassPanel(ctx, ix, iy, 165, 138, t.radiusMedium(),
                t.bgGlassStrong(), t.borderActive(), 0.2f, mouseX, mouseY);

            UIRenderer.drawText(ctx, inspectorElement.displayName() + " Settings", ix + 8, iy + 8, t.textPrimary(), 1.0f, false, false);
            UIRenderer.drawText(ctx, "[1] Enabled: " + (inspectorElement.isEnabled() ? "ON" : "OFF"), ix + 8, iy + 26, t.textSecondary(), 1.0f, false, false);
            UIRenderer.drawText(ctx, "[2] Small Caps: " + (inspectorElement.style().smallCaps ? "ON" : "OFF"), ix + 8, iy + 42, t.textSecondary(), 1.0f, false, false);
            UIRenderer.drawText(ctx, "[3] Background: " + (inspectorElement.style().backgroundEnabled ? "ON" : "OFF"), ix + 8, iy + 58, t.textSecondary(), 1.0f, false, false);
            UIRenderer.drawText(ctx, "[4] Border: " + (inspectorElement.style().borderEnabled ? "ON" : "OFF"), ix + 8, iy + 74, t.textSecondary(), 1.0f, false, false);
            UIRenderer.drawText(ctx, "[5] Locked: " + (inspectorElement.isLocked() ? "YES" : "NO"), ix + 8, iy + 90, t.textSecondary(), 1.0f, false, false);
            UIRenderer.drawText(ctx, "Scroll: Radius (" + Math.round(inspectorElement.style().cornerRadius) + "px)", ix + 8, iy + 110, t.textMuted(), 1.0f, false, false);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x();
        double my = click.y();
        int btn = click.button();

        if (inspectorElement != null && btn == 0) {
            float ix = Math.min(this.width - 175, inspectorElement.x() + inspectorElement.width() + 8);
            float iy = Math.max(10, Math.min(this.height - 150, inspectorElement.y()));
            if (mx >= ix && mx <= ix + 165 && my >= iy && my <= iy + 138) {
                if (my >= iy + 22 && my < iy + 38) inspectorElement.setEnabled(!inspectorElement.isEnabled());
                else if (my >= iy + 38 && my < iy + 54) inspectorElement.style().smallCaps = !inspectorElement.style().smallCaps;
                else if (my >= iy + 54 && my < iy + 70) inspectorElement.style().backgroundEnabled = !inspectorElement.style().backgroundEnabled;
                else if (my >= iy + 70 && my < iy + 86) inspectorElement.style().borderEnabled = !inspectorElement.style().borderEnabled;
                else if (my >= iy + 86 && my < iy + 102) inspectorElement.setLocked(!inspectorElement.isLocked());
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
        if (inspectorElement != null) {
            float next = inspectorElement.style().cornerRadius + (float) verticalAmount;
            inspectorElement.style().cornerRadius = Math.max(0.0f, Math.min(18.0f, next));
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
        if (inspectorElement != null) {
            switch (input.key()) {
                case GLFW.GLFW_KEY_1 -> inspectorElement.setEnabled(!inspectorElement.isEnabled());
                case GLFW.GLFW_KEY_2 -> inspectorElement.style().smallCaps = !inspectorElement.style().smallCaps;
                case GLFW.GLFW_KEY_3 -> inspectorElement.style().backgroundEnabled = !inspectorElement.style().backgroundEnabled;
                case GLFW.GLFW_KEY_4 -> inspectorElement.style().borderEnabled = !inspectorElement.style().borderEnabled;
                case GLFW.GLFW_KEY_5 -> inspectorElement.setLocked(!inspectorElement.isLocked());
            }
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
