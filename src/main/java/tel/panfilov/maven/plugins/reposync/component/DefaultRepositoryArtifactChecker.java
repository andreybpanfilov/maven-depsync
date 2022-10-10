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

package tel.panfilov.maven.plugins.reposync.component;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.impl.OfflineController;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.impl.RepositoryConnectorProvider;
import org.eclipse.aether.impl.VersionResolver;
import org.eclipse.aether.repository.LocalArtifactRegistration;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRequest;
import org.eclipse.aether.resolution.VersionResolutionException;
import org.eclipse.aether.resolution.VersionResult;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;
import org.eclipse.aether.transfer.RepositoryOfflineException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component(role = RepositoryArtifactChecker.class)
public class DefaultRepositoryArtifactChecker implements RepositoryArtifactChecker {

    @Requirement
    private VersionResolver versionResolver;

    @Requirement
    private RepositoryConnectorProvider repositoryConnectorProvider;

    @Requirement
    private RemoteRepositoryManager remoteRepositoryManager;

    @Requirement
    private OfflineController offlineController;

    @Override
    public ArtifactResult checkArtifact(RepositorySystemSession session, boolean checkLocal, ArtifactRequest request) {
        return checkArtifacts(session, checkLocal, Collections.singleton(request)).get(0);
    }

    @Override
    public List<ArtifactResult> checkArtifacts(RepositorySystemSession session, boolean checkLocal, Collection<? extends ArtifactRequest> requests) {
        return check(session, checkLocal, requests);
    }

    protected List<ArtifactResult> check(RepositorySystemSession session, boolean checkLocal, Collection<? extends ArtifactRequest> requests) {
        List<ArtifactResult> results = new ArrayList<>(requests.size());
        LocalRepositoryManager localRepositoryManager = session.getLocalRepositoryManager();
        List<ResolutionGroup> groups = new ArrayList<>();

        for (ArtifactRequest request : requests) {
            RequestTrace trace = RequestTrace.newChild(request.getTrace(), request);

            ArtifactResult result = new ArtifactResult(request);
            results.add(result);

            Artifact artifact = request.getArtifact();
            List<RemoteRepository> repos = request.getRepositories();

            VersionResult versionResult;
            try {
                VersionRequest versionRequest = new VersionRequest(artifact, repos, request.getRequestContext());
                versionRequest.setTrace(trace);
                versionResult = versionResolver.resolveVersion(session, versionRequest);
            } catch (VersionResolutionException e) {
                result.addException(e);
                continue;
            }

            artifact = artifact.setVersion(versionResult.getVersion());

            if (versionResult.getRepository() != null) {
                if (versionResult.getRepository() instanceof RemoteRepository) {
                    repos = Collections.singletonList((RemoteRepository) versionResult.getRepository());
                } else {
                    repos = Collections.emptyList();
                }
            }

            if (checkLocal) {
                LocalArtifactResult local = localRepositoryManager.find(session, new LocalArtifactRequest(artifact, repos, request.getRequestContext()));
                if (isLocallyInstalled(local, versionResult)) {
                    if (local.getRepository() != null) {
                        result.setRepository(local.getRepository());
                    } else {
                        result.setRepository(localRepositoryManager.getRepository());
                    }
                    result.setArtifact(artifact);
                    if (!local.isAvailable()) {
                        localRepositoryManager.add(session, new LocalArtifactRegistration(artifact));
                    }
                    continue;
                }
            }

            AtomicBoolean resolved = new AtomicBoolean(false);
            Iterator<ResolutionGroup> groupIt = groups.iterator();
            for (RemoteRepository repo : repos) {
                if (!repo.getPolicy(artifact.isSnapshot()).isEnabled()) {
                    continue;
                }

                try {
                    checkOffline(session, offlineController, repo);
                } catch (RepositoryOfflineException e) {
                    Exception exception =
                            new ArtifactNotFoundException(artifact, repo, "Cannot access " + repo.getId() + " ("
                                    + repo.getUrl() + ") in offline mode and the artifact " + artifact
                                    + " has not been downloaded from it before.", e);
                    result.addException(exception);
                    continue;
                }

                ResolutionGroup group = null;
                while (groupIt.hasNext()) {
                    ResolutionGroup t = groupIt.next();
                    if (t.matches(repo)) {
                        group = t;
                        break;
                    }
                }
                if (group == null) {
                    group = new ResolutionGroup(repo);
                    groups.add(group);
                    groupIt = Collections.emptyIterator();
                }
                group.items.add(new ResolutionItem(trace, artifact, resolved, result, repo));
            }
        }

        for (ResolutionGroup group : groups) {
            performChecks(session, group);
        }

        for (ArtifactResult result : results) {
            ArtifactRequest request = result.getRequest();
            Artifact artifact = result.getArtifact();
            if (artifact == null) {
                if (result.getExceptions().isEmpty()) {
                    Exception exception = new ArtifactNotFoundException(request.getArtifact(), null);
                    result.addException(exception);
                }
            }
        }

        return results;
    }

    protected boolean isLocallyInstalled(LocalArtifactResult result, VersionResult version) {
        if (result.isAvailable()) {
            return true;
        }
        if (result.getFile() != null) {
            if (version.getRepository() instanceof LocalRepository) {
                return true;
            }
            return version.getRepository() == null && result.getRequest().getRepositories().isEmpty();
        }
        return false;
    }

    protected void performChecks(RepositorySystemSession session, ResolutionGroup group) {
        List<ArtifactDownload> downloads = gatherChecks(session, group);
        if (downloads.isEmpty()) {
            return;
        }

        try {
            try (RepositoryConnector connector = repositoryConnectorProvider.newRepositoryConnector(session, group.repository)) {
                connector.get(downloads, null);
            }
        } catch (NoRepositoryConnectorException e) {
            for (ArtifactDownload download : downloads) {
                download.setException(new ArtifactTransferException(download.getArtifact(), group.repository, e));
            }
        }

        evaluateChecks(group);
    }

    private List<ArtifactDownload> gatherChecks(RepositorySystemSession session, ResolutionGroup group) {
        List<ArtifactDownload> downloads = new ArrayList<>();

        for (ResolutionItem item : group.items) {
            Artifact artifact = item.artifact;
            if (item.resolved.get()) {
                // resolved in previous resolution group
                continue;
            }
            boolean snapshot = artifact.isSnapshot();
            RepositoryPolicy policy = remoteRepositoryManager.getPolicy(session, group.repository, !snapshot, snapshot);
            ArtifactDownload download = new ArtifactDownload();
            download.setArtifact(artifact);
            download.setRequestContext(item.request.getRequestContext());
            download.setTrace(item.trace);
            download.setExistenceCheck(true);
            download.setChecksumPolicy(policy.getChecksumPolicy());
            download.setRepositories(item.repository.getMirroredRepositories());
            downloads.add(download);
            item.download = download;
        }

        return downloads;
    }

    protected void evaluateChecks(ResolutionGroup group) {
        for (ResolutionItem item : group.items) {
            ArtifactDownload download = item.download;
            if (download == null) {
                continue;
            }

            Artifact artifact = download.getArtifact();
            if (download.getException() == null) {
                item.resolved.set(true);
                item.result.setRepository(group.repository);
                item.result.setArtifact(artifact);
            } else {
                item.result.addException(download.getException());
            }
        }
    }

    protected void checkOffline(RepositorySystemSession session, OfflineController offlineController, RemoteRepository repository) throws RepositoryOfflineException {
        if (session.isOffline()) {
            offlineController.checkOffline(session, repository);
        }
    }

}
