/*-
 * #%L
 * reposync-maven-plugin
 * %%
 * Copyright (C) 2022 Project Contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package tel.panfilov.maven.plugins.reposync;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.interpolation.ModelInterpolator;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
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

    @Component
    protected ModelInterpolator modelInterpolator;

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
                .collect(Collectors.toCollection(ArrayList::new));

        Model originalModel = getOriginalProjectModel(project);

        DependencyManagement dependencyManagement = originalModel.getDependencyManagement();
        if (dependencyManagement != null) {
            dependencyManagement.getDependencies()
                    .stream()
                    .filter(d -> "import".equals(d.getScope()))
                    .filter(d -> "pom".equals(d.getType()))
                    .map(d -> RepositoryUtils.toDependency(d, typeRegistry))
                    .forEach(dependencies::add);
        }

        dependencyManagement = project.getDependencyManagement();
        if (dependencyManagement != null) {
            List<Dependency> managed = dependencyManagement.getDependencies()
                    .stream()
                    .map(d -> RepositoryUtils.toDependency(d, typeRegistry))
                    .collect(Collectors.toCollection(ArrayList::new));
            collectRequest.setManagedDependencies(managed);
        }

        collectRequest.setDependencies(dependencies);

        return collectDependencies(collectRequest, 0, DEFAULT_SCOPE);
    }

    protected Model getOriginalProjectModel(MavenProject project) {
        return modelInterpolator.interpolateModel(
                project.getOriginalModel(),
                project.getBasedir(),
                createModelBuildingRequest(project),
                a -> {
                }
        );
    }

    @Override
    protected boolean include(Dependency dependency) {
        return !ignoreScopes.contains(dependency.getScope());
    }

    protected ModelBuildingRequest createModelBuildingRequest(MavenProject project) {
        ModelBuildingRequest request = new DefaultModelBuildingRequest();
        request.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
        request.setProcessPlugins(false);
        request.setProfiles(project.getActiveProfiles());
        request.setSystemProperties(session.getSystemProperties());
        request.setUserProperties(session.getUserProperties());
        request.setBuildStartTime(new Date());
        return request;
    }

}
