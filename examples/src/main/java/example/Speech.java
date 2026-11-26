package example;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import net.helixvoice.sdk.HelixVoiceClient;

public final class Speech {
    public static void main(String[] args) throws Exception {
        var baseUrl = URI.create(System.getenv().getOrDefault("HELIXVOICE_BASE_URL", "https://api.helixvoice.net"));
        var client = new HelixVoiceClient(System.getenv("HELIXVOICE_API_KEY"), baseUrl, Duration.ofSeconds(180));
        var voices = client.listVoices();
        if (voices.isEmpty()) {
            throw new IllegalStateException("No voices are available to this account.");
        }

        var voice = voices.get(0);
        var generation = client.saveSpeech("Hello from Helix Voice.", voice.getId(), Path.of("speech.wav"));
        System.out.printf("Saved speech.wav with voice %s (generation %s)%n", voice.getId(), generation.getId());
    }
}
