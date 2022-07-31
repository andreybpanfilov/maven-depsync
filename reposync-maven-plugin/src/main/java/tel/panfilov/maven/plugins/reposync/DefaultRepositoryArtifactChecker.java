package tel.panfilov.maven.plugins.reposync;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.impl.OfflineController;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.impl.RepositoryConnectorProvider;
import org.eclipse.aether.impl.VersionResolver;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRequest;
import org.eclipse.aether.resolution.VersionResolutionException;
import org.eclipse.aether.resolution.VersionResult;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;
import org.eclipse.aether.transfer.RepositoryOfflineException;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Objects.requireNonNull;

@Named
public class DefaultRepositoryArtifactChecker implements RepositoryArtifactChecker, Service {

    private VersionResolver versionResolver;

    private RepositoryConnectorProvider repositoryConnectorProvider;

    private RemoteRepositoryManager remoteRepositoryManager;

    private OfflineController offlineController;

    public DefaultRepositoryArtifactChecker() {
        // enables default constructor
    }

    @SuppressWarnings("checkstyle:parameternumber")
    @Inject
    DefaultRepositoryArtifactChecker(
            VersionResolver versionResolver,
            RepositoryConnectorProvider repositoryConnectorProvider,
            RemoteRepositoryManager remoteRepositoryManager,
            OfflineController offlineController
    ) {
        setVersionResolver(versionResolver);
        setRepositoryConnectorProvider(repositoryConnectorProvider);
        setRemoteRepositoryManager(remoteRepositoryManager);
        setOfflineController(offlineController);
    }

    public void initService(ServiceLocator locator) {
        setVersionResolver(locator.getService(VersionResolver.class));
        setRepositoryConnectorProvider(locator.getService(RepositoryConnectorProvider.class));
        setRemoteRepositoryManager(locator.getService(RemoteRepositoryManager.class));
        setOfflineController(locator.getService(OfflineController.class));
    }

    public DefaultRepositoryArtifactChecker setVersionResolver(VersionResolver versionResolver) {
        this.versionResolver = requireNonNull(versionResolver, "version resolver cannot be null");
        return this;
    }

    public DefaultRepositoryArtifactChecker setRepositoryConnectorProvider(RepositoryConnectorProvider repositoryConnectorProvider) {
        this.repositoryConnectorProvider = requireNonNull(repositoryConnectorProvider, "repository connector provider cannot be null");
        return this;
    }

    public DefaultRepositoryArtifactChecker setRemoteRepositoryManager(RemoteRepositoryManager remoteRepositoryManager) {
        this.remoteRepositoryManager = requireNonNull(remoteRepositoryManager, "remote repository provider cannot be null");
        return this;
    }

    public DefaultRepositoryArtifactChecker setOfflineController(OfflineController offlineController) {
        this.offlineController = requireNonNull(offlineController, "offline controller cannot be null");
        return this;
    }

    @Override
    public ArtifactResult checkArtifact(RepositorySystemSession session, ArtifactRequest request) {
        return checkArtifacts(session, Collections.singleton(request)).get(0);
    }

    @Override
    public List<ArtifactResult> checkArtifacts(RepositorySystemSession session, Collection<? extends ArtifactRequest> requests) {
        return check(session, requests);
    }

    protected List<ArtifactResult> check(RepositorySystemSession session, Collection<? extends ArtifactRequest> requests) {
        List<ArtifactResult> results = new ArrayList<>(requests.size());

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
            performDownloads(session, group);
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

    private void performDownloads(RepositorySystemSession session, ResolutionGroup group) {
        List<ArtifactDownload> downloads = gatherDownloads(session, group);
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

        evaluateDownloads(group);
    }

    private List<ArtifactDownload> gatherDownloads(RepositorySystemSession session, ResolutionGroup group) {
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

    protected void evaluateDownloads(ResolutionGroup group) {
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
