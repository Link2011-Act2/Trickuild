import com.android.builder.model.v2.ide.SyncIssue;
import com.android.builder.model.v2.models.AndroidProject;
import com.android.builder.model.v2.models.BasicAndroidProject;
import com.android.builder.model.v2.models.ProjectSyncIssues;
import com.android.builder.model.v2.models.Versions;
import java.io.File;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.gradle.BasicGradleProject;

/** Queries public AGP v2 tooling models in the same way an IDE can during sync. */
public final class AndroidToolingProbe {
    public static void main(String[] args) {
        if (args.length != 4) {
            throw new IllegalArgumentException("Expected project directory, Gradle user home, Gradle installation, and JDK home");
        }
        GradleConnector connector = GradleConnector.newConnector()
                .forProjectDirectory(new File(args[0]))
                .useGradleUserHomeDir(new File(args[1]))
                .useInstallation(new File(args[2]));
        try (ProjectConnection connection = connector.connect()) {
            BuildActionExecuter<String> action = connection.action(new FetchAndroidModels());
            action.setJavaHome(new File(args[3]));
            action.setStandardOutput(System.out);
            action.setStandardError(System.err);
            action.withArguments("--max-workers=2");
            System.out.println(action.run());
        }
    }

    private static final class FetchAndroidModels implements BuildAction<String> {
        @Override
        public String execute(BuildController controller) {
            BasicGradleProject app = controller.getBuildModel().getProjects().stream()
                    .filter(project -> project.getPath().equals(":app"))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Missing :app project"));
            Versions versions = controller.getModel(app, Versions.class);
            BasicAndroidProject basic = controller.getModel(app, BasicAndroidProject.class);
            AndroidProject android = controller.getModel(app, AndroidProject.class);
            ProjectSyncIssues issues = controller.getModel(app, ProjectSyncIssues.class);
            long errors = issues.getSyncIssues().stream().filter(issue -> issue.getSeverity() == SyncIssue.SEVERITY_ERROR).count();
            if (errors != 0 || !"dev.trickuild.externalconsumer".equals(android.getNamespace()) || basic.getVariants().isEmpty()) {
                throw new IllegalStateException("Android model has unexpected namespace, variants, or sync errors: " + errors);
            }
            return "AGP_MODEL=" + versions.getAgp() + "; NAMESPACE=" + android.getNamespace()
                    + "; VARIANTS=" + basic.getVariants().size() + "; SYNC_ERRORS=" + errors;
        }
    }
}
