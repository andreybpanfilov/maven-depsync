package tel.panfilov.maven.plugins.reposync.component;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

import java.util.Collection;
import java.util.List;

public interface RepositoryArtifactChecker {

    ArtifactResult checkArtifact(RepositorySystemSession session, boolean checkLocal, ArtifactRequest request) throws ArtifactResolutionException;

    List<ArtifactResult> checkArtifacts(RepositorySystemSession session, boolean checkLocal, Collection<? extends ArtifactRequest> requests) throws ArtifactResolutionException;

}
