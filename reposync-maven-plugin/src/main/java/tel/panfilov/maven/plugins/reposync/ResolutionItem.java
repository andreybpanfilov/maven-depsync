package tel.panfilov.maven.plugins.reposync;

import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.impl.UpdateCheck;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.transfer.ArtifactTransferException;

import java.util.concurrent.atomic.AtomicBoolean;

public class ResolutionItem {

    final RequestTrace trace;

    final ArtifactRequest request;

    final ArtifactResult result;

    final RemoteRepository repository;

    final Artifact artifact;

    final AtomicBoolean resolved;

    ArtifactDownload download;

    UpdateCheck<Artifact, ArtifactTransferException> updateCheck;

    ResolutionItem(RequestTrace trace, Artifact artifact, AtomicBoolean resolved, ArtifactResult result, RemoteRepository repository) {
        this.trace = trace;
        this.artifact = artifact;
        this.resolved = resolved;
        this.result = result;
        this.request = result.getRequest();
        this.repository = repository;
    }

}
