package net.helixvoice.sdk;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;

/** Anonymous product preview with its own shared quota. */
public final class HelixVoicePreviewClient {
    private final VoiceTransport http;

    public HelixVoicePreviewClient() { this(VoiceTransport.BASE_URL, VoiceTransport.TIMEOUT); }

    public HelixVoicePreviewClient(URI baseUrl, Duration timeout) {
        this.http = new VoiceTransport(baseUrl, timeout, null);
    }

    public Result generate(String input) throws IOException, InterruptedException { return generate(input, null); }

    public Result generate(String input, String voice) throws IOException, InterruptedException {
        var data = new LinkedHashMap<String, String>();
        data.put("input", input);
        if (voice != null) data.put("voice", voice);
        return http.wav("POST", "/v1/audio/preview", VoiceTransport.jsonBody(data));
    }

    public static final class Result {
        private final byte[] audio;
        private final String cache;

        Result(byte[] audio, String cache) {
            this.audio = audio;
            this.cache = cache;
        }

        public byte[] getAudio() { return audio.clone(); }
        public String getCache() { return cache; }
    }
}
