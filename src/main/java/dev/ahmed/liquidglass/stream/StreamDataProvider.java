package dev.ahmed.liquidglass.stream;

import java.util.List;

/** Source of live stream data. Implementations are called only from the stream worker thread. */
public interface StreamDataProvider {

    enum LiveStatus { LIVE, UPCOMING, ENDED, NOT_LIVE }

    record VideoInfo(
        String videoId,
        String title,
        String channelId,
        String channelTitle,
        LiveStatus status,
        Long concurrentViewers, Long totalViews,
        Long likeCount,
        long actualStartEpochMs,
        String activeLiveChatId
    ) {}

    record ChannelInfo(String channelId, String name, String handle, Long subscriberCount, String avatarUrl) {}

    record ChatBatch(List<StreamManager.ChatMessage> messages, String nextPageToken, long pollIntervalMs) {}

    VideoInfo fetchVideo(String videoId) throws ProviderException;

    ChannelInfo fetchChannel(String channelId) throws ProviderException;

    ChatBatch fetchChat(String liveChatId, String pageToken) throws ProviderException;
}
