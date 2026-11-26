package net.helixvoice.sdk;

import java.io.IOException;

/** HTTP or response-format error from Helix Voice. */
public final class HelixVoiceException extends IOException {
    private final int status;
    private final String code;
    private final String requestId;
    private final String retryAfter;

    HelixVoiceException(String message, int status, String code, String requestId, String retryAfter) {
        super(message);
        this.status = status;
        this.code = code;
        this.requestId = requestId;
        this.retryAfter = retryAfter;
    }

    public int getStatus() { return status; }
    public String getCode() { return code; }
    public String getRequestId() { return requestId; }
    public String getRetryAfter() { return retryAfter; }
}
