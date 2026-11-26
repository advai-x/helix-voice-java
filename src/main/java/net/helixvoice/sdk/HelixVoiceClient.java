package net.helixvoice.sdk;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import net.helixvoice.sdk.model.Capabilities;
import net.helixvoice.sdk.model.Generation;
import net.helixvoice.sdk.model.SpeechRequest;
import net.helixvoice.sdk.model.Transcription;
import net.helixvoice.sdk.model.Voice;

/** Server-side client for the scoped developer API. */
public final class HelixVoiceClient {
    private final VoiceTransport http;

    public HelixVoiceClient() { this(System.getenv("HELIXVOICE_API_KEY")); }

    public HelixVoiceClient(String apiKey) { this(apiKey, VoiceTransport.BASE_URL, VoiceTransport.TIMEOUT); }

    public HelixVoiceClient(String apiKey, URI baseUrl, Duration timeout) {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("A Helix Voice API key is required");
        this.http = new VoiceTransport(baseUrl, timeout, apiKey.strip());
    }

    public Capabilities getCapabilities() throws IOException, InterruptedException {
        return http.json("GET", "/v1/capabilities", null, VoiceTransport.MAPPER.readerFor(Capabilities.class));
    }

    public List<Voice> listVoices() throws IOException, InterruptedException {
        return http.json("GET", "/v1/voices", null, VoiceTransport.MAPPER.readerForListOf(Voice.class));
    }

    public Voice cloneVoice(Path file, String name, String language, String transcript, boolean consent)
            throws IOException, InterruptedException {
        if (!consent) throw new IllegalArgumentException("Explicit consent is required to clone a voice");
        var body = VoiceTransport.multipart(file, Map.of(
                "name", name, "language", language, "transcript", transcript, "consent", "true"));
        return http.json("POST", "/v1/voices", body, VoiceTransport.MAPPER.readerFor(Voice.class));
    }

    public void deleteVoice(String id) throws IOException, InterruptedException {
        http.delete("/v1/voices/" + VoiceTransport.segment(id));
    }

    public Generation generateSpeech(String input, String voice) throws IOException, InterruptedException {
        var body = VoiceTransport.jsonBody(new SpeechRequest().input(input).voice(voice));
        return http.json("POST", "/v1/audio/speech", body, VoiceTransport.MAPPER.readerFor(Generation.class));
    }

    public Generation saveSpeech(String input, String voice, Path output) throws IOException, InterruptedException {
        Generation generation = generateSpeech(input, voice);
        try {
            byte[] audio = downloadGeneration(generation.getId());
            Path temp = Files.createTempFile(output.toAbsolutePath().getParent(), ".helixvoice-", ".tmp");
            try {
                Files.write(temp, audio);
                Files.move(temp, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new SpeechSaveException(generation, error);
        } catch (IOException | RuntimeException error) {
            throw new SpeechSaveException(generation, error);
        }
        return generation;
    }

    public Transcription createTranscription(Path file, String language) throws IOException, InterruptedException {
        var fields = language == null ? Map.<String, String>of() : Map.of("language", language);
        var body = VoiceTransport.multipart(file, fields);
        return http.json("POST", "/v1/audio/transcriptions", body, VoiceTransport.MAPPER.readerFor(Transcription.class));
    }

    public List<Generation> listGenerations() throws IOException, InterruptedException {
        return http.json("GET", "/v1/generations", null, VoiceTransport.MAPPER.readerForListOf(Generation.class));
    }

    public byte[] downloadGeneration(String id) throws IOException, InterruptedException {
        return http.wav("GET", "/v1/generations/" + VoiceTransport.segment(id) + "/audio", null).getAudio();
    }

    public void deleteGeneration(String id) throws IOException, InterruptedException {
        http.delete("/v1/generations/" + VoiceTransport.segment(id));
    }
}
