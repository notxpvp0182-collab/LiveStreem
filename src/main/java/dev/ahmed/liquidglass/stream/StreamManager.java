package dev.ahmed.liquidglass.stream;

import dev.ahmed.liquidglass.stream.StreamDataProvider.ChannelInfo;
import dev.ahmed.liquidglass.stream.StreamDataProvider.ChatBatch;
import dev.ahmed.liquidglass.stream.StreamDataProvider.LiveStatus;
import dev.ahmed.liquidglass.stream.StreamDataProvider.VideoInfo;
import dev.ahmed.liquidglass.youtube.YouTubeDataApiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StreamManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("LiquidGlass/Stream");

    public enum ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, LIVE, OFFLINE, RECONNECTING, ERROR, QUOTA_LIMITED
    }

    public record StreamSnapshot(
        ConnectionState state,
        String streamId,
        String channelName,
        String channelHandle,
        Long subscriberCount,
        String streamTitle,
        Long viewerCount,
        Long likeCount,
        long streamStartEpochMs,
        String statusMessage
    ) {
        public static StreamSnapshot disconnected() {
            return new StreamSnapshot(ConnectionState.DISCONNECTED, "", "--", "--", null, "--", null, null, 0L, "Not connected");
        }
    }

    public record ChatMessage(
        String id,
        String author,
        String content,
        long timestampMs,
        long receivedNanoTime,
        boolean owner,
        boolean moderator,
        boolean member
    ) {}

    private static final Pattern YT_URL_PATTERN = Pattern.compile(
        "(?:youtube\\.com/(?:watch\\?(?:[^#\\s]*&)?v=|live/|embed/)|youtu\\.be/)([A-Za-z0-9_-]{11})");
    private static final Pattern BARE_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");

    private static final int MAX_RETRIES = 8;
    private static final long MIN_CHAT_POLL_MS = 2_500L;
    private static final int SEEN_IDS_LIMIT = 800;

    private static final StreamManager INSTANCE = new StreamManager();

    private final AtomicReference<StreamSnapshot> snapshotRef = new AtomicReference<>(StreamSnapshot.disconnected());
    private final Deque<ChatMessage> chatBuffer = new ConcurrentLinkedDeque<>();
    private final Deque<ChatMessage> pendingDelayQueue = new ConcurrentLinkedDeque<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "LiquidGlass-StreamWorker");
        t.setDaemon(true);
        return t;
    });

    private volatile String lastUrl = "";
    private volatile int refreshIntervalSec = 25;
    private volatile int maxChatHistory = 500;
    private volatile int chatDelayMs = 0; // 0 = Instant YouTube real-time speed
    private volatile long lastPacedReleaseMs = 0L;
    private volatile int totalChatReceived = 0;
    private volatile long lastSuccessEpochMs = 0L;
    private volatile Session session;

    private StreamManager() {}

    public static StreamManager get() { return INSTANCE; }

    public static Optional<String> extractVideoId(String rawUrlOrId) {
        if (rawUrlOrId == null) return Optional.empty();
        String trimmed = rawUrlOrId.trim();
        if (BARE_ID.matcher(trimmed).matches()) return Optional.of(trimmed);
        Matcher m = YT_URL_PATTERN.matcher(trimmed);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    public synchronized void connect(String rawUrl) {
        connect(rawUrl, "");
    }

    public synchronized void connect(String rawUrl, String unusedKey) {
        this.lastUrl = rawUrl == null ? "" : rawUrl.trim();
        stopSession();
        chatBuffer.clear();
        pendingDelayQueue.clear();

        Optional<String> id = extractVideoId(lastUrl);
        if (id.isEmpty()) {
            snapshotRef.set(error("", "Invalid YouTube link. Use a watch?v=, /live/ or youtu.be link."));
            return;
        }
        Session s = new Session(id.get(), new YouTubeDataApiProvider());
        this.session = s;
        snapshotRef.set(new StreamSnapshot(ConnectionState.CONNECTING, id.get(), "--", "--", null, "--", null, null, 0L,
            "Connecting to YouTube stream (Keyless Mode)..."));
        s.schedule(s::refreshVideo, 0L);
    }

    public synchronized void disconnect() {
        stopSession();
        chatBuffer.clear();
        pendingDelayQueue.clear();
        snapshotRef.set(StreamSnapshot.disconnected());
    }

    public synchronized void refresh() {
        if (!lastUrl.isEmpty()) connect(lastUrl);
    }

    private void stopSession() {
        Session s = this.session;
        this.session = null;
        if (s != null) s.cancel();
    }

    private static StreamSnapshot error(String id, String message) {
        return new StreamSnapshot(ConnectionState.ERROR, id, "--", "--", null, "--", null, null, 0L, message);
    }

    private void enqueueIncomingChat(ChatMessage msg) {
        if (chatDelayMs <= 0) {
            commitToChatBuffer(new ChatMessage(
                msg.id(), msg.author(), msg.content(), msg.timestampMs(),
                System.nanoTime(), msg.owner(), msg.moderator(), msg.member()
            ));
        } else {
            while (pendingDelayQueue.size() >= 300) pendingDelayQueue.pollFirst();
            pendingDelayQueue.offerLast(msg);
        }
    }

    public void tickChatPacing() {
        if (pendingDelayQueue.isEmpty()) return;
        if (chatDelayMs <= 0) {
            ChatMessage m;
            while ((m = pendingDelayQueue.pollFirst()) != null) {
                commitToChatBuffer(new ChatMessage(
                    m.id(), m.author(), m.content(), m.timestampMs(),
                    System.nanoTime(), m.owner(), m.moderator(), m.member()
                ));
            }
            return;
        }
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastPacedReleaseMs >= chatDelayMs) {
            ChatMessage m = pendingDelayQueue.pollFirst();
            if (m != null) {
                lastPacedReleaseMs = nowMs;
                commitToChatBuffer(new ChatMessage(
                    m.id(), m.author(), m.content(), m.timestampMs(),
                    System.nanoTime(), m.owner(), m.moderator(), m.member()
                ));
            }
        }
    }

    private void commitToChatBuffer(ChatMessage msg) {
        int cap = Math.max(10, maxChatHistory);
        while (chatBuffer.size() >= cap) chatBuffer.pollFirst();
        chatBuffer.offerLast(msg);
        totalChatReceived++;
    }

    public StreamSnapshot snapshot() { return snapshotRef.get(); }
    public Deque<ChatMessage> chatMessages() { return chatBuffer; }
    public String lastUrl() { return lastUrl; }
    public int maxChatHistory() { return maxChatHistory; }
    public int totalChatReceived() { return totalChatReceived; }
    public long lastSuccessEpochMs() { return lastSuccessEpochMs; }
    public void setMaxChatHistory(int value) { this.maxChatHistory = Math.max(10, Math.min(1000, value)); }
    public int refreshIntervalSec() { return refreshIntervalSec; }
    public void setRefreshIntervalSec(int value) { this.refreshIntervalSec = Math.max(15, Math.min(300, value)); }
    public int chatDelayMs() { return chatDelayMs; }
    public void setChatDelayMs(int delayMs) { this.chatDelayMs = Math.max(0, Math.min(3000, delayMs)); }

    private final class Session {
        private final String videoId;
        private final StreamDataProvider provider;
        private final Set<ScheduledFuture<?>> futures = ConcurrentHashMap.newKeySet();
        private final Set<String> seenIds = new HashSet<>();
        private final Deque<String> seenOrder = new ArrayDeque<>();
        private volatile boolean active = true;

        private VideoInfo video;
        private ChannelInfo channel;
        private int failures = 0;
        private int chatFailures = 0;
        private String chatId;
        private String pageToken;
        private boolean chatRunning = false;
        private String chatNote = "";

        Session(String videoId, StreamDataProvider provider) {
            this.videoId = videoId;
            this.provider = provider;
        }

        void cancel() {
            active = false;
            futures.forEach(f -> f.cancel(false));
            futures.clear();
        }

        void schedule(Runnable task, long delayMs) {
            if (!active) return;
            futures.removeIf(Future::isDone);
            futures.add(executor.schedule(() -> {
                if (!active) return;
                try {
                    task.run();
                } catch (Throwable t) {
                    LOGGER.error("Unexpected stream worker failure", t);
                    if (active) publishError("Internal error: " + t.getClass().getSimpleName());
                }
            }, delayMs, TimeUnit.MILLISECONDS));
        }

        void refreshVideo() {
            try {
                video = provider.fetchVideo(videoId);
                channel = provider.fetchChannel(video.channelId());
                failures = 0;
                lastSuccessEpochMs = System.currentTimeMillis();
            } catch (ProviderException e) {
                handleFailure(e, this::refreshVideo);
                return;
            }

            switch (video.status()) {
                case LIVE -> {
                    publish(ConnectionState.LIVE, "Connected (Keyless Free Mode)");
                    if (video.activeLiveChatId() != null && chatId == null) {
                        chatId = video.activeLiveChatId();
                        chatNote = "";
                    }
                    if (chatId != null && !chatRunning) {
                        chatRunning = true;
                        chatFailures = 0;
                        schedule(this::pollChat, 0L);
                    } else if (chatId == null) {
                        chatNote = "no live chat";
                        publish(ConnectionState.LIVE, "Live");
                    }
                    schedule(this::refreshVideo, refreshIntervalSec * 1000L);
                }
                case UPCOMING -> {
                    publish(ConnectionState.OFFLINE, "Stream has not started yet");
                    schedule(this::refreshVideo, Math.max(30, refreshIntervalSec) * 1000L);
                }
                case ENDED -> publish(ConnectionState.OFFLINE, "Stream has ended");
                case NOT_LIVE -> publish(ConnectionState.OFFLINE, "This video is not a live stream");
            }
        }

        void pollChat() {
            if (chatId == null) {
                chatRunning = false;
                return;
            }
            try {
                ChatBatch batch = provider.fetchChat(chatId, pageToken);
                chatFailures = 0;
                pageToken = batch.nextPageToken();
                lastSuccessEpochMs = System.currentTimeMillis();
                for (ChatMessage msg : batch.messages()) {
                    if (!seenIds.add(msg.id())) continue;
                    seenOrder.addLast(msg.id());
                    if (seenOrder.size() > SEEN_IDS_LIMIT) seenIds.remove(seenOrder.pollFirst());
                    enqueueIncomingChat(msg);
                }
                if (!chatNote.isEmpty()) {
                    chatNote = "";
                    publish(ConnectionState.LIVE, "Connected (Keyless Free Mode)");
                }
                schedule(this::pollChat, Math.max(MIN_CHAT_POLL_MS, batch.pollIntervalMs()));
            } catch (ProviderException e) {
                handleChatFailure(e);
            }
        }

        private void handleChatFailure(ProviderException e) {
            if (e.isRetryable() && ++chatFailures <= MAX_RETRIES) {
                long delay = Math.min(30L, 1L << Math.min(5, chatFailures));
                chatNote = "chat retrying in " + delay + "s";
                publish(ConnectionState.LIVE, "Live");
                schedule(this::pollChat, delay * 1000L);
            } else {
                chatRunning = false;
                chatId = null;
                pageToken = null;
                schedule(this::refreshVideo, 3_000L);
            }
        }

        private void handleFailure(ProviderException e, Runnable retryTask) {
            if (e.kind() == ProviderException.Kind.CANCELLED) return;
            if (e.isRetryable() && ++failures <= MAX_RETRIES) {
                long delay = Math.min(60L, 1L << Math.min(6, failures));
                publish(ConnectionState.RECONNECTING,
                    e.getMessage() + " Retrying in " + delay + "s...");
                schedule(retryTask, delay * 1000L);
                return;
            }
            publishError(e.getMessage());
        }

        private void publishError(String message) {
            publish(ConnectionState.ERROR, message);
        }

        private void publish(ConnectionState state, String message) {
            if (!active) return;
            boolean live = state == ConnectionState.LIVE;
            String title = video != null && video.title() != null ? video.title() : "--";
            String channelName = channel != null && channel.name() != null ? channel.name()
                : (video != null && video.channelTitle() != null ? video.channelTitle() : "--");
            String handle = channel != null && channel.handle() != null ? channel.handle() : "--";
            String text = chatNote.isEmpty() || !live ? message : message + " (" + chatNote + ")";
            snapshotRef.set(new StreamSnapshot(
                state, videoId, channelName, handle,
                channel != null ? channel.subscriberCount() : null,
                title,
                live && video != null ? video.concurrentViewers() : null,
                video != null ? video.likeCount() : null,
                video != null ? video.actualStartEpochMs() : 0L,
                text));
        }
    }
}
