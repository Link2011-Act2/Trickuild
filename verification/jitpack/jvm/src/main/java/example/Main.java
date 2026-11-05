package example;

import dev.trickuild.generated.TrickuildMetadata;

public final class Main {
    public static void main(String[] args) throws ReflectiveOperationException {
        System.out.println("BUILD_NUMBER=" + TrickuildMetadata.BUILD_NUMBER);
        System.out.println("CODE_NAME=" + TrickuildMetadata.CODE_NAME);
        try {
            Class<?> metadata = TrickuildMetadata.class;
            System.out.println("PERSONALITY_SEED=" + metadata.getField("PERSONALITY_SEED").get(null));
            System.out.println("PERSONALITY_NUMBER=" + metadata.getField("PERSONALITY_NUMBER").getInt(null));
        } catch (NoSuchFieldException disabled) {
            System.out.println("ROULETTE_DISABLED");
        }
    }
}
