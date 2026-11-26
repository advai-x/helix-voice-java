package example;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import net.helixvoice.sdk.HelixVoiceClient;

public final class CloneVoice {
    public static void main(String[] args) throws Exception {
        if (args.length != 5 || !args[0].equals("--consent")) {
            throw new IllegalArgumentException(
                    "Usage: example.CloneVoice --consent <reference-audio> <name> <locale> <transcript>");
        }

        var baseUrl = URI.create(System.getenv().getOrDefault("HELIXVOICE_BASE_URL", "https://api.helixvoice.net"));
        var client = new HelixVoiceClient(System.getenv("HELIXVOICE_API_KEY"), baseUrl, Duration.ofSeconds(180));
        var voice = client.cloneVoice(Path.of(args[1]), args[2], args[3], args[4], true);
        System.out.println(voice.getId());
    }
}
