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

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import tel.panfilov.maven.plugins.reposync.component.LocalRepositoryVisitor;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static tel.panfilov.maven.plugins.reposync.Utils.isJavadocArtifact;
import static tel.panfilov.maven.plugins.reposync.Utils.isSourcesArtifact;

/**
 * Synchronises local repository artifacts as is
 */
@Mojo(name = "local", requiresProject = false, threadSafe = true, requiresOnline = true)
public class LocalSyncMojo extends AbstractSyncMojo {

    @Parameter(defaultValue = "${localRepository}", required = true, readonly = true)
    private ArtifactRepository localRepository;

    /**
     * Whether to failure execution in case of error during recognizing artifact in local repository.
     * This could be caused by incorrect repository structure, unexpected files which looks like as regular maven artifacts
     * or if illegal characters is present in artifact filename, version, groupId.
     * Default behaviour suppose that provided repository has no such errors.
     * Default value is true, that means that errors will be logged at level WARNING, and execution will be continued.
     */
    @Parameter(property = "failOnBadArtifact", defaultValue = "true")
    protected boolean failOnBadArtifact = true;

    @Override
    protected List<Artifact> getExistingArtifacts() throws MojoFailureException {
        Set<Artifact> collectedArtifacts = new HashSet<>();
        for (RemoteRepository repository : getSourceRepositories()) {
            collectedArtifacts.addAll(collectArtifacts(repository));
        }
        return new ArrayList<>(collectedArtifacts);
    }

    private Set<Artifact> collectArtifacts(RemoteRepository repository) throws MojoFailureException {
        try {
            URL url = new URL(repository.getUrl());
            Path localRepoPath = Paths.get(url.toURI());
            LocalRepositoryVisitor localRepositoryVisitor = new LocalRepositoryVisitor(localRepoPath, failOnBadArtifact, getLog());
            Files.walkFileTree(localRepoPath, localRepositoryVisitor);
            return localRepositoryVisitor.getResolvedArtifacts().stream()
                    .filter(this::needToSync)
                    .collect(Collectors.toSet());
        } catch (IOException | URISyntaxException e) {
            throw new MojoFailureException("Exception during collecting local artifacts" +
                    " from repository with id '" + repository.getId() + "'", e);
        }
    }

    protected boolean needToSync(Artifact artifact) {
        return (!isSourcesArtifact(artifact) || syncSources) &&
                (!isJavadocArtifact(artifact) || syncJavadoc);
    }

    @Override
    protected List<RemoteRepository> getSourceRepositories() throws MojoFailureException {
        if (source == null) {
            if (sourceRepositories == null) {
                source = getRemoteRepositories(Collections.singletonList(localRepository), false);
            }
            for (RemoteRepository userProvidedRepository : super.getSourceRepositories()) {
                validateUrlProtocolIsFile(userProvidedRepository);
            }
        }
        return super.getSourceRepositories();
    }

    protected void validateUrlProtocolIsFile(RemoteRepository repository) throws MojoFailureException {
        if (!"file".equalsIgnoreCase(repository.getProtocol())) {
            throw new MojoFailureException("Repository with id '" + repository.getId() +
                    "' has unsupported protocol '" + repository.getProtocol() + "'. All repositories must have 'file' protocol.");
        }
    }

}
