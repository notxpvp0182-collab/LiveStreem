package dev.ahmed.liquidglass.ui.clickgui;

import dev.ahmed.liquidglass.config.ConfigManager;
import dev.ahmed.liquidglass.hud.HudEditorScreen;
import dev.ahmed.liquidglass.hud.HudElement;
import dev.ahmed.liquidglass.hud.HudManager;
import dev.ahmed.liquidglass.hud.elements.LiveChatElement;
import dev.ahmed.liquidglass.ui.animation.Animation;
import dev.ahmed.liquidglass.ui.components.GlassToggle;
import dev.ahmed.liquidglass.ui.debug.DebugOverlay;
import dev.ahmed.liquidglass.ui.render.UIRenderer;
import dev.ahmed.liquidglass.ui.theme.ThemeManager;
import dev.ahmed.liquidglass.ui.theme.ThemeTokens;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class LiquidClickGuiScreen extends Screen {
    public enum Category {
        STREAM("Stream"), HUD("HUD"), CHANNEL("Channel"), CHAT("Chat"), THEME("Theme");
        private final String title;
        Category(String title) { this.title = title; }
        public String title() { return title; }
    }

    private final Animation openAnim = new Animation(0.0f, 0.24f, Animation.Easing.EASE_OUT_QUART);
    private final Animation expandAnim = new Animation(0.0f, 0.22f, Animation.Easing.EASE_OUT_CUBIC);
    private final Animation[] cardHoverAnims = new Animation[Category.values().length];

    private Category selectedCategory = null;
    private boolean closing = false;
    private long lastFrameNs = System.nanoTime();
    private float scrollOffset = 0.0f;
    private final List<GlassToggle> activeToggles = new ArrayList<>();

    public LiquidClickGuiScreen() {
        super(Text.literal("LiveStream GUI"));
        for (int i = 0; i < cardHoverAnims.length; i++) {
            cardHoverAnims[i] = new Animation(0.0f, 0.16f, Animation.Easing.EASE_OUT_CUBIC);
        }
        openAnim.animateTo(1.0f);
    }

    private void rebuildCategoryToggles(Category cat) {
        activeToggles.clear();
        if (cat == null) return;
        switch (cat) {
            case HUD -> {
                for (HudElement el : HudManager.get().elements()) {
                    if (el.id().equals("live_indicator") || el.id().equals("viewer_count")
                        || el.id().equals("like_count") || el.id().equals("stream_duration")
                        || el.id().equals("stream_title") || el.id().equals("connection_status")) {
                        activeToggles.add(new GlassToggle(el.displayName(), el::isEnabled, el::setEnabled));
                    }
                }
            }
            case CHANNEL -> {
                for (HudElement el : HudManager.get().elements()) {
                    if (el.id().equals("channel_name") || el.id().equals("channel_handle")
                        || el.id().equals("subscriber_count")) {
                        activeToggles.add(new GlassToggle(el.displayName(), el::isEnabled, el::setEnabled));
                    }
                }
                activeToggles.add(new GlassToggle("Auto Connect on Launch",
                    () -> ConfigManager.get().autoConnect(),
                    val -> ConfigManager.get().setAutoConnect(val)));
            }
            case CHAT -> {
                for (HudElement el : HudManager.get().elements()) {
                    if (el instanceof LiveChatElement chat) {
                        activeToggles.add(new GlassToggle("Enable Live Chat", chat::isEnabled, chat::setEnabled));
                        activeToggles.add(new GlassToggle("Show Username", () -> chat.showUsername, v -> chat.showUsername = v));
                        activeToggles.add(new GlassToggle("Show Timestamp", () -> chat.showTimestamp, v -> chat.showTimestamp = v));
                        activeToggles.add(new GlassToggle("Emoji Fallback", () -> chat.enableEmojiFallback, v -> chat.enableEmojiFallback = v));
                        activeToggles.add(new GlassToggle("Small Caps Text", () -> chat.style().smallCaps, v -> chat.style().smallCaps = v));
                    }
                }
            }
            case THEME -> {
                activeToggles.add(new GlassToggle("AMOLED Obsidian Theme",
                    () -> "amoled_glass".equals(ThemeManager.get().tokens().id()),
                    on -> ThemeManager.get().setActiveTheme(on ? "amoled_glass" : "dark_liquid_glass")));
                activeToggles.add(new GlassToggle("Dev Telemetry Overlay",
                    DebugOverlay::isMetricsEnabled, DebugOverlay::setMetricsEnabled));
                activeToggles.add(new GlassToggle("Show HUD Bounding Boxes",
                    DebugOverlay::isVisualBoundsEnabled, DebugOverlay::setVisualBoundsEnabled));
            }
            default -> {}
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float deltaTicks) {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastFrameNs) / 1_000_000_000.0f);
        lastFrameNs = now;

        float openProgress = openAnim.update(dt);
        if (closing && openAnim.isFinished()) {
            if (this.client != null) this.client.setScreen(null);
            return;
        }

        ThemeTokens t = ThemeManager.get().tokens();
        expandAnim.animateTo(selectedCategory != null ? 1.0f : 0.0f);
        float expand = expandAnim.update(dt);

        ctx.fill(0, 0, this.width, this.height, UIRenderer.withAlpha(t.bgOverlay(), (int) (185 * openProgress)));

        float scale = 0.98f + 0.02f * openProgress;
        Matrix3x2fStack matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(this.width * 0.5f, this.height * 0.5f);
        matrices.scale(scale, scale);
        matrices.translate(-this.width * 0.5f, -this.height * 0.5f);

        float centerX = this.width * 0.5f;
        float centerY = this.height * 0.5f;

        String title = "L I V E S T R E A M";
        int titleW = this.textRenderer.getWidth(title);
        UIRenderer.drawText(ctx, title, centerX - titleW * 0.5f, centerY - 125 - expand * 32,
            UIRenderer.withAlpha(t.textPrimary(), (int) (255 * openProgress)), 1.0f, false, true);

        Category[] cats = Category.values();
        float cardW = 68.0f;
        float cardH = 74.0f;
        float gap = 10.0f;
        float totalRowW = cats.length * cardW + (cats.length - 1) * gap;
        float startX = centerX - totalRowW * 0.5f;
        float cardY = centerY - cardH * 0.5f;

        int selIndex = selectedCategory != null ? selectedCategory.ordinal() : cats.length / 2;
        for (int i = 0; i < cats.length; i++) {
            float baseCx = startX + i * (cardW + gap);
            float cx = baseCx;
            if (expand > 0.001f && selectedCategory != null && i != selIndex) {
                int remIdx = (i < selIndex) ? i : (i - 1);
                int slot = (remIdx < 2) ? remIdx : (remIdx + 1);
                float targetCx = startX + slot * (cardW + gap) + (remIdx < 2 ? -105.0f : 105.0f);
                cx = baseCx + (targetCx - baseCx) * expand;
            }
            }
            boolean hovered = mouseX >= cx && mouseX <= cx + cardW && mouseY >= cardY && mouseY <= cardY + cardH;
            cardHoverAnims[i].animateTo(hovered ? 1.0f : 0.0f);
            float hv = cardHoverAnims[i].update(dt);

            if (selectedCategory == cats[i] && expand > 0.25f) {
                continue;
            }

            float grow = 4.0f * hv;
            UIRenderer.drawGlassPanel(
                ctx,
                cx - grow * 0.5f, cardY - grow * 0.5f,
                cardW + grow, cardH + grow,
                t.radiusMedium(),
                t.bgGlass(),
                t.borderBase(),
                hv, mouseX, mouseY
            );

            String label = cats[i].title();
            int lw = this.textRenderer.getWidth(label);
            UIRenderer.drawText(ctx, label, cx + (cardW - lw) * 0.5f, cardY + cardH - 22,
                t.textPrimary(), 1.0f, false, false);
            ctx.fill(Math.round(cx + cardW * 0.35f), Math.round(cardY + cardH - 10),
                     Math.round(cx + cardW * 0.65f), Math.round(cardY + cardH - 9),
                     UIRenderer.lerpColor(t.textMuted(), t.accentPrimary(), hv));
        }

        // Bottom Dock Buttons (Connect Stream & Edit HUD)
        float dockY = centerY + 54 + expand * 72;
        drawDockButton(ctx, centerX - 105, dockY, 100, 22, "Stream Connect", mouseX, mouseY);
        drawDockButton(ctx, centerX + 5, dockY, 100, 22, "Edit HUD", mouseX, mouseY);

        if (expand > 0.01f && selectedCategory != null) {
            float panelW = 220.0f;
            float panelH = 230.0f * expand;
            float px = centerX - panelW * 0.5f;
            float py = centerY - panelH * 0.5f;

            UIRenderer.drawGlassPanel(ctx, px, py, panelW, panelH, t.radiusLarge(),
                t.bgGlassStrong(), t.borderActive(), 0.4f, mouseX, mouseY);

            ctx.enableScissor(Math.round(px), Math.round(py), Math.round(px + panelW), Math.round(py + panelH));

            String catTitle = selectedCategory.title();
            int ctw = this.textRenderer.getWidth(catTitle);
            UIRenderer.drawText(ctx, catTitle, centerX - ctw * 0.5f, py + 14, t.textPrimary(), 1.0f, false, false);

            float btnX = px + 10;
            float btnY = py + 32;
            float btnW = panelW - 20;
            float btnH = 18;
            boolean editHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
            UIRenderer.drawRoundedRect(ctx, btnX, btnY, btnW, btnH, t.radiusSmall(),
                editHover ? t.bgGlassStrong() : t.bgElevated());
            UIRenderer.drawRoundedBorder(ctx, btnX, btnY, btnW, btnH, t.radiusSmall(), 1.0f,
                editHover ? t.borderActive() : t.borderBase());
            String topBtnText = "Edit HUD Layout";
            UIRenderer.drawText(ctx, topBtnText, centerX - this.textRenderer.getWidth(topBtnText) * 0.5f,
                btnY + 5, t.textPrimary(), 1.0f, false, false);

            float listTop = py + 56;
            float listBottom = py + panelH - 8;
            ctx.enableScissor(Math.round(px + 6), Math.round(listTop), Math.round(px + panelW - 6), Math.round(listBottom));

            float rowY = listTop - scrollOffset;
            for (GlassToggle toggle : activeToggles) {
                toggle.render(ctx, px + 10, rowY, panelW - 20, 22, mouseX, mouseY, dt);
                rowY += 26;
            }
            ctx.disableScissor();
            ctx.disableScissor();
        }

        matrices.popMatrix();
    }

    private void drawDockButton(DrawContext ctx, float x, float y, float w, float h, String label, int mouseX, int mouseY) {
        ThemeTokens t = ThemeManager.get().tokens();
        boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        UIRenderer.drawGlassPanel(ctx, x, y, w, h, t.radiusSmall(),
            hover ? t.bgGlassStrong() : t.bgGlass(), hover ? t.borderActive() : t.borderBase(),
            hover ? 1.0f : 0.0f, mouseX, mouseY);
        int lw = this.textRenderer.getWidth(label);
        UIRenderer.drawText(ctx, label, x + (w - lw) * 0.5f, y + (h - 8) * 0.5f, t.textPrimary(), 1.0f, false, false);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x();
        double my = click.y();
        int button = click.button();

        float centerX = this.width * 0.5f;
        float centerY = this.height * 0.5f;
        float expand = expandAnim.value();
        float dockY = centerY + 54 + expand * 72;

        // Bottom Dock Clicks
        if (button == 0 && my >= dockY && my <= dockY + 22) {
            if (mx >= centerX - 105 && mx <= centerX - 5) {
                if (this.client != null) this.client.setScreen(new WorkspaceModalScreen(this));
                return true;
            }
            if (mx >= centerX + 5 && mx <= centerX + 105) {
                if (this.client != null) this.client.setScreen(new HudEditorScreen(this));
                return true;
            }
        }

        if (selectedCategory != null) {
            float panelW = 220.0f;
            float panelH = 230.0f;
            float px = centerX - panelW * 0.5f;
            float py = centerY - panelH * 0.5f;

            if (button == 0 && mx >= px + 10 && mx <= px + panelW - 10 && my >= py + 32 && my <= py + 50) {
                if (this.client != null) {
                    this.client.setScreen(new HudEditorScreen(this));
                }
                return true;
            }

            float rowY = py + 56 - scrollOffset;
            for (GlassToggle toggle : activeToggles) {
                if (toggle.mouseClicked(mx, my, button, px + 10, rowY, panelW - 20, 22)) {
                    return true;
                }
                rowY += 26;
            }

            if (mx < px || mx > px + panelW || my < py || my > py + panelH) {
                Category[] cats = Category.values();
                float cardW = 68.0f, cardH = 74.0f, gap = 10.0f;
                float startX = centerX - (cats.length * cardW + (cats.length - 1) * gap) * 0.5f;
                float cardY = centerY - cardH * 0.5f;
                int selIdx = selectedCategory.ordinal();
                for (int i = 0; i < cats.length; i++) {
                    if (i == selIdx) continue;
                    int remIdx = (i < selIdx) ? i : (i - 1);
                    int slot = (remIdx < 2) ? remIdx : (remIdx + 1);
                    float cx = startX + slot * (cardW + gap) + (remIdx < 2 ? -105.0f : 105.0f);
                    if (button == 0 && mx >= cx && mx <= cx + cardW && my >= cardY && my <= cardY + cardH) {
                        if (cats[i] == Category.STREAM) {
                            if (this.client != null) this.client.setScreen(new WorkspaceModalScreen(this));
                            return true;
                        }
                        selectedCategory = cats[i];
                        scrollOffset = 0.0f;
                        rebuildCategoryToggles(selectedCategory);
                        return true;
                    }
                }
                selectedCategory = null;
                return true;
            }
        } else {
            Category[] cats = Category.values();
            float cardW = 68.0f;
            float cardH = 74.0f;
            float gap = 10.0f;
            float totalRowW = cats.length * cardW + (cats.length - 1) * gap;
            float startX = centerX - totalRowW * 0.5f;
            float cardY = centerY - cardH * 0.5f;

            for (int i = 0; i < cats.length; i++) {
                float cx = startX + i * (cardW + gap);
                if (button == 0 && mx >= cx && mx <= cx + cardW && my >= cardY && my <= cardY + cardH) {
                    if (cats[i] == Category.STREAM) {
                        if (this.client != null) this.client.setScreen(new WorkspaceModalScreen(this));
                        return true;
                    }
                    selectedCategory = cats[i];
                    scrollOffset = 0.0f;
                    rebuildCategoryToggles(selectedCategory);
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (selectedCategory != null) {
            float maxScroll = Math.max(0.0f, activeToggles.size() * 26.0f - 160.0f);
            scrollOffset = Math.max(0.0f, Math.min(maxScroll, scrollOffset - (float) verticalAmount * 18.0f));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (selectedCategory != null) {
                selectedCategory = null;
                return true;
            }
            this.closing = true;
            this.openAnim.animateTo(0.0f);
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
