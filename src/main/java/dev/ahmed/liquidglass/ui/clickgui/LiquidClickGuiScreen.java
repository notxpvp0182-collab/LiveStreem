package dev.ahmed.liquidglass.ui.clickgui;

import dev.ahmed.liquidglass.config.ConfigManager;
import dev.ahmed.liquidglass.hud.HudEditorScreen;
import dev.ahmed.liquidglass.hud.HudElement;
import dev.ahmed.liquidglass.hud.HudManager;
import dev.ahmed.liquidglass.hud.elements.LiveChatElement;
import dev.ahmed.liquidglass.stream.StreamManager;
import dev.ahmed.liquidglass.ui.animation.Animation;
import dev.ahmed.liquidglass.ui.components.GlassSlider;
import dev.ahmed.liquidglass.ui.components.GlassToggle;
import dev.ahmed.liquidglass.ui.debug.DebugOverlay;
import dev.ahmed.liquidglass.ui.render.IconRenderer;
import dev.ahmed.liquidglass.ui.render.UIRenderer;
import dev.ahmed.liquidglass.ui.theme.GlassQuality;
import dev.ahmed.liquidglass.ui.theme.ThemeManager;
import dev.ahmed.liquidglass.ui.theme.ThemeTokens;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

public final class LiquidClickGuiScreen extends Screen {
    public enum Category {
        STREAM("Stream", IconRenderer.Icon.LIVE),
        HUD("HUD", IconRenderer.Icon.DISPLAY),
        CHANNEL("Channel", IconRenderer.Icon.PROFILE),
        CHAT("Chat", IconRenderer.Icon.CHAT),
        THEME("Theme", IconRenderer.Icon.SLIDERS);

        private final String title;
        private final IconRenderer.Icon icon;
        Category(String title, IconRenderer.Icon icon) {
            this.title = title;
            this.icon = icon;
        }
        public String title() { return title; }
        public IconRenderer.Icon icon() { return icon; }
    }

    private final Animation openAnim = new Animation(0.0f, 0.25f, Animation.Easing.EASE_OUT_QUART);
    private final Animation expandAnim = new Animation(0.0f, 0.24f, Animation.Easing.EASE_OUT_CUBIC);
    private final Animation panelSwitchAnim = new Animation(1.0f, 0.22f, Animation.Easing.EASE_OUT_QUART);
    private final Animation[] cardHoverAnims = new Animation[Category.values().length];
    private final Animation[] cardXAnims = new Animation[Category.values().length];
    private boolean cardPositionsInitialized = false;

    private Category selectedCategory = null;
    private boolean closing = false;
    private boolean draggingScrollbar = false;
    private long lastFrameNs = System.nanoTime();
    private float scrollOffset = 0.0f;
    private final List<GlassToggle> activeToggles = new ArrayList<>();
    private final List<GlassSlider> activeSliders = new ArrayList<>();

    public LiquidClickGuiScreen() {
        super(Component.literal("LiveStream GUI"));
        for (int i = 0; i < Category.values().length; i++) {
            cardHoverAnims[i] = new Animation(0.0f, 0.15f, Animation.Easing.EASE_OUT_CUBIC);
            cardXAnims[i] = new Animation(0.0f, 0.24f, Animation.Easing.EASE_OUT_QUART);
        }
        openAnim.animateTo(1.0f);
    }

    /** Responsive scale factor so the entire ClickGUI adapts to any screen resolution or GUI scale. */
    private float getResponsiveScale() {
        float sx = this.width / 620.0f;
        float sy = this.height / 345.0f;
        return Math.max(0.58f, Math.min(1.25f, Math.min(sx, sy)));
    }

    private float toDesignX(double screenX) {
        float s = getResponsiveScale();
        return (float) ((screenX - this.width * 0.5) / s + this.width * 0.5);
    }

    private float toDesignY(double screenY) {
        float s = getResponsiveScale();
        return (float) ((screenY - this.height * 0.5) / s + this.height * 0.5);
    }

    private void selectCategoryWithAnimation(Category cat) {
        this.selectedCategory = cat;
        this.scrollOffset = 0.0f;
        this.panelSwitchAnim.animateTo(0.0f);
        this.panelSwitchAnim.update(1.0f);
        this.panelSwitchAnim.animateTo(1.0f);
        rebuildCategoryControls(cat);
    }

