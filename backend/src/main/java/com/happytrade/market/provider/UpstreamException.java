package com.happytrade.market.provider;

/** Failures originating from the upstream market data source. */
public abstract class UpstreamException extends RuntimeException {

    protected UpstreamException(String message) {
        super(message);
    }

    protected UpstreamException(String message, Throwable cause) {
        super(message, cause);
    }

    /** The upstream rejected us for sending too many requests. */
    public static class RateLimited extends UpstreamException {

        private final int retryAfterSeconds;

        public RateLimited(int retryAfterSeconds) {
            super("Upstream rate limited the request; retry after " + retryAfterSeconds + "s");
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public int retryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    /** The upstream refuses to serve this region. Retrying will not help. */
    public static class Blocked extends UpstreamException {

        public Blocked() {
            super("Upstream is not available from this region or network");
        }
    }

    /** The upstream did not respond within the configured timeout. */
    public static class Timeout extends UpstreamException {

        public Timeout(Throwable cause) {
            super("Upstream did not respond in time", cause);
        }
    }
}
