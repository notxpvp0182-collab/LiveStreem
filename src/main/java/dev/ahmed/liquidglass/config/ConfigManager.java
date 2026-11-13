package dev.ahmed.liquidglass.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.ahmed.liquidglass.hud.ElementStyle;
import dev.ahmed.liquidglass.hud.HudElement;
import dev.ahmed.liquidglass.hud.HudManager;
import dev.ahmed.liquidglass.hud.elements.LiveChatElement;
import dev.ahmed.liquidglass.stream.StreamManager;
import dev.ahmed.liquidglass.ui.theme.GlassQuality;
import dev.ahmed.liquidglass.ui.theme.ThemeManager;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

public final class ConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("LiquidGlass/Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ConfigManager INSTANCE = new ConfigManager();

    public static final class SavedHudEntry {
        public boolean enabled;
        public boolean locked;
        public float x, y, width, height;
        public ElementStyle style;
    }

    public static final class ChatSettings {
        public int maxVisibleMessages = 14;
        public boolean showYouTubeIcon = true;
        public boolean showUsername = true;
        public boolean showTimestamp = false;
        public boolean enableEmojiFallback = true;
        public float lineSpacing = 12.5f;
        public boolean coloredUsernames = true;
        public boolean coloredMessages = false;
        public boolean rgbMode = false;
        public boolean newestAtTop = false;
        public boolean chatShadow = false;
        public boolean textShadow = true;
        public int chatDelayMs = 0;
    }

    public static final class ConfigPayload {
        public String themeId = "dark_liquid_glass";
        public String glassQuality = "ULTRA";
        public String activeHudProfile = "Default";
        public String youtubeUrl = "";
        public String youtubeApiKey = "";
        public boolean autoConnect = false;
        public int streamRefreshSec = 25;
        public int maxChatHistory = 500;
        public ChatSettings chat = new ChatSettings();
        public Map<String, SavedHudEntry> hudElements = new HashMap<>();
    }

    private final Path configFile = FabricLoader.getInstance().getConfigDir().resolve("livestream.json");
    private ConfigPayload current = new ConfigPayload();

    private ConfigManager() {}

    public static ConfigManager get() { return INSTANCE; }

    public synchronized String youtubeUrl() { return current.youtubeUrl; }
    public synchronized String youtubeApiKey() { return current.youtubeApiKey; }
    public synchronized boolean autoConnect() { return current.autoConnect; }

    public synchronized void setStreamSettings(String url, String apiKey) {
        current.youtubeUrl = url == null ? "" : url.trim();
        current.youtubeApiKey = apiKey == null ? "" : apiKey.trim();
    }

    public synchronized void setAutoConnect(boolean value) { current.autoConnect = value; }

    public synchronized void load() {
        if (!Files.exists(configFile)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(configFile)) {
            ConfigPayload data = GSON.fromJson(reader, ConfigPayload.class);
            if (data == null) return;
            if (data.hudElements == null) data.hudElements = new HashMap<>();
            if (data.chat == null) data.chat = new ChatSettings();
            current = data;

            ThemeManager.get().setActiveTheme(data.themeId);
            try {
                ThemeManager.get().setQuality(GlassQuality.valueOf(data.glassQuality));
            } catch (IllegalArgumentException | NullPointerException ignored) {
                ThemeManager.get().setQuality(GlassQuality.ULTRA);
            }
            StreamManager.get().setRefreshIntervalSec(data.streamRefreshSec);
            StreamManager.get().setMaxChatHistory(data.maxChatHistory);
            StreamManager.get().setChatDelayMs(data.chat.chatDelayMs);
            HudManager.get().setActiveProfileName(data.activeHudProfile);

            for (HudElement el : HudManager.get().elements()) {
                SavedHudEntry saved = data.hudElements.get(el.id());
                if (saved != null) {
                    el.setEnabled(saved.enabled);
                    el.setLocked(false);
                    el.setPosition(saved.x, saved.y);
                    el.resize(saved.width, saved.height);
                    el.setLocked(saved.locked);
                    if (saved.style != null) el.style().copyFrom(saved.style);
                }
                if (el instanceof LiveChatElement chat) {
                    chat.maxVisibleMessages = Math.max(1, Math.min(40, data.chat.maxVisibleMessages));
                    chat.showYouTubeIcon = data.chat.showYouTubeIcon;
                    chat.showUsername = data.chat.showUsername;
                    chat.showTimestamp = data.chat.showTimestamp;
                    chat.enableEmojiFallback = data.chat.enableEmojiFallback;
                    chat.lineSpacing = Math.max(8.0f, Math.min(30.0f, data.chat.lineSpacing));
                    chat.coloredUsernames = data.chat.coloredUsernames;
                    chat.coloredMessages = data.chat.coloredMessages;
                    chat.rgbMode = data.chat.rgbMode;
                    chat.newestAtTop = data.chat.newestAtTop;
                    chat.chatShadow = data.chat.chatShadow;
                    chat.textShadow = data.chat.textShadow;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read livestream.json; keeping defaults: {}", e.getMessage());
        }
    }

    public synchronized void save() {
        try {
            current.themeId = ThemeManager.get().tokens().id();
            current.glassQuality = ThemeManager.get().quality().name();
            current.activeHudProfile = HudManager.get().activeProfile();
            current.streamRefreshSec = StreamManager.get().refreshIntervalSec();
            current.maxChatHistory = StreamManager.get().maxChatHistory();
            current.chat.chatDelayMs = StreamManager.get().chatDelayMs();
            current.hudElements = new HashMap<>();
            for (HudElement el : HudManager.get().elements()) {
                SavedHudEntry entry = new SavedHudEntry();
                entry.enabled = el.isEnabled();
                entry.locked = el.isLocked();
                entry.x = el.x();
                entry.y = el.y();
                entry.width = el.width();
                entry.height = el.height();
                entry.style = el.style();
                current.hudElements.put(el.id(), entry);
                if (el instanceof LiveChatElement chat) {
                    current.chat.maxVisibleMessages = chat.maxVisibleMessages;
                    current.chat.showYouTubeIcon = chat.showYouTubeIcon;
                    current.chat.showUsername = chat.showUsername;
                    current.chat.showTimestamp = chat.showTimestamp;
                    current.chat.enableEmojiFallback = chat.enableEmojiFallback;
                    current.chat.lineSpacing = chat.lineSpacing;
                    current.chat.coloredUsernames = chat.coloredUsernames;
                    current.chat.coloredMessages = chat.coloredMessages;
                    current.chat.rgbMode = chat.rgbMode;
                    current.chat.newestAtTop = chat.newestAtTop;
                    current.chat.chatShadow = chat.chatShadow;
                    current.chat.textShadow = chat.textShadow;
                }
            }
            Files.createDirectories(configFile.getParent());
            Path temp = configFile.resolveSibling("livestream.json.tmp");
            try (Writer writer = Files.newBufferedWriter(temp)) {
                GSON.toJson(current, writer);
            }
            try {
                Files.move(temp, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, configFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to save livestream.json: {}", e.getMessage());
        }
    }
}
