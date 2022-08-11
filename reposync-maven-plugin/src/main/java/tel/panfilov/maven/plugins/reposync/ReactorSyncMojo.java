package tel.panfilov.maven.plugins.reposync;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Synchronises reactor dependencies
 */
@Mojo(name = "reactor", threadSafe = true, requiresOnline = true, aggregator = true)
public class ReactorSyncMojo extends AbstractSyncMojo {

    protected final Set<String> ignoreScopes = new HashSet<>();
    /**
     * Scope threshold to include
     */
    @Parameter(property = "scope", defaultValue = DEFAULT_SCOPE)
    protected String scope = DEFAULT_SCOPE;
    /**
     * Contains the full list of projects in the reactor.
     */
    @Parameter(defaultValue = "${reactorProjects}", readonly = true)
    private List<MavenProject> reactorProjects;

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
        Set<Artifact> artifactSet = new HashSet<>();
        for (MavenProject project : reactorProjects) {
            artifactSet.addAll(collectDependencies(project));
        }
        for (MavenProject project : reactorProjects) {
            artifactSet.remove(RepositoryUtils.toArtifact(project.getArtifact()));
        }
        List<Artifact> artifacts = new ArrayList<>(artifactSet);
        return getExistingArtifacts(addClassifiersAndPoms(artifacts));
    }

    protected Collection<Artifact> collectDependencies(MavenProject project) throws MojoFailureException, MojoExecutionException {
        ArtifactTypeRegistry typeRegistry = RepositoryUtils.newArtifactTypeRegistry(artifactHandlerManager);
        Artifact projectArtifact = RepositoryUtils.toArtifact(project.getArtifact());
        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRepositories(getSourceRepositories());
        collectRequest.setRootArtifact(projectArtifact);
        collectRequest.setRequestContext("project");

        List<Dependency> dependencies = project.getDependencies()
                .stream()
                .map(d -> RepositoryUtils.toDependency(d, typeRegistry))
                .filter(this::include)
                .collect(Collectors.toList());
        collectRequest.setDependencies(dependencies);

        DependencyManagement dependencyManagement = project.getDependencyManagement();
        if (dependencyManagement != null) {
            List<Dependency> managed = dependencyManagement.getDependencies()
                    .stream()
                    .map(d -> RepositoryUtils.toDependency(d, typeRegistry))
                    .collect(Collectors.toList());
            collectRequest.setManagedDependencies(managed);
        }
        return collectDependencies(collectRequest, 0, DEFAULT_SCOPE);
    }

    @Override
    protected boolean include(Dependency dependency) {
        return !ignoreScopes.contains(dependency.getScope());
    }

}