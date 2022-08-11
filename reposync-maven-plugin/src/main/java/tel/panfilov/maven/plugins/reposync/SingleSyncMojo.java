package tel.panfilov.maven.plugins.reposync;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Synchronises single artifact
 */
@Mojo(name = "single", requiresProject = false, threadSafe = true, requiresOnline = true)
public class SingleSyncMojo extends AbstractCLISyncMojo {

    /**
     * The packaging of the artifact to sync. Ignored if {@link #artifact} is used.
     */
    @Parameter(property = "packaging", defaultValue = "jar")
    private String packaging = "jar";

    @Override
    protected List<Artifact> getExistingArtifacts() throws MojoFailureException, MojoExecutionException {
        List<Artifact> artifacts = new ArrayList<>();
        Artifact rootArtifact = getArtifact(getCoordinate());
        if (transitive) {
            artifacts.addAll(collectDependencies(rootArtifact));
        } else {
            artifacts.add(rootArtifact);
        }
        return getExistingArtifacts(addClassifiersAndPoms(artifacts));
    }

    protected Collection<Artifact> collectDependencies(Artifact rootArtifact) throws MojoFailureException, MojoExecutionException {
        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRoot(new Dependency(rootArtifact, null));
        collectRequest.setRepositories(getSourceRepositories());
        return collectDependencies(collectRequest, 0, DEFAULT_SCOPE);
    }

}
