package net.helixvoice.sdk;

import java.io.IOException;
import net.helixvoice.sdk.model.Generation;

/** Generation succeeded. Use its ID to recover the WAV. */
public final class SpeechSaveException extends IOException {
    private final Generation generation;

    SpeechSaveException(Generation generation, Throwable cause) {
        super("Speech was generated but its WAV could not be saved", cause);
        this.generation = generation;
    }

    public Generation getGeneration() { return generation; }
}
