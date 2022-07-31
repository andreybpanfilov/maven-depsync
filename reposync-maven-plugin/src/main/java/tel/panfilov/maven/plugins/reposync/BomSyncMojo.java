package tel.panfilov.maven.plugins.reposync;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;

import java.util.ArrayList;
import java.util.List;

@Mojo(name = "bom", requiresProject = false, threadSafe = true)
public class BomSyncMojo extends AbstractSyncMojo {

    /**
     * The packaging of the artifact to sync. Ignored if {@link #artifact} is used.
     */
    @Parameter(property = "packaging", defaultValue = "pom")
    private String packaging = "pom";

    @Override
    protected List<Artifact> getExistingArtifacts() throws MojoFailureException, MojoExecutionException {
        Artifact artifact = getArtifact(coordinate);
        if (!"pom".equals(artifact.getExtension())) {
            throw new MojoExecutionException("Not a pom artifact: " + artifact);
        }
        List<Dependency> managed = getManagedDependencies(artifact, getSourceRepositories());
        List<Artifact> artifacts = new ArrayList<>();
        if (transitive) {
            CollectRequest collectRequest = new CollectRequest();
            collectRequest.setRoot(new Dependency(artifact, scope, false));
            collectRequest.setRepositories(getSourceRepositories());
            for (Dependency dependency : managed) {
                collectRequest.addDependency(new Dependency(dependency.getArtifact(), scope, false));
            }
            artifacts.addAll(collectDependencies(collectRequest));
        } else {
            artifacts.add(artifact);
            for (Dependency dependency : managed) {
                artifacts.add(dependency.getArtifact());
            }
        }
        return getExistingArtifacts(addClassifiersAndPoms(artifacts));
    }

    protected List<Dependency> getManagedDependencies(Artifact artifact, List<RemoteRepository> sourceRepositories) throws MojoExecutionException {
        try {
            RepositorySystemSession repositorySession = session.getRepositorySession();
            ArtifactDescriptorRequest request = new ArtifactDescriptorRequest(artifact, sourceRepositories, null);
            ArtifactDescriptorResult descriptorResult = artifactDescriptorReader.readArtifactDescriptor(repositorySession, request);
            checkResult(descriptorResult, e -> false);
            return descriptorResult.getManagedDependencies();
        } catch (ArtifactDescriptorException ex) {
            throw new MojoExecutionException("Failed to read artifact descriptor", ex);
        }
    }

}
