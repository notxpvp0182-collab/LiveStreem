package dev.ahmed.liquidglass;

import dev.ahmed.liquidglass.config.ConfigManager;
import dev.ahmed.liquidglass.hud.HudManager;
import dev.ahmed.liquidglass.hud.elements.LiveChatElement;
import dev.ahmed.liquidglass.stream.StreamManager;
import dev.ahmed.liquidglass.ui.clickgui.LiquidClickGuiScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier;

public final class LiquidGlassClient implements ClientModInitializer {
    public static final String MOD_ID = "liquidglass";
    private static KeyMapping openGuiKey;
    private static KeyMapping chatUpKey;
    private static KeyMapping chatDownKey;
    private static int upHoldTicks = 0;
    private static int downHoldTicks = 0;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "general"));
        openGuiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.liquidglass.open_gui",
            InputConstants.Type.KEYSYM,
            344,
            category
        ));
        chatUpKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.liquidglass.chat_up",
            InputConstants.Type.KEYSYM,
            265,
            category
        ));
        chatDownKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.liquidglass.chat_down",
            InputConstants.Type.KEYSYM,
            264,
            category
        ));

        HudManager.get().initDefaults();
        ConfigManager.get().load();

        ConfigManager cfg = ConfigManager.get();
        if (cfg.autoConnect() && !cfg.youtubeUrl().isEmpty()) {
            StreamManager.get().connect(cfg.youtubeUrl());
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new LiquidClickGuiScreen());
                }
            }

            if (HudManager.get().isLiveChatEnabled()) {
                boolean upPressed = chatUpKey.isDown();
                boolean downPressed = chatDownKey.isDown();

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

        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, Identifier.fromNamespaceAndPath(MOD_ID, "overlay"),
            (drawContext, tickCounter) -> HudManager.get().renderHud(drawContext)
        );

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ConfigManager.get().save();
            StreamManager.get().disconnect();
        });
    }
}
