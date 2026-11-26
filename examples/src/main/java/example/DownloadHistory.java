package example;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import net.helixvoice.sdk.HelixVoiceClient;

public final class DownloadHistory {
    public static void main(String[] args) throws Exception {
        if (args.length > 1) {
            throw new IllegalArgumentException("Usage: example.DownloadHistory [generation-id]");
        }

        var baseUrl = URI.create(System.getenv().getOrDefault("HELIXVOICE_BASE_URL", "https://api.helixvoice.net"));
        var client = new HelixVoiceClient(System.getenv("HELIXVOICE_API_KEY"), baseUrl, Duration.ofSeconds(180));
        if (args.length == 0) {
            for (var generation : client.listGenerations()) {
                System.out.println(generation.getId());
            }
            return;
        }

        Files.write(Path.of("history.wav"), client.downloadGeneration(args[0]));
        System.out.println("Saved history.wav");
    }
}
