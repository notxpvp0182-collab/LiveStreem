package dev.ahmed.liquidglass;

import dev.ahmed.liquidglass.config.ConfigManager;
import dev.ahmed.liquidglass.hud.HudManager;
import dev.ahmed.liquidglass.hud.elements.LiveChatElement;
import dev.ahmed.liquidglass.stream.StreamManager;
import dev.ahmed.liquidglass.ui.clickgui.LiquidClickGuiScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public final class LiquidGlassClient implements ClientModInitializer {
    public static final String MOD_ID = "liquidglass";
    private static KeyBinding openGuiKey;
    private static KeyBinding chatUpKey;
    private static KeyBinding chatDownKey;
    private static int upHoldTicks = 0;
    private static int downHoldTicks = 0;

    @Override
    public void onInitializeClient() {
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "general"));
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.liquidglass.open_gui",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            category
        ));
        chatUpKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.liquidglass.chat_up",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_UP,
            category
        ));
        chatDownKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.liquidglass.chat_down",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_DOWN,
            category
        ));

        HudManager.get().initDefaults();
        ConfigManager.get().load();

        ConfigManager cfg = ConfigManager.get();
        if (cfg.autoConnect() && !cfg.youtubeUrl().isEmpty()) {
            StreamManager.get().connect(cfg.youtubeUrl());
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new LiquidClickGuiScreen());
                }
            }

            if (HudManager.get().isLiveChatEnabled() && client.getWindow() != null) {
                long handle = client.getWindow().getHandle();
                boolean upPressed = chatUpKey.isPressed()
                    || (client.currentScreen == null && GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_UP) == GLFW.GLFW_PRESS);
                boolean downPressed = chatDownKey.isPressed()
                    || (client.currentScreen == null && GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_DOWN) == GLFW.GLFW_PRESS);

                if (upPressed) {
                    upHoldTicks++;
                    if (upHoldTicks == 1) {
                        LiveChatElement.scrollUp(2);
                    } else if (upHoldTicks >= 40 && upHoldTicks % 2 == 0) {
                        LiveChatElement.scrollUp(1);
                    }
                } else {
                    upHoldTicks = 0;
                }

                if (downPressed) {
                    downHoldTicks++;
                    if (downHoldTicks == 1) {
                        LiveChatElement.scrollDown(2);
                    } else if (downHoldTicks == 40) {
                        LiveChatElement.jumpToLiveBottom();
                    }
                } else {
                    downHoldTicks = 0;
                }
            } else {
                upHoldTicks = 0;
                downHoldTicks = 0;
            }
        });

        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, Identifier.of(MOD_ID, "overlay"),
            (drawContext, tickCounter) -> HudManager.get().renderHud(drawContext)
        );

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ConfigManager.get().save();
            StreamManager.get().disconnect();
        });
    }
}
