package dev.ahmed.liquidglass.ui.components;

import dev.ahmed.liquidglass.ui.animation.Animation;
import dev.ahmed.liquidglass.ui.render.UIRenderer;
import dev.ahmed.liquidglass.ui.theme.ThemeManager;
import dev.ahmed.liquidglass.ui.theme.ThemeTokens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;

public final class GlassTextField {
    private final String placeholder;
    private String text = "";
    private boolean focused = false;
    private int cursorIndex = 0;
    private int selectionAnchor = 0;
    private boolean draggingSelection = false;
    private boolean masked = false;
    private float lastBoxX = 0.0f;
    private float lastBoxW = 200.0f;
    private float lastScrollX = 0.0f;
    private long lastClickMs = 0L;
    private final Animation focusAnim = new Animation(0.0f, 0.16f, Animation.Easing.EASE_OUT_CUBIC);

    public GlassTextField(String placeholder) {
        this.placeholder = placeholder;
    }

    private boolean hasSelection() {
        return selectionAnchor != cursorIndex;
    }

    private int selMin() {
        return Math.max(0, Math.min(text.length(), Math.min(cursorIndex, selectionAnchor)));
    }

    private int selMax() {
        return Math.max(0, Math.min(text.length(), Math.max(cursorIndex, selectionAnchor)));
    }

    private void deleteSelection() {
        int a = selMin();
        int b = selMax();
        text = text.substring(0, a) + text.substring(b);
        cursorIndex = a;
        selectionAnchor = a;
    }

    public void extractRenderState(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float mouseX, float mouseY, float dt) {
        this.lastBoxX = x;
        this.lastBoxW = w;
        ThemeTokens t = ThemeManager.get().tokens();
        focusAnim.animateTo(focused ? 1.0f : 0.0f);
        float fc = focusAnim.update(dt);

        UIRenderer.drawRoundedRect(ctx, x, y, w, h, t.radiusSmall(),
            UIRenderer.lerpColor(t.bgElevated(), t.bgGlassStrong(), fc));
        UIRenderer.drawRoundedBorder(ctx, x, y, w, h, t.radiusSmall(), 1.0f,
            UIRenderer.lerpColor(t.borderBase(), t.borderActive(), fc));

        float rightPad = text.isEmpty() ? 6.0f : 20.0f;
        ctx.enableScissor(Math.round(x + 4), Math.round(y + 2), Math.round(x + w - rightPad), Math.round(y + h - 2));
        Font tr = Minecraft.getInstance().font;

        if (text.isEmpty() && !focused) {
            lastScrollX = 0.0f;
            UIRenderer.drawText(ctx, placeholder, x + 8, y + (h - 8) * 0.5f, t.textMuted(), 1.0f, false, false);
        } else {
            String shown = masked ? "\u2022".repeat(text.length()) : text;
            cursorIndex = Math.max(0, Math.min(shown.length(), cursorIndex));
            selectionAnchor = Math.max(0, Math.min(shown.length(), selectionAnchor));

            int cursorPx = tr.width(shown.substring(0, cursorIndex));
            float maxVisibleW = Math.max(30.0f, w - rightPad - 10.0f);
            if (cursorPx - lastScrollX > maxVisibleW) {
                lastScrollX = cursorPx - maxVisibleW;
            } else if (cursorPx - lastScrollX < 0) {
                lastScrollX = cursorPx;
            }
            int totalW = tr.width(shown);
            if (totalW <= maxVisibleW) lastScrollX = 0.0f;

            // Blue selection highlight bar
            if (focused && hasSelection()) {
                int a = Math.min(shown.length(), selMin());
                int b = Math.min(shown.length(), selMax());
                float sx1 = x + 8 - lastScrollX + tr.width(shown.substring(0, a));
                float sx2 = x + 8 - lastScrollX + tr.width(shown.substring(0, b));
                ctx.fill(Math.round(sx1), Math.round(y + 4), Math.round(sx2), Math.round(y + h - 4), 0x8838BDF8);
            }

            UIRenderer.drawText(ctx, shown, x + 8 - lastScrollX, y + (h - 8) * 0.5f, t.textPrimary(), 1.0f, false, false);

            if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
                float cx = x + 8 - lastScrollX + cursorPx;
                ctx.fill(Math.round(cx), Math.round(y + 4), Math.round(cx + 1), Math.round(y + h - 4), t.accentPrimary());
            }
        }
        ctx.disableScissor();

