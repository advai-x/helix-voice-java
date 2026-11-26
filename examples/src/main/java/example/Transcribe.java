package example;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import net.helixvoice.sdk.HelixVoiceClient;

public final class Transcribe {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: example.Transcribe <audio-file>");
        }

        var baseUrl = URI.create(System.getenv().getOrDefault("HELIXVOICE_BASE_URL", "https://api.helixvoice.net"));
        var client = new HelixVoiceClient(System.getenv("HELIXVOICE_API_KEY"), baseUrl, Duration.ofSeconds(180));
        var result = client.createTranscription(Path.of(args[0]), "zh-CN");
        System.out.println(result.getText());
    }
}
