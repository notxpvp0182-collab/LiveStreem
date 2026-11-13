package dev.ahmed.liquidglass.stream;

/** Failure reported by a {@link StreamDataProvider}; carries enough detail to pick a retry policy. */
public final class ProviderException extends Exception {
    public enum Kind {
        MISSING_KEY, INVALID_KEY, QUOTA_EXCEEDED, RATE_LIMITED, FORBIDDEN, NOT_FOUND,
        CHAT_ENDED, CHAT_DISABLED, NETWORK, SERVER, MALFORMED, CANCELLED
    }

    private final Kind kind;

    public ProviderException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public ProviderException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() { return kind; }

    public boolean isRetryable() {
        return kind == Kind.NETWORK || kind == Kind.SERVER || kind == Kind.MALFORMED || kind == Kind.RATE_LIMITED;
    }
}
