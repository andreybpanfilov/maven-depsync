package tel.panfilov.maven.plugins.reposync;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static tel.panfilov.maven.plugins.reposync.Utils.getId;

/**
 * Synchronises bill of material
 */
@Mojo(name = "bom", requiresProject = false, threadSafe = true, requiresOnline = true)
public class BomSyncMojo extends AbstractCLISyncMojo {

    protected final Set<String> ignoreScopes = new HashSet<>();
    /**
     * Scope threshold to include
     */
    @Parameter(property = "scope", defaultValue = DEFAULT_SCOPE)
    protected String scope = DEFAULT_SCOPE;
    /**
     * The packaging of the artifact to sync. Ignored if {@link #artifact} is used.
     */
    @Parameter(property = "packaging", defaultValue = "pom")
    private String packaging = "pom";

    /**
     * @param scope Scope threshold to include
     */
    public void setScope(String scope) {
        this.scope = scope;
        this.ignoreScopes.clear();
        this.ignoreScopes.addAll(scopeMediator.negate(scope));
    }


    @Override
    protected List<Artifact> getExistingArtifacts() throws MojoFailureException, MojoExecutionException {
        Artifact artifact = getArtifact(getCoordinate());
        if (!"pom".equals(artifact.getExtension())) {
            throw new MojoExecutionException("Not a pom artifact: " + artifact);
        }
        List<Dependency> managed = getManagedDependencies(artifact);
        List<Artifact> artifacts = new ArrayList<>();
        artifacts.add(artifact);
        Set<String> seen = new HashSet<>();
        if (transitive) {
            for (Dependency dependency : managed) {
                if (!include(dependency)) {
                    continue;
                }
                CollectRequest collectRequest = new CollectRequest();
                collectRequest.setRoot(dependency);
                collectRequest.setRepositories(getSourceRepositories());
                collectRequest.setManagedDependencies(managed);
                for (Artifact dep : collectDependencies(collectRequest, 1, DEFAULT_SCOPE)) {
                    if (seen.add(getId(dep))) {
                        artifacts.add(dep);
                    }
                }
            }
        } else {
            for (Dependency dependency : managed) {
                Artifact dep = dependency.getArtifact();
                if (seen.add(getId(dep))) {
                    artifacts.add(dep);
                }
            }
        }
        return getExistingArtifacts(addClassifiersAndPoms(artifacts));
    }

    protected List<Dependency> getManagedDependencies(Artifact artifact) throws MojoFailureException, MojoExecutionException {
        try {
            RepositorySystemSession repositorySession = session.getRepositorySession();
            ArtifactDescriptorRequest request = new ArtifactDescriptorRequest(artifact, getSourceRepositories(), null);
            ArtifactDescriptorResult descriptorResult = artifactDescriptorReader.readArtifactDescriptor(repositorySession, request);
            Utils.checkResult(descriptorResult, e -> false);
            return descriptorResult.getManagedDependencies();
        } catch (ArtifactDescriptorException ex) {
            throw new MojoExecutionException("Failed to read artifact descriptor", ex);
        }
    }

    @Override
    protected boolean include(Dependency dependency) {
        return !ignoreScopes.contains(dependency.getScope());
    }

}
