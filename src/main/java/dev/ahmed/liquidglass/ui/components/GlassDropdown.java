package dev.ahmed.liquidglass.ui.components;

import dev.ahmed.liquidglass.ui.UIManager;
import dev.ahmed.liquidglass.ui.animation.Animation;
import dev.ahmed.liquidglass.ui.render.UIRenderer;
import dev.ahmed.liquidglass.ui.theme.ThemeManager;
import dev.ahmed.liquidglass.ui.theme.ThemeTokens;
import net.minecraft.client.gui.DrawContext;

import java.util.List;
import java.util.function.Consumer;

public final class GlassDropdown {
    private final String label;
    private final List<String> options;
    private int selectedIndex;
    private final Consumer<String> onSelect;
    private boolean expanded = false;
    private final Animation openAnim = new Animation(0.0f, 0.16f, Animation.Easing.EASE_OUT_CUBIC);

    public GlassDropdown(String label, List<String> options, int initialIndex, Consumer<String> onSelect) {
        this.label = label;
        this.options = options;
        this.selectedIndex = Math.max(0, Math.min(options.size() - 1, initialIndex));
        this.onSelect = onSelect;
    }

    public void render(DrawContext ctx, float x, float y, float w, float h, float mouseX, float mouseY, float dt) {
        ThemeTokens t = ThemeManager.get().tokens();
        openAnim.animateTo(expanded ? 1.0f : 0.0f);
        float progress = openAnim.update(dt);

        UIRenderer.drawRoundedRect(ctx, x, y, w, h, t.radiusSmall(), t.bgGlass());
        UIRenderer.drawRoundedBorder(ctx, x, y, w, h, t.radiusSmall(), 1.0f, expanded ? t.borderActive() : t.borderBase());

        String current = options.isEmpty() ? "--" : options.get(selectedIndex);
        UIRenderer.drawText(ctx, label + ": " + current, x + 8, y + (h - 8) * 0.5f, t.textPrimary(), 1.0f, false, false);

        if (progress > 0.01f) {
            float listH = options.size() * 20.0f * progress;
            UIManager.get().enqueue(UIManager.Layer.DROPDOWNS, popupCtx -> {
                UIRenderer.drawGlassPanel(popupCtx, x, y + h + 2, w, listH, t.radiusSmall(),
                    t.bgGlassStrong(), t.borderActive(), 0.2f, mouseX, mouseY);
                popupCtx.enableScissor(Math.round(x), Math.round(y + h + 2), Math.round(x + w), Math.round(y + h + 2 + listH));
                for (int i = 0; i < options.size(); i++) {
                    float oy = y + h + 2 + i * 20.0f;
                    boolean optHover = mouseX >= x && mouseX <= x + w && mouseY >= oy && mouseY <= oy + 20.0f;
                    if (optHover || i == selectedIndex) {
                        UIRenderer.drawRoundedRect(popupCtx, x + 2, oy + 1, w - 4, 18, 3.0f,
                            i == selectedIndex ? UIRenderer.withAlpha(t.accentPrimary(), 45) : t.bgElevated());
                    }
                    UIRenderer.drawText(popupCtx, options.get(i), x + 8, oy + 6,
                        i == selectedIndex ? t.accentPrimary() : t.textSecondary(), 1.0f, false, false);
                }
                popupCtx.disableScissor();
            });
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, float x, float y, float w, float h) {
        if (button != 0) return false;
        if (expanded) {
            float listTop = y + h + 2;
            float listBottom = listTop + options.size() * 20.0f;
            if (mouseX >= x && mouseX <= x + w && mouseY >= listTop && mouseY <= listBottom) {
                int idx = (int) ((mouseY - listTop) / 20.0f);
                if (idx >= 0 && idx < options.size()) {
                    selectedIndex = idx;
                    onSelect.accept(options.get(idx));
                }
                expanded = false;
                return true;
            }
            expanded = false;
        }
        if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
            expanded = !expanded;
            return true;
        }
        return false;
    }

    public boolean isExpanded() { return expanded; }
}
