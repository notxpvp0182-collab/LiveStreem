package dev.ahmed.liquidglass.ui.components;

import dev.ahmed.liquidglass.ui.animation.Animation;
import dev.ahmed.liquidglass.ui.render.UIRenderer;
import dev.ahmed.liquidglass.ui.theme.ThemeManager;
import dev.ahmed.liquidglass.ui.theme.ThemeTokens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

public final class GlassTextField {
    private final String placeholder;
    private String text = "";
    private boolean focused = false;
    private int cursorIndex = 0;
    private boolean masked = false;
    private final Animation focusAnim = new Animation(0.0f, 0.16f, Animation.Easing.EASE_OUT_CUBIC);

    public GlassTextField(String placeholder) {
        this.placeholder = placeholder;
    }

    public void render(DrawContext ctx, float x, float y, float w, float h, float mouseX, float mouseY, float dt) {
        ThemeTokens t = ThemeManager.get().tokens();
        focusAnim.animateTo(focused ? 1.0f : 0.0f);
        float fc = focusAnim.update(dt);

        UIRenderer.drawRoundedRect(ctx, x, y, w, h, t.radiusSmall(),
            UIRenderer.lerpColor(t.bgElevated(), t.bgGlassStrong(), fc));
        UIRenderer.drawRoundedBorder(ctx, x, y, w, h, t.radiusSmall(), 1.0f,
            UIRenderer.lerpColor(t.borderBase(), t.borderActive(), fc));

        ctx.enableScissor(Math.round(x + 4), Math.round(y + 2), Math.round(x + w - 4), Math.round(y + h - 2));
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        if (text.isEmpty() && !focused) {
            UIRenderer.drawText(ctx, placeholder, x + 8, y + (h - 8) * 0.5f, t.textMuted(), 1.0f, false, false);
        } else {
            String shown = masked ? "\u2022".repeat(text.length()) : text;
            int textW = tr.getWidth(shown);
            float maxVisibleW = w - 16;
            float scrollX = textW > maxVisibleW ? (textW - maxVisibleW) : 0.0f;
            UIRenderer.drawText(ctx, shown, x + 8 - scrollX, y + (h - 8) * 0.5f, t.textPrimary(), 1.0f, false, false);

            if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
                String beforeCursor = shown.substring(0, Math.min(cursorIndex, shown.length()));
                float cx = x + 8 - scrollX + tr.getWidth(beforeCursor);
                ctx.fill(Math.round(cx), Math.round(y + 4), Math.round(cx + 1), Math.round(y + h - 4), t.accentPrimary());
            }
        }
        ctx.disableScissor();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, float x, float y, float w, float h) {
        boolean inside = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        if (button == 0) {
            this.focused = inside;
            if (inside) {
                this.cursorIndex = text.length();
            }
            return inside;
        }
        return false;
    }

    public boolean keyPressed(KeyInput input) {
        if (!focused) return false;
        int key = input.key();
        int mods = input.modifiers();

        // Ctrl+V / Cmd+V Paste support
        if (key == GLFW.GLFW_KEY_V && (mods & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0) {
            String clip = MinecraftClient.getInstance().keyboard.getClipboard();
            if (clip != null && !clip.isEmpty()) {
                insert(clip.replace("\n", "").replace("\r", ""));
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE && !text.isEmpty() && cursorIndex > 0) {
            text = text.substring(0, cursorIndex - 1) + text.substring(cursorIndex);
            cursorIndex--;
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE && cursorIndex < text.length()) {
            text = text.substring(0, cursorIndex) + text.substring(cursorIndex + 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT && cursorIndex > 0) {
            cursorIndex--;
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT && cursorIndex < text.length()) {
            cursorIndex++;
            return true;
        }
        return false;
    }

    public boolean charTyped(CharInput input) {
        if (!focused) return false;
        String typed = input.asString();
        if (typed != null && !typed.isEmpty() && input.codepoint() >= 32) {
            insert(typed);
            return true;
        }
        return false;
    }

    private void insert(String str) {
        text = text.substring(0, cursorIndex) + str + text.substring(cursorIndex);
        cursorIndex += str.length();
    }

    public String getText() { return text; }
    public void setText(String text) {
        this.text = text != null ? text : "";
        this.cursorIndex = this.text.length();
    }
    public GlassTextField masked(boolean masked) {
        this.masked = masked;
        return this;
    }

    public boolean isFocused() { return focused; }
}
