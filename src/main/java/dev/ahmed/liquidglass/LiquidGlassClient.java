package dev.ahmed.liquidglass;

import dev.ahmed.liquidglass.config.ConfigManager;
import dev.ahmed.liquidglass.hud.HudManager;
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

    @Override
    public void onInitializeClient() {
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "general"));
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.liquidglass.open_gui",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            category
        ));

        HudManager.get().initDefaults();
        ConfigManager.get().load();

        ConfigManager cfg = ConfigManager.get();
        if (cfg.autoConnect() && !cfg.youtubeUrl().isEmpty()) {
            StreamManager.get().connect(cfg.youtubeUrl(), cfg.youtubeApiKey());
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new LiquidClickGuiScreen());
                }
            }
        });

        HudElementRegistry.attachElementAfter(
            VanillaHudElements.MISC_OVERLAYS,
            Identifier.of(MOD_ID, "overlay"),
            (drawContext, tickCounter) -> HudManager.get().renderHud(drawContext)
        );

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ConfigManager.get().save();
            StreamManager.get().disconnect();
        });
    }
}