    private void rebuildCategoryControls(Category cat) {
        activeToggles.clear();
        activeSliders.clear();
        if (cat == null) return;
        switch (cat) {
            case HUD -> {
                for (HudElement el : HudManager.get().elements()) {
                    if (el.id().equals("live_indicator") || el.id().equals("viewer_count") || el.id().equals("live_watching")
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
                        activeSliders.add(new GlassSlider("Chat Delay", 0.0, 2.0, 0.1, "s",
                            () -> StreamManager.get().chatDelayMs() / 1000.0,
                            sec -> StreamManager.get().setChatDelayMs((int) Math.round(sec * 1000.0))));
                        activeSliders.add(new GlassSlider("Animation", 1.0, 6.0, 1.0, "@STYLE", () -> chat.getAnimStyleIndex(), val -> chat.setAnimStyleIndex((int) Math.round(val)))); activeSliders.add(new GlassSlider("Anim Speed", 0.5, 2.5, 0.1, "x", () -> chat.chatAnimSpeed, val -> chat.chatAnimSpeed = (float) val)); activeSliders.add(new GlassSlider("Chat Text Size", 0.7, 1.8, 0.1, "x",
                            () -> chat.style().scale,
                            val -> chat.style().scale = (float) val));
                        activeToggles.add(new GlassToggle("Text Shadow", () -> chat.textShadow, v -> chat.textShadow = v));
                        activeToggles.add(new GlassToggle("Line Background", () -> chat.chatShadow, v -> chat.chatShadow = v));
                        activeToggles.add(new GlassToggle("Newest Chat at Top", () -> chat.newestAtTop, v -> chat.newestAtTop = v));
                        activeToggles.add(new GlassToggle("YouTube Icon Badge", () -> chat.showYouTubeIcon, v -> chat.showYouTubeIcon = v));
                        activeToggles.add(new GlassToggle("Colored Usernames", () -> chat.coloredUsernames, v -> chat.coloredUsernames = v));
                        activeToggles.add(new GlassToggle("Multi-Color Chat", () -> chat.coloredMessages, v -> chat.coloredMessages = v));
                        activeToggles.add(new GlassToggle("RGB Chroma Mode", () -> chat.rgbMode, v -> chat.rgbMode = v));
                        activeToggles.add(new GlassToggle("Show Timestamp", () -> chat.showTimestamp, v -> chat.showTimestamp = v));
                        activeToggles.add(new GlassToggle("Show Username", () -> chat.showUsername, v -> chat.showUsername = v));
                        activeToggles.add(new GlassToggle("Chat Box Frame", () -> chat.style().backgroundEnabled, v -> chat.style().backgroundEnabled = v));
                    }
                }
            }
            case THEME -> {
                activeSliders.add(new GlassSlider("Quality (1=Low..4=Ultra)", 1.0, 4.0, 1.0, "",
                    () -> ThemeManager.get().quality().ordinal() + 1.0,
                    val -> {
                        int idx = Math.max(0, Math.min(3, (int) Math.round(val) - 1));
                        ThemeManager.get().setQuality(GlassQuality.values()[idx]);
                        ConfigManager.get().save();
                    }));
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

    private float getContentHeight() {
        return activeSliders.size() * 28.0f + activeToggles.size() * 26.0f;
    }

    private float getMaxScroll(float viewHeight) {
        return Math.max(0.0f, getContentHeight() - viewHeight);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float deltaTicks) {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastFrameNs) / 1_000_000_000.0f);
        lastFrameNs = now;

        float openProgress = openAnim.update(dt);
        if (closing && openAnim.isFinished()) {
            if (this.minecraft != null) this.minecraft.setScreen(null);
            return;
        }

        ThemeTokens t = ThemeManager.get().tokens();
        expandAnim.animateTo(selectedCategory != null ? 1.0f : 0.0f);
        float expand = expandAnim.update(dt);
        float switchAnim = panelSwitchAnim.update(dt);

        ctx.fill(0, 0, this.width, this.height, UIRenderer.withAlpha(t.bgOverlay(), (int) (175 * openProgress)));

        float respScale = getResponsiveScale();
        float animScale = respScale * (0.95f + 0.05f * openProgress);
        float dmx = toDesignX(mouseX);
        float dmy = toDesignY(mouseY);

        Matrix3x2fStack matrices = ctx.pose();
        matrices.pushMatrix();
        matrices.translate(this.width * 0.5f, this.height * 0.5f);
        matrices.scale(animScale, animScale);
        matrices.translate(-this.width * 0.5f, -this.height * 0.5f);

        float centerX = this.width * 0.5f;
        float centerY = this.height * 0.5f;

        String title = "L I V E S T R E A M";
        int titleW = this.font.width(title);
        UIRenderer.drawText(ctx, title, centerX - titleW * 0.5f, centerY - 125 - expand * 32,
            UIRenderer.withAlpha(t.textPrimary(), (int) (255 * openProgress)), 1.0f, false, true);

        Category[] cats = Category.values();
        float cardW = 68.0f;
        float cardH = 74.0f;
        float gap = 10.0f;
        float totalRowW = cats.length * cardW + (cats.length - 1) * gap;
        float startX = centerX - totalRowW * 0.5f;
        float cardY = centerY - cardH * 0.5f;

        int selIndex = selectedCategory != null ? selectedCategory.ordinal() : -1;

        for (int i = 0; i < cats.length; i++) {
            float baseCx = startX + i * (cardW + gap);
            float targetCx = baseCx;
            if (selectedCategory != null) {
                if (i == selIndex) {
                    targetCx = centerX - cardW * 0.5f;
                } else {
                    int remIdx = (i < selIndex) ? i : (i - 1);
                    int slot = (remIdx < 2) ? remIdx : (remIdx + 1);
                    targetCx = startX + slot * (cardW + gap) + (remIdx < 2 ? -108.0f : 108.0f);
                }
            }

            if (!cardPositionsInitialized) {
                cardXAnims[i] = new Animation(targetCx, 0.24f, Animation.Easing.EASE_OUT_QUART);
            } else {
                cardXAnims[i].animateTo(targetCx);
            }
            float cx = cardXAnims[i].update(dt);

            boolean hovered = dmx >= cx && dmx <= cx + cardW && dmy >= cardY && dmy <= cardY + cardH;
            cardHoverAnims[i].animateTo(hovered ? 1.0f : 0.0f);
            float hv = cardHoverAnims[i].update(dt);

            if (selectedCategory == cats[i] && expand > 0.20f) {
                continue;
            }

            float grow = 4.0f * hv;
            UIRenderer.drawGlassPanel(
                ctx,
                cx - grow * 0.5f, cardY - grow * 0.5f,
                cardW + grow, cardH + grow,
                10.0f,
                t.bgGlass(),
                t.borderBase(),
                hv, dmx, dmy
            );

            int iconColor = UIRenderer.lerpColor(t.textSecondary(), t.accentActive(), hv);
            IconRenderer.drawIcon(ctx, cats[i].icon(), cx + (cardW - 22) * 0.5f, cardY + 14, 22, iconColor);

            String label = cats[i].title();
            int lw = this.font.width(label);
            UIRenderer.drawText(ctx, label, cx + (cardW - lw) * 0.5f, cardY + cardH - 22,
                t.textPrimary(), 1.0f, false, false);
            UIRenderer.drawRoundedRect(ctx, cx + cardW * 0.34f, cardY + cardH - 10, cardW * 0.32f, 1.5f, 1.0f,
                UIRenderer.lerpColor(t.textMuted(), t.accentPrimary(), hv));
        }
        cardPositionsInitialized = true;

        float dockY = centerY + 54 + expand * 74;
        drawDockButton(ctx, centerX - 105, dockY, 100, 22, "Stream Connect", dmx, dmy);
        drawDockButton(ctx, centerX + 5, dockY, 100, 22, "Edit HUD", dmx, dmy);

        if (expand > 0.01f && selectedCategory != null) {
            float effectiveAnim = expand * (0.85f + 0.15f * switchAnim);
            float panelW = 236.0f;
            float panelH = 236.0f * effectiveAnim;
            float px = centerX - panelW * 0.5f;
            float py = centerY - panelH * 0.5f + (1.0f - switchAnim) * 8.0f;

            UIRenderer.drawGlassPanel(ctx, px, py, panelW, panelH, 13.0f,
                t.bgGlassStrong(), t.borderActive(), 0.4f, dmx, dmy);

            int scX1 = Math.round( centerX + (px - centerX) * animScale );
            int scY1 = Math.round( centerY + (py - centerY) * animScale );
            int scX2 = Math.round( centerX + (px + panelW - centerX) * animScale );
            int scY2 = Math.round( centerY + (py + panelH - centerY) * animScale );
            ctx.enableScissor(scX1, scY1, scX2, scY2);

            IconRenderer.drawIcon(ctx, selectedCategory.icon(), centerX - 5, py + 7, 10, t.accentPrimary());
            String catTitle = selectedCategory.title();
            int ctw = this.font.width(catTitle);
            UIRenderer.drawText(ctx, catTitle, centerX - ctw * 0.5f, py + 19, t.textPrimary(), 1.0f, false, false);

            float btnX = px + 10;
            float btnY = py + 33;
            float btnW = panelW - 20;
            float btnH = 18;
            boolean editHover = dmx >= btnX && dmx <= btnX + btnW && dmy >= btnY && dmy <= btnY + btnH;
            UIRenderer.drawRoundedRect(ctx, btnX, btnY, btnW, btnH, 7.0f,
                editHover ? t.bgGlassStrong() : t.bgElevated());
            UIRenderer.drawRoundedBorder(ctx, btnX, btnY, btnW, btnH, 7.0f, 1.0f,
                editHover ? t.borderActive() : t.borderBase());
            String topBtnText = "Edit HUD Layout";
            UIRenderer.drawText(ctx, topBtnText, centerX - this.font.width(topBtnText) * 0.5f,
                btnY + 5, t.textPrimary(), 1.0f, false, false);

            float listTop = py + 56;
            float listBottom = py + panelH - 10;
            float viewH = Math.max(10.0f, listBottom - listTop);
            float maxScroll = getMaxScroll(viewH);
            scrollOffset = Math.max(0.0f, Math.min(maxScroll, scrollOffset));

            float rowWidth = maxScroll > 0.0f ? (panelW - 28.0f) : (panelW - 20.0f);
            int lScX1 = Math.round(centerX + (px + 6 - centerX) * animScale);
            int lScY1 = Math.round(centerY + (listTop - centerY) * animScale);
            int lScX2 = Math.round(centerX + (px + panelW - 6 - centerX) * animScale);
            int lScY2 = Math.round(centerY + (listBottom - centerY) * animScale);
            ctx.enableScissor(lScX1, lScY1, lScX2, lScY2);

            float rowY = listTop - scrollOffset;
            for (GlassSlider slider : activeSliders) {
                slider.render(ctx, px + 10, rowY, rowWidth, 24, dmx, dmy, dt);
                rowY += 28;
            }
            for (GlassToggle toggle : activeToggles) {
                toggle.render(ctx, px + 10, rowY, rowWidth, 22, dmx, dmy, dt);
                rowY += 26;
            }
            ctx.disableScissor();

            if (maxScroll > 0.0f) {
                float sbX = px + panelW - 11.0f;
                float sbW = 4.5f;
                UIRenderer.drawRoundedRect(ctx, sbX, listTop, sbW, viewH, 2.25f, 0x6608090C);
                float contentH = getContentHeight();
                float thumbH = Math.max(22.0f, viewH * (viewH / Math.max(viewH, contentH)));
                float thumbY = listTop + (viewH - thumbH) * (scrollOffset / maxScroll);
                boolean sbHover = draggingScrollbar || (dmx >= sbX - 3 && dmx <= sbX + sbW + 3 && dmy >= listTop && dmy <= listBottom);
                UIRenderer.drawRoundedRect(ctx, sbX, thumbY, sbW, thumbH, 2.25f,
                    sbHover ? t.accentActive() : t.accentPrimary());
            }

            ctx.disableScissor();
        }

        matrices.popMatrix();
    }

    private void drawDockButton(GuiGraphicsExtractor ctx, float x, float y, float w, float h, String label, float dmx, float dmy) {
        ThemeTokens t = ThemeManager.get().tokens();
        boolean hover = dmx >= x && dmx <= x + w && dmy >= y && dmy <= y + h;
        UIRenderer.drawGlassPanel(ctx, x, y, w, h, 8.0f,
            hover ? t.bgGlassStrong() : t.bgGlass(), hover ? t.borderActive() : t.borderBase(),
            hover ? 1.0f : 0.0f, dmx, dmy);
        int lw = this.font.width(label);
        UIRenderer.drawText(ctx, label, x + (w - lw) * 0.5f, y + (h - 8) * 0.5f, t.textPrimary(), 1.0f, false, false);
    }

    private void updateScrollFromMouse(double designMouseY) {
        float panelH = 236.0f;
        float py = this.height * 0.5f - panelH * 0.5f;
        float listTop = py + 56.0f;
        float listBottom = py + panelH - 10.0f;
        float viewH = Math.max(10.0f, listBottom - listTop);
        float maxScroll = getMaxScroll(viewH);
        if (maxScroll <= 0.0f) return;

        float contentH = getContentHeight();
        float thumbH = Math.max(22.0f, viewH * (viewH / Math.max(viewH, contentH)));
        float trackTravel = Math.max(1.0f, viewH - thumbH);
        float ratio = ((float) designMouseY - listTop - thumbH * 0.5f) / trackTravel;
        scrollOffset = Math.max(0.0f, Math.min(maxScroll, ratio * maxScroll));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mx = toDesignX(click.x());
        double my = toDesignY(click.y());
        int button = click.button();

        float centerX = this.width * 0.5f;
        float centerY = this.height * 0.5f;
        float expand = expandAnim.value();
        float dockY = centerY + 54 + expand * 74;

        if (button == 0 && my >= dockY && my <= dockY + 22) {
            if (mx >= centerX - 105 && mx <= centerX - 5) {
                if (this.minecraft != null) this.minecraft.setScreen(new WorkspaceModalScreen(this));
                return true;
            }
            if (mx >= centerX + 5 && mx <= centerX + 105) {
                if (this.minecraft != null) this.minecraft.setScreen(new HudEditorScreen(this));
                return true;
            }
        }

        Category[] cats = Category.values();
        float cardW = 68.0f;
        float cardH = 74.0f;
        float cardY = centerY - cardH * 0.5f;

        if (selectedCategory != null) {
            float panelW = 236.0f;
            float panelH = 236.0f;
            float px = centerX - panelW * 0.5f;
            float py = centerY - panelH * 0.5f;

            if (button == 0 && mx >= px + 10 && mx <= px + panelW - 10 && my >= py + 33 && my <= py + 51) {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new HudEditorScreen(this));
                }
                return true;
            }

            float listTop = py + 56;
            float listBottom = py + panelH - 10;
            float viewH = Math.max(10.0f, listBottom - listTop);
            float maxScroll = getMaxScroll(viewH);

            if (button == 0 && maxScroll > 0.0f && mx >= px + panelW - 15.0f && mx <= px + panelW - 4.0f
                && my >= listTop && my <= listBottom) {
                draggingScrollbar = true;
                updateScrollFromMouse(my);
                return true;
            }

            float rowWidth = maxScroll > 0.0f ? (panelW - 28.0f) : (panelW - 20.0f);
            if (my >= listTop && my <= listBottom) {
                float rowY = listTop - scrollOffset;
                for (GlassSlider slider : activeSliders) {
                    if (slider.mouseClicked(mx, my, button, px + 10, rowY, rowWidth, 24)) {
                        return true;
                    }
                    rowY += 28;
                }
                for (GlassToggle toggle : activeToggles) {
                    if (toggle.mouseClicked(mx, my, button, px + 10, rowY, rowWidth, 22)) {
                        return true;
                    }
                    rowY += 26;
                }
            }

            if (mx < px || mx > px + panelW || my < py || my > py + panelH) {
                for (int i = 0; i < cats.length; i++) {
                    if (cats[i] == selectedCategory) continue;
                    float cx = cardXAnims[i].value();
                    if (button == 0 && mx >= cx && mx <= cx + cardW && my >= cardY && my <= cardY + cardH) {
                        if (cats[i] == Category.STREAM) {
                            if (this.minecraft != null) this.minecraft.setScreen(new WorkspaceModalScreen(this));
                            return true;
                        }
                        selectCategoryWithAnimation(cats[i]);
                        return true;
                    }
                }
                selectedCategory = null;
                return true;
            }
        } else {
            for (int i = 0; i < cats.length; i++) {
                float cx = cardXAnims[i].value();
                if (button == 0 && mx >= cx && mx <= cx + cardW && my >= cardY && my <= cardY + cardH) {
                    if (cats[i] == Category.STREAM) {
                        if (this.minecraft != null) this.minecraft.setScreen(new WorkspaceModalScreen(this));
                        return true;
                    }
                    selectCategoryWithAnimation(cats[i]);
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        double dmx = toDesignX(click.x());
        double dmy = toDesignY(click.y());
        if (draggingScrollbar) {
            updateScrollFromMouse(dmy);
            return true;
        }
        if (selectedCategory != null) {
            float panelW = 236.0f;
            float px = this.width * 0.5f - panelW * 0.5f;
            float viewH = 170.0f;
            float rowWidth = getMaxScroll(viewH) > 0.0f ? (panelW - 28.0f) : (panelW - 20.0f);
            for (GlassSlider slider : activeSliders) {
                if (slider.mouseDragged(dmx, px + 10, rowWidth)) {
                    return true;
                }
            }
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        draggingScrollbar = false;
        for (GlassSlider slider : activeSliders) {
            slider.mouseReleased();
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (selectedCategory != null) {
            float maxScroll = getMaxScroll(170.0f);
            scrollOffset = Math.max(0.0f, Math.min(maxScroll, scrollOffset - (float) verticalAmount * 18.0f));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == 256) {
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
    public boolean isPauseScreen() {
        return false;
    }
}
