package dev.ahmed.liquidglass.ui;

import dev.ahmed.liquidglass.ui.animation.Animation;
import dev.ahmed.liquidglass.ui.render.UIRenderer;
import dev.ahmed.liquidglass.ui.theme.ThemeManager;
import dev.ahmed.liquidglass.ui.theme.ThemeTokens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class UIManager {
    public enum Layer {
        WORLD, HUD, BACKGROUND, GLASS_PANELS, POPUPS, DROPDOWNS, TOOLTIPS, MODALS, CURSOR
    }

    private static final UIManager INSTANCE = new UIManager();
    private final Map<Layer, List<Consumer<GuiGraphicsExtractor>>> layerTasks = new EnumMap<>(Layer.class);

    private String pendingTooltip = null;
    private float tooltipX, tooltipY;
    private final Animation tooltipFade = new Animation(0.0f, 0.14f, Animation.Easing.EASE_OUT_CUBIC);

    private UIManager() {
        for (Layer layer : Layer.values()) {
            layerTasks.put(layer, new ArrayList<>());
        }
    }

    public static UIManager get() { return INSTANCE; }

    public void enqueue(Layer layer, Consumer<GuiGraphicsExtractor> renderTask) {
        layerTasks.get(layer).add(renderTask);
    }

    public void requestTooltip(String text, float mouseX, float mouseY) {
        this.pendingTooltip = text;
        this.tooltipX = mouseX + 10;
        this.tooltipY = mouseY - 6;
    }

    public void flushLayers(GuiGraphicsExtractor ctx, float dt) {
        tooltipFade.animateTo(pendingTooltip != null ? 1.0f : 0.0f);
        float tf = tooltipFade.update(dt);

        if (tf > 0.01f && pendingTooltip != null) {
            final String textToDraw = pendingTooltip;
            enqueue(Layer.TOOLTIPS, drawCtx -> renderGlassTooltip(drawCtx, textToDraw, tooltipX, tooltipY - (1.0f - tf) * 4.0f, tf));
        }
        pendingTooltip = null;

        for (Layer layer : Layer.values()) {
            List<Consumer<GuiGraphicsExtractor>> tasks = layerTasks.get(layer);
            for (Consumer<GuiGraphicsExtractor> task : tasks) {
                task.accept(ctx);
            }
            tasks.clear();
        }
    }

    private void renderGlassTooltip(GuiGraphicsExtractor ctx, String text, float x, float y, float alphaProgress) {
        ThemeTokens t = ThemeManager.get().tokens();
        int tw = Minecraft.getInstance().font.width(text);
        float w = tw + 14.0f;
        float h = 18.0f;
        int alpha = Math.round(240 * alphaProgress);

        UIRenderer.drawShadow(ctx, x, y + 2, w, h, t.radiusSmall(), 8.0f, UIRenderer.withAlpha(t.shadowColor(), alpha / 2));
        UIRenderer.drawRoundedRect(ctx, x, y, w, h, t.radiusSmall(), UIRenderer.withAlpha(t.bgGlassStrong(), alpha));
        UIRenderer.drawRoundedBorder(ctx, x, y, w, h, t.radiusSmall(), 1.0f, UIRenderer.withAlpha(t.borderActive(), alpha / 2));
        UIRenderer.drawText(ctx, text, x + 7, y + 5, UIRenderer.withAlpha(t.textPrimary(), alpha), 0.9f, false, false);
    }
}
