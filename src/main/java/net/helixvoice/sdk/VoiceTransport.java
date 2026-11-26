package net.helixvoice.sdk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** HTTP transport for the authenticated developer API. */
final class VoiceTransport {
    static final URI BASE_URL = URI.create("https://api.helixvoice.net");
    static final Duration TIMEOUT = Duration.ofSeconds(180);
    static final ObjectMapper MAPPER = ApiClient.createDefaultObjectMapper();
    private static final int MAX_WAV = 16 << 20;
    private static final int MAX_JSON = 4 << 20;
    private static final int MAX_UPLOAD = 20 << 20;

    private final String baseUrl;
    private final String key;
    private final Duration timeout;
    private final HttpClient http;

    VoiceTransport(URI baseUrl, Duration timeout, String key) {
        if (!("http".equals(baseUrl.getScheme()) || "https".equals(baseUrl.getScheme()))
                || baseUrl.getHost() == null || baseUrl.getUserInfo() != null
                || baseUrl.getQuery() != null || baseUrl.getFragment() != null) {
            throw new IllegalArgumentException("baseUrl must be an HTTP(S) base URL");
        }
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.baseUrl = baseUrl.toString().replaceFirst("/+$", "");
        this.key = key;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(timeout).build();
    }

    static String segment(String id) {
        if (id == null || id.isEmpty() || ".".equals(id) || "..".equals(id)) {
            throw new IllegalArgumentException("A resource ID is required");
        }
        return URLEncoder.encode(id, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static Body jsonBody(Object data) throws JsonProcessingException {
        return new Body("application/json", MAPPER.writeValueAsBytes(data));
    }

    static Body multipart(Path path, Map<String, String> fields) throws IOException {
        if (!Files.isRegularFile(path) || Files.size(path) > MAX_UPLOAD) {
            throw new IllegalArgumentException("Upload must be a regular file at most 20 MiB");
        }
        String boundary = "helixvoice-" + UUID.randomUUID();
        var data = new ByteArrayOutputStream();
        for (var field : fields.entrySet()) {
            write(data, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field.getKey()
                    + "\"\r\n\r\n" + field.getValue() + "\r\n");
        }
        String filename = path.getFileName().toString().replace("\r", "%0D").replace("\n", "%0A")
                .replace("\"", "%22").replace("\\", "%5C");
        write(data, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                + filename + "\"\r\nContent-Type: application/octet-stream\r\n\r\n");
        try (var source = Files.newInputStream(path)) {
            byte[] file = source.readNBytes(MAX_UPLOAD + 1);
            if (file.length > MAX_UPLOAD) throw new IllegalArgumentException("Upload exceeds 20 MiB");
            data.writeBytes(file);
        }
        write(data, "\r\n--" + boundary + "--\r\n");
        return new Body("multipart/form-data; boundary=" + boundary, data.toByteArray());
    }

    private static void write(ByteArrayOutputStream stream, String value) {
        stream.writeBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    <T> T json(String method, String route, Body body, ObjectReader reader) throws IOException, InterruptedException {
        var response = send(method, route, body, "application/json", MAX_JSON);
        if (mediaType(response).equals("application/json") && response.body().isPresent()) {
            try {
                JsonNode envelope = MAPPER.readTree(response.body().get());
                if (envelope != null && envelope.hasNonNull("data")) return reader.readValue(envelope.get("data"));
            } catch (JsonProcessingException ignored) {
                // Preserve response status and request ID below.
            }
        }
        throw failure(response, "Unexpected Helix Voice JSON response");
    }

    byte[] wav(String route) throws IOException, InterruptedException {
        var response = send("GET", route, null, "audio/wav", MAX_WAV);
        if (!mediaType(response).equals("audio/wav")) throw failure(response, "Expected a WAV response");
        return response.body().orElseThrow(() -> failure(response, "WAV exceeds the SDK response size limit"));
    }

    void delete(String route) throws IOException, InterruptedException {
        var response = send("DELETE", route, null, "application/json", 0);
        if (response.statusCode() != 204) throw failure(response, "Expected HTTP 204");
    }

    private HttpResponse<Optional<byte[]>> send(String method, String route, Body body, String accept, int limit)
            throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create(baseUrl + route)).timeout(timeout)
                .header("Accept", accept).header("Authorization", "Bearer " + key);
        var publisher = HttpRequest.BodyPublishers.noBody();
        if (body != null) {
            request.header("Content-Type", body.contentType);
            publisher = HttpRequest.BodyPublishers.ofByteArray(body.bytes);
        }
        var pending = http.sendAsync(request.method(method, publisher).build(), info ->
                new BoundedBody(success(info.statusCode()) ? limit : 64 << 10));
        HttpResponse<Optional<byte[]>> response;
        try {
            response = pending.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException error) {
            pending.cancel(true);
            throw new HttpTimeoutException("Helix Voice request timed out");
        } catch (InterruptedException error) {
            pending.cancel(true);
            throw error;
        } catch (ExecutionException error) {
            if (error.getCause() instanceof IOException) throw (IOException) error.getCause();
            throw new IOException("Helix Voice request failed", error.getCause());
        }
        if (!success(response.statusCode())) {
            throw failure(response, "Helix Voice returned HTTP " + response.statusCode());
        }
        return response;
    }

    private static boolean success(int status) { return status >= 200 && status < 300; }

    private static String mediaType(HttpResponse<?> response) {
        return response.headers().firstValue("Content-Type").orElse("")
                .split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private static String header(HttpResponse<?> response, String name) {
        return response.headers().firstValue(name).orElse(null);
    }

    private static HelixVoiceException failure(HttpResponse<Optional<byte[]>> response, String message) {
        String requestId = header(response, "X-Request-ID");
        String code = null;
        if (!success(response.statusCode()) && mediaType(response).equals("application/json") && response.body().isPresent()) {
            try {
                JsonNode root = MAPPER.readTree(response.body().get());
                if (root != null) {
                    JsonNode details = root.has("error") ? root.path("error") : root;
                    message = details.path("message").asText(message);
                    code = details.path("code").asText(null);
                    String bodyId = details.path("requestId").asText(details.path("transactionId").asText(""));
                    if (!bodyId.isEmpty()) requestId = bodyId;
                }
            } catch (IOException ignored) {
                // Preserve response status and headers when the error body is malformed.
            }
        }
        return new HelixVoiceException(message, response.statusCode(), code, requestId, header(response, "Retry-After"));
    }

    static final class Body {
        final String contentType;
        final byte[] bytes;

        Body(String contentType, byte[] bytes) {
            this.contentType = contentType;
            this.bytes = bytes;
        }
    }

    private static final class BoundedBody implements HttpResponse.BodySubscriber<Optional<byte[]>> {
        private final CompletableFuture<Optional<byte[]>> result = new CompletableFuture<>();
        private final ByteArrayOutputStream data = new ByteArrayOutputStream();
        private final int limit;
        private Flow.Subscription subscription;

        BoundedBody(int limit) { this.limit = limit; }

        @Override public CompletionStage<Optional<byte[]>> getBody() { return result; }

        @Override public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > limit - data.size()) {
                    result.complete(Optional.empty());
                    subscription.cancel();
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                data.writeBytes(chunk);
            }
        }

        @Override public void onError(Throwable error) { result.completeExceptionally(error); }

        @Override public void onComplete() { result.complete(Optional.of(data.toByteArray())); }
    }
}
