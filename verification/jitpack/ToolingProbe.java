import java.io.File;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.idea.IdeaProject;

/** Fetches an IDE tooling model without running build tasks or consulting another project. */
public final class ToolingProbe {
    public static void main(String[] args) {
        if (args.length != 4) {
            throw new IllegalArgumentException("Expected project directory, Gradle user home, Gradle installation, and JDK home");
        }
        GradleConnector connector = GradleConnector.newConnector()
                .forProjectDirectory(new File(args[0]))
                .useGradleUserHomeDir(new File(args[1]))
                .useInstallation(new File(args[2]));
        try (ProjectConnection connection = connector.connect()) {
            ModelBuilder<IdeaProject> builder = connection.model(IdeaProject.class);
            builder.setJavaHome(new File(args[3]));
            builder.setStandardOutput(System.out);
            builder.setStandardError(System.err);
            builder.withArguments("--max-workers=2");
            IdeaProject model = builder.get();
            if (model.getModules().isEmpty()) {
                throw new IllegalStateException("The IDE model contains no modules");
            }
            System.out.println("TOOLING_MODEL=" + model.getName() + "; MODULES=" + model.getModules().size());
            model.getModules().forEach(module -> System.out.println("MODULE=" + module.getName()));
        }
    }
}