        // One-click Clear (×) button on right side when text is not empty
        if (!text.isEmpty()) {
            float clearX = x + w - 16.0f;
            float clearY = y + (h - 12.0f) * 0.5f;
            boolean clearHover = mouseX >= clearX - 2 && mouseX <= x + w - 2 && mouseY >= y + 2 && mouseY <= y + h - 2;
            UIRenderer.drawText(ctx, "x", clearX + 3, clearY + 2,
                clearHover ? t.statusError() : t.textMuted(), 1.0f, false, false);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, float x, float y, float w, float h) {
        boolean inside = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        if (button == 0) {
            this.focused = inside;
            if (!inside) {
                this.draggingSelection = false;
                return false;
            }
            // Check Clear (x) button click
            if (!text.isEmpty() && mouseX >= x + w - 18.0f) {
                text = "";
                cursorIndex = 0;
                selectionAnchor = 0;
                lastScrollX = 0.0f;
                draggingSelection = false;
                return true;
            }
            long now = System.currentTimeMillis();
            if (now - lastClickMs < 320L) {
                // Double-click selects all text
                selectionAnchor = 0;
                cursorIndex = text.length();
                draggingSelection = false;
            } else {
                int idx = charIndexAtMouse(mouseX);
                cursorIndex = idx;
                selectionAnchor = idx;
                draggingSelection = true;
            }
            lastClickMs = now;
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX) {
        if (focused && draggingSelection) {
            cursorIndex = charIndexAtMouse(mouseX);
            return true;
        }
        return false;
    }

    public void mouseReleased() {
        draggingSelection = false;
    }

    private int charIndexAtMouse(double mouseX) {
        String shown = masked ? "\u2022".repeat(text.length()) : text;
        if (shown.isEmpty()) return 0;
        float relX = (float) mouseX - (lastBoxX + 8.0f) + lastScrollX;
        if (relX <= 0) return 0;
        Font tr = Minecraft.getInstance().font;
        for (int i = 1; i <= shown.length(); i++) {
            int w = tr.width(shown.substring(0, i));
            if (w >= relX) {
                int prevW = tr.width(shown.substring(0, i - 1));
                return (relX - prevW < w - relX) ? (i - 1) : i;
            }
        }
        return shown.length();
    }

    public boolean keyPressed(KeyEvent input) {
        if (!focused) return false;
        int key = input.key();
        int mods = input.modifiers();
        boolean ctrl = (mods & (2 | 8)) != 0;
        boolean shift = (mods & 1) != 0;

        // Ctrl+A: Select All
        if (key == 65 && ctrl) {
            selectionAnchor = 0;
            cursorIndex = text.length();
            return true;
        }
        // Ctrl+C: Copy
        if (key == 67 && ctrl) {
            if (hasSelection() && !masked) {
                Minecraft.getInstance().keyboardHandler.setClipboard(text.substring(selMin(), selMax()));
            }
            return true;
        }
        // Ctrl+X: Cut
        if (key == 88 && ctrl) {
            if (hasSelection()) {
                if (!masked) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(text.substring(selMin(), selMax()));
                }
                deleteSelection();
            }
            return true;
        }
        // Ctrl+V: Paste (replaces selection if any)
        if (key == 86 && ctrl) {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clip != null && !clip.isEmpty()) {
                insert(clip.replace("\n", "").replace("\r", ""));
            }
            return true;
        }
        if (key == 259) {
            if (hasSelection()) {
                deleteSelection();
            } else if (ctrl && cursorIndex > 0) {
                text = text.substring(cursorIndex);
                cursorIndex = 0;
                selectionAnchor = 0;
            } else if (!text.isEmpty() && cursorIndex > 0) {
                text = text.substring(0, cursorIndex - 1) + text.substring(cursorIndex);
                cursorIndex--;
                selectionAnchor = cursorIndex;
            }
            return true;
        }
        if (key == 261) {
            if (hasSelection()) {
                deleteSelection();
            } else if (cursorIndex < text.length()) {
                text = text.substring(0, cursorIndex) + text.substring(cursorIndex + 1);
            }
            return true;
        }
        if (key == 263) {
            if (hasSelection() && !shift) {
                cursorIndex = selMin();
                selectionAnchor = cursorIndex;
            } else if (cursorIndex > 0) {
                cursorIndex--;
                if (!shift) selectionAnchor = cursorIndex;
            }
            return true;
        }
        if (key == 262) {
            if (hasSelection() && !shift) {
                cursorIndex = selMax();
                selectionAnchor = cursorIndex;
            } else if (cursorIndex < text.length()) {
                cursorIndex++;
                if (!shift) selectionAnchor = cursorIndex;
            }
            return true;
        }
        if (key == 268) {
            cursorIndex = 0;
            if (!shift) selectionAnchor = 0;
            return true;
        }
        if (key == 269) {
            cursorIndex = text.length();
            if (!shift) selectionAnchor = cursorIndex;
            return true;
        }
        return false;
    }

    public boolean charTyped(CharacterEvent input) {
        if (!focused) return false;
        String typed = input.codepointAsString();
        if (typed != null && !typed.isEmpty() && input.codepoint() >= 32) {
            insert(typed);
            return true;
        }
        return false;
    }

    private void insert(String str) {
        if (hasSelection()) {
            deleteSelection();
        }
        cursorIndex = Math.max(0, Math.min(text.length(), cursorIndex));
        text = text.substring(0, cursorIndex) + str + text.substring(cursorIndex);
        cursorIndex += str.length();
        selectionAnchor = cursorIndex;
    }

    public String getText() { return text; }
    public void setText(String text) {
        this.text = text != null ? text : "";
        this.cursorIndex = this.text.length();
        this.selectionAnchor = this.cursorIndex;
    }
    public GlassTextField masked(boolean masked) {
        this.masked = masked;
        return this;
    }

    public boolean isFocused() { return focused; }
}
