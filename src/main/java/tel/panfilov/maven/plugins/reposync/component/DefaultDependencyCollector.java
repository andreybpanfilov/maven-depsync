package tel.panfilov.maven.plugins.reposync.component;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.resolution.ArtifactDescriptorPolicy;
import tel.panfilov.maven.plugins.reposync.selector.AndDependencySelector;
import tel.panfilov.maven.plugins.reposync.selector.ExclusionDependencySelector;
import tel.panfilov.maven.plugins.reposync.selector.OptionalDependencySelector;
import tel.panfilov.maven.plugins.reposync.selector.ScopeDependencySelector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static tel.panfilov.maven.plugins.reposync.Utils.getId;

@Component(role = DependencyCollector.class)
public class DefaultDependencyCollector implements DependencyCollector {

    @Requirement
    protected RepositorySystem repoSystem;

    @Requirement
    protected ScopeMediator scopeMediator;

    @Override
    public List<Artifact> collectDependencies(RepositorySystemSession session, CollectRequest collectRequest, int depth, String scope) throws DependencyCollectionException {
        DependencySelector selector = new AndDependencySelector(
                new OptionalDependencySelector(depth),
                new ScopeDependencySelector(depth, null, scopeMediator.negate(scope)),
                new ExclusionDependencySelector()
        );
        DefaultRepositorySystemSession newSession = new DefaultRepositorySystemSession(session)
                .setArtifactDescriptorPolicy((s, r) -> ArtifactDescriptorPolicy.IGNORE_ERRORS)
                .setDependencySelector(selector)
                .setIgnoreArtifactDescriptorRepositories(true);
        CollectResult collectResult = repoSystem.collectDependencies(newSession, collectRequest);
        return extractArtifacts(collectResult);
    }

    protected List<Artifact> extractArtifacts(CollectResult collectResult) {
        CollectAllDependenciesVisitor visitor = new CollectAllDependenciesVisitor();
        collectResult.getRoot().accept(visitor);
        List<Artifact> artifacts = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Artifact artifact : visitor.getArtifacts()) {
            if (seen.add(getId(artifact))) {
                artifacts.add(artifact);
            }
        }
        Artifact rootArtifact = collectResult.getRoot().getArtifact();
        if (seen.add(getId(rootArtifact))) {
            artifacts.add(rootArtifact);
        }
        return artifacts;
    }

    static class CollectAllDependenciesVisitor implements DependencyVisitor {

        private boolean root = true;
        private Set<Artifact> artifacts = new HashSet<>();

        @Override
        public boolean visitEnter(DependencyNode node) {
            if (root) {
                root = false;
                return true;
            }
            return artifacts.add(node.getArtifact());
        }

        @Override
        public boolean visitLeave(DependencyNode node) {
            return true;
        }

        public Set<Artifact> getArtifacts() {
            return artifacts;
        }
    }
}
