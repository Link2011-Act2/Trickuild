package example;

import dev.trickuild.generated.TrickuildMetadata;

public final class Main {
    public static void main(String[] args) {
        System.out.println(TrickuildMetadata.BUILD_NUMBER);
        System.out.println(TrickuildMetadata.CODE_NAME);
    }
}
