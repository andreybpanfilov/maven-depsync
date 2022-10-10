package tel.panfilov.maven.plugins.reposync.component;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;

import java.util.List;

public interface DependencyCollector {

    List<Artifact> collectDependencies(RepositorySystemSession session, CollectRequest collectRequest, int depth, String scope) throws DependencyCollectionException;

}