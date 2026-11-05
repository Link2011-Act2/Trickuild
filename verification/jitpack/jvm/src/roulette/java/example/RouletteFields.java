package example;

import dev.trickuild.generated.TrickuildMetadata;

/** Compile-time verification of optional fields when roulette is enabled. */
public final class RouletteFields {
    public static final String SEED = TrickuildMetadata.PERSONALITY_SEED;
    public static final int NUMBER = TrickuildMetadata.PERSONALITY_NUMBER;
}
