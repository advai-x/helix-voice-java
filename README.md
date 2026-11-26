# Helix Voice SDK for Java

Use the Helix Voice developer API to generate speech, transcribe audio, and manage voices from a Java service.

## Install

Requires JDK 11 or later and Maven 3.

### Maven Central

Add this dependency to your project's `pom.xml`. Maven resolves it from Central. [Maven Central artifacts](https://repo.maven.apache.org/maven2/net/helixvoice/helix-voice-sdk-java/0.2.0/)

```xml
<dependency>
    <groupId>net.helixvoice</groupId>
    <artifactId>helix-voice-sdk-java</artifactId>
    <version>0.2.0</version>
</dependency>
```

### Alternative install from the GitHub release

Download and extract the [v0.2.0 ZIP from GitHub](https://github.com/advai-x/helix-voice-java/releases/tag/v0.2.0). From the extracted directory, install the SDK and its POM into your local Maven repository.

```sh
mvn org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file \
  -Dfile=helix-voice-sdk-java-0.2.0.jar \
  -DpomFile=helix-voice-sdk-java-0.2.0.pom \
  -Dsources=helix-voice-sdk-java-0.2.0-sources.jar \
  -Djavadoc=helix-voice-sdk-java-0.2.0-javadoc.jar
```

Then add the [dependency above](#maven-central) to your project.

## Quickstart

Run the speech example from a checkout of this repository. The example project already declares the SDK dependency.

```sh
export HELIXVOICE_API_KEY='YOUR_API_KEY'
mvn -f examples/pom.xml compile exec:java -Dexec.mainClass=example.Speech
```

The [speech example](examples/src/main/java/example/Speech.java) chooses the first available voice returned by `listVoices`, calls `saveSpeech`, and writes `speech.wav`. It prints the voice and generation IDs.

## API keys and account access

For production use, ask your account administrator for a scoped API key and set `HELIXVOICE_API_KEY` in your server environment. The speech example needs `voices:read` to look up a voice, `speech:write` to generate speech, and `generations:read` to download the WAV. Keep the key in server-side secret storage. [API keys and scopes](https://docs.helixvoice.net/en/api/authentication/)

The examples default to `https://api.helixvoice.net`. Set `HELIXVOICE_BASE_URL` when running them against a trusted staging or test API.

## Capabilities and scopes

| Operation | Required scope |
| --- | --- |
| Read service capabilities and limits | `capabilities:read` |
| List public and account-owned voices | `voices:read` |
| Clone or delete an owned voice | `voices:write` |
| Generate speech metadata | `speech:write` |
| Transcribe a Mandarin audio file | `transcriptions:write` |
| List generations or download WAV audio | `generations:read` |
| Delete generation history | `generations:write` |

Voice cloning requires explicit consent. Consult the capabilities response for the limits of your environment. [Developer API reference](https://docs.helixvoice.net/en/api/speech/)

## Troubleshooting

- **401 Unauthorized** means the key is missing, invalid, expired, or revoked. Check the key used by the running process.
- **403 Forbidden** with `insufficient_scope` means the key lacks a required permission. Check the scopes for each request.
- **429 Too Many Requests** means a quota or rate limit was reached. Honor `Retry-After` when available.
- **Speech generated but WAV saving failed** means you can recover the generation ID from the save error and retry the download while that record is retained. Retrying generation can consume quota again.

Keep the request ID when investigating an API error. [Errors and rate limits](https://docs.helixvoice.net/en/api/errors/) · [Generation downloads](https://docs.helixvoice.net/en/api/speech/#history-and-audio)

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
